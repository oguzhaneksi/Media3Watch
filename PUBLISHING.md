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
    * The base64 representation of your GPG private key.
    * If you haven't created one yet, run `gpg --full-generate-key` (Use RSA, 4096 bits, no expiry).
    * Make sure to upload your public key to a keyserver: `gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID`.
    * Export the private key as base64 to put in this secret (avoid line wrapping in the output):
      ```bash
      # Linux (GNU coreutils base64)
      gpg --armor --export-secret-keys YOUR_KEY_ID | base64 -w 0

      # macOS (BSD base64, no wrapping by default)
      gpg --armor --export-secret-keys YOUR_KEY_ID | base64
      ```

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
