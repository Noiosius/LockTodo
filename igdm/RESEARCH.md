# IG DM v0.4 candidate — 2026-09-29

## Decision

Keep WebView for the restricted interface. Use native DM / STORY / PROFILE tabs and explicit system-bar, cutout and keyboard insets. A browser wrapper cannot both inherit Chrome's push infrastructure and arbitrarily remove Instagram's interface. This candidate is not a verified replacement for Instagram notifications.

The v0.3 baseline is commit `48530174a537a2e82afb48f21a8b230c4ad90ad0`. It used target SDK 34, a full-screen WebView, and link hiding injected only after DM page loads. It did not implement notification delivery or SPA route enforcement. v0.4 targets SDK 35, explicitly applies insets, separates navigation policy from UI adaptation, and installs a document-start adapter when supported. On older WebViews the native loading surface covers the page until post-load filtering succeeds.

## Notification alternatives

| Method | Assessment for a personal account with Instagram uninstalled |
| --- | --- |
| WebView + Service Worker | Worker support is not Push API support. Android WebView does not expose the standard PushManager/Notification capabilities needed for browser web push. No background delivery implemented. |
| Notification API shim | Can show an Android notification for an event received while running. Does not supply a missing DM event source or wake a killed process. Not a background solution. |
| Chrome / installed Instagram PWA + Web Push | Worth testing first. Browser push can work without the native Instagram app, **if Instagram currently offers and delivers DM web push for this account/browser**. Site permission, active subscription and actual delivery must all be tested. Installing a PWA alone proves none of these. Notifications belong to the browser/PWA and normally open its unrestricted website. |
| Chrome push + NotificationListenerService relay | Conditional experimental option: Chrome receives the event; an opt-in native listener filters verified Instagram-origin notifications and republishes with an IG DM PendingIntent. This still depends on Chrome and is not independent push. First capture a redacted on-device notification schema and validate origin/category filtering, deduplication and lock-screen privacy. Do not match arbitrary message body text or relay all Chrome notifications. Not implemented without that evidence. |
| Custom Tabs | Browser session and web capabilities, but no arbitrary DOM control. Unsuitable as the primary restricted surface. |
| TWA | Requires the website's cooperation through Digital Asset Links; we do not control instagram.com. The host cannot directly access the page DOM/cookies. Not a solution for filtering this third-party site. |
| WorkManager polling | Periodic interval minimum is 15 minutes and actual execution can be delayed. Requires a reliable authenticated data source, which the personal-account public API does not provide. WebView/Chrome cookies are separate. Not instant delivery. |
| Foreground service + web internal WebSocket/polling | An experimental reverse-engineered client could maintain an authenticated connection, but internal protocols/session expiry, Doze/network suspension, foreground-service restrictions, battery cost and account challenges remain. A persistent notification does not guarantee an uninterrupted network connection. Not implemented or verified. |
| Server-side private session + FCM | Can technically bridge a private event source to push, but requires storing an Instagram session on a server and maintaining undocumented protocols. Considerable maintenance and account/session exposure; not selected. |
| Official Instagram Messaging API + webhook + FCM | Documented route for professional accounts. Not a drop-in personal-account client; account conversion changes the premise and supported conversation types differ (including no group messaging in the referenced Send API). |
| Listener forwarding the official Instagram app | Depends on the app the user wants removed; fails the stated requirement. |

## S21 experiment (not yet executed)

Samsung documents One UI 7 / Android 15 for the S21 family. Exact installed build, carrier/region and Android System WebView/Chrome versions must be recorded on the actual phone; “latest One UI” should not be interpreted as the latest version on newer Galaxy models.

1. Install the candidate as an update. Check login and 2FA, DM sending, keyboard open/close, gesture navigation and three-button navigation. Check display/font scaling and restored activity after background/process recreation. Credentials are entered by the user in Instagram.
2. Check STORY with available stories and no available stories. Open, advance, reply and close the viewer. Check that loading, errors and layout changes never expose feed. Check PROFILE resolves the logged-in account. The current adapter recognizes English/Korean profile labels; an unrecognized account link deliberately shows an explanation rather than guessing another person's profile.
3. On Chrome Android, manually log into instagram.com and enable website notifications if Instagram offers the prompt/settings. First establish a baseline with no relay. PWA installation is optional and is a separate test variable.
4. With the native Instagram app absent, have a consenting sender send test DMs while the browser is foreground, background and screen-locked. Repeat after 5/30/60 minutes, browser-tab closure, reboot and Wi-Fi/mobile-data changes. Distinguish tab closure from Android force-stop: force-stop is not a normal background-delivery test.
5. Record sent/received timestamps and the package/site identity shown on each notification, without copying message bodies or cookies. Test ordinary messages, requests and group DMs separately. No notification subscription or no actual delivery means the relay cannot help.
6. Only if baseline succeeds, implement the opt-in Chrome relay with strict site identity, deduplication, self-loop prevention, generic lock-screen content and an IG DM inbox launch action. Android 13+ notification permission and explicit notification-access authorization would be needed. Test Samsung sleeping/deep-sleeping app settings without assuming a battery exemption guarantees delivery.

Long-press DM in v0.4 to inspect the current WebView's Service Worker / PushManager / Notification feature presence. This is capability detection, not a push-delivery test.

## Scope and limitations

Only igdm source and a dedicated candidate build workflow change. Other application modules and the existing main-branch build workflow are untouched. APK signing uses the existing cached key and fails if the key is missing; it never silently generates a replacement identity.

STORY preserves recognized story DOM in place so Instagram's handlers remain attached; siblings are hidden rather than copied. If a tray is missing/unrecognized, the page stays hidden. The detection is deliberately conservative and must be tuned against the actual logged-in DOM. Synthetic tests do not establish Instagram compatibility. No private API endpoints, session export, JavaScript-to-Android bridge, notification listener or background poller are introduced. Media upload/camera/microphone support was not added; existing DM text and web story interactions require device testing.

This is a distraction-reduction UI, not a security boundary against a hostile website. Instagram may change DOM, routing, labels or behavior. Web resources can still be fetched even when corresponding UI is hidden. PROFILE allows only the detected account's main profile, not other profiles or post/reel detail routes. Feed/reel content shared in a DM is blocked when it attempts navigation; this does not classify every image/video already embedded in a conversation or story.

## Sources checked

- Meta's official Instagram Send API collection: https://www.postman.com/meta/instagram/folder/uxudqu0/send-api
- Android WebView: https://developer.android.com/develop/ui/views/layout/webapps/webview
- MDN maintained compatibility data, PushManager: https://github.com/mdn/browser-compat-data/blob/main/api/PushManager.json
- MDN maintained compatibility data, Notification: https://github.com/mdn/browser-compat-data/blob/main/api/Notification.json
- Push API and subscription model: https://developer.mozilla.org/en-US/docs/Web/API/Push_API
- TWA ownership and host limitations: https://developer.chrome.com/docs/android/trusted-web-activity
- Android embedded web options: https://developer.android.com/develop/ui/views/layout/webapps/in-app-browsing-embedded-web
- WorkManager interval and timing: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- Notification listener: https://developer.android.com/reference/android/service/notification/NotificationListenerService
- Android edge-to-edge and insets: https://developer.android.com/develop/ui/views/layout/edge-to-edge
- Samsung S21 update record (regional model): https://doc.samsungmobile.com/SCG09/019349210720/eng.html

The Chrome-relay proposal is an architectural inference from browser push and Android notification-listener capabilities, not evidence that Instagram currently sends this user's DM web push. No authenticated Instagram session or connected Galaxy S21 was available during this work.
