package com.zoeykl.simpleboard;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

public final class SuggestionStrip extends LinearLayout {
    public interface Listener { void onSuggestion(String text); void onVoice(); void onGrammar(); }
    private final TextView[] cells = new TextView[3];
    private final TextView voice,grammar;
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

        grammar=new TextView(c);grammar.setText("✓");grammar.setTextSize(22);grammar.setGravity(Gravity.CENTER);grammar.setContentDescription("Check grammar");
        grammar.setOnClickListener(v->{if(listener!=null)listener.onGrammar();});addView(grammar,new LayoutParams(dp(40),dp(42)));
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
        for (int i = 0; i < cells.length; i++) {String text=i<s.size()?s.get(i):"";if(!text.contentEquals(cells[i].getText()))cells[i].setText(text);}
    }

    public void refresh() {
        setBackgroundColor(Prefs.boardBg(getContext()));
        for (TextView t : cells) t.setTextColor(Prefs.textColor(getContext()));
        voice.setTextColor(Prefs.accent(getContext()));grammar.setTextColor(Prefs.accent(getContext()));grammar.setVisibility(Prefs.b(getContext(),"grammar_enabled",true)?View.VISIBLE:View.GONE);
        boolean showVoice = Prefs.b(getContext(), "voice_enabled", true);
        voice.setVisibility(showVoice ? View.VISIBLE : View.GONE);
    }

    void setToolsVisible(boolean microphone,boolean checkGrammar){voice.setVisibility(microphone?View.VISIBLE:View.GONE);grammar.setVisibility(checkGrammar?View.VISIBLE:View.GONE);}

    private int dp(float x) { return (int) (x * getResources().getDisplayMetrics().density + 0.5f); }
}
