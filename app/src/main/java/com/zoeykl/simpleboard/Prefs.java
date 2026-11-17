package com.zoeykl.simpleboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import java.util.Locale;

public final class Prefs {
    private Prefs() {}
    public static final String NAME = "simpleboard";

    public static SharedPreferences p(Context c) { return c.getSharedPreferences(NAME, Context.MODE_PRIVATE); }

    public static boolean b(Context c, String k, boolean d) { return p(c).getBoolean(k, d); }
    public static int i(Context c, String k, int d) { return p(c).getInt(k, d); }
    public static String s(Context c, String k, String d) { return p(c).getString(k, d); }

    public static int boardBg(Context c) { return i(c, "color_board", Color.rgb(30,30,30)); }
    public static int keyBg(Context c) { return i(c, "color_key", Color.rgb(55,55,55)); }
    public static int functionBg(Context c) { return i(c, "color_function", Color.rgb(45,45,45)); }
    public static int spaceBg(Context c) { return i(c, "color_space", Color.rgb(55,55,55)); }
    public static int textColor(Context c) { return i(c, "color_text", Color.WHITE); }
    public static int hintColor(Context c) { return i(c, "color_hint", Color.LTGRAY); }
    public static int borderColor(Context c) { return i(c, "color_border", Color.rgb(90,90,90)); }
    public static int accent(Context c) { return i(c, "color_accent", Color.rgb(140,72,190)); }
    public static int popupBg(Context c) { return i(c, "color_popup_bg", Color.rgb(32,32,32)); }
    public static int popupText(Context c) { return i(c, "color_popup_text", Color.WHITE); }

    public static String popupKey(String label) { return "popup_" + label.toLowerCase(Locale.US); }
}
