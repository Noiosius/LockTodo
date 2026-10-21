# Day Count signing

Day Count v0.4.0 (versionCode 4) is the first version signed with its
dedicated, persistent Android release certificate.

GitHub Actions builds an unsigned release APK. For delivery, always zipalign
the unsigned APK, then sign it with Android apksigner (v2/v3 enabled) using
the matching, private key stored in the user's ChatGPT Library:

- /DayCount/signing/daycount-stable-v1.jks
- /DayCount/signing/signing.properties
- Alias: daycount
- Certificate SHA-256:
  31:9B:7D:0A:0A:22:B7:45:EE:13:FB:AB:28:FB:58:EF:6E:AF:5A:14:64:92:4A:91:A5:ED:4A:FA:2B:7B:F9:4C

Do not use a newly generated debug or release key if the persistent key is
unavailable. Verify the output with apksigner verify --print-certs before release.

Day Count v0.3 used ephemeral debug signing. Existing v0.3 installations
may require one uninstall/reinstall to switch to v0.4. Once v0.4 is installed,
subsequent versions signed with the same key can update in place.
