package com.aonon.pixelslot;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;

/** v0.13: native full/SD assets plus perspective-mapped reel digits. */
public class PixelSlotV13View extends PixelSlotV12View {
    private static final int MOMO = 0;
    private static final int LUNA = 1;
    private static final int MIO = 2;

    private static final int DIGIT_W = 512;
    private static final int DIGIT_H = 300;
    private static final float[] DIGIT_SRC = {
            0f, 0f, DIGIT_W, 0f, DIGIT_W, DIGIT_H, 0f, DIGIT_H
    };

    /** Top-left, top-right, bottom-right and bottom-left corners in SD image space. */
    private static final float[][] CARD_QUADS = {
            {.346f,.360f, .849f,.379f, .824f,.568f, .333f,.539f},
            {.381f,.362f, .842f,.386f, .818f,.568f, .354f,.537f},
            {.335f,.337f, .800f,.366f, .776f,.539f, .315f,.504f}
    };

    private static final int[] ACCENTS = {
            Color.rgb(245, 58, 137),
            Color.rgb(137, 81, 235),
            Color.rgb(0, 174, 190)
    };

    private final Bitmap[] full = new Bitmap[3];
    private final Bitmap[] sd = new Bitmap[3];
    private final Bitmap[][] digitArt = new Bitmap[3][10];
    private final Paint bitmapPaint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint fxPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Matrix digitMatrix = new Matrix();
    private final float[] digitDst = new float[8];

    private Field fShown;
    private Field fPulse;
    private Field fStage;
    private Field fSpinning;
    private Field fReach;
    private Field fPremiumStart;
    private Field fPremiumUntil;
    private Field fRevivalStart;
    private Field fRevivalActive;
    private Field fCutStart;
    private Field fCutUntil;
    private Field fCutType;
    private Field fCutTitle;
    private Field fCutMessage;

    private boolean assetsReady;
    private boolean stateReady;
    private int previousStage;
    private int activeFull = -1;
    private long activeFullStart;
    private long activeFullUntil;

