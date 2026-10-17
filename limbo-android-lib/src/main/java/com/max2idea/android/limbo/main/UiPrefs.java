package com.max2idea.android.limbo.main;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/** User interface preferences: theme mode, app wallpaper and per-machine icons. */
public final class UiPrefs {

    private static final String PREFS = "limbo_ui_prefs";
    private static final String KEY_THEME = "theme_mode";
    private static final String KEY_WALLPAPER = "wallpaper_uri";
    private static final String KEY_ICON_PREFIX = "machine_icon_";

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    private UiPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int getThemeMode(Context context) {
        return prefs(context).getInt(KEY_THEME, THEME_SYSTEM);
    }

    public static void setThemeMode(Context context, int mode) {
        prefs(context).edit().putInt(KEY_THEME, mode).apply();
    }

    public static void applyTheme(Context context) {
        switch (getThemeMode(context)) {
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    public static String getWallpaper(Context context) {
        return prefs(context).getString(KEY_WALLPAPER, null);
    }

    public static void setWallpaper(Context context, String uri) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (uri == null) {
            editor.remove(KEY_WALLPAPER);
        } else {
            editor.putString(KEY_WALLPAPER, uri);
        }
        editor.apply();
    }

    public static String getMachineIcon(Context context, String machine) {
        if (machine == null) {
            return null;
        }
        return prefs(context).getString(KEY_ICON_PREFIX + machine, null);
    }

    public static void setMachineIcon(Context context, String machine, String uri) {
        if (machine == null) {
            return;
        }
        SharedPreferences.Editor editor = prefs(context).edit();
        if (uri == null) {
            editor.remove(KEY_ICON_PREFIX + machine);
        } else {
            editor.putString(KEY_ICON_PREFIX + machine, uri);
        }
        editor.apply();
    }
}
