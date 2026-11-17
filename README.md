# SimpleBoard

**A customizable, offline-first Android keyboard with local learning, predictive typing, and on-device voice recognition.**

SimpleBoard puts control over typing, appearance, and personal language data in your hands. It provides an English QWERTY keyboard, suggestions and autocorrect, optional neural predictions, a review-before-applying grammar tool, configurable long-press shortcuts, themes, and offline speech recognition. The keyboard itself does not request Android's `INTERNET` permission.

## Features

### Typing and language assistance

- **Suggestions and autocorrect:** English word suggestions, next-word predictions, contraction and proper-noun fixes, and deliberately conservative automatic spelling corrections.
- **Personal dictionary:** Add trusted words, define explicit text replacements, remove learned words, and manage correction rules.
- **On-device learning:** Optional learning of word usage and short word sequences, with controls for confidence and how often a new word must be typed before it is learned.
- **Experimental neural predictions:** A bundled ONNX model can improve context-aware suggestions on supported devices. The lightweight prediction engine remains available when the neural model is disabled, slow, or unsupported.
- **Offline grammar review:** A rule-based English grammar and punctuation checker can suggest changes to selected or nearby text. You review the proposed result before applying it.
- **Typing conveniences:** Auto-capitalization, double-space period, optional number row, key previews, vibration, and key sounds.

### Layout and customization

- QWERTY, symbols, numeric, phone, and emoji keyboard layouts.
- Adjustable keyboard height, letter size, key gaps, key corner radius, and borders.
- Adjustable spacebar height and Enter-key width, optional emoji key, and configurable period-key placement.
- Left- or right-handed compact keyboard mode with adjustable width.
- Custom long-press popups per key, including accented characters, text, emoji, and built-in actions such as paste, cursor movement, settings, and voice typing.
- Custom colors, key opacity, and local keyboard background images.
- Theme import/export using `.simpleboardtheme` files, plus compatible color import/export for HeliBoard JSON.

### Offline voice typing

- **Bundled English Vosk model:** On-device English speech recognition, with partial results during dictation.
- **Optional Whisper models:** Offline transcription using separately downloaded Whisper models, including multilingual transcription. Supported model choices in the app include base, small, and turbo Q5.
- **Local processing:** Audio is processed on the device rather than sent to an external speech service. Whisper records first and transcribes after you tap **Done**; **Cancel** discards the session.
- **Model management:** Select or remove installed models from Voice settings. The app opens model-download links in your browser rather than downloading files itself.

## Privacy

SimpleBoard is designed to keep keyboard processing local:

- The Android manifest does **not** request the `INTERNET` permission.
- Learned words, correction rules, and short word/phrase statistics are stored in a **SQLCipher-encrypted** local database. Its randomly generated encryption key is protected using **Android Keystore**.
- The app is designed not to save raw keystroke logs or full-message history. Temporary neural contexts and unfinished voice transcripts are discarded as input changes or sessions end.
- Password fields, email and URL fields, and fields that prohibit personalized learning are excluded from language learning.
- Local learning can be switched off, and personal dictionary data can be cleared in **Privacy & behavior**.
- Android application backup is disabled.

**Scope:** Theme, shortcut, and ordinary app settings are kept in private app storage but are not covered by the encrypted learning database. Root access or a compromised operating system can undermine device-local protections. Opening external model links in a browser is separate from SimpleBoard's offline keyboard processing.

## Getting started

After installing a compatible APK:

1. Open **SimpleBoard**.
2. Tap **Enable / select SimpleBoard** to open Android's keyboard settings.
3. Enable SimpleBoard and select it as your input method.
4. Return to the app to customize typing, layout, popups, appearance, voice typing, and privacy preferences.

The keyboard supports **Android 7.0 (API 24) or later**. The included Android build configuration targets `arm64-v8a` and `armeabi-v7a` devices.

### Using optional Whisper models

1. Open **SimpleBoard → Voice typing** and grant microphone permission if needed.
2. Set up the dedicated folder `Documents/SimpleBoard/VoiceModels` on internal storage.
3. Open one of the model links from Voice settings in a browser, and save the completed `.bin` file in that folder **without renaming it**.
4. Return to Voice settings, tap **Scan voice-model folder**, and choose the model.

Whisper model files are checked against expected sizes and SHA-256 hashes before being installed into the app's private storage. Larger models require more memory and may transcribe slowly on some phones. The bundled Vosk model does not require an additional Whisper download.

Key third-party components include **Vosk**, **whisper.cpp**, **ONNX Runtime**, and **SQLCipher**. Their respective licenses and model terms are separate from this repository.

Uses [No Fault License](https://github.com/ZoKLav/no_fault_license) for all non-third party code
