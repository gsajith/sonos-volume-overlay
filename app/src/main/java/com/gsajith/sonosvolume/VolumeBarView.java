package com.gsajith.sonosvolume;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** A dark pill with a white fill bar and the volume number (or "Muted") on the right. */
public class VolumeBarView extends View {
    private final float dp;
    private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int volume;
    private boolean muted;

    public VolumeBarView(Context context) {
        super(context);
        dp = context.getResources().getDisplayMetrics().density;
        background.setColor(Color.argb(210, 20, 20, 20));
        track.setColor(Color.argb(70, 255, 255, 255));
        fill.setColor(Color.WHITE);
        text.setColor(Color.WHITE);
        text.setTextSize(16 * dp);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextAlign(Paint.Align.RIGHT);
    }

    void setLevel(int volume, boolean muted) {
        this.volume = volume;
        this.muted = muted;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight(), pad = 16 * dp;
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, h / 2, h / 2, background);

        // Number on the right, bar fills the rest.
        float textRight = w - pad;
        float barRight = textRight - 56 * dp;
        float barH = 6 * dp, barTop = (h - barH) / 2;
        rect.set(pad, barTop, barRight, barTop + barH);
        canvas.drawRoundRect(rect, barH / 2, barH / 2, track);
        if (!muted) {
            rect.right = pad + (barRight - pad) * volume / 100f;
            canvas.drawRoundRect(rect, barH / 2, barH / 2, fill);
        }

        String label = muted ? "Muted" : String.valueOf(volume);
        float baseline = h / 2 - (text.descent() + text.ascent()) / 2;
        canvas.drawText(label, textRight, baseline, text);
    }
}
