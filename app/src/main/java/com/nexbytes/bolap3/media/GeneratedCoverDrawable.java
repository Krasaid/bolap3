package com.nexbytes.bolap3.media;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Cover for music without artwork: a diagonal gradient unique to the album, a faint vinyl ring
 * and the album's initials. Resolution independent, so it is sharp from a 56dp thumbnail to the
 * full-screen player.
 */
public final class GeneratedCoverDrawable extends Drawable {

    private final int[] colors;
    private final String initials;
    private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect textBounds = new Rect();

    public GeneratedCoverDrawable(@Nullable String seed, @Nullable String label) {
        colors = CoverArt.colors(seed);
        initials = CoverArt.initials(label);
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(0x26FFFFFF);
        text.setColor(0xEBFFFFFF);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER);
    }

    /** Start color of the gradient (used as the accent when a song has no artwork). */
    public int getPrimaryColor() {
        return colors[0];
    }

    public static Bitmap toBitmap(@Nullable String seed, @Nullable String label, int sizePx) {
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        GeneratedCoverDrawable drawable = new GeneratedCoverDrawable(seed, label);
        drawable.setBounds(0, 0, sizePx, sizePx);
        drawable.draw(new Canvas(bitmap));
        return bitmap;
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        background.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                colors[0], colors[1], Shader.TileMode.CLAMP));
        float size = Math.min(bounds.width(), bounds.height());
        ring.setStrokeWidth(Math.max(1f, size * 0.035f));
        text.setTextSize(size * (initials.length() > 1 ? 0.34f : 0.42f));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        canvas.drawRect(bounds, background);

        float size = Math.min(bounds.width(), bounds.height());
        float cx = bounds.right - size * 0.12f;
        float cy = bounds.bottom - size * 0.12f;
        canvas.drawCircle(cx, cy, size * 0.42f, ring);
        canvas.drawCircle(cx, cy, size * 0.27f, ring);

        text.getTextBounds(initials, 0, initials.length(), textBounds);
        canvas.drawText(initials, bounds.exactCenterX(), bounds.exactCenterY() - textBounds.exactCenterY(), text);
    }

    @Override
    public void setAlpha(int alpha) {
        background.setAlpha(alpha);
        ring.setAlpha(alpha);
        text.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        background.setColorFilter(colorFilter);
        text.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
