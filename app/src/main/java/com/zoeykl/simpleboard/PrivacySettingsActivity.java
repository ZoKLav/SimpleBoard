package com.zoeykl.simpleboard;

import android.os.Bundle;

public final class PrivacySettingsActivity extends SettingsPageActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        initPage("Privacy & behavior", "SimpleBoard is intentionally local-first.");
        section("Network");
        text("SimpleBoard itself does not request android.permission.INTERNET. Keyboard input, learned words, explicit correction pairs, shortcuts and themes remain on-device.");
        section("Neural prediction");
        text("The bundled neural model runs offline through ONNX Runtime, with runtime telemetry explicitly disabled. It reads a short context before the cursor only in eligible fields. Context and native inference caches are temporary and are discarded when input changes or the keyboard closes; no neural text history is written. Personal word and phrase counts still follow the local learning setting.");
        section("Voice typing");
        text("The selected Vosk or Whisper model processes microphone audio inside SimpleBoard. Whisper models are read only from the dedicated VoiceModels folder you authorize, verified against pinned hashes and copied into private storage. Download buttons open your browser; SimpleBoard does not download files itself. No external speech service or network is used. Audio and unfinished transcripts remain in memory and are discarded when dictation ends; only a completed transcript is inserted into your editor.");
        section("Local learning");
        check("Learn words locally", "learn_words", true);
        text("Word counts, recent-use weights, short word-pair/triple counts, dictionary entries and correction rules are stored in a SQLCipher-encrypted database. Its random key is wrapped by Android Keystore and is never included in the APK or source repository. These short patterns can reveal phrases you use; no full-message history or raw keystroke log is saved. Password, private, email and URL fields do not train the dictionary. Turning learning off pauses updates; clearing removes existing data.");
        button("Clear learned words and replacement rules", v -> new android.app.AlertDialog.Builder(this)
            .setTitle("Clear personal dictionary?").setMessage("This also clears earlier learned typos, blocked words, usage counts, phrase patterns, correction rejections and manual replacement rules. Settings stay unchanged.")
            .setNegativeButton("Cancel",null).setPositiveButton("Clear",(d,w)->{WordStore store=new WordStore(this);try{store.clearPersonalData();}finally{store.close();}}).show());
        section("Encryption limits");
        text("Other ordinary apps cannot access the learning database or its Keystore key. Root access, a compromised operating system, or code running inside SimpleBoard can defeat this protection. Settings, themes and manually configured shortcuts use ordinary private app storage; the learning database is encrypted. Losing the app data or its Keystore key loses the learned history.");
        section("Backups");
        text("Android application backup is disabled for SimpleBoard, so its local keyboard data is not silently copied into the platform backup service.");
    }
}
