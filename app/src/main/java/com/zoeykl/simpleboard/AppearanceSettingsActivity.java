package com.zoeykl.simpleboard;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class AppearanceSettingsActivity extends SettingsPageActivity {
    private static final int REQ_BACKGROUND = 40;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        initPage("Appearance & themes", "Colors use a visual picker with opacity control. Key fill alpha is useful when you have a keyboard background image.");

        section("Board background");
        colorPreference("Board color", "color_board", Prefs.boardBg(this));
        button("Choose keyboard background image", v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("image/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i, REQ_BACKGROUND);
        });
        button("Clear keyboard background image", v -> {
            String p = Prefs.s(this, "background_path", "");
            if (!p.isEmpty()) new File(p).delete();
            Prefs.p(this).edit().putString("background_path", "").apply();
            toast("Background cleared");
        });

        section("Key surfaces");
        Button keyFill = colorPreference("Key fill", "color_key", Prefs.keyBg(this));
        alphaSlider("Key fill opacity", "color_key", Prefs.keyBg(this), keyFill, "Key fill");
        Button functionFill = colorPreference("Function key fill", "color_function", Prefs.functionBg(this));
        alphaSlider("Function key opacity", "color_function", Prefs.functionBg(this), functionFill, "Function key fill");
        Button spaceFill = colorPreference("Spacebar fill", "color_space", Prefs.spaceBg(this));
        alphaSlider("Spacebar opacity", "color_space", Prefs.spaceBg(this), spaceFill, "Spacebar fill");
        Button accentFill = colorPreference("Accent / enter key", "color_accent", Prefs.accent(this));
        alphaSlider("Accent key opacity", "color_accent", Prefs.accent(this), accentFill, "Accent / enter key");
        colorPreference("Key border / outline", "color_border", Prefs.borderColor(this));

        section("Key text");
        colorPreference("Letter color", "color_text", Prefs.textColor(this));
        colorPreference("Hint color", "color_hint", Prefs.hintColor(this));

        section("Popup colors");
        colorPreference("Popup background", "color_popup_bg", Prefs.popupBg(this));
        colorPreference("Popup text", "color_popup_text", Prefs.popupText(this));
        colorPreference("Popup border", "color_popup_border", Prefs.borderColor(this));

        section("Themes");
        button("Import / export themes", v -> startActivity(new Intent(this, ThemeActivity.class)));
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req == REQ_BACKGROUND && result == RESULT_OK && data != null && data.getData() != null) {
            try {
                File out = new File(getFilesDir(), "keyboard_background");
                InputStream in = getContentResolver().openInputStream(data.getData());
                FileOutputStream fos = new FileOutputStream(out);
                byte[] buf = new byte[32768];
                int n;
                while (in != null && (n = in.read(buf)) > 0) fos.write(buf, 0, n);
                if (in != null) in.close();
                fos.close();
                Prefs.p(this).edit().putString("background_path", out.getAbsolutePath()).apply();
                toast("Background image saved locally");
            } catch (Exception e) {
                toast("Couldn't import image: " + e.getMessage());
            }
        }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