    public PixelSlotV13View(Context context) {
        super(context);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        textPaint.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));

        try {
            full[MOMO] = loadBitmap("momo_full_v13.webp", 941, 1672);
            full[LUNA] = loadBitmap("luna_full_v13.webp", 941, 1672);
            full[MIO] = loadBitmap("mio_full_v13.webp", 941, 1672);
            sd[MOMO] = loadBitmap("momo_sd_idle_v13.webp", 512, 768);
            sd[LUNA] = loadBitmap("luna_sd_idle_v13.webp", 512, 768);
            sd[MIO] = loadBitmap("mio_sd_idle_v13.webp", 512, 768);
            installSdSprites();
            disableLegacyAtlas();
            buildDigitArt();
            assetsReady = true;
        } catch (Throwable t) {
            t.printStackTrace();
            assetsReady = false;
        }

        try {
            bindState();
            stateReady = true;
        } catch (Throwable t) {
            t.printStackTrace();
            stateReady = false;
        }
    }

    private Bitmap loadBitmap(String name, int expectedWidth, int expectedHeight) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inScaled = false;
        Bitmap decoded;
        try (InputStream in = getContext().getAssets().open(name)) {
            decoded = BitmapFactory.decodeStream(in, null, options);
        }
        if (decoded == null) throw new IOException("Unable to decode asset " + name);
        if (decoded.getWidth() != expectedWidth || decoded.getHeight() != expectedHeight) {
            decoded.recycle();
            throw new IOException("Unexpected dimensions for " + name);
        }
        return decoded;
    }

    private void installSdSprites() throws Exception {
        Field girlsField = PixelSlotV9View.class.getDeclaredField("girls");
        girlsField.setAccessible(true);
        Bitmap[] girls = (Bitmap[]) girlsField.get(this);
        for (int i = 0; i < 3; i++) {
            Bitmap old = girls[i];
            girls[i] = sd[i];
            if (old != null && old != sd[i] && !old.isRecycled()) old.recycle();
        }
    }

    /** v0.13 never consumes the experimental v0.11 atlas. */
    private void disableLegacyAtlas() throws Exception {
        Field readyField = PixelSlotV11View.class.getDeclaredField("ready");
        readyField.setAccessible(true);
        readyField.setBoolean(this, false);

        Field atlasField = PixelSlotV11View.class.getDeclaredField("atlas");
        atlasField.setAccessible(true);
        Bitmap oldAtlas = (Bitmap) atlasField.get(this);
        atlasField.set(this, null);
        if (oldAtlas != null && !oldAtlas.isRecycled()) oldAtlas.recycle();
    }

    private void bindState() throws Exception {
        Class<?> engine = PixelSlotV5View.class;
        fShown = field(engine, "shown");
        fPulse = field(engine, "pulseUntil");
        fStage = field(engine, "reachStage");
        fSpinning = field(engine, "spinning");
        fReach = field(engine, "reach");
        fPremiumStart = field(engine, "premiumStart");
        fPremiumUntil = field(engine, "premiumUntil");
        fRevivalStart = field(engine, "revivalStart");
        fRevivalActive = field(engine, "revivalActive");
        fCutStart = field(engine, "cutStart");
        fCutUntil = field(engine, "cutUntil");
        fCutType = field(engine, "cutType");
        fCutTitle = field(engine, "cutTitle");
        fCutMessage = field(engine, "cutMessage");
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private void buildDigitArt() {
        for (int ch = 0; ch < 3; ch++) {
            for (int number = 0; number < 10; number++) {
                digitArt[ch][number] = createDigitBitmap(ch, number);
            }
        }
    }

    private Bitmap createDigitBitmap(int ch, int number) {
        Bitmap out = Bitmap.createBitmap(DIGIT_W, DIGIT_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        Paint digit = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        digit.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        digit.setTextAlign(Paint.Align.CENTER);
        digit.setTextSize(246f);
        Paint.FontMetrics fm = digit.getFontMetrics();
        float baseline = DIGIT_H / 2f - (fm.ascent + fm.descent) / 2f;
        String value = String.valueOf(number);

        digit.setStyle(Paint.Style.STROKE);
        digit.setStrokeJoin(Paint.Join.ROUND);
        digit.setStrokeWidth(number == 7 ? 38f : 32f);
        digit.setColor(number == 7 ? Color.rgb(92, 34, 8) : Color.rgb(35, 21, 54));
        canvas.drawText(value, DIGIT_W / 2f, baseline, digit);

        if (number == 7) {
            digit.setStrokeWidth(25f);
            digit.setColor(Color.rgb(255, 210, 55));
            canvas.drawText(value, DIGIT_W / 2f, baseline, digit);
        } else {
            digit.setStrokeWidth(13f);
            digit.setColor(Color.WHITE);
            canvas.drawText(value, DIGIT_W / 2f, baseline, digit);
        }

        digit.setStyle(Paint.Style.FILL);
        digit.setColor(number == 7 ? Color.rgb(238, 45, 62) : ACCENTS[ch]);
        canvas.drawText(value, DIGIT_W / 2f, baseline, digit);

        digit.setStyle(Paint.Style.STROKE);
        digit.setStrokeWidth(3f);
        digit.setColor(Color.argb(150, 255, 255, 255));
        canvas.drawText(value, DIGIT_W / 2f, baseline - 2f, digit);
        return out;
    }

    @Override
    protected void drawDigit(Canvas canvas, RectF girlRect, int ch, int number) {
        if (!assetsReady || ch < 0 || ch >= 3 || number < 0 || number > 9
                || digitArt[ch][number] == null) {
            super.drawDigit(canvas, girlRect, ch, number);
            return;
        }

        float[] quad = CARD_QUADS[ch];
        for (int i = 0; i < 4; i++) {
            digitDst[i * 2] = girlRect.left + girlRect.width() * quad[i * 2];
            digitDst[i * 2 + 1] = girlRect.top + girlRect.height() * quad[i * 2 + 1];
        }

        float pulse = digitPulse(ch, number, System.currentTimeMillis());
        if (pulse != 1f) scaleQuad(digitDst, pulse);

        digitMatrix.reset();
        if (!digitMatrix.setPolyToPoly(DIGIT_SRC, 0, digitDst, 0, 4)) {
            super.drawDigit(canvas, girlRect, ch, number);
            return;
        }
        bitmapPaint.setAlpha(255);
        canvas.drawBitmap(digitArt[ch][number], digitMatrix, bitmapPaint);
    }

    private float digitPulse(int ch, int number, long now) {
        float scale = number == 7 ? 1f + .022f * (float)Math.sin(now / 115.0) : 1f;
        if (!stateReady) return scale;
        try {
            long[] pulses = (long[]) fPulse.get(this);
            if (pulses != null && ch < pulses.length && now < pulses[ch]) {
                scale += .065f * Math.max(0f, (float)Math.sin((pulses[ch] - now) / 85.0));
            }
            if (fSpinning.getBoolean(this) && fReach.getBoolean(this)
                    && ch == MIO && fStage.getInt(this) >= 3) {
                scale += .025f * (float)Math.sin(now / 55.0);
            }
        } catch (Throwable ignored) {
            // A plain perspective-mapped digit is still a safe fallback.
        }
        return Math.max(.92f, Math.min(1.10f, scale));
    }

    private static void scaleQuad(float[] quad, float scale) {
        float cx = 0f;
        float cy = 0f;
        for (int i = 0; i < 4; i++) {
            cx += quad[i * 2];
            cy += quad[i * 2 + 1];
        }
        cx /= 4f;
        cy /= 4f;
        for (int i = 0; i < 4; i++) {
            quad[i * 2] = cx + (quad[i * 2] - cx) * scale;
            quad[i * 2 + 1] = cy + (quad[i * 2 + 1] - cy) * scale;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = System.currentTimeMillis();
        super.onDraw(canvas);
        if (!assetsReady || !stateReady) return;

        try {
            updateStageCutins(now);
            boolean animated = drawHighestPriorityFull(canvas, now);
            if (animated) postInvalidateOnAnimation();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void updateStageCutins(long now) throws Exception {
        boolean spinning = fSpinning.getBoolean(this);
        boolean reach = fReach.getBoolean(this);
        int stage = fStage.getInt(this);
        if (spinning && reach) {
            if (stage >= 3 && previousStage < 3) {
                startFull(MOMO, now, 1800L);
            } else if (stage >= 5 && previousStage < 5) {
                startFull(LUNA, now, 2200L);
            }
            previousStage = stage;
        } else {
            previousStage = 0;
        }
    }

    private void startFull(int ch, long now, long duration) {
        activeFull = ch;
        activeFullStart = now;
        activeFullUntil = now + duration;
    }

    private boolean drawHighestPriorityFull(Canvas canvas, long now) throws Exception {
        long premiumStart = fPremiumStart.getLong(this);
        long premiumUntil = fPremiumUntil.getLong(this);
        if (premiumStart > 0 && now >= premiumStart + 1600L
                && now < Math.min(premiumUntil, premiumStart + 6100L)) {
            drawFull(canvas, LUNA, premiumStart + 1600L,
                    Math.min(premiumUntil, premiumStart + 6100L), now,
                    "PREMIUM", "777  確定", true);
            return true;
        }

        long revivalStart = fRevivalStart.getLong(this);
        if (fRevivalActive.getBoolean(this) && revivalStart > 0
                && now >= revivalStart + 620L && now < revivalStart + 1700L) {
            drawFull(canvas, MIO, revivalStart + 620L, revivalStart + 1700L, now,
                    "……まだ。", "ミオが呼び戻す！", true);
            return true;
        }

        long cutUntil = fCutUntil.getLong(this);
        int cutType = fCutType.getInt(this);
        if (now < cutUntil && (cutType == 2 || cutType == 10 || cutType == 11)) {
            int ch = cutType == 10 ? MIO : cutType == 11 ? LUNA : MOMO;
            drawFull(canvas, ch, fCutStart.getLong(this), cutUntil, now,
                    stringValue(fCutTitle), stringValue(fCutMessage), cutType != 2);
            return true;
        }

        if (activeFull >= 0 && now < activeFullUntil) {
            if (activeFull == MOMO) {
                drawFull(canvas, MOMO, activeFullStart, activeFullUntil, now,
                        "CHANCE", "リーチ加速！", false);
            } else {
                drawFull(canvas, LUNA, activeFullStart, activeFullUntil, now,
                        "激アツ", "月光、覚醒", true);
            }
            return true;
        }
        if (activeFull >= 0) activeFull = -1;
        return false;
    }

    private String stringValue(Field field) throws IllegalAccessException {
        Object value = field.get(this);
        return value == null ? "" : String.valueOf(value);
    }

    private void drawFull(Canvas canvas, int ch, long start, long until, long now,
                          String title, String subtitle, boolean special) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0 || full[ch] == null) return;

        float t = clamp((now - start) / (float)Math.max(1L, until - start));
        float enter = smooth(Math.min(1f, t / .13f));
        float exit = t > .84f ? smooth(Math.max(0f, (1f - t) / .16f)) : 1f;
        float visibility = Math.min(enter, exit);
        int accent = ACCENTS[ch];

        LinearGradient backdrop = new LinearGradient(0, 0, 0, h,
                Color.rgb(8, 4, 18), darken(accent, .24f), Shader.TileMode.CLAMP);
        fxPaint.setShader(backdrop);
        fxPaint.setAlpha((int)(245 * visibility));
        canvas.drawRect(0, 0, w, h, fxPaint);
        fxPaint.setShader(null);
        fxPaint.setAlpha(255);

        Bitmap art = full[ch];
        float scale = Math.min(w / (float)art.getWidth(), h / (float)art.getHeight());
        float dw = art.getWidth() * scale;
        float dh = art.getHeight() * scale;
        float slide = (1f - enter) * (ch == LUNA ? w * .12f : -w * .12f);
        RectF dst = new RectF((w - dw) / 2f + slide, (h - dh) / 2f,
                (w + dw) / 2f + slide, (h + dh) / 2f);
        bitmapPaint.setAlpha((int)(255 * visibility));
        canvas.drawBitmap(art, null, dst, bitmapPaint);
        bitmapPaint.setAlpha(255);

        drawSideEnergy(canvas, w, h, now, accent, visibility, special);

        float plateTop = h * .72f;
        LinearGradient plate = new LinearGradient(0, plateTop, 0, h,
                Color.argb(0, 3, 1, 10), Color.argb((int)(238 * visibility), 3, 1, 10),
                Shader.TileMode.CLAMP);
        fxPaint.setShader(plate);
        canvas.drawRect(0, plateTop, w, h, fxPaint);
        fxPaint.setShader(null);
        fxPaint.setColor(withAlpha(accent, (int)(245 * visibility)));
        canvas.drawRect(0, plateTop, w, plateTop + Math.max(4f, h * .007f), fxPaint);

        float pulse = special ? 1f + .025f * (float)Math.sin(now / 58.0) : 1f;
        drawOutlinedText(canvas, title, w / 2f, h * .835f,
                w * (special ? .088f : .078f) * pulse, Color.WHITE, visibility);
        drawOutlinedText(canvas, subtitle, w / 2f, h * .895f,
                w * (special ? .040f : .035f), accent, visibility);
    }

    private void drawSideEnergy(Canvas canvas, int w, int h, long now,
                                int accent, float visibility, boolean special) {
        int count = special ? 54 : 30;
        for (int i = 0; i < count; i++) {
            float phase = ((now / 6f + i * 71f) % 1000f) / 1000f;
            boolean left = (i & 1) == 0;
            float x = left ? w * (.02f + (i % 7) * .013f) : w * (.98f - (i % 7) * .013f);
            float y = h * (.06f + phase * .78f);
            float radius = w * (.0025f + (i % 4) * .0013f);
            fxPaint.setColor(withAlpha(accent, (int)(visibility * (80 + (i % 5) * 24))));
            canvas.drawCircle(x, y, radius, fxPaint);
        }
        if (special) {
            fxPaint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 4; i++) {
                float phase = ((now / 8f + i * 160f) % 720f) / 720f;
                fxPaint.setStrokeWidth(w * (.003f + (1f - phase) * .006f));
                fxPaint.setColor(withAlpha(accent,
                        (int)(visibility * (1f - phase) * 145f)));
                canvas.drawCircle(w / 2f, h * .43f, w * (.10f + phase * .58f), fxPaint);
            }
            fxPaint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawOutlinedText(Canvas canvas, String value, float x, float y,
                                  float size, int fill, float visibility) {
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(size);
        textPaint.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        textPaint.setStyle(Paint.Style.STROKE);
        textPaint.setStrokeWidth(Math.max(3f, size * .13f));
        textPaint.setColor(Color.argb((int)(235 * visibility), 16, 5, 28));
        canvas.drawText(value == null ? "" : value, x, y, textPaint);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(withAlpha(fill, (int)(255 * visibility)));
        canvas.drawText(value == null ? "" : value, x, y, textPaint);
    }

    private static int darken(int color, float amount) {
        return Color.rgb((int)(Color.red(color) * amount),
                (int)(Color.green(color) * amount),
                (int)(Color.blue(color) * amount));
    }

    private static int withAlpha(int color, int alpha) {
        int safe = Math.max(0, Math.min(255, alpha));
        return Color.argb(safe, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float smooth(float value) {
        float v = clamp(value);
        return v * v * (3f - 2f * v);
    }
}
