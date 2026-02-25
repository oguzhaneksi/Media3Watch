# Maven Central Publishing Guide

This project is configured to be automatically published to Maven Central Portal using GitHub Actions.

## First-Time setup for GitHub Actions Secrets

To make the existing `.github/workflows/publish-sdk.yml` pipeline work, you need to configure the following **Repository Secrets** in your GitHub repository settings (`Settings > Secrets and variables > Actions`):

1. **`SONATYPE_USERNAME_TOKEN`**
    * Get this from your [Sonatype Central Portal](https://central.sonatype.com/) account.
    * Click on your profile at top right -> `View Account` -> `Generate User Token`.
    * Use the **Username** part of the generated token.

2. **`SONATYPE_PASSWORD_TOKEN`**
    * The **Password** part of the generated token from the previous step.

3. **`OSSRH_GPG_SECRET_KEY`**
    * **IMPORTANT WARNING for macOS/brew users:** GnuPG v2.3+ encrypts private keys using an "AEAD" algorithm which Gradle's crypto library (BouncyCastle) **cannot read**, resulting in `Could not read PGP secret key` errors.
    * **The Fix:** You must temporarily remove the passphrase from your private key before exporting it for GitHub Actions.
    * 1. Run `gpg --edit-key YOUR_KEY_ID`
    * 2. Type `passwd`. Enter your current passphrase. When asked for the new passphrase, **leave it blank** and confirm. Type `save` to exit.
    * 3. Export the unencrypted key to base64:
      ```bash
      # Linux (GNU coreutils base64)
      gpg --armor --export-secret-keys YOUR_KEY_ID | base64 -w 0

      # macOS (BSD base64)
      gpg --armor --export-secret-keys YOUR_KEY_ID | base64
      ```
    * 4. Once exported, you can run `gpg --edit-key YOUR_KEY_ID` and use `passwd` again to put your passphrase back for local security.

4. **`OSSRH_GPG_SECRET_KEY_ID`**
    * The last 8 characters (or full ID) of your GPG key. You can find it with `gpg --list-keys`.

5. **`OSSRH_GPG_SECRET_KEY_PASSWORD`**
    * The passphrase you set when creating the GPG key.

## Triggering a Release

Once the secrets are set up, doing a release is completely automated:

1. Update the SDK version by bumping the `VERSION_NAME` project property (the source of truth used by `android/sdk/build.gradle.kts`, typically defined in `gradle.properties`; e.g. from `1.0.0-alpha01` to `1.0.0-alpha02`).
2. Commit and push the version bump to the `main` branch.
3. Open your GitHub repository and go to the **Releases** tab.
4. Click **Draft a new release**.
5. Create a new tag that matches the new `VERSION_NAME` (e.g. `v1.0.0-alpha02`), and fill in the release title and description.
6. Click **Publish release**.

The GitHub Action `Publish SDK to Maven Central` will automatically start. Once it completes successfully, your SDK will be available on Maven Central (it usually takes 15-30 minutes for new releases to sync visually to the Maven Central search UI).
