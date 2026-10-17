# SimpleBoard

**SimpleBoard 0.3.1 — Usability Update**  
**Android 7.0+** · **Offline by design** · **No `INTERNET` permission**

Gboard can keep Google's ecosystem. Your keyboard handles your passwords, private conversations, searches, and everything else you type. That's a terrible place for corporate data harvesting, advertising incentives, and the expectation that every useful feature must be connected to a company server.

**SimpleBoard is an Android keyboard built to work for the person typing, not the company watching.** No Google services, account, ads, analytics SDK, cloud sync, or Internet permission. Autocorrect, word learning, predictions, and voice recognition run on your device. Your keyboard doesn't need to phone home to do its job.

And it isn't just a bare-bones protest keyboard. SimpleBoard has local neural predictions, two offline speech engines, encrypted learning, extensive layout controls, editable autocorrect, and themes you can actually customize.

## Features

### Typing and autocorrect

- Three-slot suggestion strip, next-word suggestions, local autocorrect, and configurable correction confidence.
- Personal word and short-phrase learning that persists across ordinary text fields and apps, without copying one app's text into another.
- An editable dictionary: add and trust words, remove learned entries, block unwanted words from being learned again, and create explicit `typed → replacement` rules.
- Optional **experimental offline neural prediction** using a bundled ONNX model based on **SmolLM2-135M**, with a lightweight local prediction engine as a fallback.
- Automatic capitalization, double-space period, optional number row, key previews, vibration, and sound feedback.
- Number and phone layouts when requested by the active text field.

### Layout customization

- Adjustable keyboard height, key spacing, text size, corner radius, and borders.
- Independently adjustable spacebar height and Enter-key width.
- Move the period key to the left of Space; show or hide the emoji key.
- Left- or right-handed one-handed mode with adjustable width.
- Custom long-press actions for individual keys, including symbols, accented characters, emoji, navigation, paste, settings, and voice typing.
- Fine-grained popup controls for timing, size, placement, columns, appearance, and selection behavior.

### Themes and appearance

- Custom colors for the keyboard surface, keys, function keys, spacebar, Enter key, letters, symbols, hints, popups, and borders.
- Independent key transparency and support for a local keyboard background image.
- Import and export of `.simpleboardtheme` themes.
- Import and export of HeliBoard-compatible color JSON for supported appearance settings.

### Offline voice typing

SimpleBoard doesn't hand your microphone over to Google Voice Typing or depend on an external Android speech provider. It has two on-device engines:

| Engine | What it does |
| --- | --- |
| **Vosk** | Bundled English offline model, streaming partial transcription, and recording sessions up to **120 seconds**. |
| **Whisper (whisper.cpp)** | Optional multilingual models, automatic language detection or supported language selection, and transcription after recording up to **60 seconds**. |

The microphone appears at the right edge of the suggestion strip when voice typing is enabled and permitted in the current field. Microphone audio is processed locally without intentionally saving audio recordings. Whisper model weights are separate from the app; the recognition itself runs offline.

### Privacy and control

- **No `INTERNET` permission.** No ads, analytics SDK, account requirement, or cloud-based typing pipeline.
- **Encrypted local learning.** Personal word frequencies, phrase patterns, and correction rules are kept in a SQLCipher database with a key protected by Android Keystore.
- **No intentional full-message or audio archive.** The keyboard learns limited word and phrase patterns, not a running copy of every conversation.
- **Sensitive-field exclusions.** Password, protected/private, email, and URL fields are excluded from personal learning.
- **User-controlled learning.** Pause learning, clear learned data, edit individual entries, and override autocorrect behavior.
- **No automatic Android app-data backup.** Themes and some ordinary settings are stored locally but are not part of the encrypted learning database.

None of that makes a compromised phone magically secure. It means SimpleBoard itself isn't designed as another corporate collection point for the contents of your keyboard.

## Changelog

### 0.3.1 — Usability Update

A release focused on making typing more responsive and offline dictation more reliable, without pretending a keyboard needs the cloud to accomplish either.

**Typing and prediction**

- Moved routine suggestion searches and learning writes off the main typing thread.
- Coalesced suggestion requests over **80 ms** and discarded stale results.
- Delayed neural prediction until a **600 ms pause** in typing; a new keypress interrupts pending inference.
- Limited neural inference to one background-priority thread. After three timeouts, neural work backs off for the current keyboard-service lifetime instead of repeatedly retrying on app switches.

**Learning and persistence**

- Kept recently used personal words and phrases at full ranking weight for **seven days**, followed by the existing **30-day half-life** decay.
- Made phrase pruning less aggressive: recently used entries are protected, older entries are retained more generously, and maintenance runs at most once per hour.
- Preserved encrypted, persistent learning across apps while keeping actual editor text isolated between them.

**Voice typing**

- Moved the microphone permanently to the **right of the suggestion strip**, freeing the keyboard's bottom row.
- Reworked Vosk capture around a dedicated audio thread and bounded in-memory queue.
- Allowed microphone capture to begin while the Vosk recognition model finishes loading, reducing the chance of missing speech at the start of a session.
- Made **Done** drain queued audio before finalizing and **Cancel** discard the session.
- Increased Vosk recording time to **two minutes** and improved reporting of capture failures or queue overload instead of silently returning incomplete output.
- Left Whisper's existing one-minute, record-then-transcribe workflow unchanged.

**Other fixes**

- Included a SQLCipher startup fix and native build-cache/timestamp fixes in the 0.3.1 source changes.
- Updated the app version to **0.3.1**.

### 0.3.0 — Multilingual offline speech

- Added on-device **Whisper / whisper.cpp** transcription as an alternative to Vosk.
- Added optional verified multilingual Whisper model management, with model integrity checks.
- Added automatic speech-language detection and supported language choices.
- Expanded parts of the settings interface with additional localization.

### Earlier 0.2.x and 0.1.x development

The project has changed substantially since its early releases. Features added over that development include:

- Offline **Vosk** dictation with a bundled English model.
- Encrypted and persistent personal learning, editable corrections, and phrase-based suggestions.
- Optional local neural predictions with an ONNX model.
- Expanded keyboard sizing, layout, long-press, one-handed, and appearance controls.
- Theme import/export and compatible color-theme exchange.
- A much more extensive settings interface and finer control over autocorrect and privacy.

## License

SimpleBoard is distributed under the author's **[No Fault License](https://github.com/ZoKLav/no_fault_license/blob/main/no_fault_license.md)**. Third-party software and models retain their own licenses; see the repository's third-party notices for attribution.

Whisper, ONNX, and Vosk all retain their own licenses.

**Your keyboard should type what you tell it, learn what you allow it to learn, and keep its business out of everyone else's hands. Gboard and the data-harvesting business model it represents can stay off your keyboard.**
