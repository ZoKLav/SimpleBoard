package com.zoeykl.simpleboard;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

final class ColorPickerDialog {
    interface Listener { void onColor(int color); }

    static void show(Context c, String title, int initial, Listener listener) {
        final float[] hsv = new float[3];
        Color.colorToHSV(initial, hsv);
        final int[] alpha = {Color.alpha(initial)};

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(c, 16);
        root.setPadding(pad, dp(c, 8), pad, 0);

        final TextView preview = new TextView(c);
        preview.setMinHeight(dp(c, 54));
        preview.setGravity(android.view.Gravity.CENTER);
        root.addView(preview, new LinearLayout.LayoutParams(-1, dp(c, 54)));

        final SaturationValueView sv = new SaturationValueView(c);
        sv.setHue(hsv[0]);
        sv.setSaturationValue(hsv[1], hsv[2]);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(-1, dp(c, 180));
        svLp.topMargin = dp(c, 12);
        root.addView(sv, svLp);

        TextView hueLabel = label(c, "Hue"); root.addView(hueLabel);
        SeekBar hue = new SeekBar(c); hue.setMax(360); hue.setProgress(Math.round(hsv[0])); root.addView(hue, new LinearLayout.LayoutParams(-1, -2));

        TextView alphaLabel = label(c, "Opacity: " + Math.round(alpha[0] * 100f / 255f) + "%"); root.addView(alphaLabel);
        SeekBar a = new SeekBar(c); a.setMax(255); a.setProgress(alpha[0]); root.addView(a, new LinearLayout.LayoutParams(-1, -2));

        TextView hex = label(c, ThemeConfig.hex(initial));
        hex.setTextSize(15);
        hex.setPadding(0, dp(c, 8), 0, 0);
        root.addView(hex);

        final Runnable update = () -> {
            float[] cur = new float[]{hue.getProgress(), sv.getSaturation(), sv.getValue()};
            int rgb = Color.HSVToColor(alpha[0], cur);
            GradientDrawable g = new GradientDrawable();
            g.setColor(rgb);
            g.setCornerRadius(dp(c, 8));
            g.setStroke(dp(c, 1), Color.GRAY);
            preview.setBackground(g);
            hex.setText(ThemeConfig.hex(rgb));
            alphaLabel.setText("Opacity: " + Math.round(alpha[0] * 100f / 255f) + "%");
        };

        sv.setListener((sat, value) -> update.run());
        hue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean from) { sv.setHue(p); update.run(); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        a.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean from) { alpha[0] = p; update.run(); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        update.run();

        AlertDialog dialog = new AlertDialog.Builder(c)
                .setTitle(title)
                .setView(root)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int out = Color.HSVToColor(alpha[0], new float[]{hue.getProgress(), sv.getSaturation(), sv.getValue()});
            listener.onColor(out);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private static TextView label(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(Color.DKGRAY);
        t.setPadding(0, dp(c, 8), 0, 0);
        return t;
    }

    private static int dp(Context c, float v) { return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f); }

    private static final class SaturationValueView extends View {
        interface Listener { void onChanged(float saturation, float value); }
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float hue = 0f, saturation = 1f, value = 1f;
        private Listener listener;

        SaturationValueView(Context c) { super(c); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        void setHue(float h) { hue = h; invalidate(); }
        void setSaturationValue(float s, float v) { saturation = clamp01(s); value = clamp01(v); invalidate(); }
        float getSaturation() { return saturation; }
        float getValue() { return value; }
        void setListener(Listener l) { listener = l; }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            int hueColor = Color.HSVToColor(new float[]{hue, 1f, 1f});
            Shader sat = new LinearGradient(0, 0, getWidth(), 0, Color.WHITE, hueColor, Shader.TileMode.CLAMP);
            Shader val = new LinearGradient(0, 0, 0, getHeight(), Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP);
            paint.setShader(new ComposeShader(sat, val, PorterDuff.Mode.MULTIPLY));
            c.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setShader(null);
            float x = saturation * getWidth();
            float y = (1f - value) * getHeight();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 2));
            paint.setColor(Color.WHITE);
            c.drawCircle(x, y, dp(getContext(), 7), paint);
            paint.setStrokeWidth(dp(getContext(), 1));
            paint.setColor(Color.BLACK);
            c.drawCircle(x, y, dp(getContext(), 9), paint);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN || e.getActionMasked() == MotionEvent.ACTION_MOVE || e.getActionMasked() == MotionEvent.ACTION_UP) {
                saturation = clamp01(e.getX() / Math.max(1f, getWidth()));
                value = clamp01(1f - e.getY() / Math.max(1f, getHeight()));
                invalidate();
                if (listener != null) listener.onChanged(saturation, value);
                return true;
            }
            return super.onTouchEvent(e);
        }

        private float clamp01(float f) { return Math.max(0f, Math.min(1f, f)); }
    }
}
