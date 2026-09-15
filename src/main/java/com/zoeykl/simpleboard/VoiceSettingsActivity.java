package com.zoeykl.simpleboard;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public final class VoiceSettingsActivity extends SettingsPageActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        initPage("Voice typing", "SimpleBoard can hand dictation to an enabled voice keyboard such as Google Voice Typing, or use Android's SpeechRecognizer inline. SimpleBoard itself never needs Internet permission.");

        section("Voice access");
        check("Enable voice typing", "voice_enabled", true);
        spinner("Microphone button location", "voice_key_location", new String[]{"Bottom row", "Suggestion strip (right)", "Hidden"}, 0);
        text("Hiding the microphone button does not remove voice support; custom long-hold shortcuts can still use @voice.");

        section("Implementation");
        spinner("Voice implementation", "voice_implementation", new String[]{
                "Auto — prefer external voice keyboard",
                "External voice keyboard",
                "Inline Android speech recognizer"
        }, 0);
        text("External voice keyboard is the best choice when Android exposes Google Voice Typing as its own keyboard. Auto uses it when available and falls back to the inline recognizer otherwise.");

        externalImeSpinner();
        button("Open Android keyboard settings", v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));

        section("Inline recognizer");
        providerSpinner();
        text(SpeechRecognizer.isRecognitionAvailable(this)
                ? "Android reports that an inline speech-recognition service is available."
                : "Android does not currently report an inline speech-recognition service. External voice keyboard mode may still work.");
        spinnerString("Voice language mode", "voice_language_mode", new String[]{"system", "keyboard", "explicit"}, "system");
        editString("Explicit language tag (example: en-US)", "voice_language_tag", "en-US");

        section("Recognition behavior");
        check("Prefer offline recognition when provider supports it", "voice_offline", false);
        check("Show partial voice results while speaking", "voice_partial", true);
        slider("Voice max results", "voice_max_results", 1, 5, 3, "");

        section("Microphone access for inline mode");
        text("The external voice-keyboard implementation uses that keyboard's own microphone permission. SimpleBoard only needs microphone permission for inline recognition.");
        Button mic = button(micButtonText(), v -> {
            if (android.os.Build.VERSION.SDK_INT >= 23) requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 77);
        });
        if (android.os.Build.VERSION.SDK_INT < 23) mic.setEnabled(false);
    }

    private String micButtonText() {
        if (android.os.Build.VERSION.SDK_INT < 23) return "Microphone permission granted by install";
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                ? "Microphone permission: granted"
                : "Grant microphone permission";
    }

    private void externalImeSpinner() {
        final List<String> labels = new ArrayList<>(), values = new ArrayList<>();
        labels.add("Auto-detect voice keyboard"); values.add("");

        InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
                if (info == null || getPackageName().equals(info.getPackageName())) continue;
                CharSequence lab = info.loadLabel(getPackageManager());
                String name = lab == null ? info.getId() : lab.toString();
                boolean voice = isLikelyVoiceIme(info, name);
                labels.add((voice ? "Voice: " : "Keyboard: ") + name + "  [" + info.getPackageName() + "]");
                values.add(info.getId());
            }
        }

        label("External voice input method");
        Spinner sp = new Spinner(this);
        sp.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        String cur = Prefs.s(this, "voice_external_ime", "");
        int idx = values.indexOf(cur);
        sp.setSelection(idx < 0 ? 0 : idx);
        sp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                Prefs.p(VoiceSettingsActivity.this).edit().putString("voice_external_ime", values.get(pos)).apply();
            }
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        root.addView(sp, wide());
    }

    private boolean isLikelyVoiceIme(InputMethodInfo info, String label) {
        String hay = (label + " " + info.getId() + " " + info.getPackageName()).toLowerCase(java.util.Locale.US);
        if (hay.contains("voice") || hay.contains("speech") || hay.contains("dictat")) return true;
        for (int n = 0; n < info.getSubtypeCount(); n++) {
            InputMethodSubtype st = info.getSubtypeAt(n);
            if (st != null && "voice".equalsIgnoreCase(st.getMode())) return true;
        }
        return false;
    }

    private void providerSpinner() {
        final List<String> labels = new ArrayList<>(), values = new ArrayList<>();
        labels.add("System default"); values.add("");
        Intent i = new Intent(RecognitionService.SERVICE_INTERFACE);
        List<ResolveInfo> infos = getPackageManager().queryIntentServices(i, PackageManager.MATCH_ALL);
        for (ResolveInfo r : infos) {
            if (r.serviceInfo == null) continue;
            ComponentName c = new ComponentName(r.serviceInfo.packageName, r.serviceInfo.name);
            CharSequence lab = r.loadLabel(getPackageManager());
            labels.add((lab == null ? c.flattenToShortString() : lab.toString()) + "  [" + c.getPackageName() + "]");
            values.add(c.flattenToString());
        }
        label("Inline recognition provider");
        Spinner sp = new Spinner(this);
        sp.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        String cur = Prefs.s(this, "voice_provider", "");
        int idx = values.indexOf(cur);
        sp.setSelection(idx < 0 ? 0 : idx);
        sp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                Prefs.p(VoiceSettingsActivity.this).edit().putString("voice_provider", values.get(pos)).apply();
            }
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        root.addView(sp, wide());
    }

    @Override public void onRequestPermissionsResult(int request, String[] perms, int[] results) {
        super.onRequestPermissionsResult(request, perms, results);
        if (request == 77) Toast.makeText(this,
                results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED
                        ? "Microphone permission granted"
                        : "Microphone permission denied",
                Toast.LENGTH_SHORT).show();
    }
}
