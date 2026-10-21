# Vibe & Sound signing

The application ID is unchanged: `com.vibedrain.app`.
The visible application name changed from Vibe Drain to Vibe & Sound in v0.8.0.

v0.8.0 uses a dedicated, persistent release key saved privately to the
user's ChatGPT Library:

- /VibeSound/signing/vibesound-stable-v1.jks
- /VibeSound/signing/signing.properties
- Alias: vibesound
- Certificate SHA-256: A0:54:CD:E0:F3:2F:2B:99:18:40:38:25:1E:AE:A1:1A:AC:4E:95:E1:4D:5A:6F:07:75:96:F4:41:EA:D5:11:7F

GitHub Actions builds unsigned release APKs. Align and sign only with the
persistent key using Android zipalign and apksigner, with v2/v3 signatures.
Always verify the APK signing certificate before delivering it.
If the key is missing, stop rather than creating another key.

Older Vibe Drain builds were debug-signed with an ephemeral signing key.
Users may need to uninstall that old APK once to install v0.8.0; this
will erase app data and any previous user settings. After v0.8.0 is
installed, subsequent versions signed with this same key can update in place.

Microphone monitoring must be manually started from the visible activity.
It cannot reliably relaunch automatically on device reboot on Android 14+.
No microphone samples are persisted or sent over the network.
