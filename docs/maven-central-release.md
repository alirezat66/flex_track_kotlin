# Maven Central release

FlexTrack Kotlin publishes as `io.github.alirezat66:flextrack` through the
Sonatype Central Publisher Portal. Credentials and signing material must never
be committed to this repository.

## One-time publisher setup

1. Sign in to <https://central.sonatype.com> with the `alirezat66` GitHub
   account and confirm that `io.github.alirezat66` is verified under
   **View Namespaces**.
2. Generate a Central Portal user token. Store its generated username and
   password as GitHub Actions secrets `MAVEN_CENTRAL_USERNAME` and
   `MAVEN_CENTRAL_PASSWORD`.
3. Create a password-protected OpenPGP signing key and publish its public key to
   a supported keyserver.
4. Export the complete ASCII-armored private key to the `SIGNING_KEY` GitHub
   Actions secret and store its passphrase as `SIGNING_PASSWORD`.

## Release

1. Update the project version and changelog.
2. Merge the release branch into `main` and ensure CI passes.
3. Create and push a matching `vMAJOR.MINOR.PATCH` tag.
4. The release workflow verifies the tag, runs tests/lint/release assembly,
   signs every publication, uploads it, and requests automatic release.
5. Confirm the deployment reaches `PUBLISHED` in the Central Portal, then test
   resolution from a clean project using only `mavenCentral()`.
