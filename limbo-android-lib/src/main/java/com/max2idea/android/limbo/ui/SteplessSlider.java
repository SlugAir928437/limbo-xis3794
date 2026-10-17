package com.max2idea.android.limbo.ui;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.core.content.ContextCompat;

import com.limbo.emu.lib.R;

/**
 * A continuous (stepless) slider with a glassy gradient track and a spring-ish
 * thumb. Values are snapped to {@code step} so the host can map progress to
 * sensible units (e.g. 8 MB of RAM at a time) while the drag itself stays smooth.
 */
public class SteplessSlider extends View {

    public interface OnValueChangedListener {
        void onValueChanged(int value, boolean fromUser);

        void onValueCommitted(int value);
    }

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int min = 0;
    private int max = 100;
    private int step = 1;
    private int value = 0;

    private float trackHeight;
    private float thumbRadius;
    private float startX;
    private float endX;
    private float centerY;
    private float thumbX;
    private float thumbScale = 1f;

    private boolean dragging;
    private OnValueChangedListener listener;
    private int lastHapticValue = Integer.MIN_VALUE;
    private int accentColor;
    private int surfaceColor;

    public SteplessSlider(Context context) {
        super(context);
        init();
    }

    public SteplessSlider(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SteplessSlider(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        final float density = getResources().getDisplayMetrics().density;
        trackHeight = 6f * density;
        thumbRadius = 11f * density;

        accentColor = ContextCompat.getColor(getContext(), R.color.limbo_accent);
        surfaceColor = ContextCompat.getColor(getContext(), R.color.limbo_surface);

        trackPaint.setColor(ContextCompat.getColor(getContext(), R.color.limbo_slider_track));
        activePaint.setColor(accentColor);
        trackPaint.setStyle(Paint.Style.FILL);

        thumbPaint.setStyle(Paint.Style.FILL);
        thumbPaint.setColor(accentColor);
        thumbRingPaint.setStyle(Paint.Style.STROKE);
        thumbRingPaint.setStrokeWidth(2f * density);
        thumbRingPaint.setColor(surfaceColor);
        thumbGlowPaint.setStyle(Paint.Style.FILL);
        thumbGlowPaint.setColor((accentColor & 0x00FFFFFF) | 0x33000000);
        thumbCorePaint.setStyle(Paint.Style.FILL);
        thumbCorePaint.setColor(surfaceColor);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setElevation(4f * density);
        }
        setFocusable(true);
        setClickable(true);
    }

    public void setRange(int min, int max, int step) {
        this.min = min;
        this.max = Math.max(min + 1, max);
        this.step = Math.max(1, step);
        setValue(this.value);
    }

    public void setOnValueChangedListener(OnValueChangedListener listener) {
        this.listener = listener;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int newValue) {
        setValueInternal(snap(newValue), false);
    }

    private int snap(int raw) {
        int clamped = Math.max(min, Math.min(max, raw));
        if (step > 1) {
            clamped = min + Math.round((clamped - min) / (float) step) * step;
            clamped = Math.max(min, Math.min(max, clamped));
        }
        return clamped;
    }

    private void setValueInternal(int newValue, boolean fromUser) {
        final boolean changed = newValue != value;
        value = newValue;
        recalcThumbX();
        if (changed) {
            if (fromUser) {
                final int bucket = (int) ((value - min) / (float) Math.max(1, max - min) * 10);
                if (bucket != lastHapticValue) {
                    lastHapticValue = bucket;
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }
            }
            if (listener != null) {
                listener.onValueChanged(value, fromUser);
            }
        }
        invalidate();
    }

    private void recalcThumbX() {
        if (endX <= startX) {
            thumbX = startX;
            return;
        }
        final float fraction = (value - min) / (float) Math.max(1, max - min);
        thumbX = startX + fraction * (endX - startX);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        startX = getPaddingLeft() + thumbRadius + dp(2f);
        endX = w - getPaddingRight() - thumbRadius - dp(2f);
        centerY = h / 2f;
        recalcThumbX();
        activePaint.setShader(new LinearGradient(startX, 0f, endX, 0f,
                new int[]{accentColor, accentColor}, null, Shader.TileMode.CLAMP));
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final float radius = trackHeight / 2f;
        canvas.drawRoundRect(startX, centerY - radius, endX, centerY + radius,
                radius, radius, trackPaint);

        if (thumbX > startX) {
            canvas.drawRoundRect(startX, centerY - radius, thumbX, centerY + radius,
                    radius, radius, activePaint);
        }

        // glow
        canvas.drawCircle(thumbX, centerY, thumbRadius * 2.1f * thumbScale, thumbGlowPaint);

        // thumb
        canvas.save();
        canvas.translate(thumbX, centerY);
        canvas.scale(thumbScale, thumbScale);
        canvas.translate(-thumbX, -centerY);
        thumbPaint.setShader(null);
        thumbPaint.setColor(accentColor);
        canvas.drawCircle(thumbX, centerY, thumbRadius, thumbPaint);
        canvas.drawCircle(thumbX, centerY, thumbRadius * 0.42f, thumbCorePaint);
        canvas.drawCircle(thumbX, centerY, thumbRadius - 1f, thumbRingPaint);
        canvas.restore();
    }

    private void animateThumbTo(float target) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(this, "thumbScale", target);
        animator.setDuration(dragging ? 160 : 220);
        animator.setInterpolator(dragging
                ? new OvershootInterpolator(1.6f) : new DecelerateInterpolator());
        animator.start();
    }

    public void setThumbScale(float scale) {
        this.thumbScale = scale;
        invalidate();
    }

    public float getThumbScale() {
        return thumbScale;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                animateThumbTo(1.22f);
                updateFromTouch(event.getX());
                return true;
            case MotionEvent.ACTION_MOVE:
                updateFromTouch(event.getX());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                animateThumbTo(1f);
                if (listener != null) {
                    listener.onValueCommitted(value);
                }
                performClick();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void updateFromTouch(float x) {
        if (endX <= startX) {
            return;
        }
        final float fraction = Math.max(0f, Math.min(1f, (x - startX) / (endX - startX)));
        final int raw = Math.round(min + fraction * (max - min));
        setValueInternal(snap(raw), true);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
