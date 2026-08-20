# Release signing

Vita releases use a dedicated key that is unrelated to any other application.

- Store `VITA_KEYSTORE_BASE64`, `VITA_KEYSTORE_PASSWORD`, `VITA_KEY_ALIAS`, and `VITA_KEY_PASSWORD` only in the GitHub `release` Environment.
- Keep a user-controlled encrypted offline backup of the keystore and recovery material outside the repository.
- Never commit a keystore, print secret values, or reuse another application's signing key.
- The release workflow verifies the APK signature, package name, permissions, contents, checksum, SBOMs, and provenance before publishing.

If the signing key or password is lost, existing installations cannot be upgraded. Test restoration from the offline backup before the first public release.
