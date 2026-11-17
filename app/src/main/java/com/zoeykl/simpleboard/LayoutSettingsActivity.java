package com.zoeykl.simpleboard;

import android.os.Bundle;

public final class LayoutSettingsActivity extends SettingsPageActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        initPage("Layout & sizing", "Controls that change the keyboard's physical footprint, bottom row and key geometry.");

        section("Keyboard");
        slider("Keyboard height", "keyboard_height", 70, 150, 100, "%");
        slider("Letter size", "text_scale", 70, 160, 100, "%");

        section("Bottom row");
        slider("Spacebar height", "spacebar_height", 100, 150, 125, "%");
        slider("Enter key width", "enter_key_width", 100, 220, 170, "% of a letter key");
        check("Show emoji key", "show_emoji_key", true);
        check("Move period key to the left of the spacebar", "period_left", false);
        text("Moving the period key keeps the comma where it is and moves the entire period key to the left of Space, shifting the spacebar to the right. The taller spacebar gets the extra vertical hit area; neighboring bottom-row keys stay normal height.");

        section("Keys");
        slider("Key gap", "key_gap", 0, 12, 3, " dp");
        slider("Key corner radius", "key_radius", 0, 24, 8, " dp");
        slider("Key border width", "border_width", 0, 6, 1, " dp");

        section("One-handed mode");
        spinner("Mode", "one_hand_mode", new String[]{"Off", "Left", "Right"}, 0);
        slider("One-handed width", "one_hand_width", 55, 100, 76, "%");
    }
}
