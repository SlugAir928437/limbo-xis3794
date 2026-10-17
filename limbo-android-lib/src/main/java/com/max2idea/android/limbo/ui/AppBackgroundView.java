package com.max2idea.android.limbo.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.limbo.emu.lib.R;
import com.max2idea.android.limbo.main.UiUtils;

import java.lang.ref.WeakReference;

/**
 * Neutral app background: a soft vertical gradient, optionally replaced by a
 * user picked wallpaper (downscaled + blurred + scrimmed so foreground content
 * always stays readable).
 */
public class AppBackgroundView extends View {

    private static String cachedKey;
    private static WeakReference<Bitmap> cachedBitmap = new WeakReference<>(null);

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();

    private Bitmap wallpaper;
    private LinearGradient gradient;
    private int scrimColor;
    private int bgTop;
    private int bgBottom;
    private long loadToken;

    public AppBackgroundView(Context context) {
        super(context);
        init();
    }

    public AppBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AppBackgroundView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        bgTop = ContextCompat.getColor(getContext(), R.color.limbo_bg_top);
        bgBottom = ContextCompat.getColor(getContext(), R.color.limbo_bg);
        scrimColor = ContextCompat.getColor(getContext(), R.color.limbo_scrim);
        scrimPaint.setColor(scrimColor);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        gradient = new LinearGradient(0f, 0f, 0f, h, bgTop, bgBottom, Shader.TileMode.CLAMP);
    }

    public void setWallpaperUri(Uri uri) {
        final long token = ++loadToken;
        if (uri == null) {
            wallpaper = null;
            cachedKey = null;
            cachedBitmap = new WeakReference<>(null);
            invalidate();
            return;
        }
        final String key = uri.toString();
        Bitmap cached = cachedBitmap.get();
        if (cached != null && !cached.isRecycled() && key.equals(cachedKey)) {
            wallpaper = cached;
            invalidate();
            return;
        }
        final Context appContext = getContext().getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = UiUtils.loadSampled(appContext, uri, 420);
                if (bitmap != null) {
                    bitmap = UiUtils.boxBlur(bitmap, 5);
                }
                final Bitmap result = bitmap;
                post(new Runnable() {
                    @Override
                    public void run() {
                        if (token != loadToken) {
                            return;
                        }
                        wallpaper = result;
                        cachedKey = key;
                        cachedBitmap = new WeakReference<>(result);
                        invalidate();
                    }
                });
            }
        }, "limbo-wallpaper").start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        gradientPaint.setShader(gradient);
        canvas.drawRect(0f, 0f, w, h, gradientPaint);
        gradientPaint.setShader(null);

        final Bitmap bitmap = wallpaper;
        if (bitmap != null && !bitmap.isRecycled()) {
            final float scale = Math.max(w / (float) bitmap.getWidth(), h / (float) bitmap.getHeight());
            matrix.reset();
            matrix.setScale(scale, scale);
            matrix.postTranslate((w - bitmap.getWidth() * scale) / 2f,
                    (h - bitmap.getHeight() * scale) / 2f);
            canvas.drawBitmap(bitmap, matrix, bitmapPaint);
            canvas.drawRect(0f, 0f, w, h, scrimPaint);
        }
    }
}
