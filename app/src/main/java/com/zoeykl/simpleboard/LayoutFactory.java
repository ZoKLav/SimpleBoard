package com.zoeykl.simpleboard;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LayoutFactory {
    private LayoutFactory() {}

    public static List<List<KeySpec>> alphabet(Context c, boolean shifted, boolean capsLocked) {
        List<List<KeySpec>> out = new ArrayList<>();
        if (Prefs.b(c, "number_row", false)) out.add(textRow("1234567890", false));
        out.add(textRow("qwertyuiop", shifted));
        out.add(textRow("asdfghjkl", shifted));
        List<KeySpec> r3 = new ArrayList<>();
        r3.add(KeySpec.function(capsLocked ? "⇪" : "⇧", KeySpec.Action.SHIFT, 1.35f));
        for (char ch : "zxcvbnm".toCharArray()) {
            String s = String.valueOf(ch);
            if (shifted) s = s.toUpperCase(Locale.US);
            r3.add(KeySpec.text(s));
        }
        r3.add(KeySpec.function("⌫", KeySpec.Action.DELETE, 1.35f));
        out.add(r3);
        out.add(alphaBottomRow(c));
        return out;
    }

    public static List<List<KeySpec>> symbols(Context c) {
        List<List<KeySpec>> out = new ArrayList<>();
        out.add(textRow("1234567890", false));
        out.add(row("@", "#", "$", "%", "&", "*", "-", "+", "(", ")"));
        out.add(row("!", "?", "'", "\"", ":", ";", "/", "\\", "=", "_"));
        List<KeySpec> bottom = new ArrayList<>();
        bottom.add(KeySpec.function("ABC", KeySpec.Action.ALPHA, 1.35f));
        if (Prefs.b(c, "show_emoji_key", true)) bottom.add(KeySpec.function("☺", KeySpec.Action.EMOJI, 1.05f));
        bottom.add(KeySpec.text("<"));
        bottom.add(KeySpec.space(4.2f));
        bottom.add(KeySpec.text(">"));
        if (showBottomVoice(c)) bottom.add(KeySpec.function("🎤", KeySpec.Action.VOICE, 1.05f));
        bottom.add(KeySpec.enter(enterWeight(c)));
        out.add(bottom);
        return out;
    }

    public static List<List<KeySpec>> numpad(Context c, boolean phone) {
        List<List<KeySpec>> out = new ArrayList<>();
        out.add(row("1", "2", "3"));
        out.add(row("4", "5", "6"));
        out.add(row("7", "8", "9"));
        if (phone) out.add(row("*", "0", "#"));
        else out.add(row(".", "0", "-"));
        List<KeySpec> bottom = new ArrayList<>();
        bottom.add(KeySpec.function("ABC", KeySpec.Action.ALPHA, 1.5f));
        bottom.add(KeySpec.function("⌫", KeySpec.Action.DELETE, 1.5f));
        bottom.add(KeySpec.enter(enterWeight(c)));
        out.add(bottom);
        return out;
    }

    public static List<List<KeySpec>> emoji(Context c) {
        String[][] e = new String[][] {
            {"😀","😂","😊","🙂","😉","😍","😘","😎"},
            {"😭","😡","😱","🤔","🙃","😏","🥺","😴"},
            {"👍","👎","👌","✌","🤞","👏","🙏","❤️"},
            {"🔥","✨","💀","💯","🎉","✅","❌","⚠"},
            {"🐺","🦊","🐍","🐱","🐶","🐉","🌙","⭐"}
        };
        List<List<KeySpec>> out = new ArrayList<>();
        for (String[] rr : e) {
            List<KeySpec> row = new ArrayList<>();
            for (String s : rr) row.add(KeySpec.text(s));
            out.add(row);
        }
        List<KeySpec> bottom = new ArrayList<>();
        bottom.add(KeySpec.function("ABC", KeySpec.Action.ALPHA, 1.4f));
        bottom.add(KeySpec.space(4f));
        bottom.add(KeySpec.function("⌫", KeySpec.Action.DELETE, 1.4f));
        bottom.add(KeySpec.enter(enterWeight(c)));
        out.add(bottom);
        return out;
    }

    private static List<KeySpec> alphaBottomRow(Context c) {
        List<KeySpec> bottom = new ArrayList<>();
        bottom.add(KeySpec.function("?123", KeySpec.Action.SYMBOLS, 1.35f));
        if (Prefs.b(c, "show_emoji_key", true)) bottom.add(KeySpec.function("☺", KeySpec.Action.EMOJI, 1.05f));
        bottom.add(KeySpec.text(","));
        if (Prefs.b(c, "period_left", false)) {
            // Move the period key itself to the left of Space. The comma does not move.
            // This shifts the spacebar right by one key-width instead of swapping punctuation.
            bottom.add(KeySpec.text("."));
            bottom.add(KeySpec.space(4.2f));
        } else {
            bottom.add(KeySpec.space(4.2f));
            bottom.add(KeySpec.text("."));
        }
        if (showBottomVoice(c)) bottom.add(KeySpec.function("🎤", KeySpec.Action.VOICE, 1.05f));
        bottom.add(KeySpec.enter(enterWeight(c)));
        return bottom;
    }

    private static boolean showBottomVoice(Context c) {
        return false; // Voice is always at the right of the suggestion strip.
    }

    private static float enterWeight(Context c) {
        return Math.max(1f, Prefs.i(c, "enter_key_width", 170) / 100f);
    }

    private static List<KeySpec> textRow(String chars, boolean shifted) {
        List<KeySpec> r = new ArrayList<>();
        for (char ch : chars.toCharArray()) {
            String s = String.valueOf(ch);
            if (shifted && Character.isLetter(ch)) s = s.toUpperCase(Locale.US);
            r.add(KeySpec.text(s));
        }
        return r;
    }

    private static List<KeySpec> row(String... vals) {
        List<KeySpec> r = new ArrayList<>();
        for (String v : vals) r.add(KeySpec.text(v));
        return r;
    }
}
