package com.example.firstaid.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

public class CprOverlayView extends View {

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float chestX = 0.5f;
    private float chestY = 0.5f;
    private boolean hasChest;

    private long lastBeatTimeMs;
    private long beatIntervalMs = 545L;
    private String rhythmHint = "Good Rhythm";

    public CprOverlayView(Context context) {
        super(context);
        init();
    }

    public CprOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CprOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(4));
        ringPaint.setColor(Color.argb(235, 255, 64, 64));

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(Color.argb(75, 255, 64, 64));

        pointPaint.setStyle(Paint.Style.FILL);
        pointPaint.setColor(Color.WHITE);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(sp(16));
        textPaint.setFakeBoldText(true);
    }

    public void updateChestPoint(float normalizedX, float normalizedY) {
        hasChest = true;
        chestX = clamp01(normalizedX);
        chestY = clamp01(normalizedY);
        invalidate();
    }

    public void clearChestPoint() {
        hasChest = false;
        invalidate();
    }

    public void onBeat(long intervalMs) {
        if (intervalMs > 0L) {
            beatIntervalMs = intervalMs;
        }
        lastBeatTimeMs = System.currentTimeMillis();
        if (beatIntervalMs < 500L) {
            rhythmHint = "Press Slower";
        } else if (beatIntervalMs > 650L) {
            rhythmHint = "Press Faster";
        } else {
            rhythmHint = "Good Rhythm";
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = hasChest ? chestX * getWidth() : getWidth() * 0.5f;
        float cy = hasChest ? chestY * getHeight() : getHeight() * 0.5f;

        float baseRadius = Math.min(getWidth(), getHeight()) * 0.08f;
        float pulse = getPulseFactor();
        float radius = baseRadius * (0.85f + pulse * 0.45f);

        canvas.drawCircle(cx, cy, radius, fillPaint);
        canvas.drawCircle(cx, cy, radius, ringPaint);
        canvas.drawCircle(cx, cy, dp(4), pointPaint);

        String anchorText = hasChest ? "Chest Target Locked" : "Searching Chest...";
        canvas.drawText(anchorText, dp(16), dp(28), textPaint);
        canvas.drawText("BPM Target: 110", dp(16), dp(52), textPaint);
        canvas.drawText("Rhythm: " + rhythmHint, dp(16), dp(76), textPaint);

        // Keep pulse animation smooth between beats.
        postInvalidateOnAnimation();
    }

    private float getPulseFactor() {
        if (lastBeatTimeMs <= 0L) {
            return 0f;
        }
        long elapsed = System.currentTimeMillis() - lastBeatTimeMs;
        if (elapsed >= beatIntervalMs) {
            return 0f;
        }
        float t = 1f - (elapsed * 1f / beatIntervalMs);
        return t;
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }

    private float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
