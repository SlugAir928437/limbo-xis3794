package com.max2idea.android.limbo.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * Slow drifting aurora / nebula wallpaper.
 *
 * <p>Three huge soft light blobs (indigo, violet, cyan) are moved with sinusoidal
 * paths over an almost-black base and finished with a vertical vignette. The
 * gradients are built once and re-positioned through a {@link Matrix} on every
 * frame, so the cost per frame is a handful of big rect fills at ~14fps.</p>
 */
public class AuroraBackgroundView extends View {

    private static final float FRAME_DELAY_MS = 70f;

    private final Paint basePaint = new Paint();
    private final Paint blobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vignettePaint = new Paint();
    private final Matrix shaderMatrix = new Matrix();

    private RadialGradient indigo;
    private RadialGradient violet;
    private RadialGradient cyan;
    private LinearGradient vignette;

    private int width;
    private int height;
    private long startTime;
    private boolean running;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            invalidate();
            postInvalidateDelayed((long) FRAME_DELAY_MS);
        }
    };

    public AuroraBackgroundView(Context context) {
        super(context);
        init();
    }

    public AuroraBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AuroraBackgroundView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        basePaint.setColor(0xFF07080E);
        blobPaint.setStyle(Paint.Style.FILL);
        vignettePaint.setStyle(Paint.Style.FILL);
        startTime = SystemClock.uptimeMillis();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        height = h;
        rebuildShaders();
    }

    private void rebuildShaders() {
        if (width <= 0 || height <= 0) {
            return;
        }
        // radius 1 gradients, positioned & scaled via the local matrix
        indigo = new RadialGradient(0f, 0f, 1f,
                new int[]{0x80566FFF, 0x40566FFF, 0x00566FFF},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        violet = new RadialGradient(0f, 0f, 1f,
                new int[]{0x66B266FF, 0x33B266FF, 0x00B266FF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        cyan = new RadialGradient(0f, 0f, 1f,
                new int[]{0x553AD9FF, 0x223AD9FF, 0x003AD9FF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        vignette = new LinearGradient(0f, 0f, 0f, height,
                new int[]{0x66000000, 0x00000000, 0x00000000, 0x99000000},
                new float[]{0f, 0.22f, 0.68f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        startTime = SystemClock.uptimeMillis();
        postInvalidateDelayed(16);
        post(ticker);
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        removeCallbacks(ticker);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE && isAttachedToWindow()) {
            running = true;
            post(ticker);
        } else {
            running = false;
            removeCallbacks(ticker);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (w != width || h != height) {
            width = w;
            height = h;
            rebuildShaders();
        }

        canvas.drawRect(0f, 0f, w, h, basePaint);

        final float t = (SystemClock.uptimeMillis() - startTime) / 1000f;

        // Indigo glow: upper-left
        drawBlob(canvas, indigo,
                w * (0.16f + 0.05f * (float) Math.sin(t * 0.17)),
                h * (0.10f + 0.035f * (float) Math.cos(t * 0.12)),
                w * 1.05f);

        // Violet glow: right side
        drawBlob(canvas, violet,
                w * (0.92f + 0.07f * (float) Math.cos(t * 0.11)),
                h * (0.32f + 0.05f * (float) Math.sin(t * 0.15)),
                w * 0.95f);

        // Cyan glow: lower-left, behind the content
        drawBlob(canvas, cyan,
                w * (0.30f + 0.09f * (float) Math.sin(t * 0.09)),
                h * (0.92f + 0.04f * (float) Math.cos(t * 0.13)),
                w * 1.15f);

        vignettePaint.setShader(vignette);
        canvas.drawRect(0f, 0f, w, h, vignettePaint);
        vignettePaint.setShader(null);
    }

    private void drawBlob(Canvas canvas, RadialGradient shader, float cx, float cy, float r) {
        if (shader == null) {
            return;
        }
        shaderMatrix.reset();
        shaderMatrix.setScale(r, r);
        shaderMatrix.postTranslate(cx, cy);
        shader.setLocalMatrix(shaderMatrix);
        blobPaint.setShader(shader);
        canvas.drawRect(cx - r, cy - r, cx + r, cy + r, blobPaint);
        blobPaint.setShader(null);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Fill whatever the parent gives us.
        setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec));
    }

    @Override
    public void setBackgroundColor(int color) {
        basePaint.setColor(color);
        invalidate();
    }
}
