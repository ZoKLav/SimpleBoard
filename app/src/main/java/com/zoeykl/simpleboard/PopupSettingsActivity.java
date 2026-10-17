package com.zoeykl.simpleboard;

import android.content.Intent;
import android.os.Bundle;

public final class PopupSettingsActivity extends SettingsPageActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        initPage("Popups & shortcuts", "Long-press behavior and the actions attached to individual keys.");

        section("Per-key shortcuts");
        button("Edit key-hold shortcuts", v -> startActivity(new Intent(this, ShortcutEditorActivity.class)));

        section("Timing & size");
        slider("Hold delay", "hold_delay", 150, 800, 350, " ms");
        slider("Popup size", "popup_scale", 80, 180, 115, "%");
        slider("Popup letter size", "popup_text_scale", 70, 170, 100, "%");
        slider("Popup columns", "popup_columns", 1, 6, 4, "");

        section("Shape & spacing");
        spinnerString("Popup shape", "popup_shape", new String[]{"rounded", "square", "pill"}, "rounded");
        slider("Popup corner radius", "popup_radius", 0, 30, 10, " dp");
        slider("Popup key gap", "popup_gap", 0, 10, 1, " dp");
        slider("Popup border width", "popup_border_width", 0, 6, 1, " dp");
        spinnerString("Popup position", "popup_position", new String[]{"auto", "above", "below"}, "auto");

        section("Selection behavior");
        check("Hold without sliding selects first popup", "popup_autoselect", true);
        check("Release outside popup cancels selection", "popup_cancel_outside", true);
    }
}
