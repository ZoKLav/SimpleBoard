package com.zoeykl.simpleboard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class SettingsActivity extends Activity {
    private LinearLayout root;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sc = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(28));
        sc.addView(root);
        UiInsets.apply(sc);
        setContentView(sc);

        TextView title = new TextView(this);
        title.setText("SimpleBoard");
        title.setTextSize(30);
        title.setTextColor(Color.BLACK);
        root.addView(title, wide());

        TextView note = new TextView(this);
        note.setText(getString(R.string.settings_note));
        note.setTextColor(Color.DKGRAY);
        note.setPadding(0, dp(4), 0, dp(12));
        root.addView(note, wide());

        Button enable = button(getString(R.string.enable_keyboard), v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        enable.setPadding(enable.getPaddingLeft(), dp(12), enable.getPaddingRight(), dp(12));

        category(getString(R.string.nav_typing),getString(R.string.nav_typing_desc),TypingSettingsActivity.class);
        category(getString(R.string.nav_layout),getString(R.string.nav_layout_desc),LayoutSettingsActivity.class);
        category(getString(R.string.nav_shortcuts),getString(R.string.nav_shortcuts_desc),PopupSettingsActivity.class);
        category(getString(R.string.nav_appearance),getString(R.string.nav_appearance_desc),AppearanceSettingsActivity.class);
        category(getString(R.string.nav_voice),getString(R.string.nav_voice_desc),VoiceSettingsActivity.class);
        category(getString(R.string.nav_privacy),getString(R.string.nav_privacy_desc),PrivacySettingsActivity.class);
    }

    private void category(String name, String description, Class<?> cls) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.rgb(245, 245, 245));
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), Color.rgb(220, 220, 220));
        card.setBackground(bg);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> startActivity(new Intent(this, cls)));

        TextView t = new TextView(this);
        t.setText(name);
        t.setTextSize(18);
        t.setTextColor(Color.BLACK);
        card.addView(t, wide());

        TextView d = new TextView(this);
        d.setText(description);
        d.setTextSize(13);
        d.setTextColor(Color.DKGRAY);
        d.setPadding(0, dp(4), 0, 0);
        card.addView(d, wide());

        LinearLayout.LayoutParams lp = wide();
        lp.topMargin = dp(10);
        root.addView(card, lp);
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        root.addView(b, wide());
        return b;
    }

    private LinearLayout.LayoutParams wide() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
