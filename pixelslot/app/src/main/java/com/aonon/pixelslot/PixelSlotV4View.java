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

public class PixelSlotV4View extends View {
    private final Paint paint = new Paint();
    private final Random random = new Random();
    private final RectF spinButton = new RectF();
    private final int[] shown = {2, 5, 8};
    private final int[] target = {2, 5, 8};
    private final boolean[] reelLocked = {false, false, false};
    private final long[] reelPulseUntil = {0L, 0L, 0L};

    private boolean spinning = false;
    private boolean reachTriggered = false;
    private boolean resultTriggered = false;
    private int reachStage = 0;
    private long spinStart = 0L;

    private boolean premiumSpin = false;
    private boolean premiumRevealTriggered = false;
    private long premiumRevealStart = 0L;
    private long premiumRevealUntil = 0L;

    private boolean revivalSpin = false;
    private boolean revivalActive = false;
    private boolean revivalResolved = false;
    private long revivalStart = 0L;
    private long revivalFlashUntil = 0L;

    private long celebrationStart = 0L;
    private long celebrationUntil = 0L;

    private long cutInStart = 0L;
    private long cutInUntil = 0L;
    private String cutInTitle = "";
    private String cutInMessage = "";
    private int cutInType = 0;

    private int spins = 0;
    private int wins = 0;

    private final int[] hairColors = {
            Color.rgb(238, 113, 153), Color.rgb(104, 92, 176), Color.rgb(85, 157, 178)
    };
    private final int[] outfitColors = {
            Color.rgb(255, 190, 211), Color.rgb(174, 157, 240), Color.rgb(143, 220, 218)
    };
    private final String[] names = {"モモ", "ルナ", "ミオ"};

    public PixelSlotV4View(Context context) {
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
            float sx = (float) Math.sin(now / 10.0) * shake;
            float sy = (float) Math.cos(now / 14.0) * shake * 0.70f;
            canvas.translate(sx, sy);
        }
        drawHeader(canvas, w, h);
        drawMachine(canvas, w, h, now);
        drawSpinButton(canvas, w, h, now);
        canvas.restore();

        if (reachTriggered && spinning) {
            if (reachStage <= 2) drawOmen(canvas, w, h, now);
            if (reachStage >= 3) drawHypeEffects(canvas, w, h, now, false);
        }
        if (now < celebrationUntil) {
            drawHypeEffects(canvas, w, h, now, true);
            drawSparkles(canvas, w, h, now);
            if (premiumSpin) drawPremiumVictoryBanner(canvas, w, h, now);
        }
        drawReelPulses(canvas, w, h, now);
        if (now < cutInUntil) drawCutIn(canvas, w, h, now);
        if (now < premiumRevealUntil) drawPremiumReveal(canvas, w, h, now);
        if (revivalActive) drawRevivalOverlay(canvas, w, h, now);
        if (now < revivalFlashUntil) drawRevivalFlash(canvas, w, h, now);

