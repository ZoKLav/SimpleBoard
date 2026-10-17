package com.zoeykl.simpleboard;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

public final class SuggestionStrip extends LinearLayout {
    public interface Listener { void onSuggestion(String text); void onVoice(); }
    private final TextView[] cells = new TextView[3];
    private final TextView voice;
    private Listener listener;

    public SuggestionStrip(Context c) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(2), dp(2), dp(2), dp(2));
        setBackgroundColor(Prefs.boardBg(c));

        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView t = new TextView(c);
            cells[i] = t;
            t.setTextColor(Prefs.textColor(c));
            t.setTextSize(16);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setPadding(dp(4), 0, dp(4), 0);
            t.setOnClickListener(v -> {
                String s = cells[idx].getText().toString();
                if (listener != null && !s.isEmpty()) listener.onSuggestion(s);
            });
            addView(t, new LayoutParams(0, dp(42), 1f));
        }

        voice = new TextView(c);
        voice.setText("🎤");
        voice.setTextSize(20);
        voice.setGravity(Gravity.CENTER);
        voice.setContentDescription("Voice typing");
        voice.setOnClickListener(v -> { if (listener != null) listener.onVoice(); });
        addView(voice, new LayoutParams(dp(50), dp(42)));
        refresh();
    }

    public void setListener(Listener l) { listener = l; }

    public void setSuggestions(List<String> s) {
        for (int i = 0; i < cells.length; i++) cells[i].setText(i < s.size() ? s.get(i) : "");
    }

    public void refresh() {
        setBackgroundColor(Prefs.boardBg(getContext()));
        for (TextView t : cells) t.setTextColor(Prefs.textColor(getContext()));
        voice.setTextColor(Prefs.accent(getContext()));
        boolean showVoice = Prefs.b(getContext(), "voice_enabled", true);
        voice.setVisibility(showVoice ? View.VISIBLE : View.GONE);
    }

    private int dp(float x) { return (int) (x * getResources().getDisplayMetrics().density + 0.5f); }
}
