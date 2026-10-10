# LockTodo distribution signing

GitHub Actions builds LockTodo as an unsigned release APK. Do not distribute that
artifact directly.

The final APK must be signed outside GitHub with the persistent LockTodo signing key
stored privately in the user's ChatGPT Library:

- /LockTodo/signing/locktodo-stable-v1.jks
- /LockTodo/signing/signing.properties
- Alias: locktodo
- Certificate SHA-256:
  2B:19:31:AB:0E:8E:C1:83:6D:C6:97:15:31:A5:44:2C:36:16:D1:ED:DE:5E:D5:19:B3:6F:52:AE:9D:AC:65:60

Never generate a replacement key for an update. If the persistent key cannot be
retrieved, stop instead of distributing an APK signed by another key.

Transition:
- v0.9.6 uses the previous ephemeral debug key.
- v0.9.7 is the first version using the persistent LockTodo key.
- v0.9.7 therefore requires one final uninstall/reinstall over v0.9.6.
- v0.9.8 and later can update v0.9.7 in place when signed with this same key.