        if (spinning || revivalActive || now < cutInUntil || now < celebrationUntil
                || now < premiumRevealUntil || now < revivalFlashUntil
                || now < reelPulseUntil[0] || now < reelPulseUntil[1] || now < reelPulseUntil[2]) {
            postInvalidateOnAnimation();
        }
    }

    private void drawBackground(Canvas canvas, int w, int h, long now) {
        int top = Color.rgb(30, 20, 46);
        int bottom = Color.rgb(13, 9, 23);
        if (spinning && reachStage >= 4) top = Color.rgb(60 + Math.min(32, reachStage * 4), 14, 72);
        if (now < celebrationUntil) {
            top = premiumSpin ? Color.rgb(78, 50, 8) : Color.rgb(82, 17, 54);
            bottom = premiumSpin ? Color.rgb(32, 18, 3) : Color.rgb(48, 13, 20);
        }
        paint.setShader(new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
        paint.setColor(Color.argb(16, 255, 207, 236));
        canvas.drawCircle(w * 0.10f, h * 0.15f, w * 0.29f, paint);
        paint.setColor(Color.argb(10, 132, 222, 255));
        canvas.drawCircle(w * 0.94f, h * 0.65f, w * 0.33f, paint);
    }

    private void drawHeader(Canvas canvas, int w, int h) {
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setColor(Color.rgb(255, 221, 244));
        paint.setTextSize(w * 0.073f);
        canvas.drawText("PIXEL SLOT", w / 2f, h * 0.075f, paint);
        paint.setColor(Color.rgb(190, 176, 204));
        paint.setTextSize(w * 0.029f);
        canvas.drawText("静寂のあとに、暗転・復活・プレミア。", w / 2f, h * 0.112f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(Color.rgb(235, 226, 244));
        paint.setTextSize(w * 0.031f);
        canvas.drawText("SPIN  " + spins, w * 0.07f, h * 0.155f, paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("WIN  " + wins, w * 0.93f, h * 0.155f, paint);
    }

    private void drawMachine(Canvas canvas, int w, int h, long now) {
        float left = w * 0.045f, top = h * 0.19f, right = w * 0.955f, bottom = h * 0.73f;
        paint.setAntiAlias(true);
        paint.setColor(Color.argb(220, 55, 37, 76));
        canvas.drawRoundRect(new RectF(left, top, right, bottom), w * 0.05f, w * 0.05f, paint);
        paint.setStyle(Paint.Style.STROKE);
        float stroke = spinning && reachStage >= 3 ? 0.007f + reachStage * 0.0012f : 0.006f;
        paint.setStrokeWidth(w * stroke);
        if (spinning && reachStage >= 3) {
            float p = 0.5f + 0.5f * (float) Math.sin(now / Math.max(34.0, 102.0 - reachStage * 9.0));
            paint.setColor(Color.rgb(255, (int) (105 + p * 115), (int) (70 + p * 145)));
        } else paint.setColor(Color.rgb(184, 124, 174));
        canvas.drawRoundRect(new RectF(left, top, right, bottom), w * 0.05f, w * 0.05f, paint);
        paint.setStyle(Paint.Style.FILL);

        float[] centers = {w * 0.20f, w * 0.50f, w * 0.80f};
        boolean celebrating = now < celebrationUntil;
        boolean reaching = spinning && reachTriggered;
        for (int i = 0; i < 3; i++) {
            float bob = (float) Math.sin((now / 360.0) + i * 1.5) * h * 0.0018f;
            float tremble = 0f;
            if (reaching && i == 2 && reachStage >= 3) {
                float power = 0.0025f + Math.min(0.027f, (reachStage - 2) * 0.0045f);
                tremble = (float) Math.sin(now / Math.max(7.0, 29.0 - reachStage * 3.0)) * w * power;
            }
            float jump = 0f;
            if (celebrating) {
                float t = (now % 340L) / 340f;
                jump = (float) -Math.abs(Math.sin(t * Math.PI)) * h * (0.043f + i * 0.007f);
            }
            boolean cheerPose = celebrating || (reaching && reachStage >= 6 && i != 2);
            drawPixelGirl(canvas, centers[i] + tremble, h * 0.385f + bob + jump, w * 0.0105f, i, cheerPose);
            float scale = 1f;
            if (now < reelPulseUntil[i]) {
                float leftPulse = Math.max(0f, (reelPulseUntil[i] - now) / 950f);
                scale = 1f + 0.20f * (float) Math.sin((1f - leftPulse) * Math.PI);
            }
            if (reaching && i == 2 && reachStage >= 5) scale *= 1f + 0.04f * (0.5f + 0.5f * (float) Math.sin(now / 45.0));
            drawNumberCard(canvas, centers[i], h * 0.545f + jump * 0.30f, w, h, shown[i], i, scale);
            drawNameTag(canvas, centers[i], h * 0.675f, w, names[i]);
        }
        if (reaching) {
            String[] labels = {"", "……？", "もしかして…", "リーチ！！", "まだ続く…！", "激アツッ！！", "限界突破…！", "止まれぇぇぇ！！"};
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setColor(reachStage <= 2 ? Color.rgb(220, 211, 228) : Color.rgb(255, 236, 102));
            paint.setTextSize(w * (0.033f + Math.max(0, reachStage - 1) * 0.006f));
            canvas.drawText(labels[Math.min(reachStage, 7)], w / 2f, h * 0.715f, paint);
        }
    }

    private void drawPixelGirl(Canvas canvas, float cx, float cy, float s, int index, boolean celebrate) {
        paint.setAntiAlias(false);
        int hair = hairColors[index], outfit = outfitColors[index], skin = Color.rgb(255, 220, 202), dark = Color.rgb(51, 35, 58);
        block(canvas, cx - 8*s, cy - 11*s, 16*s, 13*s, hair);
        block(canvas, cx - 10*s, cy - 7*s, 3*s, 12*s, hair);
        block(canvas, cx + 7*s, cy - 7*s, 3*s, 12*s, hair);
        if (index == 0) {
            block(canvas, cx - 13*s, cy - 6*s, 5*s, 5*s, hair);
            block(canvas, cx + 8*s, cy - 6*s, 5*s, 5*s, hair);
        } else if (index == 1) block(canvas, cx + 7*s, cy + 1*s, 5*s, 9*s, hair);
        else block(canvas, cx - 9*s, cy - 13*s, 6*s, 4*s, hair);
        block(canvas, cx - 6*s, cy - 7*s, 12*s, 9*s, skin);
        block(canvas, cx - 4*s, cy - 4*s, 2*s, 2*s, dark);
        block(canvas, cx + 2*s, cy - 4*s, 2*s, 2*s, dark);
        block(canvas, cx - (celebrate ? 2f : 1f)*s, cy, (celebrate ? 4f : 2f)*s, celebrate ? 1.5f*s : 1f*s, Color.rgb(198, 74, 112));
        block(canvas, cx - 5*s, cy + 2*s, 10*s, 9*s, outfit);
        block(canvas, cx - 7*s, cy + 10*s, 14*s, 5*s, mix(outfit, Color.WHITE));
        block(canvas, cx - 5*s, cy + 15*s, 3*s, 7*s, skin);
        block(canvas, cx + 2*s, cy + 15*s, 3*s, 7*s, skin);
        block(canvas, cx - 6*s, cy + 21*s, 5*s, 2*s, dark);
        block(canvas, cx + 1*s, cy + 21*s, 5*s, 2*s, dark);
        if (celebrate) {
            block(canvas, cx - 9*s, cy + 2*s, 4*s, 3*s, skin); block(canvas, cx - 11*s, cy - 2*s, 3*s, 5*s, skin);
            block(canvas, cx + 5*s, cy + 2*s, 4*s, 3*s, skin); block(canvas, cx + 8*s, cy - 2*s, 3*s, 5*s, skin);
        } else {
            block(canvas, cx - 9*s, cy + 5*s, 5*s, 3*s, skin); block(canvas, cx + 4*s, cy + 5*s, 5*s, 3*s, skin);
        }
        block(canvas, cx - 5*s, cy - 9*s, 2*s, 1.5f*s, Color.argb(180, 255, 255, 255));
        paint.setAntiAlias(true);
    }

    private void drawNumberCard(Canvas canvas, float cx, float cy, int w, int h, int number, int index, float scale) {
        float cw = w * 0.235f, ch = h * 0.135f;
        canvas.save(); canvas.scale(scale, scale, cx, cy);
        RectF card = new RectF(cx - cw/2f, cy - ch/2f, cx + cw/2f, cy + ch/2f);
        paint.setShadowLayer(w * 0.020f, 0, h * 0.006f, Color.argb(105, 0, 0, 0));
        paint.setColor(Color.rgb(247, 241, 250)); canvas.drawRoundRect(card, w * 0.035f, w * 0.035f, paint); paint.clearShadowLayer();
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(w * 0.007f); paint.setColor(outfitColors[index]);
        canvas.drawRoundRect(card, w * 0.035f, w * 0.035f, paint); paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER); paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        paint.setTextSize(w * 0.145f); paint.setColor(Color.rgb(52, 34, 68));
        Paint.FontMetrics fm = paint.getFontMetrics(); float baseline = cy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(String.valueOf(number), cx, baseline, paint); canvas.restore();
    }

    private void drawNameTag(Canvas canvas, float cx, float y, int w, String name) {
        paint.setTextAlign(Paint.Align.CENTER); paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(w * 0.031f); paint.setColor(Color.rgb(213, 199, 222)); canvas.drawText(name, cx, y, paint);
    }

    private void drawSpinButton(Canvas canvas, int w, int h, long now) {
        float bw = w * 0.64f, bh = h * 0.105f, cx = w / 2f, cy = h * 0.845f;
        spinButton.set(cx - bw/2f, cy - bh/2f, cx + bw/2f, cy + bh/2f);
        paint.setShadowLayer(w * 0.018f, 0, h * 0.006f, Color.argb(110, 0, 0, 0));
        if (spinning && reachStage >= 4) {
            float p = 0.5f + 0.5f * (float) Math.sin(now / Math.max(38.0, 94.0 - reachStage * 8.0));
            paint.setColor(Color.rgb(245, (int) (58 + p * 102), 114));
        } else paint.setColor(spinning || revivalActive ? Color.rgb(92, 78, 108) : Color.rgb(219, 83, 148));
        canvas.drawRoundRect(spinButton, bh/2f, bh/2f, paint); paint.clearShadowLayer();
        paint.setTextAlign(Paint.Align.CENTER); paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(w * 0.060f); paint.setColor(Color.WHITE);
        Paint.FontMetrics fm = paint.getFontMetrics(); float baseline = cy - (fm.ascent + fm.descent) / 2f;
        String label;
        if (revivalActive) label = "……"; else if (!spinning) label = "SPIN!"; else if (reachStage <= 1) label = "……";
        else if (reachStage == 2) label = "ん……？"; else if (reachStage <= 4) label = "来てる…！"; else label = "うわあああ！！";
        canvas.drawText(label, cx, baseline, paint);
        paint.setTextSize(w * 0.027f); paint.setColor(Color.rgb(177, 163, 190));
        canvas.drawText("普段は静か。来た時だけ世界が壊れる。", cx, h * 0.925f, paint);
    }

    private void updateGameState(long now) {
        if (revivalActive) {
            long re = now - revivalStart;
            if (!revivalResolved && re >= 1500L) {
                revivalResolved = true; revivalActive = false; shown[2] = target[0]; reelPulseUntil[2] = now + 1200L;
                revivalFlashUntil = now + 900L; wins++; celebrationStart = now; celebrationUntil = now + 7600L;
                triggerCutIn(now, 3100L, 10, "復活ッッッ！！！", "まだ終わってへん！ 揃ったぁぁぁ！！");
            }
            return;
        }
        if (!spinning) return;
        long e = now - spinStart;
        boolean reachSpin = target[0] == target[1];
        long firstStop = 650L, secondStop = 1000L, thirdStop = reachSpin ? 11200L : 1550L;
        updateReel(0, e, firstStop, now, 58L); updateReel(1, e, secondStop, now, 62L);
        if (e < thirdStop) {
            long interval = 55L;
            if (reachSpin && e >= secondStop) {
                if (e >= 10500L) interval = 320L; else if (e >= 9600L) interval = 240L; else if (e >= 8300L) interval = 175L;
                else if (e >= 6800L) interval = 128L; else if (e >= 5200L) interval = 100L; else if (e >= 3500L) interval = 82L; else interval = 68L;
            }
            shown[2] = (int) ((e / interval + 6L) % 10L);
        } else {
            shown[2] = target[2];
            if (!reelLocked[2]) { reelLocked[2] = true; reelPulseUntil[2] = now + 1000L; }
        }
        if (reachSpin && !reachTriggered && e >= secondStop) { reachTriggered = true; reachStage = 1; triggerCutIn(now, 820L, 7, "……ん？", "今、ふたり揃った……？"); }
        if (reachSpin && reachStage < 2 && e >= 2800L) { reachStage = 2; triggerCutIn(now, 950L, 8, "もしかして…", "ミオだけ、まだ回ってる……"); }
        if (reachSpin && reachStage < 3 && e >= 4500L) { reachStage = 3; triggerCutIn(now, 1150L, 1, "リーチ！！", "ここからやで旦那はん……！"); }
        if (reachSpin && reachStage < 4 && e >= 6200L) { reachStage = 4; triggerCutIn(now, 1150L, 4, "まだ終わらないッ！", "回る、回る……まだ止まらへん！！"); }
        if (premiumSpin && !premiumRevealTriggered && e >= 7350L) { premiumRevealTriggered = true; premiumRevealStart = now; premiumRevealUntil = now + 2450L; cutInUntil = 0L; }
        if (reachSpin && reachStage < 5 && e >= 7900L) { reachStage = 5; if (!premiumSpin) triggerCutIn(now, 1300L, 5, "激アツッ！！！", "これ来てる！ 全部光ってるぅぅ！！"); }
        if (reachSpin && reachStage < 6 && e >= 9400L) { reachStage = 6; if (!premiumSpin) triggerCutIn(now, 1350L, 9, "限界突破ッ！！！", "ここまで来たら、もう止められへん！！"); }
        if (reachSpin && reachStage < 7 && e >= 10400L) { reachStage = 7; if (!premiumSpin) triggerCutIn(now, 1450L, 6, "止まれぇぇぇぇ！！！", "旦那はん見てて！ ここで止まってぇぇ！！"); }
        if (!resultTriggered && e >= thirdStop) {
            resultTriggered = true; spinning = false;
            if (revivalSpin) { revivalActive = true; revivalStart = now; cutInUntil = 0L; return; }
            boolean jackpot = target[0] == target[1] && target[1] == target[2];
            if (jackpot) {
                wins++; celebrationStart = now; celebrationUntil = now + (premiumSpin ? 9000L : 6800L);
                if (premiumSpin) triggerCutIn(now, 3800L, 11, "PREMIUM 777！！！", "旦那はん、これは特別やでーーっ！！");
                else triggerCutIn(now, 3300L, 2, "大当たりィィィィ！！！！！", "やったぁぁぁ！ 旦那はん最強ーーーっ！！");
            } else if (reachSpin) triggerCutIn(now, 1800L, 3, "うわぁぁぁ惜しい！！", "そこまで行ったのにぃ！ 次、絶対いこ！！");
        }
    }

    private void updateReel(int index, long elapsed, long stopAt, long now, long interval) {
        if (elapsed < stopAt) shown[index] = (int) ((elapsed / interval + index * 3L) % 10L);
        else { shown[index] = target[index]; if (!reelLocked[index]) { reelLocked[index] = true; reelPulseUntil[index] = now + 720L; } }
    }

    private void startSpin() {
        if (spinning || revivalActive) return;
        long now = System.currentTimeMillis();
        spins++; spinning = true; reachTriggered = false; resultTriggered = false; reachStage = 0; celebrationStart = 0L; celebrationUntil = 0L; cutInUntil = 0L; spinStart = now;
        premiumSpin = false; premiumRevealTriggered = false; premiumRevealStart = 0L; premiumRevealUntil = 0L;
        revivalSpin = false; revivalActive = false; revivalResolved = false; revivalStart = 0L; revivalFlashUntil = 0L;
        for (int i = 0; i < 3; i++) { reelLocked[i] = false; reelPulseUntil[i] = 0L; }
        float roll = random.nextFloat();
        if (roll < 0.03f) { premiumSpin = true; target[0] = 7; target[1] = 7; target[2] = 7; }
        else if (roll < 0.08f) { revivalSpin = true; int n = random.nextInt(10); target[0] = n; target[1] = n; do { target[2] = random.nextInt(10); } while (target[2] == n); }
        else if (roll < 0.14f) { int n = random.nextInt(10); target[0] = n; target[1] = n; target[2] = n; }
        else if (roll < 0.44f) { int n = random.nextInt(10); target[0] = n; target[1] = n; do { target[2] = random.nextInt(10); } while (target[2] == n); }
        else { target[0] = random.nextInt(10); do { target[1] = random.nextInt(10); } while (target[1] == target[0]); target[2] = random.nextInt(10); }
        performClick(); invalidate();
    }

    private void triggerCutIn(long now, long duration, int type, String title, String message) {
        cutInStart = now; cutInUntil = now + duration; cutInType = type; cutInTitle = title; cutInMessage = message;
    }

    private void drawCutIn(Canvas canvas, int w, int h, long now) {
        float duration = Math.max(1f, cutInUntil - cutInStart), t = Math.min(1f, (now - cutInStart) / duration);
        float enter = Math.min(1f, t / 0.13f), exit = t > 0.86f ? Math.max(0f, (1f - t) / 0.14f) : 1f, visible = Math.min(enter, exit);
        boolean whisper = cutInType == 7 || cutInType == 8;
        boolean huge = cutInType == 2 || cutInType == 6 || cutInType == 9 || cutInType == 10 || cutInType == 11;
        float top = whisper ? h * 0.285f : (huge ? h * 0.175f : h * 0.235f), bottom = whisper ? h * 0.395f : (huge ? h * 0.475f : h * 0.415f);
        float slide = whisper ? (1f - visible) * w * 0.18f : (1f - visible) * w * (cutInType % 2 == 0 ? -0.95f : 0.95f);
        int accent = cutInAccent(cutInType);
        canvas.save(); canvas.translate(slide, 0f);
        if (cutInType == 6 || cutInType == 9 || cutInType == 10) canvas.rotate((float) Math.sin(now / 31.0) * 1.7f, w / 2f, (top + bottom) / 2f);
        paint.setColor(Color.argb(whisper ? 180 : 248, 21, 10, 36)); canvas.drawRect(-w * 0.05f, top, w * 1.05f, bottom, paint);
        paint.setColor(accent); float edge = whisper ? h * 0.004f : h * 0.011f; canvas.drawRect(0, top, w, top + edge, paint); canvas.drawRect(0, bottom - edge, w, bottom, paint);
        if (!whisper) for (int i = 0; i < (huge ? 26 : 14); i++) { float x = ((i * 97 + now / 7) % 1200L) / 1200f * w; paint.setColor(Color.argb(huge ? 110 : 65, Color.red(accent), Color.green(accent), Color.blue(accent))); canvas.drawRect(x, top, x + w * (huge ? 0.018f : 0.011f), bottom, paint); }
        int girlIndex = cutInType == 2 || cutInType == 11 ? 0 : (cutInType == 3 ? 1 : 2);
        float girlScale = whisper ? w * 0.0105f : (huge ? w * 0.0180f : w * 0.0135f);
        drawPixelGirl(canvas, whisper ? w * 0.20f : w * 0.17f, (top + bottom) * 0.5f, girlScale, girlIndex, cutInType == 2 || cutInType >= 5);
        paint.setTextAlign(Paint.Align.LEFT); paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); paint.setColor(accent); paint.setTextSize(w * (whisper ? 0.050f : (huge ? 0.082f : 0.067f)));
        canvas.drawText(cutInTitle, w * 0.31f, top + h * (whisper ? 0.048f : huge ? 0.110f : 0.075f), paint);
        paint.setColor(Color.WHITE); paint.setTextSize(w * (whisper ? 0.030f : (huge ? 0.038f : 0.035f))); canvas.drawText(cutInMessage, w * 0.31f, top + h * (whisper ? 0.088f : huge ? 0.176f : 0.128f), paint);
        if (huge) { paint.setTextAlign(Paint.Align.RIGHT); paint.setTextSize(w * 0.22f); paint.setColor(Color.argb(100, 255, 255, 255)); canvas.drawText("!!!", w * 0.99f, bottom - h * 0.010f, paint); }
        canvas.restore();
        if (huge) { float flash = (float) Math.sin(t * Math.PI); paint.setColor(Color.argb((int) (125 * flash), 255, cutInType == 11 ? 222 : 255, cutInType == 11 ? 80 : 255)); canvas.drawRect(0, 0, w, h, paint); }
    }

    private int cutInAccent(int type) {
        if (type == 2) return Color.rgb(255, 222, 64); if (type == 3) return Color.rgb(138, 197, 255); if (type == 4) return Color.rgb(139, 236, 255);
        if (type == 5) return Color.rgb(255, 122, 44); if (type == 6) return Color.rgb(255, 42, 112); if (type == 7) return Color.rgb(207, 195, 216);
        if (type == 8) return Color.rgb(215, 195, 240); if (type == 9) return Color.rgb(255, 65, 65); if (type == 10) return Color.rgb(255, 80, 205);
        if (type == 11) return Color.rgb(255, 221, 61); return Color.rgb(255, 105, 181);
    }

    private void drawOmen(Canvas canvas, int w, int h, long now) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 310.0);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(w * 0.003f); paint.setColor(Color.argb((int) (20 + pulse * 30), 235, 215, 255));
        canvas.drawCircle(w * 0.80f, h * 0.545f, w * (0.13f + pulse * 0.015f), paint);
        if (reachStage >= 2) { paint.setStrokeWidth(w * 0.004f); paint.setColor(Color.argb((int) (22 + pulse * 35), 255, 142, 214)); canvas.drawCircle(w * 0.80f, h * 0.545f, w * (0.17f + pulse * 0.02f), paint); }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawHypeEffects(Canvas canvas, int w, int h, long now, boolean victory) {
        float cx = victory ? w * 0.5f : w * 0.80f, cy = h * 0.53f; int stage = victory ? 8 : Math.max(1, reachStage);
        int[] lineCounts = {0,0,0,14,28,48,72,96,118}, particleCounts = {0,0,0,16,34,62,92,128,156};
        int lines = lineCounts[Math.min(stage, 8)], particles = particleCounts[Math.min(stage, 8)];
        paint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < lines; i++) {
            double a = i * (Math.PI * 2.0 / Math.max(1, lines)) + now / (victory ? 240.0 : Math.max(240.0, 820.0 - stage * 72.0));
            float inner = w * (0.10f + (i % 4) * 0.010f), outer = w * (0.22f + stage * 0.031f + (i % 3) * 0.025f);
            int c = i % 4 == 0 ? Color.rgb(255,234,78) : (i % 4 == 1 ? Color.rgb(255,61,164) : (i % 4 == 2 ? Color.rgb(103,228,255) : Color.WHITE));
            paint.setColor(Color.argb(victory ? 170 : Math.min(195, 18 + stage * 24), Color.red(c), Color.green(c), Color.blue(c))); paint.setStrokeWidth(w * (0.0025f + (i % 4) * 0.0014f));
            canvas.drawLine(cx + (float)Math.cos(a)*inner, cy + (float)Math.sin(a)*inner, cx + (float)Math.cos(a)*outer, cy + (float)Math.sin(a)*outer, paint);
        }
        paint.setStyle(Paint.Style.FILL); paint.setAntiAlias(false);
        for (int i = 0; i < particles; i++) {
            long seed = i * 173L + now / (victory ? 5L : Math.max(5L, 18L - stage * 2L)); float x = ((seed * 37L) % 1000L) / 1000f * w;
            float y = h * 0.12f + (((seed * 91L) % 1000L) / 1000f) * h * 0.70f; float s = w * (0.004f + (i % 6) * 0.0018f);
            int c = i % 4 == 0 ? Color.rgb(255,231,72) : (i % 4 == 1 ? Color.rgb(255,92,187) : (i % 4 == 2 ? Color.rgb(104,229,255) : Color.WHITE));
            block(canvas, x, y, s, s * (1.6f + (i % 3)), c);
        }
        paint.setAntiAlias(true);
        if (stage >= 4) { float pulse = 0.5f + 0.5f * (float)Math.sin(now / (victory ? 32.0 : Math.max(32.0, 95.0 - stage * 8.0))); int alpha = victory ? (int)(30 + pulse * 88) : (int)((stage - 3) * 10 + pulse * stage * 7); paint.setColor(Color.argb(Math.min(135, alpha), 255, premiumSpin && victory ? 210 : 62, premiumSpin && victory ? 54 : 174)); canvas.drawRect(0, 0, w, h, paint); }
        if (!victory && stage >= 3) { String hype = stage == 3 ? "CHANCE" : stage == 4 ? "CHANCE UP" : stage == 5 ? "激アツ" : stage == 6 ? "LIMIT BREAK" : "MAXIMUM!!!"; paint.setTextAlign(Paint.Align.CENTER); paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); paint.setTextSize(w * (0.032f + stage * 0.006f)); paint.setColor(Color.argb(210,255,243,102)); canvas.drawText(hype, w * 0.80f, h * 0.25f, paint); }
        if (stage >= 6) drawMaximumBurst(canvas, w, h, now, victory);
    }

    private void drawMaximumBurst(Canvas canvas, int w, int h, long now, boolean victory) {
        float cx = victory ? w * 0.50f : w * 0.80f, cy = h * 0.54f, pulse = 0.5f + 0.5f * (float)Math.sin(now / 40.0);
        paint.setStyle(Paint.Style.STROKE);
        for (int ring = 0; ring < 5; ring++) { float rr = w * (0.14f + ring * 0.065f + pulse * 0.040f); paint.setStrokeWidth(w * Math.max(0.003f, 0.010f - ring * 0.0014f)); int alpha = 165 - ring * 24; int r = ring % 2 == 0 ? 255 : 115, g = premiumSpin && victory ? 218 : 225, b = premiumSpin && victory ? 78 : (ring % 2 == 0 ? 90 : 255); paint.setColor(Color.argb(alpha,r,g,b)); canvas.drawCircle(cx,cy,rr,paint); }
        paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER); paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); paint.setTextSize(w * (victory ? 0.23f : 0.16f)); paint.setColor(Color.argb((int)(45 + pulse * 65),255,255,255));
        canvas.save(); canvas.rotate((float)Math.sin(now / 90.0) * 4f, w/2f, h/2f); canvas.drawText(victory ? "WIN!!! WIN!!!" : "!!!  !!!  !!!", w/2f, h * 0.48f, paint); canvas.restore();
    }

    private void drawReelPulses(Canvas canvas, int w, int h, long now) {
        float[] centers = {w * 0.20f, w * 0.50f, w * 0.80f}; paint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 3; i++) if (now < reelPulseUntil[i]) { float left = Math.max(0f, (reelPulseUntil[i] - now) / 1000f), progress = 1f - left; for (int ring = 0; ring < 2; ring++) { float radius = w * (0.11f + progress * 0.16f + ring * 0.035f); paint.setStrokeWidth(w * 0.009f * (1f - progress * 0.6f)); paint.setColor(Color.argb((int)(190 * (1f - progress)),255,i == 2 ? 221 : 150,i == 2 ? 80 : 225)); canvas.drawCircle(centers[i], h * 0.545f, radius, paint); } }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawSparkles(Canvas canvas, int w, int h, long now) {
        paint.setAntiAlias(false); int count = premiumSpin ? 150 : 118;
        for (int i = 0; i < count; i++) { float phase = ((now / 6L + i * 71L) % 1000L) / 1000f, x = ((i * 173) % 997) / 997f * w, y = h * 0.10f + phase * h * 0.74f, s = w * (0.005f + (i % 6) * 0.002f); int c = premiumSpin ? (i % 3 == 0 ? Color.rgb(255,224,72) : (i % 3 == 1 ? Color.WHITE : Color.rgb(255,164,55))) : (i % 4 == 0 ? Color.rgb(255,221,70) : (i % 4 == 1 ? Color.rgb(255,110,196) : (i % 4 == 2 ? Color.rgb(120,229,255) : Color.WHITE))); block(canvas,x,y,s,s*2.2f,c); block(canvas,x-s/2f,y+s*0.6f,s*2f,s,c); }
        paint.setAntiAlias(true);
    }

    private void drawPremiumReveal(Canvas canvas, int w, int h, long now) {
        long e = now - premiumRevealStart;
        if (e < 300L) { paint.setColor(Color.BLACK); canvas.drawRect(0,0,w,h,paint); float s = w * (0.010f + e / 300f * 0.014f); block(canvas,w/2f-s/2f,h*0.50f-s*2f,s,s*4f,Color.rgb(255,220,72)); block(canvas,w/2f-s*2f,h*0.50f-s/2f,s*4f,s,Color.rgb(255,220,72)); return; }
        float p = Math.min(1f, (e - 300L) / 2150f); paint.setShader(new LinearGradient(0,0,0,h,Color.rgb(45,27,3),Color.rgb(8,5,1),Shader.TileMode.CLAMP)); canvas.drawRect(0,0,w,h,paint); paint.setShader(null);
        float pulse = 0.5f + 0.5f * (float)Math.sin(now / 36.0), cx = w/2f, cy = h * 0.46f; paint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 56; i++) { double a = i * Math.PI * 2.0 / 56.0 + now / 330.0; float inner = w * 0.12f, outer = w * (0.44f + (i % 3) * 0.04f); paint.setStrokeWidth(w * (0.003f + (i % 4) * 0.001f)); paint.setColor(Color.argb(110 + (i % 3) * 25,255,218,i % 2 == 0 ? 72 : 155)); canvas.drawLine(cx + (float)Math.cos(a)*inner, cy + (float)Math.sin(a)*inner, cx + (float)Math.cos(a)*outer, cy + (float)Math.sin(a)*outer, paint); }
        for (int ring = 0; ring < 5; ring++) { paint.setStrokeWidth(w * Math.max(0.003f, 0.012f - ring * 0.0018f)); paint.setColor(Color.argb(180 - ring * 25,255,221,72)); canvas.drawCircle(cx,cy,w * (0.16f + ring * 0.075f + pulse * 0.025f),paint); }
        paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER); paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); paint.setColor(Color.rgb(255,225,83)); paint.setTextSize(w * (0.10f + p * 0.035f)); canvas.drawText("PREMIUM",cx,h * 0.23f,paint); paint.setTextSize(w * 0.28f); paint.setColor(Color.WHITE); canvas.drawText("777",cx,h * 0.45f,paint); paint.setTextSize(w * 0.040f); paint.setColor(Color.rgb(255,229,135)); canvas.drawText("旦那はん、これは特別やで。",cx,h * 0.53f,paint);
        float[] xs = {w * 0.25f,w * 0.50f,w * 0.75f}; for (int i = 0; i < 3; i++) { float jump = (float)-Math.abs(Math.sin((now / 260.0) + i)) * h * 0.018f; drawPixelGirl(canvas,xs[i],h * 0.64f + jump,w * 0.015f,i,true); }
        paint.setColor(Color.argb((int)(55 * pulse * (0.4f + p * 0.6f)),255,231,115)); canvas.drawRect(0,0,w,h,paint);
    }

    private void drawPremiumVictoryBanner(Canvas canvas, int w, int h, long now) {
        float pulse = 0.5f + 0.5f * (float)Math.sin(now / 55.0), top = h * 0.12f, bottom = h * 0.205f; paint.setColor(Color.argb(215,33,20,2)); canvas.drawRect(0,top,w,bottom,paint); paint.setColor(Color.rgb(255,220,66)); canvas.drawRect(0,top,w,top+h*0.008f,paint); canvas.drawRect(0,bottom-h*0.008f,w,bottom,paint); paint.setTextAlign(Paint.Align.CENTER); paint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD)); paint.setTextSize(w * (0.054f + pulse * 0.006f)); paint.setColor(Color.WHITE); canvas.drawText("★ PREMIUM JACKPOT ★",w/2f,h * 0.175f,paint);
    }

    private void drawRevivalOverlay(Canvas canvas, int w, int h, long now) {
        long e = now - revivalStart; if (e < 760L) return; float alpha = e < 980L ? (e - 760L) / 220f : 1f; paint.setColor(Color.argb((int)(255 * Math.min(1f, alpha)),0,0,0)); canvas.drawRect(0,0,w,h,paint);
        if (e > 1120L) { float p = Math.min(1f, (e - 1120L) / 330f); paint.setTextAlign(Paint.Align.CENTER); paint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD)); paint.setTextSize(w * 0.042f); paint.setColor(Color.argb((int)(150*p),235,220,240)); canvas.drawText("……まだ。",w/2f,h * 0.52f,paint); float s = w * (0.004f + p * 0.010f); block(canvas,w/2f-s/2f,h*0.59f-s*2f,s,s*4f,Color.argb((int)(220*p),255,90,210)); block(canvas,w/2f-s*2f,h*0.59f-s/2f,s*4f,s,Color.argb((int)(220*p),255,90,210)); }
    }

    private void drawRevivalFlash(Canvas canvas, int w, int h, long now) {
        float left = Math.max(0f, (revivalFlashUntil - now) / 900f), progress = 1f - left; int alpha = (int)(210 * (1f - progress)); paint.setColor(Color.argb(alpha,255,120,230)); canvas.drawRect(0,0,w,h,paint); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(w * 0.014f * (1f - progress * 0.6f)); paint.setColor(Color.argb(alpha,255,255,255)); canvas.drawCircle(w * 0.80f,h * 0.545f,w * (0.12f + progress * 0.32f),paint); canvas.drawCircle(w * 0.80f,h * 0.545f,w * (0.18f + progress * 0.38f),paint); paint.setStyle(Paint.Style.FILL);
    }

    private float currentShake(long now, int w) {
        if (spinning && reachTriggered) { if (reachStage <= 2) return 0f; float[] powers = {0f,0f,0f,0.0014f,0.0028f,0.0050f,0.0072f,0.0100f}; return w * powers[Math.min(reachStage,7)]; }
        if (now < celebrationUntil && now - celebrationStart < 1800L) { float fade = 1f - (now - celebrationStart) / 1800f; return w * (premiumSpin ? 0.020f : 0.017f) * Math.max(0f, fade); }
        return 0f;
    }

    private void block(Canvas canvas, float x, float y, float w, float h, int color) { paint.setColor(color); paint.setStyle(Paint.Style.FILL); canvas.drawRect(x,y,x+w,y+h,paint); }
    private int mix(int a, int b) { return Color.rgb((Color.red(a)+Color.red(b))/2,(Color.green(a)+Color.green(b))/2,(Color.blue(a)+Color.blue(b))/2); }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) { if (spinButton.contains(event.getX(), event.getY())) startSpin(); return true; }
        return true;
    }

    @Override
    public boolean performClick() { super.performClick(); return true; }
}
