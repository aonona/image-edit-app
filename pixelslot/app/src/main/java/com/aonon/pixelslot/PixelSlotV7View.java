package com.aonon.pixelslot;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.lang.reflect.Field;

/**
 * v0.7: visual correction overlay.
 *
 * The v0.5 game/animation logic stays untouched. This layer rebuilds the visual
 * sprites before the first frame, removes neighboring sprite fragments, aligns
 * every character to one baseline, measures the white number board from pixels,
 * and redraws the reel/cut-in/premium trio cleanly over the v0.5 rendering.
 */
public class PixelSlotV7View extends PixelSlotV5View {
    private static final int ALPHA_THRESHOLD = 16;
    private static final int NORMAL_W = 400;
    private static final int NORMAL_H = 620;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private Bitmap[][] sourceBitmaps;
    private final Bitmap[][] cleanBitmaps = new Bitmap[3][3];
    private final RectF[] numberBoards = new RectF[3];

    private Field fShown, fSpinning, fReachTriggered, fReachStage;
    private Field fCelebrationUntil, fCutInStart, fCutInUntil, fCutInTitle, fCutInMessage, fCutInType;
    private Field fPremiumRevealStart, fPremiumRevealUntil, fRevivalActive;
    private boolean ready;

    private static final int[] ACCENTS = {
            Color.rgb(255, 126, 170),
            Color.rgb(166, 139, 241),
            Color.rgb(92, 218, 220)
    };

    public PixelSlotV7View(Context context) {
        super(context);
        initReflection();
        if (ready) prepareSprites();
    }

    private void initReflection() {
        try {
            Class<?> c = PixelSlotV5View.class;
            sourceBitmaps = (Bitmap[][]) field(c, "characterBitmaps").get(this);
            fShown = field(c, "shown");
            fSpinning = field(c, "spinning");
            fReachTriggered = field(c, "reachTriggered");
            fReachStage = field(c, "reachStage");
            fCelebrationUntil = field(c, "celebrationUntil");
            fCutInStart = field(c, "cutInStart");
            fCutInUntil = field(c, "cutInUntil");
            fCutInTitle = field(c, "cutInTitle");
            fCutInMessage = field(c, "cutInMessage");
            fCutInType = field(c, "cutInType");
            fPremiumRevealStart = field(c, "premiumRevealStart");
            fPremiumRevealUntil = field(c, "premiumRevealUntil");
            fRevivalActive = field(c, "revivalActive");
            ready = true;
        } catch (Throwable t) {
            ready = false;
            t.printStackTrace();
        }
    }

