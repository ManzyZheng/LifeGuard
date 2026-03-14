package com.example.firstaid.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class RiskGaugeView extends View {
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private int score = 100;

    public RiskGaugeView(Context context) {
        super(context);
        init();
    }

    public RiskGaugeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RiskGaugeView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float stroke = dpToPx(10f);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(stroke);
        trackPaint.setColor(Color.parseColor("#E9EDF3"));

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setStrokeWidth(stroke);
        progressPaint.setColor(Color.parseColor("#10B981"));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float strokeHalf = progressPaint.getStrokeWidth() / 2f;
        float inset = strokeHalf + dpToPx(2f);
        arcRect.set(inset, inset, getWidth() - inset, getHeight() - inset);

        canvas.drawArc(arcRect, -90f, 360f, false, trackPaint);
        float sweep = Math.max(0f, Math.min(360f, 360f * score / 100f));
        canvas.drawArc(arcRect, -90f, sweep, false, progressPaint);
    }

    public void setScore(int score) {
        this.score = Math.max(0, Math.min(100, score));
        invalidate();
    }

    public void setProgressColor(int color) {
        progressPaint.setColor(color);
        invalidate();
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}
