package com.aonon.pixelslot;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

/**
 * v0.12 HD bridge.
 *
 * Keeps the proven v0.10/v0.11 engine and v0.12 hype timeline, but replaces the
 * broken experimental atlas with a complete 4495x2823 in-memory HD atlas built
 * from the known-good v0.10 character artwork before any cut-in is drawn.
 *
 * It also removes the common transparent headroom from the three normalized
 * reel girls using one shared crop, so all three keep exactly the same scale
 * basis and baseline. No character-specific shrinking is performed.
 */
public class PixelSlotV12RuntimeHdView extends PixelSlotV12View {
    private static final int ATLAS_W = 4495;
    private static final int ATLAS_H = 2823;
    private static final int FULL_W = 941;
    private static final int FULL_H = 1672;
    private static final int BANNER_X = 2823;
    private static final int BANNER_W = 1672;
    private static final int BANNER_H = 941;

    private static final int REEL_SOURCE_W = 1200;
    private static final int REEL_SOURCE_H = 1600;
    private static final int REEL_CROP_TOP = 190;
    private static final int REEL_CROP_BOTTOM = 40;

    private final Paint hdPaint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);

    public PixelSlotV12RuntimeHdView(Context context) {
        super(context);
        try {
            installHdAtlas();
            tightenSharedReelHeadroom();
        } catch (Throwable t) {
            // Keep the proven renderer alive even if the HD bridge ever fails.
            t.printStackTrace();
        }
    }

    private void installHdAtlas() throws Exception {
        Bitmap atlas = buildAtlasFromKnownGoodV10();
        if (atlas == null) return;

        Field atlasField = PixelSlotV11View.class.getDeclaredField("atlas");
        atlasField.setAccessible(true);
        Bitmap old = (Bitmap) atlasField.get(this);
        atlasField.set(this, atlas);

        Field readyField = PixelSlotV11View.class.getDeclaredField("ready");
        readyField.setAccessible(true);
        readyField.setBoolean(this, true);

        // The old v0.11 experimental bitmap is never used after replacement.
        if (old != null && old != atlas && !old.isRecycled()) old.recycle();
    }

    private Bitmap buildAtlasFromKnownGoodV10() throws Exception {
        StringBuilder encoded = new StringBuilder(100_000);
        byte[] buffer = new byte[8192];
        for (int i = 0; i < 8; i++) {
            try (InputStream in = getContext().getAssets().open("v10_cutin_" + i + ".txt");
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                encoded.append(out.toString(StandardCharsets.UTF_8.name()).trim());
            }
        }

        byte[] webp = Base64.decode(encoded.toString(), Base64.DEFAULT);
        Bitmap strip = BitmapFactory.decodeByteArray(webp, 0, webp.length);
        if (strip == null || strip.getWidth() < 3 || strip.getHeight() < 1) return null;

        final int cellW = strip.getWidth() / 3;
        final int cellH = strip.getHeight();
        Bitmap atlas = Bitmap.createBitmap(ATLAS_W, ATLAS_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(atlas);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        for (int ch = 0; ch < 3; ch++) {
            Bitmap source = Bitmap.createBitmap(strip, ch * cellW, 0, cellW, cellH);

            // Two/three-step filtered enlargement gives a much cleaner Fold8 cut-in
            // than stretching the old 260x462 slice directly to the display.
            Bitmap full = progressiveScale(source, FULL_W, FULL_H);
            canvas.drawBitmap(full, null,
                    new RectF(ch * FULL_W, 0, (ch + 1) * FULL_W, FULL_H), hdPaint);

            // Dedicated wide banner composition: an upper-body/face crop, not the
            // normal reel sprite. Every character uses the same crop geometry.
            int cropH = Math.max(1, Math.round(cellW / (BANNER_W / (float) BANNER_H)));
            int cropTop = Math.round(cellH * 0.055f);
            if (cropTop + cropH > cellH) cropTop = Math.max(0, cellH - cropH);
            Bitmap bannerSource = Bitmap.createBitmap(source, 0, cropTop, cellW,
                    Math.min(cropH, cellH - cropTop));
            Bitmap banner = progressiveScale(bannerSource, BANNER_W, BANNER_H);
            canvas.drawBitmap(banner, null,
                    new RectF(BANNER_X, ch * BANNER_H,
                            BANNER_X + BANNER_W, (ch + 1) * BANNER_H), hdPaint);

            if (source != strip && !source.isRecycled()) source.recycle();
            if (full != source && !full.isRecycled()) full.recycle();
            if (bannerSource != source && !bannerSource.isRecycled()) bannerSource.recycle();
            if (banner != bannerSource && !banner.isRecycled()) banner.recycle();
        }

        if (!strip.isRecycled()) strip.recycle();
        return atlas;
    }

    private Bitmap progressiveScale(Bitmap source, int targetW, int targetH) {
        Bitmap current = source;
        boolean ownsCurrent = false;

        // Enlarge in moderate steps, then make one exact final resize. This is
        // intentionally shared by portrait and banner art.
        while (current.getWidth() < targetW * 0.72f || current.getHeight() < targetH * 0.72f) {
            int nextW = Math.min(targetW, Math.max(current.getWidth() + 1, current.getWidth() * 2));
            int nextH = Math.min(targetH, Math.max(current.getHeight() + 1, current.getHeight() * 2));
            Bitmap next = Bitmap.createScaledBitmap(current, nextW, nextH, true);
            if (ownsCurrent && !current.isRecycled()) current.recycle();
            current = next;
            ownsCurrent = true;
        }

        if (current.getWidth() != targetW || current.getHeight() != targetH) {
            Bitmap exact = Bitmap.createScaledBitmap(current, targetW, targetH, true);
            if (ownsCurrent && !current.isRecycled()) current.recycle();
            current = exact;
        }
        return current;
    }

    private void tightenSharedReelHeadroom() throws Exception {
        Field girlsField = PixelSlotV9View.class.getDeclaredField("girls");
        Field boardsField = PixelSlotV9View.class.getDeclaredField("boards");
        girlsField.setAccessible(true);
        boardsField.setAccessible(true);

        Bitmap[] girls = (Bitmap[]) girlsField.get(this);
        float[][] boards = (float[][]) boardsField.get(this);
        if (girls == null || boards == null) return;

        final int newH = REEL_SOURCE_H - REEL_CROP_TOP - REEL_CROP_BOTTOM;
        for (int ch = 0; ch < 3; ch++) {
            Bitmap old = girls[ch];
            if (old == null || old.getWidth() != REEL_SOURCE_W || old.getHeight() != REEL_SOURCE_H) continue;

            Bitmap cropped = Bitmap.createBitmap(old, 0, REEL_CROP_TOP, REEL_SOURCE_W, newH);
            girls[ch] = cropped;

            // Board coordinates follow the same shared crop. X is unchanged.
            boards[ch][1] = (boards[ch][1] * REEL_SOURCE_H - REEL_CROP_TOP) / newH;
            boards[ch][3] = (boards[ch][3] * REEL_SOURCE_H - REEL_CROP_TOP) / newH;
        }
    }
}
