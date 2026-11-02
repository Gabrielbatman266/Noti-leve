package com.gabriel.notileve;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.view.View;

/** O anel de tempo: enche/esvazia conforme o silêncio passa. */
class RingView extends View {
    static final int MODE_IDLE = 0, MODE_TIMED = 1, MODE_FREE = 2, MODE_OVER = 3;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final float d;

    private int mode = MODE_IDLE;
    private float progress = 1f;
    private String top = "", main = "", sub = "";
    private SweepGradient grad, gradOver;

    RingView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(12 * d);
        track.setColor(0xFF15171F);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(12 * d);
        arc.setStrokeCap(Paint.Cap.ROUND);
        dot.setColor(Color.WHITE);
        big.setColor(0xFFECEFF7);
        big.setTextAlign(Paint.Align.CENTER);
        big.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        small.setColor(0xFF8A90A2);
        small.setTextAlign(Paint.Align.CENTER);
        small.setTextSize(13 * d);
    }

    void set(int mode, float progress, String top, String main, String sub) {
        this.mode = mode;
        this.progress = Math.max(0f, Math.min(1f, progress));
        this.top = top;
        this.main = main;
        this.sub = sub;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int size = MeasureSpec.getSize(w);
        int s = (int) Math.min(size, 280 * d);
        setMeasuredDimension(size, s);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float cx = w / 2f, cy = h / 2f;
        Matrix m = new Matrix();
        m.setRotate(-90, cx, cy);
        grad = new SweepGradient(cx, cy,
                new int[]{0xFF5EEAD4, 0xFFA78BFA, 0xFF5EEAD4}, null);
        grad.setLocalMatrix(m);
        gradOver = new SweepGradient(cx, cy,
                new int[]{0xFFF87171, 0xFFFBBF24, 0xFFF87171}, null);
        gradOver.setLocalMatrix(m);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float r = Math.min(w, h) / 2f - 14 * d;
        float cx = w / 2f, cy = h / 2f;
        box.set(cx - r, cy - r, cx + r, cy + r);
        c.drawCircle(cx, cy, r, track);

        arc.setShader(mode == MODE_OVER ? gradOver : grad);
        arc.setAlpha(mode == MODE_IDLE ? 90 : 255);

        if (mode == MODE_FREE) {
            // modo "até eu desligar": um cometa girando devagar
            float start = (System.currentTimeMillis() / 12) % 360;
            c.drawArc(box, start, 110, false, arc);
            postInvalidateDelayed(40);
        } else if (mode == MODE_OVER) {
            float pulse = (float) (0.55 + 0.45 * Math.sin(System.currentTimeMillis() / 260.0));
            arc.setAlpha((int) (255 * pulse));
            c.drawArc(box, -90, 360, false, arc);
            postInvalidateDelayed(50);
        } else {
            float sweep = 360f * progress;
            c.drawArc(box, -90, sweep, false, arc);
            if (mode == MODE_TIMED && progress > 0.005f) {
                double a = Math.toRadians(-90 + sweep);
                c.drawCircle(cx + (float) (r * Math.cos(a)), cy + (float) (r * Math.sin(a)), 5 * d, dot);
            }
        }

        big.setTextSize((main.length() > 5 ? 44 : 54) * d);
        c.drawText(main, cx, cy + 16 * d, big);
        small.setColor(0xFF8A90A2);
        c.drawText(sub, cx, cy + 44 * d, small);
        small.setColor(mode == MODE_OVER ? 0xFFFBBF24 : 0xFF5EEAD4);
        c.drawText(top, cx, cy - 42 * d, small);
    }
}
