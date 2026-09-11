package com.aonon.pixelslot;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

public class PixelSlotView extends View {
    private final Paint paint = new Paint();
    private final Random random = new Random();
    private final int[] shown = {2, 5, 8};
    private final int[] target = {2, 5, 8};
    private final RectF spinButton = new RectF();

    private boolean spinning = false;
    private boolean reachTriggered = false;
    private boolean resultTriggered = false;
    private int reachStage = 0;
    private long spinStart = 0L;

    private long celebrationStart = 0L;
    private long celebrationUntil = 0L;
    private final boolean[] reelLocked = {false, false, false};
    private final long[] reelPulseUntil = {0L, 0L, 0L};

    private long cutInStart = 0L;
    private long cutInUntil = 0L;
    private String cutInTitle = "";
    private String cutInMessage = "";
    private int cutInType = 0;

    private int spins = 0;
    private int wins = 0;

    private final int[] hairColors = {
            Color.rgb(238, 113, 153),
            Color.rgb(104, 92, 176),
            Color.rgb(85, 157, 178)
    };
    private final int[] outfitColors = {
            Color.rgb(255, 190, 211),
            Color.rgb(174, 157, 240),
            Color.rgb(143, 220, 218)
    };
    private final String[] names = {"モモ", "ルナ", "ミオ"};

    public PixelSlotView(Context context) {
        super(context);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setFocusable(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final int w = getWidth();
        final int h = getHeight();
        final long now = System.currentTimeMillis();

        updateGameState(now);
        drawBackground(canvas, w, h, now);

        float shake = currentShake(now, w);
        canvas.save();
        if (shake > 0f) {
            float sx = (float) Math.sin(now / 11.0) * shake;
            float sy = (float) Math.cos(now / 15.0) * shake * 0.68f;
            canvas.translate(sx, sy);
        }
        drawHeader(canvas, w, h);
        drawMachine(canvas, w, h, now);
        drawSpinButton(canvas, w, h, now);
        canvas.restore();

        if (reachTriggered && spinning) {
            if (reachStage <= 2) drawOmen(canvas, w, h, now);
            if (reachStage >= 2) drawHypeEffects(canvas, w, h, now, false);
        }
        if (now < celebrationUntil) {
            drawHypeEffects(canvas, w, h, now, true);
            drawSparkles(canvas, w, h, now);
        }
        drawReelPulses(canvas, w, h, now);

        if (now < cutInUntil) {
            drawCutIn(canvas, w, h, now);
        }

        if (spinning || now < cutInUntil || now < celebrationUntil
                || now < reelPulseUntil[0] || now < reelPulseUntil[1] || now < reelPulseUntil[2]) {
            postInvalidateOnAnimation();
        }
    }

    private void drawBackground(Canvas canvas, int w, int h, long now) {
        paint.setAntiAlias(true);
        int top = Color.rgb(32, 21, 48);
        int bottom = Color.rgb(14, 10, 24);

        if (spinning && reachStage >= 3) {
            top = Color.rgb(55 + Math.min(30, reachStage * 4), 16, 70);
        }
        if (now < celebrationUntil) {
            top = Color.rgb(78, 18, 54);
            bottom = Color.rgb(50, 15, 19);
        }

        paint.setShader(new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        // Normal play is intentionally quiet and restrained.
        paint.setColor(Color.argb(18, 255, 207, 236));
        float r = w * 0.29f;
        canvas.drawCircle(w * 0.10f, h * 0.15f, r, paint);
        paint.setColor(Color.argb(12, 132, 222, 255));
        canvas.drawCircle(w * 0.94f, h * 0.65f, r * 1.15f, paint);

        if (reachTriggered && spinning && reachStage >= 2) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / (reachStage >= 5 ? 55.0 : 110.0));
            int alpha = 8 + reachStage * 10 + (int) (pulse * reachStage * 5);
            paint.setColor(Color.argb(Math.min(100, alpha), 255, 55, 170));
            canvas.drawCircle(w * 0.80f, h * 0.54f,
                    w * (0.12f + reachStage * 0.018f + pulse * 0.045f), paint);
        }
    }

