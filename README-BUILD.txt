# AlHaramInviter Android — fixed GitHub build

This project is configured to build on GitHub Actions without requiring Gradle Wrapper files.

Build environment:
- JDK 17
- Android Gradle Plugin 8.6.1
- Gradle 8.10.2
- Android SDK 35 / Build Tools 35.0.0

Important:
- Keep the `.github/workflows/build-apk.yml` path exactly as shown.
- Upload the extracted project contents to the repository root.
- Do not upload the ZIP itself as the project source.
