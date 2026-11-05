<p align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" alt="Scrollia icon" width="128">
</p>

# Scrollia

A free Android ebook reader powered by [Project Gutenberg](https://gutenberg.org).

Named for the **scroll** — the form that carried the written word for millennia before the printed book.

## Features

- Browse and download a curated catalog of public-domain classics
- Built-in EPUB reader with customizable typography
- Import your own EPUB books
- Paper, sepia, night, and AMOLED reading themes
- Reading progress tracking
- English and Spanish interface and catalog

## Tech Stack

- 100% Kotlin
- Jetpack Compose + Material 3
- Dagger Hilt (DI)
- Room (database)
- OkHttp (networking)
- Coil (image loading)

## Building

```bash
git clone https://github.com/scrollia-org/scrollia-android.git
cd scrollia-android
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew assembleDebug
```

## Release signing

Release builds read `keystore.properties` from the repository root, which is
gitignored. Keep the upload keystore out of version control (any path works;
relative paths resolve from the repository root) and back both up securely.
The properties file uses this format:

```properties
storeFile=/path/to/scrollia-upload.keystore
storePassword=<upload-store-password>
keyAlias=upload
keyPassword=<upload-key-password>
```

Build the Play App Bundle with `./gradlew bundleRelease`. Never use the Android
debug certificate for a Play release.

## Credits

Scrollia is a fork of [Myne](https://github.com/Pool-Of-Tears/Myne) by Starry Shivam, licensed under Apache 2.0.

## License

```
Copyright 2026-Present Scrollia contributors

Licensed under the Apache License, Version 2.0
```