    private static Field field(Class<?> c, String name) throws Exception {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private long gl(Field f) throws Exception { return f.getLong(this); }
    private int gi(Field f) throws Exception { return f.getInt(this); }
    private boolean gb(Field f) throws Exception { return f.getBoolean(this); }
    private String gs(Field f) throws Exception { return (String) f.get(this); }

    private void prepareSprites() {
        try {
            for (int ch = 0; ch < 3; ch++) {
                for (int state = 0; state < 3; state++) {
                    Bitmap cleaned = keepDominantSprite(sourceBitmaps[ch][state]);
                    cleanBitmaps[ch][state] = normalizeSprite(cleaned, NORMAL_W, NORMAL_H);
                }
                numberBoards[ch] = detectNumberBoard(cleanBitmaps[ch][0]);
            }
        } catch (Throwable t) {
            ready = false;
            t.printStackTrace();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!ready) return;
        try {
            long now = System.currentTimeMillis();
            int w = getWidth();
            int h = getHeight();
            boolean premium = now < gl(fPremiumRevealUntil);
            boolean revival = gb(fRevivalActive);

            if (!premium && !revival) drawCorrectedReels(canvas, w, h, now);
            if (now < gl(fCutInUntil)) drawCorrectedCutIn(canvas, w, h, now);
            if (premium && now - gl(fPremiumRevealStart) > 5200L) {
                drawCorrectedPremiumTrio(canvas, w, h, now);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void drawCorrectedReels(Canvas canvas, int w, int h, long now) throws Exception {
        float[] cx = {w * 0.19f, w * 0.50f, w * 0.81f};
        float panelTop = h * 0.295f;
        float panelBottom = h * 0.645f;

        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(55, 37, 76));
        canvas.drawRect(w * 0.055f, panelTop, w * 0.945f, panelBottom, p);

        int[] shown = (int[]) fShown.get(this);
        boolean spinning = gb(fSpinning);
        boolean reaching = spinning && gb(fReachTriggered);
        int reachStage = gi(fReachStage);
        boolean celebrating = now < gl(fCelebrationUntil);
        float baseline = h * 0.590f;

        for (int i = 0; i < 3; i++) {
            int state = celebrating ? 2 : ((reaching && i == 2 && reachStage >= 2) ? 1 : 0);
            float jump = 0f;
            if (celebrating) {
                float t = (now % 390L) / 390f;
                jump = (float) -Math.abs(Math.sin(t * Math.PI)) * h * (0.022f + i * 0.003f);
            }
            float tremble = 0f;
            if (reaching && i == 2 && reachStage >= 3) {
                tremble = (float) Math.sin(now / 18.0) * w
                        * Math.min(0.014f, 0.0025f + (reachStage - 2) * 0.0024f);
            }

            RectF dst = drawSprite(canvas, i, state, cx[i] + tremble,
                    baseline + jump, w * 0.260f, h * 0.285f);

            if (state == 0) {
                drawDigitOnSign(canvas, dst, i, shown[i]);
            } else {
                drawSimpleCard(canvas, cx[i], h * 0.575f + jump * 0.2f,
                        shown[i], i, w, h);
            }
        }
    }

    private RectF drawSprite(Canvas canvas, int ch, int state, float cx, float baseline,
                             float maxW, float maxH) {
        Bitmap b = cleanBitmaps[ch][state];
        if (b == null) return new RectF(cx - maxW / 2f, baseline - maxH, cx + maxW / 2f, baseline);
        Rect src = new Rect(0, 0, b.getWidth(), b.getHeight());
        float scale = Math.min(maxW / src.width(), maxH / src.height());
        float dw = src.width() * scale;
        float dh = src.height() * scale;
        RectF dst = new RectF(cx - dw / 2f, baseline - dh, cx + dw / 2f, baseline);
        canvas.drawBitmap(b, src, dst, bp);
        return dst;
    }

    private void drawDigitOnSign(Canvas canvas, RectF sprite, int ch, int number) {
        RectF n = numberBoards[ch] != null
                ? numberBoards[ch]
                : new RectF(0.30f, 0.38f, 0.70f, 0.62f);
        RectF r = new RectF(
                sprite.left + sprite.width() * n.left,
                sprite.top + sprite.height() * n.top,
                sprite.left + sprite.width() * n.right,
                sprite.top + sprite.height() * n.bottom
        );

        String text = String.valueOf(number);
        float size = Math.min(r.height() * 0.66f, r.width() * 0.62f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        p.setTextSize(size);
        Paint.FontMetrics fm = p.getFontMetrics();
        float y = r.centerY() - (fm.ascent + fm.descent) / 2f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(69, 43, 103));
        p.setShadowLayer(Math.max(1f, size * 0.035f), 0f, size * 0.025f,
                Color.argb(90, 120, 74, 150));
        canvas.drawText(text, r.centerX(), y, p);
        p.clearShadowLayer();
    }

    private void drawSimpleCard(Canvas canvas, float cx, float cy, int number,
                                int ch, int w, int h) {
        float cw = w * 0.115f;
        float chh = h * 0.067f;
        RectF r = new RectF(cx - cw / 2f, cy - chh / 2f, cx + cw / 2f, cy + chh / 2f);
        p.setColor(Color.rgb(250, 246, 251));
        canvas.drawRoundRect(r, w * 0.018f, w * 0.018f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w * 0.004f);
        p.setColor(ACCENTS[ch]);
        canvas.drawRoundRect(r, w * 0.018f, w * 0.018f, p);
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        p.setTextSize(w * 0.070f);
        p.setColor(Color.rgb(69, 43, 103));
        Paint.FontMetrics fm = p.getFontMetrics();
        canvas.drawText(String.valueOf(number), cx, cy - (fm.ascent + fm.descent) / 2f, p);
    }

    private void drawCorrectedCutIn(Canvas canvas, int w, int h, long now) throws Exception {
        long start = gl(fCutInStart);
        long until = gl(fCutInUntil);
        float duration = Math.max(1f, until - start);
        float t = Math.min(1f, (now - start) / duration);
        float enter = Math.min(1f, t / 0.14f);
        float exit = t > 0.84f ? Math.max(0f, (1f - t) / 0.16f) : 1f;
        float vis = Math.min(enter, exit);

        int type = gi(fCutInType);
        String title = gs(fCutInTitle);
        String msg = gs(fCutInMessage);
        boolean whisper = type == 7 || type == 8;
        boolean huge = type == 2 || type == 6 || type == 9 || type == 10 || type == 11;
        int girl = (type == 2 || type == 11) ? 0 : (type == 3 ? 1 : 2);
        int state = (type == 2 || type == 6 || type == 10 || type == 11) ? 2 : 1;
        int accent = accent(type);
        float top = whisper ? h * 0.275f : (huge ? h * 0.145f : h * 0.205f);
        float bottom = whisper ? h * 0.405f : (huge ? h * 0.505f : h * 0.425f);
        float slide = (1f - vis) * (type % 2 == 0 ? -w * 0.95f : w * 0.95f);

        canvas.save();
        canvas.translate(slide, 0f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(18, 10, 33));
        canvas.drawRect(0, top, w, bottom, p);
        p.setColor(accent);
        canvas.drawRect(0, top, w, top + h * 0.009f, p);
        canvas.drawRect(0, bottom - h * 0.009f, w, bottom, p);

        RectF portrait = new RectF(w * 0.025f, top + h * 0.012f,
                w * 0.300f, bottom - h * 0.012f);
        drawPortrait(canvas, girl, state, portrait);

        float tx = w * 0.335f;
        p.setTextAlign(Paint.Align.LEFT);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setColor(accent);
        p.setTextSize(w * (whisper ? 0.050f : huge ? 0.075f : 0.061f));
        canvas.drawText(title, tx, top + h * (whisper ? 0.055f : huge ? 0.098f : 0.076f), p);
        p.setColor(Color.WHITE);
        p.setTextSize(w * (whisper ? 0.030f : huge ? 0.037f : 0.034f));
        canvas.drawText(msg, tx, top + h * (whisper ? 0.096f : huge ? 0.161f : 0.128f), p);
        canvas.restore();
    }

    private void drawPortrait(Canvas canvas, int ch, int state, RectF area) {
        Bitmap b = cleanBitmaps[ch][state];
        if (b == null) return;

        Rect src = new Rect((int) (b.getWidth() * 0.18f), 0,
                (int) (b.getWidth() * 0.82f), (int) (b.getHeight() * 0.64f));
        float scale = Math.max(area.width() / src.width(), area.height() / src.height());
        float dw = src.width() * scale;
        float dh = src.height() * scale;
        float left = area.centerX() - dw / 2f;
        float top = area.top + (area.height() - dh) * 0.28f;
        RectF dst = new RectF(left, top, left + dw, top + dh);

        canvas.save();
        canvas.clipRect(area);
        canvas.drawBitmap(b, src, dst, bp);
        canvas.restore();
    }

    private int accent(int type) {
        if (type == 2 || type == 11) return Color.rgb(255, 222, 64);
        if (type == 3) return Color.rgb(138, 197, 255);
        if (type == 4) return Color.rgb(139, 236, 255);
        if (type == 5) return Color.rgb(255, 122, 44);
        if (type == 6) return Color.rgb(255, 42, 112);
        if (type == 7 || type == 8) return Color.rgb(215, 195, 240);
        if (type == 9) return Color.rgb(255, 65, 65);
        if (type == 10) return Color.rgb(255, 80, 205);
        return Color.rgb(255, 105, 181);
    }

    private void drawCorrectedPremiumTrio(Canvas canvas, int w, int h, long now) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(18, 11, 2));
        canvas.drawRect(0, h * 0.61f, w, h * 0.86f, p);
        float[] xs = {w * 0.22f, w * 0.50f, w * 0.78f};
        for (int i = 0; i < 3; i++) {
            float jump = (float) -Math.abs(Math.sin(now / 310.0 + i)) * h * 0.012f;
            drawSprite(canvas, i, 2, xs[i], h * 0.82f + jump, w * 0.23f, h * 0.20f);
        }
    }

    private Bitmap keepDominantSprite(Bitmap source) {
        if (source == null) return null;
        int w = source.getWidth();
        int h = source.getHeight();
        int n = w * h;
        int[] pixels = new int[n];
        source.getPixels(pixels, 0, w, 0, 0, w, h);

        int[] labels = new int[n];
        int[] queue = new int[n];
        int label = 0;
        int bestLabel = 0;
        int bestCount = 0;

        for (int i = 0; i < n; i++) {
            if (labels[i] != 0 || alpha(pixels[i]) < ALPHA_THRESHOLD) continue;
            label++;
            int head = 0, tail = 0, count = 0;
            queue[tail++] = i;
            labels[i] = label;

            while (head < tail) {
                int idx = queue[head++];
                count++;
                int x = idx % w;
                int y = idx / w;
                if (x > 0) tail = enqueueSprite(idx - 1, label, pixels, labels, queue, tail);
                if (x + 1 < w) tail = enqueueSprite(idx + 1, label, pixels, labels, queue, tail);
                if (y > 0) tail = enqueueSprite(idx - w, label, pixels, labels, queue, tail);
                if (y + 1 < h) tail = enqueueSprite(idx + w, label, pixels, labels, queue, tail);
            }

            if (count > bestCount) {
                bestCount = count;
                bestLabel = label;
            }
        }

        if (bestLabel == 0) return source;

        boolean[] keep = new boolean[n];
        for (int i = 0; i < n; i++) keep[i] = labels[i] == bestLabel;
        for (int pass = 0; pass < 2; pass++) {
            boolean[] next = keep.clone();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int idx = y * w + x;
                    if (!keep[idx]) continue;
                    for (int dy = -1; dy <= 1; dy++) {
                        int yy = y + dy;
                        if (yy < 0 || yy >= h) continue;
                        for (int dx = -1; dx <= 1; dx++) {
                            int xx = x + dx;
                            if (xx < 0 || xx >= w) continue;
                            next[yy * w + xx] = true;
                        }
                    }
                }
            }
            keep = next;
        }

        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                if (!keep[idx]) {
                    pixels[idx] &= 0x00ffffff;
                } else if (alpha(pixels[idx]) > 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        if (maxX < minX || maxY < minY) return source;
        Bitmap clean = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        clean.setPixels(pixels, 0, w, 0, 0, w, h);
        int pad = 3;
        int l = Math.max(0, minX - pad);
        int t = Math.max(0, minY - pad);
        int r = Math.min(w, maxX + pad + 1);
        int b = Math.min(h, maxY + pad + 1);
        return Bitmap.createBitmap(clean, l, t, r - l, b - t);
    }

