package com.zoeykl.simpleboard;

import android.content.Intent;
import android.os.Bundle;

public final class TypingSettingsActivity extends SettingsPageActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        initPage("Typing & autocorrect", "Core typing behavior and local language learning.");

        section("Suggestions & correction");
        check("Suggestion strip", "suggestions", true);
        check("Autocorrect", "autocorrect", true);
        check("Learn words locally", "learn_words", true);
        slider("Autocorrect confidence", "autocorrect_confidence", 45, 95, 70, "%");
        button("Autocorrect & learned words", v -> startActivity(new Intent(this, DictionaryActivity.class)));

        section("Typing behavior");
        check("Number row", "number_row", false);
        check("Auto-capitalize sentences", "auto_caps", true);
        check("Double-space inserts period", "double_space_period", true);

        section("Feedback");
        check("Key preview", "key_preview", true);
        check("Vibrate on key press", "vibrate", true);
        check("Key click sound", "sound", false);
    }
}
