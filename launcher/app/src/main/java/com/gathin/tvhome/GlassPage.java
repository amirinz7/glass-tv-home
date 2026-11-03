package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** Shared look for the full-screen pages (weather, prayer times): the home wallpaper behind real glass tiles. */
final class GlassPage {
    private GlassPage() {}

    /** The home screen's wallpaper, centre-cropped and dimmed a little, or a plain dark sky if it is not in memory. */
    static View wallpaper(Activity a) {
        return picture(a, MainActivity.baseShown != null ? MainActivity.baseShown : MainActivity.shownWallpaper, MainActivity.baseShown != null ? MainActivity.baseDim : MainActivity.shownDim);
    }

    /** A picture centre-cropped behind a dark scrim, or a plain dark sky when there is none. */
    static View picture(Activity a, Bitmap bmp, boolean dim) {
        return picture(a, bmp, dim, 0x80000000);
    }

    static View picture(Activity a, Bitmap bmp, boolean dim, int scrimColor) {
        FrameLayout box = new FrameLayout(a);
        if (bmp != null && !bmp.isRecycled()) {
            // The glass tiles frost this very picture, not whatever app wallpaper the home screen showed last.
            try {
                android.util.DisplayMetrics dm = a.getResources().getDisplayMetrics();
                Bitmap blur = Backdrop.blur(bmp, dm.widthPixels, dm.heightPixels, dim ? 0xFF7A7A7A : 0);
                if (blur != null) Backdrop.present(blur, 0);
            } catch (Throwable ignored) { }
            ImageView iv = new ImageView(a);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(bmp);
            if (dim) iv.setColorFilter(0xFF7A7A7A, PorterDuff.Mode.MULTIPLY);
            box.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            box.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF14223A, 0xFF0B1220, 0xFF1A1033}));
        }
        View scrim = new View(a);
        scrim.setBackgroundColor(scrimColor);
        box.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return box;
    }

    /** The sky for the weather page: a gradient picked by the weather, frosted by the glass like any wallpaper. */
    static View sky(Activity a, int top, int bottom) {
        Bitmap bmp = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(bmp);
        android.graphics.Paint p = new android.graphics.Paint();
        p.setShader(new android.graphics.LinearGradient(0, 0, 0, 180, top, bottom, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 320, 180, p);
        return picture(a, bmp, false, 0x33000000);
    }

    /** A dark veil with rounded corners, laid over glass so text stays readable on bright pictures. */
    static Drawable veil(float radiusPx, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(radiusPx);
        g.setColor(color);
        return g;
    }

    /** Frosted glass from the home screen's blurred wallpaper; a bright rim and light fill while selected. */
    static void glass(View v, float density, float radiusDp, boolean focused) {
        GlassDrawable g = new GlassDrawable(v, radiusDp * density, density, null, 0.5f);
        Backdrop.watch(v);
        GradientDrawable tint = new GradientDrawable();
        tint.setCornerRadius(radiusDp * density);
        tint.setColor(focused ? 0x33FFFFFF : 0x14FFFFFF);
        tint.setStroke((int) ((focused ? 2f : 1f) * density), focused ? 0xE6FFFFFF : 0x4DFFFFFF);
        v.setBackground(new LayerDrawable(new Drawable[]{g, veil(radiusDp * density, 0x70101522), tint}));
    }

    /** Selected tiles grow and come in front of their neighbours, so nothing overlaps and crops them. */
    static void lift(View v, float density, boolean focused, float scale) {
        v.setOutlineProvider(null);
        v.setTranslationZ(focused ? 12 * density : 0f);
        // Rows and columns above it too, or a neighbouring row would draw over the grown tile and crop it.
        for (android.view.ViewParent p = v.getParent(); p instanceof ViewGroup && ((ViewGroup) p).getParent() instanceof ViewGroup; p = p.getParent()) {
            ((ViewGroup) p).setTranslationZ(focused ? 6 * density : 0f);
            ((ViewGroup) p).setOutlineProvider(null);
            ((ViewGroup) p).setClipChildren(false);
        }
        // Selected tiles keep their size (growing them showed their edges cut off); the brighter rim shows the focus.
        float s = 1f;
        v.animate().scaleX(focused ? s : 1f).scaleY(focused ? s : 1f).setDuration(140).start();
    }

    /** Gives any dialog the same frosted glass as the rest of the launcher; call it right after show(). */
    static void glassDialog(android.app.Dialog d) {
        try {
            android.view.Window w = d.getWindow();
            if (w == null) return;
            final float density = d.getContext().getResources().getDisplayMetrics().density;
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            w.setDimAmount(0.30f);
            View panel = null;
            int id = d.getContext().getResources().getIdentifier("parentPanel", "id", "android");
            if (id != 0) panel = w.findViewById(id);
            if (panel == null) panel = w.findViewById(android.R.id.content);
            if (panel == null) return;
            final float r = 26 * density;
            final View target = panel;
            target.setBackground(new android.graphics.drawable.LayerDrawable(new Drawable[]{new GlassDrawable(target, r, density, null, 0.6f), veil(r, 0x400B1220)}));
            Backdrop.watch(target);
            target.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
                }
            });
            target.setClipToOutline(true);
        } catch (Throwable ignored) { }
    }
}
