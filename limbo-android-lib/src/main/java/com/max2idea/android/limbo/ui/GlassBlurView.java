package com.max2idea.android.limbo.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * A real frosted-glass surface: captures the content that scrolls underneath it,
 * downsamples + blurs it and paints the result through a rounded clip, finished
 * with a readability tint, a specular highlight and a 1dp rim.
 *
 * <p>The capture only rasterises the small region that lies behind this view, so
 * a full-screen blurred snapshot is never taken. Call {@link #refresh()} from a
 * scroll listener (it throttles itself) to keep the refraction in sync.</p>
 */
public class GlassBlurView extends View {

    private static final float CAPTURE_SCALE = 0.2f;
    private static final long REFRESH_INTERVAL_MS = 70L;
    private static final int BLUR_RADIUS = 4;

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RectF rect = new RectF();
    private LinearGradient glow;
    private final int[] targetLocation = new int[2];
    private final int[] selfLocation = new int[2];

    private View target;
    private Bitmap bitmap;
    private int bitmapWidth;
    private int bitmapHeight;
    private float cornerRadius = -1f;
    private long lastCapture;
    private boolean capturing;
    private boolean pendingRefresh;

    public GlassBlurView(Context context) {
        super(context);
        init();
    }

    public GlassBlurView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GlassBlurView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        tintPaint.setColor(0x1AFFFFFF);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(dp(1f));
        rimPaint.setColor(0x33FFFFFF);
        innerRimPaint.setStyle(Paint.Style.STROKE);
        innerRimPaint.setStrokeWidth(dp(1f));
        innerRimPaint.setColor(0x12000000);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    /** The view whose pixels should be refracted (usually the scroll view). */
    public void setTarget(View target) {
        this.target = target;
        refresh();
    }

    /** Use a fixed corner radius (dp). By default the view is drawn as a capsule. */
    public void setCornerRadiusDp(float radiusDp) {
        this.cornerRadius = dp(radiusDp);
        invalidate();
    }

    /** Re-capture the backdrop. Safe to call very often; internally throttled. */
    public void refresh() {
        if (target == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        final long now = SystemClock.uptimeMillis();
        if (now - lastCapture < REFRESH_INTERVAL_MS) {
            if (!pendingRefresh) {
                pendingRefresh = true;
                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        pendingRefresh = false;
                        capture();
                        invalidate();
                    }
                }, REFRESH_INTERVAL_MS - (now - lastCapture));
            }
            return;
        }
        capture();
        invalidate();
    }

    private void capture() {
        if (capturing || target == null) {
            return;
        }
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        capturing = true;
        try {
            final int bw = Math.max(1, (int) (w * CAPTURE_SCALE));
            final int bh = Math.max(1, (int) (h * CAPTURE_SCALE));
            if (bitmap == null || bitmapWidth != bw || bitmapHeight != bh) {
                if (bitmap != null) {
                    bitmap.recycle();
                }
                bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                bitmapWidth = bw;
                bitmapHeight = bh;
            }

            target.getLocationOnScreen(targetLocation);
            getLocationOnScreen(selfLocation);

            final Canvas canvas = new Canvas(bitmap);
            bitmap.eraseColor(Color.TRANSPARENT);
            canvas.scale(CAPTURE_SCALE, CAPTURE_SCALE);
            canvas.translate(targetLocation[0] - selfLocation[0],
                    targetLocation[1] - selfLocation[1]);
            target.draw(canvas);

            boxBlur(bitmap, BLUR_RADIUS);
            lastCapture = SystemClock.uptimeMillis();
        } catch (Exception ignored) {
            // A failed capture must never break the UI; the tint below is the fallback.
        } finally {
            capturing = false;
        }
    }

    private static void boxBlur(Bitmap bmp, int radius) {
        final int w = bmp.getWidth();
        final int h = bmp.getHeight();
        if (w < 2 || h < 2 || radius < 1) {
            return;
        }
        final int[] pixels = new int[w * h];
        bmp.getPixels(pixels, 0, w, 0, 0, w, h);
        final int[] temp = new int[Math.max(w, h)];

        // horizontal pass
        for (int y = 0; y < h; y++) {
            final int row = y * w;
            for (int x = 0; x < w; x++) {
                int a = 0, r = 0, g = 0, b = 0, n = 0;
                for (int k = -radius; k <= radius; k++) {
                    final int xx = x + k;
                    if (xx < 0 || xx >= w) {
                        continue;
                    }
                    final int c = pixels[row + xx];
                    a += (c >>> 24);
                    r += (c >> 16) & 0xFF;
                    g += (c >> 8) & 0xFF;
                    b += c & 0xFF;
                    n++;
                }
                temp[x] = ((a / n) << 24) | ((r / n) << 16) | ((g / n) << 8) | (b / n);
            }
            System.arraycopy(temp, 0, pixels, row, w);
        }

        // vertical pass
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int a = 0, r = 0, g = 0, b = 0, n = 0;
                for (int k = -radius; k <= radius; k++) {
                    final int yy = y + k;
                    if (yy < 0 || yy >= h) {
                        continue;
                    }
                    final int c = pixels[yy * w + x];
                    a += (c >>> 24);
                    r += (c >> 16) & 0xFF;
                    g += (c >> 8) & 0xFF;
                    b += c & 0xFF;
                    n++;
                }
                temp[y] = ((a / n) << 24) | ((r / n) << 16) | ((g / n) << 8) | (b / n);
            }
            for (int y = 0; y < h; y++) {
                pixels[y * w + x] = temp[y];
            }
        }
        bmp.setPixels(pixels, 0, w, 0, 0, w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rect.set(0f, 0f, w, h);
        glow = new LinearGradient(0f, 0f, w * 0.35f, h,
                new int[]{0x1FFFFFFF, 0x08FFFFFF, 0x00000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        clipPath.reset();
        clipPath.addRoundRect(rect, cornerRadius >= 0 ? cornerRadius : h / 2f,
                cornerRadius >= 0 ? cornerRadius : h / 2f, Path.Direction.CW);
        refresh();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        final float radius = cornerRadius >= 0 ? cornerRadius : h / 2f;
        if (rect.width() != w || rect.height() != h) {
            rect.set(0f, 0f, w, h);
            clipPath.reset();
            clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW);
        }

        canvas.save();
        canvas.clipPath(clipPath);

        if (bitmap != null) {
            canvas.drawBitmap(bitmap, null, rect, bitmapPaint);
        }

        // readability tint
        canvas.drawRect(rect, tintPaint);

        // specular wash from the top-left
        if (glow == null) {
            glow = new LinearGradient(0f, 0f, w * 0.35f, h,
                    new int[]{0x1FFFFFFF, 0x08FFFFFF, 0x00000000},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        }
        glowPaint.setShader(glow);
        canvas.drawRect(rect, glowPaint);
        glowPaint.setShader(null);

        canvas.restore();

        // rim + inner shadow
        canvas.drawRoundRect(rect.left + 0.5f, rect.top + 0.5f, rect.right - 0.5f,
                rect.bottom - 0.5f, radius, radius, rimPaint);
        canvas.drawRoundRect(rect.left + 1.5f, rect.top + 1.5f, rect.right - 1.5f,
                rect.bottom - 1.5f, radius, radius, innerRimPaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
            bitmap = null;
        }
        super.onDetachedFromWindow();
    }
}
