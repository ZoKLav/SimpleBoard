package com.zoeykl.simpleboard;

public final class KeySpec {
    public enum Action { TEXT, SHIFT, DELETE, ENTER, SPACE, SYMBOLS, ALPHA, NUMPAD, EMOJI, VOICE, SETTINGS, LEFT, RIGHT, PASTE }
    public enum Kind { NORMAL, FUNCTION, SPACE, ACTION }

    public final String label;
    public final String output;
    public final Action action;
    public final Kind kind;
    public final float weight;

    public KeySpec(String label, String output, Action action, Kind kind, float weight) {
        this.label = label;
        this.output = output;
        this.action = action;
        this.kind = kind;
        this.weight = weight;
    }

    public static KeySpec text(String s) { return new KeySpec(s, s, Action.TEXT, Kind.NORMAL, 1f); }
    public static KeySpec text(String label, String output, float weight) { return new KeySpec(label, output, Action.TEXT, Kind.NORMAL, weight); }
    public static KeySpec function(String label, Action action, float weight) { return new KeySpec(label, "", action, Kind.FUNCTION, weight); }
    public static KeySpec space(float weight) { return new KeySpec("space", " ", Action.SPACE, Kind.SPACE, weight); }
    public static KeySpec enter(float weight) { return new KeySpec("↵", "", Action.ENTER, Kind.ACTION, weight); }
}