    private int enqueueSprite(int idx, int label, int[] pixels,
                              int[] labels, int[] queue, int tail) {
        if (labels[idx] == 0 && alpha(pixels[idx]) >= ALPHA_THRESHOLD) {
            labels[idx] = label;
            queue[tail++] = idx;
        }
        return tail;
    }

    private Bitmap normalizeSprite(Bitmap sprite, int targetW, int targetH) {
        if (sprite == null) return null;
        Bitmap out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint draw = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float maxW = targetW * 0.92f;
        float maxH = targetH * 0.94f;
        float scale = Math.min(maxW / sprite.getWidth(), maxH / sprite.getHeight());
        float dw = sprite.getWidth() * scale;
        float dh = sprite.getHeight() * scale;
        float left = (targetW - dw) / 2f;
        float bottom = targetH * 0.968f;
        RectF dst = new RectF(left, bottom - dh, left + dw, bottom);
        canvas.drawBitmap(sprite, null, dst, draw);
        return out;
    }

    private RectF detectNumberBoard(Bitmap bitmap) {
        if (bitmap == null) return new RectF(0.30f, 0.38f, 0.70f, 0.62f);
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int n = w * h;
        int[] pixels = new int[n];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        int[] labels = new int[n];
        int[] queue = new int[n];
        int label = 0;
        int bestCount = 0;
        int bestMinX = 0, bestMinY = 0, bestMaxX = w - 1, bestMaxY = h - 1;

        for (int i = 0; i < n; i++) {
            if (labels[i] != 0 || !isBoardPixel(pixels[i])) continue;
            label++;
            int head = 0, tail = 0, count = 0;
            int minX = w, minY = h, maxX = -1, maxY = -1;
            labels[i] = label;
            queue[tail++] = i;
            while (head < tail) {
                int idx = queue[head++];
                count++;
                int x = idx % w;
                int y = idx / w;
                minX = Math.min(minX, x); minY = Math.min(minY, y);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                if (x > 0) tail = enqueueBoard(idx - 1, label, pixels, labels, queue, tail);
                if (x + 1 < w) tail = enqueueBoard(idx + 1, label, pixels, labels, queue, tail);
                if (y > 0) tail = enqueueBoard(idx - w, label, pixels, labels, queue, tail);
                if (y + 1 < h) tail = enqueueBoard(idx + w, label, pixels, labels, queue, tail);
            }
            if (count > bestCount) {
                bestCount = count;
                bestMinX = minX; bestMinY = minY;
                bestMaxX = maxX; bestMaxY = maxY;
            }
        }

        if (bestCount == 0) return new RectF(0.30f, 0.38f, 0.70f, 0.62f);
        float boardW = bestMaxX - bestMinX + 1f;
        float boardH = bestMaxY - bestMinY + 1f;
        float insetX = Math.max(5f, boardW * 0.12f);
        float insetY = Math.max(5f, boardH * 0.12f);
        return new RectF(
                (bestMinX + insetX) / w,
                (bestMinY + insetY) / h,
                (bestMaxX + 1f - insetX) / w,
                (bestMaxY + 1f - insetY) / h
        );
    }

    private int enqueueBoard(int idx, int label, int[] pixels,
                             int[] labels, int[] queue, int tail) {
        if (labels[idx] == 0 && isBoardPixel(pixels[idx])) {
            labels[idx] = label;
            queue[tail++] = idx;
        }
        return tail;
    }

    private boolean isBoardPixel(int color) {
        int a = alpha(color);
        int r = (color >>> 16) & 0xff;
        int g = (color >>> 8) & 0xff;
        int b = color & 0xff;
        return a > 32 && r > 218 && g > 218 && b > 218;
    }

    private int alpha(int color) {
        return (color >>> 24) & 0xff;
    }
}
