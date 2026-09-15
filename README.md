# SimpleBoard

### A keyboard. Not a data-mining business model with keys attached.

SimpleBoard exists because typing on your own phone should not require surrendering another piece of your life to a corporation that has somehow convinced the industry that every basic utility needs analytics, cloud hooks, engagement metrics, personalization pipelines, account integration, telemetry, growth features, and three layers of bullshit between you and the thing you were trying to do.

It is a keyboard. Its job is to put letters on the screen when you touch them.

---

## Why this exists

Modern Android keyboards are technically impressive and philosophically rotten. They are built by companies whose incentives are not aligned with yours. The keyboard is one of the most intimate pieces of software on a phone: it sits between you and your messages, passwords, searches, notes, arguments, jokes, banking apps, work, private conversations, and every other text field you touch.

That should make keyboard software **more boring, more transparent, more local, and more under your control**.

Instead, the industry went the other direction.

Google's own Gboard documentation says the app sends usage statistics to Google, and that its standard talk-to-text feature sends voice to Google for transcription. Google also documents newer advanced voice typing modes that can keep more processing on-device, which is genuinely better, but the larger point remains: your keyboard should not require trusting a corporate platform's current privacy posture, current defaults, current server behavior, current product roadmap, or current promise that this particular category of data is being handled nicely today.

SimpleBoard is the answer to that entire mindset.

No telemetry. No analytics SDK. No ad network. No account. No cloud sync. No engagement garbage. No recommendation feed. No AI assistant wedged into the keyboard because a quarterly roadmap needed another checkbox. No mysterious network traffic from the keyboard itself. No pretending that a fucking keyboard needs to become a platform.

SimpleBoard does not request Android's `INTERNET` permission.

If a feature can be local, it stays local.

---

## The point

SimpleBoard is built around three ideas: **usability, customization, and user ownership**.

