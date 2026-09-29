// Synthetic regression fixtures, not a claim about Instagram's current logged-in DOM.
const {chromium} = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const script = fs.readFileSync(path.join(__dirname, '../src/main/assets/minimal.js'), 'utf8');
(async () => {
  const browser = await chromium.launch(process.env.CHROME_PATH ? {executablePath: process.env.CHROME_PATH} : {});
  try {
    const page = await browser.newPage({viewport: {width: 360, height: 740}});
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    const nav = '<nav><a href="/">Home</a><a href="/explore/">Explore</a><a href="/direct/inbox/">DM</a><a href="/me/" aria-label="Profile">Profile</a></nav>';
    async function load(mode, url, html) {
      await page.unrouteAll();
      await page.route('**/*', route => route.fulfill({contentType: 'text/html', body: html}));
      await page.goto('https://www.instagram.com' + url);
      await page.evaluate(c => window.__igConfig = c, {mode, username: 'me'});
      await page.evaluate(script);
    }
    await load('STORY', '/', nav + '<main><div role="list" style="height:100px;width:340px" id="tray"><a href="/stories/a/1/">A</a><a href="/stories/b/2/">B</a></div><article id="feed">Feed</article><aside id="recommend">Recommended</aside></main>');
    assert.equal(await page.evaluate(() => window.__igMinimal.status().state), 'ready');
    assert.equal(await page.locator('#tray').isVisible(), true);
    assert.equal(await page.locator('#feed').isVisible(), false);
    assert.equal(await page.locator('#recommend').isVisible(), false);
    assert.equal(await page.locator('nav').isVisible(), false);
    await page.evaluate(() => document.querySelector('main').insertAdjacentHTML('beforeend', '<div id="late">Late feed</div>'));
    assert.equal(await page.locator('#late').isVisible(), false);
    await page.evaluate(() => { history.pushState({}, '', '/stories/a/1/'); document.body.innerHTML = '<div id="viewer">Story viewer<button>Next</button></div>'; });
    assert.equal(await page.locator('#viewer').isVisible(), true);
    await page.evaluate(() => history.pushState({}, '', '/'));
    assert.equal(await page.evaluate(() => window.__igMinimal.status().state), 'missing-story');
    assert.equal(await page.locator('#viewer').isVisible(), false);
    await load('STORY', '/', '<main><article id="feed">Unknown home layout</article></main>');
    assert.equal(await page.evaluate(() => window.__igMinimal.status().state), 'missing-story');
    assert.equal(await page.locator('#feed').isVisible(), false);
    await load('DM', '/direct/inbox/', nav + '<main id="messages"><input><a href="/direct/t/123/">Chat</a><a href="/reels/123/" id="reel">Shared reel</a></main>');
    assert.equal(await page.locator('#messages').isVisible(), true);
    assert.equal(await page.locator('nav').isVisible(), false);
    assert.equal(await page.locator('#reel').isVisible(), false);
    assert.equal(await page.evaluate(() => window.__igMinimal.status().username), 'me');
    await page.evaluate(() => history.pushState({}, '', '/explore/'));
    assert.equal(await page.evaluate(() => window.__igMinimal.status().state), 'blocked');
    assert.equal(await page.locator('#messages').isVisible(), false);
    await load('STORY', '/accounts/login/', '<form><input id="login"></form>');
    assert.equal(await page.locator('#login').isVisible(), true);
    await load('PROFILE', '/me/', nav + '<main id="profile">My profile<a href="/friend/" id="friend">Friend</a></main>');
    assert.equal(await page.locator('#profile').isVisible(), true);
    assert.equal(await page.locator('#friend').isVisible(), false);
    assert.deepEqual(errors, []);
    console.log('PASS: story isolation, late feed, viewer/close, unknown layout, DM, profile, auth and SPA navigation');
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exitCode = 1; });
