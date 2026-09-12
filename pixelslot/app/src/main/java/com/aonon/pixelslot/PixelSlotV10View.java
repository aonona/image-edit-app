package com.aonon.pixelslot;

import android.content.Context;
import android.graphics.*;
import android.util.Base64;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

/** v0.10: full-art character cut-ins layered over the v0.9 equal-scale/bombastic renderer. */
public class PixelSlotV10View extends PixelSlotV9View {
    private static final int NONE = 0;
    private static final int MOMO = 1;
    private static final int LUNA = 2;
    private static final int MIO = 3;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private Bitmap cutinSheet;

    private Field fStage, fSpinning, fReach, fPremiumStart, fPremiumUntil, fRevivalStart, fRevivalFlash;
    private boolean engineReady;

    private int previousStage = 0;
    private int activeCutin = NONE;
    private long activeStart = 0L;
    private long activeUntil = 0L;

    public PixelSlotV10View(Context context) {
        super(context);
        cutinSheet = loadSheet();
        try {
            Class<?> c = PixelSlotV5View.class;
            fStage = field(c, "reachStage");
            fSpinning = field(c, "spinning");
            fReach = field(c, "reach");
            fPremiumStart = field(c, "premiumStart");
            fPremiumUntil = field(c, "premiumUntil");
            fRevivalStart = field(c, "revivalStart");
            fRevivalFlash = field(c, "revivalFlashUntil");
            engineReady = true;
        } catch (Throwable t) {
            t.printStackTrace();
            engineReady = false;
        }
    }

    private static Field field(Class<?> c, String name) throws Exception {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private Bitmap loadSheet() {
        try {
            StringBuilder encoded = new StringBuilder(170000);
            for (int i = 0; i < 8; i++) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(
                        getContext().getAssets().open("v10_cutin_" + i + ".txt"), StandardCharsets.US_ASCII))) {
                    String line;
                    while ((line = br.readLine()) != null) encoded.append(line.trim());
                }
            }
            byte[] data = Base64.decode(encoded.toString(), Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    private int intValue(Field f) throws Exception { return f.getInt(this); }
    private long longValue(Field f) throws Exception { return f.getLong(this); }
    private boolean boolValue(Field f) throws Exception { return f.getBoolean(this); }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!engineReady || cutinSheet == null) return;

