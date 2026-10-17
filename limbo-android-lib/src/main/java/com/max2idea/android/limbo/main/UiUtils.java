package com.max2idea.android.limbo.main;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.DisplayMetrics;

import java.io.InputStream;

/** Small drawing/format helpers shared by the home screen and the config screen. */
public final class UiUtils {

    private UiUtils() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static String formatMemory(int mb) {
        if (mb <= 0) {
            return "—";
        }
        if (mb >= 1024) {
            return String.format(java.util.Locale.US, "%.1f GB", mb / 1024f);
        }
        return mb + " MB";
    }

    /** Decode a content/file uri downsampled to roughly {@code maxDim} px on the long edge. */
    public static Bitmap loadSampled(Context context, Uri uri, int maxDim) {
        if (context == null || uri == null) {
            return null;
        }
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) {
                    sample *= 2;
                }
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = Math.max(1, sample);
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Two-pass box blur; used for wallpapers (small bitmaps only). */
    public static Bitmap boxBlur(Bitmap source, int radius) {
        if (source == null || radius < 1) {
            return source;
        }
        final int w = source.getWidth();
        final int h = source.getHeight();
        if (w < 2 || h < 2) {
            return source;
        }
        try {
            final int[] pixels = new int[w * h];
            source.getPixels(pixels, 0, w, 0, 0, w, h);
            final int[] temp = new int[Math.max(w, h)];

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
            source.setPixels(pixels, 0, w, 0, 0, w, h);
            return source;
        } catch (Throwable ignored) {
            return source;
        }
    }

    public static int screenWidth(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return metrics.widthPixels;
    }
}