The keyboard should adapt to you instead of training you to accommodate somebody else's product decisions. If you want a giant spacebar because you keep typing `typingnlikenthis.because.people.aren'tnperfect`, you should be able to make the spacebar taller. If you hate the emoji key, remove it. If you want the period on the left side, move the entire fucking key there. If you want the microphone in the suggestion strip instead of eating space on the bottom row, put it there. If you want translucent keys over a background image, you should not need to beg a theme engine for permission.

Likewise, autocorrect should learn **your** vocabulary and let **you** correct its mistakes. A keyboard that insists it knows better than the person typing is not intelligent. It is annoying software with confidence problems.

SimpleBoard is designed to expose those decisions instead of hiding them behind unexplained heuristics.

---

## Features

- **Fully local keyboard core.** No `INTERNET` permission, no telemetry, no analytics, no advertising, and no account requirement.
- **Android 7.0 Nougat and newer.** Minimum API 24.
- **Suggestion strip and local autocorrect.** Suggestions, automatic correction, configurable confidence, local learned words, explicit correction pairs, and a real dictionary/learning manager so bad behavior can be corrected manually instead of waiting for some invisible model to change its mind.
- **Customizable number row and numeric layouts.** Normal number row support plus automatic numeric/phone layouts when apps correctly identify their input fields.
- **One-handed mode.** Left or right alignment with configurable keyboard width.
- **Independent keyboard sizing.** Keyboard height, letter size, key spacing, corner radius, border width, Enter-key width, and dedicated spacebar height controls.
- **A deliberately larger spacebar.** The spacebar can grow vertically without making every neighboring punctuation key equally easy to hit by accident.
- **Bottom-row control.** Show or hide the emoji key, relocate the period key entirely to the left side of the spacebar, resize Enter, and choose where the microphone control lives.
- **Voice typing without forcing one implementation.** Use an external voice keyboard such as Google Voice Typing, use Android's `SpeechRecognizer` inline, or let Auto mode choose. The microphone can live on the bottom row, on the right side of the suggestion strip, or be hidden entirely.
- **Configurable voice behavior.** Select an external voice input method, select an inline recognition provider, choose language behavior, prefer offline recognition when supported, enable or disable partial results, and control result count.
- **Real appearance controls.** Visual color pickers instead of making normal people type hex values into boxes.
- **Per-surface transparency.** Key fill, function keys, spacebar, and accent/Enter keys can use independent alpha values so keyboard backgrounds are actually visible.
- **Keyboard background images.** Pick an image from storage and keep it locally.
- **Full color control.** Board, key surfaces, function keys, spacebar, accent keys, letters, hints, borders, popup background, popup text, and popup borders.
- **Theme import/export.** Portable SimpleBoard themes can include colors and the keyboard background image. HeliBoard-compatible color JSON import/export is also supported where the fields overlap.
- **Per-key hold shortcuts.** Long-press behavior can be customized key by key instead of being baked into the layout forever.
- **Text, symbols, emoji, and actions as shortcuts.** A hold shortcut can output characters, strings, emoji, or actions such as numpad, settings, voice, paste, cursor movement, emoji, Enter, and Tab.
- **Custom popup editor.** Configure popup contents, delay, size, text scale, number of columns, shape, radius, spacing, border width, position, autoselection, and cancel behavior.
- **Accent-character popups.** Useful defaults for common accented Latin characters remain available and can be overridden.
- **Proper system-bar handling.** The keyboard respects Android navigation-bar insets instead of drawing half the bottom row underneath system controls.
- **Local settings and local learning.** The things SimpleBoard learns belong on the device running SimpleBoard.
- **No glide typing.** Intentionally. Absolutely not. If drawing shapes across a keyboard is your preferred lifestyle choice, there are other keyboards for that.

---

## Voice typing and the unavoidable reality of other people's software

SimpleBoard itself does not need Internet access, but Android lets it hand voice input to another installed service or keyboard. That provider has its own permissions, privacy policy, network behavior, and bad decisions.

If you select Google Voice Typing, Google is doing the voice recognition. SimpleBoard is not secretly laundering that through its own servers because SimpleBoard has no servers and no Internet permission in the first place.

Inline mode uses Android's public speech-recognition APIs. Android's `SpeechRecognizer` framework can route recognition to an installed recognition service, including a specific provider where supported. Whether that provider performs recognition locally or remotely is the provider's problem and should be evaluated separately.

The important distinction is simple: **SimpleBoard does not need network access just because somebody wants a microphone button.**

---

## Privacy model

There is no privacy policy full of reassuring prose because there is almost nothing for one to describe.

SimpleBoard does not contain telemetry code. It does not contain an analytics SDK. It does not upload typing statistics. It does not have an account system. It does not sync learned words to a corporate backend. It does not phone home because there is nowhere to phone home to.

The app requests microphone permission only for inline speech recognition. External voice-keyboard mode uses the external keyboard or voice provider's own microphone access instead.

Background images, preferences, learned words, corrections, shortcuts, and themes are handled locally unless **you** explicitly export something.

That is what “privacy by design” is supposed to mean before marketing departments hollow the phrase out and staple it to a banner ad.

---

## Autocorrect that can be told when it is wrong

Autocorrect is useful. Autocorrect that cannot be corrected is a tiny authoritarian living under your thumbs.

SimpleBoard separates learned words from explicit corrections. Learned behavior can influence suggestions, but an explicit correction is an instruction from the person who owns the device. The keyboard should respect that hierarchy.

If SimpleBoard keeps mangling a name, technical term, character name, acronym, bit of slang, or deliberately unusual spelling, add it. If it learned garbage, remove it. If a correction pair should always win, define it yourself.

No mystical cloud brain required.

---

## Themes without a subscription, storefront, or artificial scarcity

Themes are configuration, not content monetization.

SimpleBoard lets you control the keyboard background, colors, transparency, borders, key shapes, sizing, popup appearance, and related visual behavior directly. Theme files can be exported and shared like normal files because settings created by the user should be owned by the user.

There is no theme marketplace. There are no premium colors. There is no monthly subscription to make the fucking keys purple.

---

## What SimpleBoard is not trying to become

SimpleBoard is not trying to become Google Search, an AI chatbot, a sticker storefront, a social network, a browser, a writing coach, a shopping assistant, a crypto wallet, a content feed, or whatever other product-management hallucination gets stapled onto keyboards next year.

Feature requests are welcome when they make the keyboard **better at being a keyboard**.

If a proposed feature requires collecting user behavior because somebody wants a dashboard, the answer is no.

If it requires an account for no technical reason, the answer is no.

If it requires cloud infrastructure for something that works perfectly well locally, the answer is probably no.

If it exists primarily because a corporation discovered another surface on which to extract data, attention, money, or dependency, the answer is **fuck no**.

---

## License

All of my software uses my proprietary No Fault License.

https://github.com/ZoKLav/no_fault_license/blob/main/no_fault_license.md