        long now = System.currentTimeMillis();
        try {
            int stage = intValue(fStage);
            boolean spinning = boolValue(fSpinning);
            boolean reach = boolValue(fReach);
            long premiumStart = longValue(fPremiumStart);
            long premiumUntil = longValue(fPremiumUntil);
            long revivalStart = longValue(fRevivalStart);
            long revivalFlash = longValue(fRevivalFlash);

            // Role assignment requested by the user:
            // Momo = strong reach, Luna = super-hot/premium, Mio = revival.
            if (spinning && reach) {
                if (stage >= 5 && previousStage < 5) {
                    startCutin(LUNA, now, 2050L);
                } else if (stage >= 3 && previousStage < 3) {
                    startCutin(MOMO, now, 1650L);
                }
                previousStage = stage;
            } else if (!spinning) {
                previousStage = 0;
            }

            int forced = NONE;
            long forcedStart = 0L;
            long forcedUntil = 0L;
            String title = null;
            String subtitle = null;

            // Premium overrides every other cut-in and deliberately stays long enough to read.
            if (premiumStart > 0L && now >= premiumStart && now < premiumStart + 3200L && now < premiumUntil) {
                forced = LUNA;
                forcedStart = premiumStart;
                forcedUntil = Math.min(premiumUntil, premiumStart + 3200L);
                title = "PREMIUM";
                subtitle = "777  確定";
            }
            // Revival gets highest priority while its own reveal is happening.
            if (revivalStart > 0L && now >= revivalStart && now < revivalStart + 2350L) {
                forced = MIO;
                forcedStart = revivalStart;
                forcedUntil = revivalStart + 2350L;
                title = "復活ッ！！";
                subtitle = "まだ終わってへん！";
            }

            if (forced != NONE) {
                drawFullArtCutin(canvas, forced, forcedStart, forcedUntil, now, title, subtitle, true);
                postInvalidateOnAnimation();
            } else if (activeCutin != NONE && now < activeUntil) {
                if (activeCutin == MOMO) {
                    drawFullArtCutin(canvas, MOMO, activeStart, activeUntil, now,
                            "CHANCE!", "モモが押すでっ！", false);
                } else {
                    drawFullArtCutin(canvas, LUNA, activeStart, activeUntil, now,
                            "激アツッ！！", "月光チャンス", false);
                }
                postInvalidateOnAnimation();
            } else {
                activeCutin = NONE;
            }

            if (now < revivalFlash) postInvalidateOnAnimation();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void startCutin(int character, long now, long duration) {
        activeCutin = character;
        activeStart = now;
        activeUntil = now + duration;
    }

    private Rect sourceRect(int character) {
        int third = cutinSheet.getWidth() / 3;
        int index = Math.max(0, Math.min(2, character - 1));
        int left = index * third;
        int right = (index == 2) ? cutinSheet.getWidth() : left + third;
        return new Rect(left, 0, right, cutinSheet.getHeight());
    }

    private void drawFullArtCutin(Canvas c, int character, long start, long until, long now,
                                  String title, String subtitle, boolean special) {
        int w = getWidth(), h = getHeight();
        long duration = Math.max(1L, until - start);
        float t = clamp((now - start) / (float) duration);
        float enter = smooth(Math.min(1f, t / 0.14f));
        float exit = t > 0.80f ? smooth(Math.max(0f, (1f - t) / 0.20f)) : 1f;
        float visibility = Math.min(enter, exit);

        int accent = character == MOMO ? Color.rgb(255, 91, 169)
                : character == LUNA ? Color.rgb(174, 116, 255)
                : Color.rgb(72, 225, 225);

        // Dark snap before the illustration lands.
        paint.setColor(Color.argb((int)(185 * visibility), 8, 4, 18));
        c.drawRect(0, 0, w, h, paint);

        Rect src = sourceRect(character);
        float srcRatio = src.width() / (float) src.height();
        float targetRatio = w / (float) h;
        Rect crop = new Rect(src);
        if (srcRatio < targetRatio) {
            int newH = Math.round(src.width() / targetRatio);
            int cy = src.centerY();
            crop.top = Math.max(src.top, cy - newH / 2);
            crop.bottom = Math.min(src.bottom, crop.top + newH);
        } else if (srcRatio > targetRatio) {
            int newW = Math.round(src.height() * targetRatio);
            int cx = src.centerX();
            crop.left = Math.max(src.left, cx - newW / 2);
            crop.right = Math.min(src.right, crop.left + newW);
        }

        float scaleIn = 1.085f - 0.085f * enter;
        float slide = (1f - enter) * (character == LUNA ? w * 0.16f : -w * 0.16f);
        c.save();
        c.translate(slide, 0f);
        c.scale(scaleIn, scaleIn, w / 2f, h / 2f);
        paint.setAlpha((int)(255 * visibility));
        c.drawBitmap(cutinSheet, crop, new RectF(0, 0, w, h), paint);
        paint.setAlpha(255);
        c.restore();

        // Character-colour flash and diagonal speed bars on entry.
        float impact = (float)Math.sin(Math.min(1f, t / 0.24f) * Math.PI);
        paint.setColor(withAlpha(accent, (int)(95 * impact * visibility)));
        c.drawRect(0, 0, w, h, paint);
        paint.setStrokeWidth(Math.max(3f, w * 0.007f));
        for (int i = 0; i < 8; i++) {
            float y = h * (0.12f + i * 0.105f) + (float)Math.sin(now / 70.0 + i) * h * 0.008f;
            paint.setColor(Color.argb((int)(80 * visibility), 255, 255, 255));
            c.drawLine(-w * 0.08f, y, w * 0.27f, y - h * 0.055f, paint);
            c.drawLine(w * 0.73f, y + h * 0.03f, w * 1.08f, y - h * 0.025f, paint);
        }

        // Text-safe plate at the bottom so the generated art remains readable.
        float plateTop = special ? h * 0.695f : h * 0.735f;
        LinearGradient gradient = new LinearGradient(0, plateTop, 0, h,
                Color.argb((int)(25 * visibility), 0,0,0),
                Color.argb((int)(230 * visibility), 7,3,18), Shader.TileMode.CLAMP);
        paint.setShader(gradient);
        c.drawRect(0, plateTop, w, h, paint);
        paint.setShader(null);

        // Accent rails make each girl's role visually distinct.
        paint.setColor(withAlpha(accent, (int)(230 * visibility)));
        c.drawRect(0, plateTop, w, plateTop + h * 0.009f, paint);
        c.drawRect(0, h - h * 0.013f, w, h, paint);

        float pulse = 1f + 0.035f * (float)Math.sin(now / 60.0);
        drawText(c, title, w/2f, h * (special ? 0.805f : 0.825f),
                w * (special ? 0.092f : 0.079f) * pulse, Color.WHITE, Paint.Align.CENTER, true);
        drawText(c, subtitle, w/2f, h * (special ? 0.865f : 0.878f),
                w * (special ? 0.042f : 0.037f), withAlpha(accent, 255), Paint.Align.CENTER, true);

        // Special overlays get extra rings and flash so premium/revival feel exceptional.
        if (special) {
            paint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 4; i++) {
                float phase = ((now / 7f + i * 180f) % 720f) / 720f;
                paint.setStrokeWidth(w * (0.004f + (1f-phase)*0.007f));
                paint.setColor(withAlpha(accent, (int)((1f-phase) * 150 * visibility)));
                c.drawCircle(w/2f, h*0.46f, w*(0.12f + phase*0.62f), paint);
            }
            paint.setStyle(Paint.Style.FILL);
            float blink = Math.max(0f, (float)Math.sin(now / 45.0));
            paint.setColor(Color.argb((int)(55 * blink * visibility), 255,255,255));
            c.drawRect(0,0,w,h,paint);
        }
    }

    private void drawText(Canvas c, String text, float x, float y, float size, int color,
                          Paint.Align align, boolean bold) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(align);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL));
        paint.setTextSize(size);
        paint.setColor(color);
        paint.setShadowLayer(Math.max(2f, size * 0.09f), 0f, size * 0.035f, Color.argb(220,0,0,0));
        c.drawText(text, x, y, paint);
        paint.clearShadowLayer();
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }

    private static float clamp(float x) { return Math.max(0f, Math.min(1f, x)); }
    private static float smooth(float x) { x = clamp(x); return x*x*(3f-2f*x); }
}
