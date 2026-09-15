package com.zoeykl.simpleboard;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

abstract class SettingsPageActivity extends Activity {
    protected LinearLayout root;

    protected void initPage(String title, String subtitle) {
        ScrollView sc = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(28));
        sc.addView(root);
        UiInsets.apply(sc);
        setContentView(sc);
        title(title);
        if (subtitle != null && !subtitle.isEmpty()) text(subtitle);
    }

    protected void title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(28);
        t.setTextColor(Color.BLACK);
        t.setPadding(0, 0, 0, dp(4));
        root.addView(t, wide());
    }

    protected void section(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(19);
        t.setTextColor(Color.rgb(90, 30, 120));
        t.setPadding(0, dp(18), 0, dp(6));
        root.addView(t, wide());
    }

    protected void text(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(14);
        t.setTextColor(Color.DKGRAY);
        t.setPadding(0, dp(3), 0, dp(8));
        root.addView(t, wide());
    }

    protected void label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.DKGRAY);
        t.setPadding(0, dp(8), 0, 0);
        root.addView(t, wide());
    }

    protected Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        root.addView(b, wide());
        return b;
    }

    protected CheckBox check(String s, String key, boolean def) {
        CheckBox c = new CheckBox(this);
        c.setText(s);
        c.setChecked(Prefs.b(this, key, def));
        c.setOnCheckedChangeListener((v, on) -> Prefs.p(this).edit().putBoolean(key, on).apply());
        root.addView(c, wide());
        return c;
    }

    protected void slider(String name, String key, int min, int max, int def, String suffix) {
        final TextView t = new TextView(this);
        final SeekBar sb = new SeekBar(this);
        int cur = Prefs.i(this, key, def);
        t.setText(name + ": " + cur + suffix);
        t.setTextColor(Color.DKGRAY);
        t.setPadding(0, dp(8), 0, 0);
        sb.setMax(max - min);
        sb.setProgress(Math.max(0, Math.min(max - min, cur - min)));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean from) {
                int v = p + min;
                t.setText(name + ": " + v + suffix);
                if (from) Prefs.p(SettingsPageActivity.this).edit().putInt(key, v).apply();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        root.addView(t, wide());
        root.addView(sb, wide());
    }

    protected void spinner(String name, String key, String[] values, int def) {
        label(name);
        Spinner sp = new Spinner(this);
        sp.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, values));
        sp.setSelection(Math.max(0, Math.min(values.length - 1, Prefs.i(this, key, def))));
        sp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                Prefs.p(SettingsPageActivity.this).edit().putInt(key, pos).apply();
            }
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        root.addView(sp, wide());
    }

    protected void spinnerString(String name, String key, String[] values, String def) {
        label(name);
        Spinner sp = new Spinner(this);
        sp.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, values));
        String cur = Prefs.s(this, key, def);
        int idx = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(cur)) idx = i;
        sp.setSelection(idx);
        sp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                Prefs.p(SettingsPageActivity.this).edit().putString(key, values[pos]).apply();
            }
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        root.addView(sp, wide());
    }

    protected void editString(String name, String key, String def) {
        label(name);
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setText(Prefs.s(this, key, def));
        e.setOnFocusChangeListener((v, has) -> {
            if (!has) Prefs.p(this).edit().putString(key, e.getText().toString().trim()).apply();
        });
        root.addView(e, wide());
    }

    protected Button colorPreference(String name, String key, int def) {
        Button b = button(name, null);
        updateColorButton(b, name, Prefs.i(this, key, def));
        b.setOnClickListener(v -> ColorPickerDialog.show(this, name, Prefs.i(this, key, def), color -> {
            Prefs.p(this).edit().putInt(key, color).apply();
            updateColorButton(b, name, color);
        }));
        return b;
    }

    protected void alphaSlider(String name, String key, int def, Button linkedButton, String linkedName) {
        final TextView t = new TextView(this);
        final SeekBar sb = new SeekBar(this);
        int color = Prefs.i(this, key, def);
        int percent = Math.round(Color.alpha(color) * 100f / 255f);
        t.setText(name + ": " + percent + "%");
        t.setTextColor(Color.DKGRAY);
        t.setPadding(0, dp(6), 0, 0);
        sb.setMax(100);
        sb.setProgress(percent);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean from) {
                t.setText(name + ": " + p + "%");
                if (from) {
                    int old = Prefs.i(SettingsPageActivity.this, key, def);
                    int a = Math.round(p * 255f / 100f);
                    int updated = Color.argb(a, Color.red(old), Color.green(old), Color.blue(old));
                    Prefs.p(SettingsPageActivity.this).edit().putInt(key, updated).apply();
                    if (linkedButton != null) updateColorButton(linkedButton, linkedName, updated);
                }
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        root.addView(t, wide());
        root.addView(sb, wide());
    }

    protected void updateColorButton(Button b, String name, int color) {
        b.setText(name + "   " + ThemeConfig.hex(color));
        ColorDrawable swatch = new ColorDrawable(color);
        swatch.setBounds(0, 0, dp(22), dp(22));
        b.setCompoundDrawables(swatch, null, null, null);
        b.setCompoundDrawablePadding(dp(10));
    }

    @Override protected void onPause() {
        super.onPause();
        View f = getCurrentFocus();
        if (f != null) f.clearFocus();
    }

    protected LinearLayout.LayoutParams wide() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    protected int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
