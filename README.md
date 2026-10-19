# Scrollia

A free Android ebook reader powered by [Project Gutenberg](https://gutenberg.org).

Named for the **scroll** — the form that carried the written word for millennia before the printed book.

## Features

- Browse and download 70,000+ free ebooks from Project Gutenberg
- Built-in EPUB reader with customizable typography
- Import your own EPUB books
- Material You design with light/dark themes
- Reading progress tracking
- Multi-language support

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

## Credits

Scrollia is a fork of [Myne](https://github.com/Pool-Of-Tears/Myne) by Starry Shivam, licensed under Apache 2.0.

## License

```
Copyright 2024-Present Scrollia

Licensed under the Apache License, Version 2.0
```