    private void drawHeader(Canvas canvas, int w, int h) {
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        paint.setColor(Color.rgb(255, 221, 244));
        paint.setTextSize(w * 0.073f);
        canvas.drawText("PIXEL SLOT", w / 2f, h * 0.075f, paint);

        paint.setColor(Color.rgb(192, 178, 207));
        paint.setTextSize(w * 0.030f);
        canvas.drawText("静か……と思ったら、そこから全部盛り。", w / 2f, h * 0.112f, paint);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(Color.rgb(235, 226, 244));
        paint.setTextSize(w * 0.031f);
        canvas.drawText("SPIN  " + spins, w * 0.07f, h * 0.155f, paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("WIN  " + wins, w * 0.93f, h * 0.155f, paint);
    }

    private void drawMachine(Canvas canvas, int w, int h, long now) {
        float left = w * 0.045f;
        float top = h * 0.19f;
        float right = w * 0.955f;
        float bottom = h * 0.73f;

        paint.setAntiAlias(true);
        int machineAlpha = reachStage >= 4 && spinning ? 245 : 220;
        paint.setColor(Color.argb(machineAlpha, 55, 37, 76));
        canvas.drawRoundRect(new RectF(left, top, right, bottom), w * 0.05f, w * 0.05f, paint);

        paint.setStyle(Paint.Style.STROKE);
        float stroke = reachStage >= 3 && spinning ? 0.008f + reachStage * 0.0012f : 0.0065f;
        paint.setStrokeWidth(w * stroke);
        if (reachStage >= 3 && spinning) {
            float p = 0.5f + 0.5f * (float) Math.sin(now / Math.max(38.0, 100.0 - reachStage * 9.0));
            paint.setColor(Color.rgb(255, (int) (110 + p * 110), (int) (70 + p * 140)));
        } else {
            paint.setColor(Color.rgb(188, 127, 177));
        }
        canvas.drawRoundRect(new RectF(left, top, right, bottom), w * 0.05f, w * 0.05f, paint);
        paint.setStyle(Paint.Style.FILL);

        float[] centers = {w * 0.20f, w * 0.50f, w * 0.80f};
        boolean celebrating = now < celebrationUntil;
        boolean reaching = spinning && reachTriggered;

        for (int i = 0; i < 3; i++) {
            float bob = (float) Math.sin((now / 360.0) + i * 1.5) * h * 0.0018f;
            float tremble = 0f;
            if (reaching && i == 2 && reachStage >= 3) {
                float power = 0.003f + Math.min(0.024f, (reachStage - 2) * 0.005f);
                tremble = (float) Math.sin(now / Math.max(8.0, 28.0 - reachStage * 3.1)) * w * power;
            }

            float jump = 0f;
            if (celebrating) {
                float t = (now % 360L) / 360f;
                jump = (float) -Math.abs(Math.sin(t * Math.PI)) * h * (0.040f + i * 0.007f);
            }

            boolean cheerPose = celebrating || (reaching && reachStage >= 5 && i != 2);
            drawPixelGirl(canvas, centers[i] + tremble, h * 0.385f + bob + jump,
                    w * 0.0105f, i, cheerPose);

            float scale = 1f;
            if (now < reelPulseUntil[i]) {
                float remaining = (reelPulseUntil[i] - now) / 720f;
                scale = 1f + 0.19f * (float) Math.sin((1f - remaining) * Math.PI);
            }
            if (reaching && i == 2 && reachStage >= 5) {
                scale *= 1f + 0.035f * (0.5f + 0.5f * (float) Math.sin(now / 47.0));
            }
            drawNumberCard(canvas, centers[i], h * 0.545f + jump * 0.30f,
                    w, h, shown[i], i, scale);
            drawNameTag(canvas, centers[i], h * 0.675f, w, names[i]);
        }

        if (reaching) {
            paint.setAntiAlias(true);
            paint.setTextAlign(Paint.Align.CENTER);
            String[] labels = {"", "……？", "もしかして…", "リーチ！！", "まだ続く…！", "激アツッ！！", "止まれぇぇぇ！！"};
            paint.setColor(reachStage <= 2 ? Color.rgb(221, 211, 228) : Color.rgb(255, 236, 102));
            paint.setTextSize(w * (0.034f + Math.max(0, reachStage - 1) * 0.006f));
            canvas.drawText(labels[Math.min(reachStage, 6)], w / 2f, h * 0.715f, paint);
        }
    }

    private void drawPixelGirl(Canvas canvas, float cx, float cy, float s, int index, boolean celebrate) {
        paint.setAntiAlias(false);
        int hair = hairColors[index];
        int outfit = outfitColors[index];
        int skin = Color.rgb(255, 220, 202);
        int dark = Color.rgb(51, 35, 58);

        block(canvas, cx - 8*s, cy - 11*s, 16*s, 13*s, hair);
        block(canvas, cx - 10*s, cy - 7*s, 3*s, 12*s, hair);
        block(canvas, cx + 7*s, cy - 7*s, 3*s, 12*s, hair);
        if (index == 0) {
            block(canvas, cx - 13*s, cy - 6*s, 5*s, 5*s, hair);
            block(canvas, cx + 8*s, cy - 6*s, 5*s, 5*s, hair);
        } else if (index == 1) {
            block(canvas, cx + 7*s, cy + 1*s, 5*s, 9*s, hair);
        } else {
            block(canvas, cx - 9*s, cy - 13*s, 6*s, 4*s, hair);
        }

        block(canvas, cx - 6*s, cy - 7*s, 12*s, 9*s, skin);
        block(canvas, cx - 4*s, cy - 4*s, 2*s, 2*s, dark);
        block(canvas, cx + 2*s, cy - 4*s, 2*s, 2*s, dark);
        if (celebrate) {
            block(canvas, cx - 2*s, cy, 4*s, 1.5f*s, Color.rgb(198, 74, 112));
        } else {
            block(canvas, cx - 1*s, cy, 2*s, 1*s, Color.rgb(198, 74, 112));
        }

        block(canvas, cx - 5*s, cy + 2*s, 10*s, 9*s, outfit);
        block(canvas, cx - 7*s, cy + 10*s, 14*s, 5*s, mix(outfit, Color.WHITE));
        block(canvas, cx - 5*s, cy + 15*s, 3*s, 7*s, skin);
        block(canvas, cx + 2*s, cy + 15*s, 3*s, 7*s, skin);
        block(canvas, cx - 6*s, cy + 21*s, 5*s, 2*s, dark);
        block(canvas, cx + 1*s, cy + 21*s, 5*s, 2*s, dark);

        if (celebrate) {
            block(canvas, cx - 9*s, cy + 2*s, 4*s, 3*s, skin);
            block(canvas, cx - 11*s, cy - 2*s, 3*s, 5*s, skin);
            block(canvas, cx + 5*s, cy + 2*s, 4*s, 3*s, skin);
            block(canvas, cx + 8*s, cy - 2*s, 3*s, 5*s, skin);
        } else {
            block(canvas, cx - 9*s, cy + 5*s, 5*s, 3*s, skin);
            block(canvas, cx + 4*s, cy + 5*s, 5*s, 3*s, skin);
        }

        block(canvas, cx - 5*s, cy - 9*s, 2*s, 1.5f*s, Color.argb(180, 255, 255, 255));
        paint.setAntiAlias(true);
    }

    private void drawNumberCard(Canvas canvas, float cx, float cy, int w, int h,
                                int number, int index, float scale) {
        float cw = w * 0.235f;
        float ch = h * 0.135f;

        canvas.save();
        canvas.scale(scale, scale, cx, cy);
        RectF card = new RectF(cx - cw/2f, cy - ch/2f, cx + cw/2f, cy + ch/2f);

        paint.setAntiAlias(true);
        paint.setShadowLayer(w * 0.020f, 0, h * 0.006f, Color.argb(105, 0, 0, 0));
        paint.setColor(Color.rgb(247, 241, 250));
        canvas.drawRoundRect(card, w * 0.035f, w * 0.035f, paint);
        paint.clearShadowLayer();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(w * 0.007f);
        paint.setColor(outfitColors[index]);
        canvas.drawRoundRect(card, w * 0.035f, w * 0.035f, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        paint.setTextSize(w * 0.145f);
        paint.setColor(Color.rgb(52, 34, 68));
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = cy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(String.valueOf(number), cx, baseline, paint);
        canvas.restore();
    }

    private void drawNameTag(Canvas canvas, float cx, float y, int w, String name) {
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(w * 0.031f);
        paint.setColor(Color.rgb(213, 199, 222));
        canvas.drawText(name, cx, y, paint);
    }

    private void drawSpinButton(Canvas canvas, int w, int h, long now) {
        float bw = w * 0.64f;
        float bh = h * 0.105f;
        float cx = w / 2f;
        float cy = h * 0.845f;
        spinButton.set(cx - bw/2f, cy - bh/2f, cx + bw/2f, cy + bh/2f);

        paint.setAntiAlias(true);
        paint.setShadowLayer(w * 0.018f, 0, h * 0.006f, Color.argb(110, 0, 0, 0));
        if (spinning && reachStage >= 4) {
            float p = 0.5f + 0.5f * (float) Math.sin(now / Math.max(42.0, 95.0 - reachStage * 8.0));
            paint.setColor(Color.rgb(245, (int) (65 + p * 95), 117));
        } else {
            paint.setColor(spinning ? Color.rgb(92, 78, 108) : Color.rgb(219, 83, 148));
        }
        canvas.drawRoundRect(spinButton, bh/2f, bh/2f, paint);
        paint.clearShadowLayer();

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(w * 0.060f);
        paint.setColor(Color.WHITE);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = cy - (fm.ascent + fm.descent) / 2f;
        String label;
        if (!spinning) label = "SPIN!";
        else if (reachStage <= 1) label = "……";
        else if (reachStage == 2) label = "ん……？";
        else if (reachStage <= 4) label = "来てる…！";
        else label = "うわあああ！！";
        canvas.drawText(label, cx, baseline, paint);

        paint.setTextSize(w * 0.027f);
        paint.setColor(Color.rgb(177, 163, 190));
        canvas.drawText("普段は静か。来た時だけ全部盛り。", cx, h * 0.925f, paint);
    }

    private void updateGameState(long now) {
        if (!spinning) return;

        long e = now - spinStart;
        boolean reachSpin = target[0] == target[1];
        long firstStop = 650L;
        long secondStop = 1000L;
        long thirdStop = reachSpin ? 9200L : 1550L;

        updateReel(0, e, firstStop, now, 58L);
        updateReel(1, e, secondStop, now, 62L);

        if (e < thirdStop) {
            long interval = 55L;
            if (reachSpin && e >= secondStop) {
                if (e >= 8500L) interval = 260L;
                else if (e >= 7600L) interval = 190L;
                else if (e >= 6500L) interval = 135L;
                else if (e >= 5100L) interval = 105L;
                else if (e >= 3700L) interval = 84L;
                else interval = 68L;
            }
            shown[2] = (int) ((e / interval + 6L) % 10L);
        } else {
            shown[2] = target[2];
            if (!reelLocked[2]) {
                reelLocked[2] = true;
                reelPulseUntil[2] = now + 950L;
            }
        }

        // Slow burn: the first part of a reach is intentionally almost silent.
        if (reachSpin && !reachTriggered && e >= secondStop) {
            reachTriggered = true;
            reachStage = 1;
            triggerCutIn(now, 850L, 7, "……ん？", "今、ふたり揃った……？");
        }
        if (reachSpin && reachStage < 2 && e >= 2400L) {
            reachStage = 2;
            triggerCutIn(now, 950L, 8, "もしかして…", "ミオだけ、まだ回ってる……");
        }
        if (reachSpin && reachStage < 3 && e >= 3700L) {
            reachStage = 3;
            triggerCutIn(now, 1150L, 1, "リーチ！！", "ここからやで旦那はん……！");
        }
        if (reachSpin && reachStage < 4 && e >= 5100L) {
            reachStage = 4;
            triggerCutIn(now, 1100L, 4, "まだ終わらないッ！", "回る、回る……まだ止まらへん！！");
        }
        if (reachSpin && reachStage < 5 && e >= 6500L) {
            reachStage = 5;
            triggerCutIn(now, 1250L, 5, "激アツッ！！！", "これ来てる！ 全部光ってるぅぅ！！");
        }
        if (reachSpin && reachStage < 6 && e >= 7900L) {
            reachStage = 6;
            triggerCutIn(now, 1450L, 6, "止まれぇぇぇぇ！！！", "旦那はん見てて！ ここ！ ここで止まってぇぇ！！");
        }

        if (!resultTriggered && e >= thirdStop) {
            resultTriggered = true;
            spinning = false;
            boolean jackpot = target[0] == target[1] && target[1] == target[2];

            if (jackpot) {
                wins++;
                celebrationStart = now;
                celebrationUntil = now + 6500L;
                triggerCutIn(now, 3300L, 2, "大当たりィィィィ！！！！！", "やったぁぁぁ！ 旦那はん最強ーーーっ！！");
            } else if (reachSpin) {
                triggerCutIn(now, 1800L, 3, "うわぁぁぁ惜しい！！", "そこまで行ったのにぃ！ 次、絶対いこ！！");
            }
        }
    }

    private void updateReel(int index, long elapsed, long stopAt, long now, long interval) {
        if (elapsed < stopAt) {
            shown[index] = (int) ((elapsed / interval + index * 3L) % 10L);
        } else {
            shown[index] = target[index];
            if (!reelLocked[index]) {
                reelLocked[index] = true;
                reelPulseUntil[index] = now + 720L;
            }
        }
    }

    private void startSpin() {
        if (spinning) return;

        long now = System.currentTimeMillis();
        spins++;
        spinning = true;
        reachTriggered = false;
        resultTriggered = false;
        reachStage = 0;
        celebrationStart = 0L;
        celebrationUntil = 0L;
        cutInUntil = 0L;
        spinStart = now;

        for (int i = 0; i < 3; i++) {
            reelLocked[i] = false;
            reelPulseUntil[i] = 0L;
        }

        float roll = random.nextFloat();
        if (roll < 0.14f) {
            int n = random.nextInt(10);
            target[0] = n;
            target[1] = n;
            target[2] = n;
        } else if (roll < 0.44f) {
            int n = random.nextInt(10);
            target[0] = n;
            target[1] = n;
            do {
                target[2] = random.nextInt(10);
            } while (target[2] == n);
        } else {
            target[0] = random.nextInt(10);
            do {
                target[1] = random.nextInt(10);
            } while (target[1] == target[0]);
            target[2] = random.nextInt(10);
        }

        performClick();
        invalidate();
    }

    private void triggerCutIn(long now, long duration, int type, String title, String message) {
        cutInStart = now;
        cutInUntil = now + duration;
        cutInType = type;
        cutInTitle = title;
        cutInMessage = message;
    }

    private void drawCutIn(Canvas canvas, int w, int h, long now) {
        float duration = Math.max(1f, cutInUntil - cutInStart);
        float t = Math.min(1f, (now - cutInStart) / duration);
        float enter = Math.min(1f, t / 0.13f);
        float exit = t > 0.86f ? Math.max(0f, (1f - t) / 0.14f) : 1f;
        float visible = Math.min(enter, exit);

        boolean whisper = cutInType == 7 || cutInType == 8;
        boolean huge = cutInType >= 5 && cutInType <= 6 || cutInType == 2;
        float top = whisper ? h * 0.285f : (huge ? h * 0.185f : h * 0.235f);
        float bottom = whisper ? h * 0.395f : (huge ? h * 0.465f : h * 0.415f);
        float slide = whisper ? (1f - visible) * w * 0.18f
                : (1f - visible) * w * (cutInType % 2 == 0 ? -0.95f : 0.95f);

        int accent = cutInAccent(cutInType);

        canvas.save();
        canvas.translate(slide, 0f);
        if (cutInType == 6) {
            float wobble = (float) Math.sin(now / 31.0) * 1.8f;
            canvas.rotate(wobble, w / 2f, (top + bottom) / 2f);
        }

        paint.setAntiAlias(true);
        paint.setColor(Color.argb(whisper ? 180 : 247, 21, 10, 36));
        canvas.drawRect(-w * 0.05f, top, w * 1.05f, bottom, paint);

        paint.setColor(accent);
        float edge = whisper ? h * 0.004f : h * 0.011f;
        canvas.drawRect(0, top, w, top + edge, paint);
        canvas.drawRect(0, bottom - edge, w, bottom, paint);

        if (!whisper) {
            int stripeCount = huge ? 24 : 13;
            for (int i = 0; i < stripeCount; i++) {
                float x = ((i * 97 + now / 7) % 1200L) / 1200f * w;
                paint.setColor(Color.argb(huge ? 105 : 65,
                        Color.red(accent), Color.green(accent), Color.blue(accent)));
                canvas.drawRect(x, top, x + w * (huge ? 0.018f : 0.011f), bottom, paint);
            }
        }

        int girlIndex = cutInType == 2 ? 0 : (cutInType == 3 ? 1 : 2);
        float girlScale = whisper ? w * 0.0105f : (huge ? w * 0.0175f : w * 0.0135f);
        drawPixelGirl(canvas, whisper ? w * 0.20f : w * 0.17f, (top + bottom) * 0.5f,
                girlScale, girlIndex, cutInType == 2 || cutInType >= 5 && cutInType <= 6);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setColor(accent);
        paint.setTextSize(w * (whisper ? 0.050f : (huge ? 0.084f : 0.067f)));
        canvas.drawText(cutInTitle, w * 0.31f, top + h * (whisper ? 0.048f : huge ? 0.108f : 0.075f), paint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(w * (whisper ? 0.030f : (huge ? 0.039f : 0.035f)));
        canvas.drawText(cutInMessage, w * 0.31f, top + h * (whisper ? 0.088f : huge ? 0.173f : 0.128f), paint);

        if (cutInType == 6 || cutInType == 2) {
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setTextSize(w * 0.22f);
            paint.setColor(Color.argb(105, 255, 255, 255));
            canvas.drawText("!!!", w * 0.99f, bottom - h * 0.010f, paint);
        }
        canvas.restore();

        if (cutInType == 6 || cutInType == 2) {
            float flash = (float) Math.sin(t * Math.PI);
            paint.setColor(Color.argb((int) (125 * flash), 255, 255, 255));
            canvas.drawRect(0, 0, w, h, paint);
        }
    }

    private int cutInAccent(int type) {
        if (type == 2) return Color.rgb(255, 222, 64);
        if (type == 3) return Color.rgb(138, 197, 255);
        if (type == 4) return Color.rgb(139, 236, 255);
        if (type == 5) return Color.rgb(255, 122, 44);
        if (type == 6) return Color.rgb(255, 42, 112);
        if (type == 7) return Color.rgb(207, 195, 216);
        if (type == 8) return Color.rgb(215, 195, 240);
        return Color.rgb(255, 105, 181);
    }

    private void drawOmen(Canvas canvas, int w, int h, long now) {
        // Barely-there signs before the real reach explodes.
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 310.0);
        paint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(true);
        paint.setStrokeWidth(w * 0.003f);
        paint.setColor(Color.argb((int) (20 + pulse * 30), 235, 215, 255));
        canvas.drawCircle(w * 0.80f, h * 0.545f, w * (0.13f + pulse * 0.015f), paint);
        if (reachStage >= 2) {
            paint.setStrokeWidth(w * 0.004f);
            paint.setColor(Color.argb((int) (22 + pulse * 35), 255, 142, 214));
            canvas.drawCircle(w * 0.80f, h * 0.545f, w * (0.17f + pulse * 0.02f), paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawHypeEffects(Canvas canvas, int w, int h, long now, boolean victory) {
        float cx = victory ? w * 0.5f : w * 0.80f;
        float cy = h * 0.53f;
        int stage = victory ? 7 : Math.max(1, reachStage);

        int[] lineCounts = {0, 0, 4, 15, 28, 46, 72, 92};
        int[] particleCounts = {0, 0, 4, 18, 34, 62, 100, 132};
        int lines = lineCounts[Math.min(stage, 7)];
        int particles = particleCounts[Math.min(stage, 7)];

        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < lines; i++) {
            double a = i * (Math.PI * 2.0 / Math.max(1, lines)) + now / (victory ? 260.0 : Math.max(260.0, 820.0 - stage * 75.0));
            float inner = w * (0.10f + (i % 4) * 0.010f);
            float outer = w * (0.22f + stage * 0.030f + (i % 3) * 0.025f);
            float x1 = cx + (float) Math.cos(a) * inner;
            float y1 = cy + (float) Math.sin(a) * inner;
            float x2 = cx + (float) Math.cos(a) * outer;
            float y2 = cy + (float) Math.sin(a) * outer;
            int alpha = victory ? 160 : Math.min(185, 18 + stage * 25);
            int c = i % 4 == 0 ? Color.rgb(255, 234, 78)
                    : (i % 4 == 1 ? Color.rgb(255, 61, 164)
                    : (i % 4 == 2 ? Color.rgb(103, 228, 255) : Color.rgb(255, 255, 255)));
            paint.setColor(Color.argb(alpha, Color.red(c), Color.green(c), Color.blue(c)));
            paint.setStrokeWidth(w * (0.0025f + (i % 4) * 0.0014f));
            canvas.drawLine(x1, y1, x2, y2, paint);
        }
        paint.setStyle(Paint.Style.FILL);

        paint.setAntiAlias(false);
        for (int i = 0; i < particles; i++) {
            long seed = i * 173L + now / (victory ? 5L : Math.max(6L, 18L - stage * 2L));
            float x = ((seed * 37L) % 1000L) / 1000f * w;
            float y = h * 0.14f + (((seed * 91L) % 1000L) / 1000f) * h * 0.66f;
            float s = w * (0.004f + (i % 6) * 0.0018f);
            int c = i % 4 == 0 ? Color.rgb(255, 231, 72)
                    : (i % 4 == 1 ? Color.rgb(255, 92, 187)
                    : (i % 4 == 2 ? Color.rgb(104, 229, 255) : Color.WHITE));
            block(canvas, x, y, s, s * (1.6f + (i % 3)), c);
        }
        paint.setAntiAlias(true);

        if (stage >= 4) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / (victory ? 35.0 : Math.max(35.0, 95.0 - stage * 9.0)));
            int alpha = victory ? (int) (28 + pulse * 80) : (int) ((stage - 3) * 9 + pulse * stage * 7);
            paint.setColor(Color.argb(Math.min(125, alpha), 255, victory ? 226 : 62, victory ? 100 : 174));
            canvas.drawRect(0, 0, w, h, paint);
        }

        if (!victory && stage >= 3) {
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(w * (0.032f + stage * 0.006f));
            paint.setColor(Color.argb(205, 255, 243, 102));
            String hype = stage == 3 ? "CHANCE" : stage == 4 ? "CHANCE UP" : stage == 5 ? "激アツ" : "MAXIMUM!!!";
            canvas.drawText(hype, w * 0.80f, h * 0.25f, paint);
        }

        if (stage >= 6) {
            drawMaximumBurst(canvas, w, h, now, victory);
        }
    }

    private void drawMaximumBurst(Canvas canvas, int w, int h, long now, boolean victory) {
        float cx = victory ? w * 0.50f : w * 0.80f;
        float cy = h * 0.54f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 42.0);

        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        for (int ring = 0; ring < 4; ring++) {
            float rr = w * (0.15f + ring * 0.075f + pulse * 0.035f);
            paint.setStrokeWidth(w * (0.010f - ring * 0.0015f));
            int alpha = 150 - ring * 25;
            paint.setColor(Color.argb(alpha, ring % 2 == 0 ? 255 : 115, 225, ring % 2 == 0 ? 90 : 255));
            canvas.drawCircle(cx, cy, rr, paint);
        }
        paint.setStyle(Paint.Style.FILL);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(w * (victory ? 0.23f : 0.16f));
        paint.setColor(Color.argb((int) (45 + pulse * 65), 255, 255, 255));
        canvas.save();
        canvas.rotate((float) Math.sin(now / 90.0) * 4f, w / 2f, h / 2f);
        canvas.drawText(victory ? "WIN!!! WIN!!!" : "!!!  !!!  !!!", w / 2f, h * 0.48f, paint);
        canvas.restore();
    }

    private void drawReelPulses(Canvas canvas, int w, int h, long now) {
        float[] centers = {w * 0.20f, w * 0.50f, w * 0.80f};
        paint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(true);
        for (int i = 0; i < 3; i++) {
            if (now >= reelPulseUntil[i]) continue;
            float left = Math.max(0f, (reelPulseUntil[i] - now) / 950f);
            float progress = 1f - left;
            for (int ring = 0; ring < 2; ring++) {
                float radius = w * (0.11f + progress * 0.15f + ring * 0.035f);
                paint.setStrokeWidth(w * 0.009f * (1f - progress * 0.6f));
                paint.setColor(Color.argb((int) (190 * (1f - progress)),
                        255, i == 2 ? 221 : 150, i == 2 ? 80 : 225));
                canvas.drawCircle(centers[i], h * 0.545f, radius, paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawSparkles(Canvas canvas, int w, int h, long now) {
        paint.setAntiAlias(false);
        for (int i = 0; i < 112; i++) {
            float phase = ((now / 6L + i * 71L) % 1000L) / 1000f;
            float x = ((i * 173) % 997) / 997f * w;
            float y = h * 0.12f + phase * h * 0.70f;
            float s = w * (0.005f + (i % 6) * 0.002f);
            int c = (i % 4 == 0) ? Color.rgb(255, 221, 70)
                    : (i % 4 == 1) ? Color.rgb(255, 110, 196)
                    : (i % 4 == 2) ? Color.rgb(120, 229, 255) : Color.WHITE;
            block(canvas, x, y, s, s * 2.2f, c);
            block(canvas, x - s/2f, y + s*0.6f, s * 2f, s, c);
        }
        paint.setAntiAlias(true);
    }

    private float currentShake(long now, int w) {
        if (spinning && reachTriggered) {
            if (reachStage <= 2) return 0f;
            float[] powers = {0f, 0f, 0f, 0.0015f, 0.0030f, 0.0055f, 0.0090f};
            return w * powers[Math.min(reachStage, 6)];
        }
        if (now < celebrationUntil && now - celebrationStart < 1700L) {
            float fade = 1f - (now - celebrationStart) / 1700f;
            return w * 0.017f * Math.max(0f, fade);
        }
        return 0f;
    }

    private void block(Canvas canvas, float x, float y, float w, float h, int color) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(x, y, x + w, y + h, paint);
    }

    private int mix(int a, int b) {
        return Color.rgb(
                (Color.red(a) + Color.red(b)) / 2,
                (Color.green(a) + Color.green(b)) / 2,
                (Color.blue(a) + Color.blue(b)) / 2
        );
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (spinButton.contains(event.getX(), event.getY())) {
                startSpin();
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
