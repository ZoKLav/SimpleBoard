package com.zoeykl.simpleboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import org.json.JSONObject;

public final class ThemeConfig {
    public int board, key, function, space, text, hint, border, accent, popupBg, popupText, popupBorder;
    public int borderWidth, radius, gap, textScale, keyboardHeight, popupScale, popupTextScale, popupRadius, popupColumns, popupGap, popupBorderWidth;
    public String popupShape, popupPosition;
    public String backgroundPath;

    public static ThemeConfig read(Context c) {
        ThemeConfig t = new ThemeConfig();
        t.board = Prefs.boardBg(c); t.key = Prefs.keyBg(c); t.function = Prefs.functionBg(c); t.space = Prefs.spaceBg(c);
        t.text = Prefs.textColor(c); t.hint = Prefs.hintColor(c); t.border = Prefs.borderColor(c); t.accent = Prefs.accent(c);
        t.popupBg = Prefs.popupBg(c); t.popupText = Prefs.popupText(c); t.popupBorder = Prefs.i(c, "color_popup_border", t.border);
        t.borderWidth = Prefs.i(c, "border_width", 1); t.radius = Prefs.i(c, "key_radius", 8); t.gap = Prefs.i(c, "key_gap", 3);
        t.textScale = Prefs.i(c, "text_scale", 100); t.keyboardHeight = Prefs.i(c, "keyboard_height", 100);
        t.popupScale = Prefs.i(c, "popup_scale", 115); t.popupTextScale = Prefs.i(c, "popup_text_scale", 100); t.popupRadius = Prefs.i(c, "popup_radius", 10); t.popupColumns = Prefs.i(c, "popup_columns", 4);
        t.popupGap = Prefs.i(c, "popup_gap", 1); t.popupBorderWidth = Prefs.i(c, "popup_border_width", 1);
        t.popupShape = Prefs.s(c, "popup_shape", "rounded"); t.popupPosition = Prefs.s(c, "popup_position", "auto"); t.backgroundPath = Prefs.s(c, "background_path", "");
        return t;
    }

    public void apply(Context c) {
        SharedPreferences.Editor e = Prefs.p(c).edit();
        e.putInt("color_board", board).putInt("color_key", key).putInt("color_function", function).putInt("color_space", space)
         .putInt("color_text", text).putInt("color_hint", hint).putInt("color_border", border).putInt("color_accent", accent)
         .putInt("color_popup_bg", popupBg).putInt("color_popup_text", popupText).putInt("color_popup_border", popupBorder).putInt("border_width", borderWidth)
         .putInt("key_radius", radius).putInt("key_gap", gap).putInt("text_scale", textScale).putInt("keyboard_height", keyboardHeight)
         .putInt("popup_scale", popupScale).putInt("popup_text_scale", popupTextScale).putInt("popup_radius", popupRadius).putInt("popup_columns", popupColumns)
         .putInt("popup_gap", popupGap).putInt("popup_border_width", popupBorderWidth)
         .putString("popup_shape", popupShape == null ? "rounded" : popupShape).putString("popup_position", popupPosition == null ? "auto" : popupPosition)
         .putString("background_path", backgroundPath == null ? "" : backgroundPath).apply();
    }

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("format", "SimpleBoardTheme"); o.put("version", 1);
        o.put("board", board); o.put("key", key); o.put("function", function); o.put("space", space); o.put("text", text);
        o.put("hint", hint); o.put("border", border); o.put("accent", accent); o.put("popupBg", popupBg); o.put("popupText", popupText); o.put("popupBorder", popupBorder);
        o.put("borderWidth", borderWidth); o.put("radius", radius); o.put("gap", gap); o.put("textScale", textScale);
        o.put("keyboardHeight", keyboardHeight); o.put("popupScale", popupScale); o.put("popupTextScale", popupTextScale); o.put("popupRadius", popupRadius); o.put("popupColumns", popupColumns);
        o.put("popupGap", popupGap); o.put("popupBorderWidth", popupBorderWidth);
        o.put("popupShape", popupShape == null ? "rounded" : popupShape); o.put("popupPosition", popupPosition == null ? "auto" : popupPosition);
        return o;
    }

    public JSONObject toHeliBoardJson() throws Exception {
        JSONObject root = new JSONObject(); root.put("name", "SimpleBoard export"); root.put("moreColors", 0);
        JSONObject colors = new JSONObject();
        colors.put("accent", pair(accent)); colors.put("keys", pair(key)); colors.put("hint_text", pair(hint)); colors.put("spacebar", pair(space));
        colors.put("background", pair(board)); colors.put("functional_keys", pair(function)); colors.put("text", pair(text));
        root.put("colors", colors); return root;
    }

    private static JSONObject pair(int value) throws Exception { JSONObject p = new JSONObject(); p.put("first", value); p.put("second", false); return p; }

    public static ThemeConfig fromJson(Context c, JSONObject o) throws Exception {
        ThemeConfig t = read(c);
        if ("SimpleBoardTheme".equals(o.optString("format"))) {
            t.board=o.optInt("board",t.board); t.key=o.optInt("key",t.key); t.function=o.optInt("function",t.function); t.space=o.optInt("space",t.space);
            t.text=o.optInt("text",t.text); t.hint=o.optInt("hint",t.hint); t.border=o.optInt("border",t.border); t.accent=o.optInt("accent",t.accent);
            t.popupBg=o.optInt("popupBg",t.popupBg); t.popupText=o.optInt("popupText",t.popupText); t.popupBorder=o.optInt("popupBorder",t.popupBorder); t.borderWidth=o.optInt("borderWidth",t.borderWidth);
            t.radius=o.optInt("radius",t.radius); t.gap=o.optInt("gap",t.gap); t.textScale=o.optInt("textScale",t.textScale); t.keyboardHeight=o.optInt("keyboardHeight",t.keyboardHeight);
            t.popupScale=o.optInt("popupScale",t.popupScale); t.popupTextScale=o.optInt("popupTextScale",t.popupTextScale); t.popupRadius=o.optInt("popupRadius",t.popupRadius); t.popupColumns=o.optInt("popupColumns",t.popupColumns);
            t.popupGap=o.optInt("popupGap",t.popupGap); t.popupBorderWidth=o.optInt("popupBorderWidth",t.popupBorderWidth);
            t.popupShape=o.optString("popupShape",t.popupShape); t.popupPosition=o.optString("popupPosition",t.popupPosition); return t;
        }
        JSONObject colors = o.optJSONObject("colors");
        if (colors != null) {
            t.accent = hb(colors,"accent",t.accent); t.key=hb(colors,"keys",t.key); t.hint=hb(colors,"hint_text",t.hint);
            t.space=hb(colors,"spacebar",t.space); t.board=hb(colors,"background",t.board); t.function=hb(colors,"functional_keys",t.function); t.text=hb(colors,"text",t.text);
            t.popupBg=hb(colors,"popup_keys",t.popupBg); t.popupText=hb(colors,"popup_text",t.popupText);
        }
        return t;
    }

    private static int hb(JSONObject colors, String key, int def) {
        JSONObject p=colors.optJSONObject(key); return p==null?def:p.optInt("first",def);
    }

    public static String hex(int color) { return String.format("#%08X", color); }
    public static int parseColor(String s, int def) { try { return Color.parseColor(s.trim()); } catch (Exception e) { return def; } }
}
