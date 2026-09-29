(() => {
  'use strict';
  if (window.top !== window) return;
  if (window.__igMinimal) { window.__igMinimal.refresh(); return; }
  const auth = p => /^\/(accounts\/(login|logout|onetap|signup|password|two_factor_authentication|confirm_email)|challenge|checkpoint|two_factor|oauth|consent)(\/.*)?$/.test(p);
  const validUser = s => /^[a-z0-9._]{1,30}$/i.test(s || '') && s !== '.' && s !== '..';
  const config = () => window.__igConfig || {mode: 'DM', username: ''};
  function allowed(value) {
    try {
      const u = new URL(value, location.href), p = u.pathname, c = config();
      if (u.protocol !== 'https:' || !['instagram.com', 'www.instagram.com'].includes(u.hostname)
          || u.username || u.password || u.port || p.includes('%')) return false;
      if (auth(p)) return true;
      if (c.mode === 'DM') return p.startsWith('/direct/');
      if (c.mode === 'STORY') return p === '/' || p.startsWith('/stories/');
      return c.mode === 'PROFILE' && validUser(c.username)
        && [('/' + c.username).toLowerCase(), ('/' + c.username + '/').toLowerCase()].includes(p.toLowerCase());
    } catch (_) { return false; }
  }
  let state = 'loading', username = '', scheduled = false, blockedNavigation = false;
  const hidden = new Set();
  const style = document.createElement('style');
  style.textContent = `html:not([data-ig-ready]) {visibility:hidden!important}
    [data-ig-hidden] {display:none!important}
    html[data-ig-home] article, html[data-ig-home] [role="feed"] {display:none!important}`;
  function hide(el) { if (el && !el.hasAttribute('data-ig-hidden')) { el.setAttribute('data-ig-hidden', ''); hidden.add(el); } }
  function reset() { for (const el of hidden) el.removeAttribute('data-ig-hidden'); hidden.clear(); }
  function discoverProfile() {
    for (const a of document.querySelectorAll('a[href]')) {
      const label = (a.getAttribute('aria-label') || a.textContent || '').trim();
      const icon = a.querySelector('[aria-label="Profile"],[aria-label="프로필"]');
      if (!/^(Profile|프로필)$/i.test(label) && !icon) continue;
      const u = new URL(a.href, location.href), match = u.pathname.match(/^\/([a-z0-9._]{1,30})\/?$/i);
      if (u.origin === location.origin && match && validUser(match[1])) return match[1];
    }
    return '';
  }
  function globalNavigation() {
    // Require multiple global destinations; do not remove DM conversation controls.
    for (const link of document.querySelectorAll('a[href="/explore/"],a[href="/reels/"],a[href="/reels"]')) {
      let node = link.parentElement;
      for (let depth = 0; node && depth < 6; depth++, node = node.parentElement) {
        if (node === document.body || node.tagName === 'MAIN' || node.querySelector('main,[role="main"],article')) break;
        if (node.querySelector('a[href="/"]') && node.querySelector('a[href^="/direct/"]')) { hide(node); break; }
      }
    }
  }
  function findTray() {
    const candidates = [...document.querySelectorAll('[aria-label="Stories"],[aria-label="스토리"], [role="list"]')];
    for (const a of document.querySelectorAll('a[href^="/stories/"]')) {
      let parent = a.parentElement;
      for (let i = 0; parent && i < 5; i++, parent = parent.parentElement) candidates.push(parent);
    }
    return candidates.find(el => {
      if (el === document.body || el.tagName === 'MAIN' || el.querySelector('article,[role="feed"],main')) return false;
      const rect = el.getBoundingClientRect();
      const named = /^(Stories|스토리)$/i.test(el.getAttribute('aria-label') || '');
      const links = [...el.querySelectorAll('a[href]')];
      const stories = links.filter(a => new URL(a.href, location.href).pathname.startsWith('/stories/'));
      // Unknown button-only layouts stay hidden unless Instagram labels their story region.
      return rect.height > 0 && rect.height <= 300 && rect.width >= 160
        && (named || stories.length >= 2) && links.every(a => {
          const p = new URL(a.href, location.href).pathname;
          return p.startsWith('/stories/') || (username && p === '/' + username + '/');
        });
    });
  }
  function isolate(tray) {
    let child = tray;
    while (child && child !== document.body) {
      const parent = child.parentElement;
      if (!parent) return false;
      for (const sibling of parent.children) if (sibling !== child && sibling !== style) hide(sibling);
      child = parent;
    }
    return child === document.body;
  }
  const observer = new MutationObserver(() => schedule());
  function refresh() {
    if (!document.documentElement) return;
    observer.disconnect();
    try {
      if (!style.isConnected) document.documentElement.appendChild(style);
      document.documentElement.removeAttribute('data-ig-ready');
      document.documentElement.toggleAttribute('data-ig-home', config().mode === 'STORY' && location.pathname === '/');
      reset();
      if (blockedNavigation || !allowed(location.href)) { state = 'blocked'; return; }
      if (auth(location.pathname)) { username = ''; state = 'ready'; }
      else {
        username = discoverProfile() || username;
        globalNavigation();
        for (const a of document.querySelectorAll('a[href]')) if (!allowed(a.href)) hide(a);
        if (config().mode === 'STORY' && location.pathname === '/') {
          const tray = findTray();
          state = tray && isolate(tray) ? 'ready' : 'missing-story';
        } else state = 'ready';
      }
      if (state === 'ready') document.documentElement.setAttribute('data-ig-ready', '');
    } finally {
      observer.observe(document.documentElement, {childList: true, subtree: true, attributes: true,
        attributeFilter: ['href', 'aria-label', 'role']});
    }
  }
  function schedule() {
    if (scheduled) return;
    scheduled = true;
    queueMicrotask(() => { scheduled = false; refresh(); });
  }
  for (const name of ['pushState', 'replaceState']) {
    const original = history[name];
    history[name] = function(state, unused, url) {
      if (url != null && !allowed(url)) { blockedNavigation = true; refresh(); return; }
      const result = original.apply(this, arguments); refresh(); return result;
    };
  }
  addEventListener('popstate', refresh);
  document.addEventListener('click', event => {
    const link = event.target.closest && event.target.closest('a[href]');
    if (link && !allowed(link.href)) { event.preventDefault(); event.stopImmediatePropagation(); }
  }, true);
  document.addEventListener('submit', event => {
    if (!allowed(event.target.action || location.href)) { event.preventDefault(); event.stopImmediatePropagation(); }
  }, true);
  window.__igMinimal = {refresh, allowed, status: () => ({state, username, auth: auth(location.pathname)})};
  document.addEventListener('DOMContentLoaded', refresh);
  refresh();
})();
