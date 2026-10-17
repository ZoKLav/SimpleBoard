package com.zoeykl.simpleboard;

import android.content.Intent;
import android.os.Bundle;

public final class TypingSettingsActivity extends SettingsPageActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        initPage("Typing & autocorrect", "Core typing behavior and local language learning.");

        section("Suggestions & correction");
        check("Suggestion strip", "suggestions", true);
        check("Next-word predictions", "next_word_predictions", true);
        check("Neural predictions (experimental)", "neural_predictions", true);
        text("Uses a bundled offline model for broader context, with your local phrase preferences. It uses more memory and battery; the lightweight engine stays available while it loads or if it is too slow. Low-memory and 32-bit devices use the lightweight engine.");
        text("Last neural status: "+Prefs.s(this,"neural_status","Not run yet"));
        check("Autocorrect", "autocorrect", true);
        check("Learn words locally", "learn_words", true);
        slider("Autocorrect confidence", "autocorrect_confidence", 75, 95, 85, "%");
        slider("Typed uses before learning a new word", "learning_repetitions", 3, 10, 5, "");
        text("Suggestions never create replacement rules. Add intentional replacements in the dictionary. Automatic correction only changes unambiguous lowercase words of four or more letters.");
        button("Autocorrect & learned words", v -> startActivity(new Intent(this, DictionaryActivity.class)));

        text("Next words use the previous two words and common starter phrases. Repeated phrases and suggestion choices get more weight; older habits fade over about a month. Choosing a new word at least twice can lower its learning threshold, but it always needs at least three occurrences. Backspace cancels the most recent pending observation. Rejecting the same automatic correction twice pauses it for 30 days; explicit dictionary rules remain overrides.");
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
