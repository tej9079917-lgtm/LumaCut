# LumaCut

**LumaCut** is a native Android multilayer video editor built with Kotlin, Jetpack Compose, CameraX, and Jetpack Media3.

## Download APK

Anyone can download the latest installable Android APK directly from the **[Releases](../../releases/latest)** page:

1. Open the **[Latest Release](../../releases/latest)** on this repository.
2. Under **Assets**, tap **`LumaCut.apk`** to download it.
3. Open the downloaded file on your Android device (Android 7.0 / API 24+) to install.

## Automatic APK Publishing

This repository includes a GitHub Actions workflow (`.github/workflows/release-apk.yml`) that automatically builds `LumaCut.apk` and publishes it to **GitHub Releases** on every push to `main` (or when triggered manually from the **Actions** tab).

### Optional Play-Store Release Signing
By default, GitHub Actions builds a ready-to-install signed APK with zero extra setup. To build a production release-signed APK instead, add these three secrets under **Settings → Secrets and variables → Actions**:
- `KEYSTORE_BASE64`: Base64-encoded `.jks` upload keystore (`base64 -w 0 my-upload-key.jks`)
- `STORE_PASSWORD`: Keystore password
- `KEY_PASSWORD`: Key password (alias: `upload`)
