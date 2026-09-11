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
            float sx = (float) Math.sin(now / 13.0) * shake;
            float sy = (float) Math.cos(now / 17.0) * shake * 0.65f;
            canvas.translate(sx, sy);
        }
        drawHeader(canvas, w, h);
        drawMachine(canvas, w, h, now);
        drawSpinButton(canvas, w, h);
        canvas.restore();

        if (reachTriggered && spinning) {
            drawHypeEffects(canvas, w, h, now, false);
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
        int top = reachTriggered && spinning ? Color.rgb(72, 21, 86) : Color.rgb(41, 24, 64);
        int bottom = now < celebrationUntil ? Color.rgb(58, 21, 25) : Color.rgb(20, 12, 35);
        paint.setShader(new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        paint.setColor(Color.argb(45, 255, 207, 236));
        float r = w * 0.32f;
        canvas.drawCircle(w * 0.12f, h * 0.16f, r, paint);
        paint.setColor(Color.argb(28, 132, 222, 255));
        canvas.drawCircle(w * 0.92f, h * 0.62f, r * 1.2f, paint);

        if (reachTriggered && spinning) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 95.0);
            paint.setColor(Color.argb((int) (28 + pulse * 34), 255, 71, 177));
            canvas.drawCircle(w * 0.80f, h * 0.54f, w * (0.18f + pulse * 0.05f), paint);
        }
    }

    private void drawHeader(Canvas canvas, int w, int h) {
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        paint.setColor(Color.rgb(255, 221, 244));
        paint.setTextSize(w * 0.073f);
        canvas.drawText("PIXEL SLOT", w / 2f, h * 0.075f, paint);

        paint.setColor(Color.rgb(214, 196, 230));
        paint.setTextSize(w * 0.032f);
        canvas.drawText("リーチからが本番。煽って、溜めて、ドカン！", w / 2f, h * 0.112f, paint);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(Color.rgb(245, 234, 255));
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
        int machineAlpha = reachTriggered && spinning ? 245 : 225;
        paint.setColor(Color.argb(machineAlpha, 65, 42, 88));
        canvas.drawRoundRect(new RectF(left, top, right, bottom), w * 0.05f, w * 0.05f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(w * (reachTriggered && spinning ? 0.013f : 0.008f));
        if (reachTriggered && spinning) {
            float p = 0.5f + 0.5f * (float) Math.sin(now / 75.0);
            paint.setColor(Color.rgb(255, (int) (135 + p * 85), (int) (90 + p * 120)));
        } else {
            paint.setColor(Color.rgb(240, 169, 218));
        }
        canvas.drawRoundRect(new RectF(left, top, right, bottom), w * 0.05f, w * 0.05f, paint);
        paint.setStyle(Paint.Style.FILL);

        float[] centers = {w * 0.20f, w * 0.50f, w * 0.80f};
        boolean celebrating = now < celebrationUntil;
        boolean reaching = spinning && reachTriggered;

        for (int i = 0; i < 3; i++) {
            float bob = (float) Math.sin((now / 210.0) + i * 1.5) * h * 0.004f;
            float tremble = 0f;
            if (reaching && i == 2) {
                float power = 0.006f + Math.min(0.018f, reachStage * 0.0035f);
                tremble = (float) Math.sin(now / (25.0 - reachStage * 2.2)) * w * power;
            }

            float jump = 0f;
            if (celebrating) {
                float t = (now % 420L) / 420f;
                jump = (float) -Math.abs(Math.sin(t * Math.PI)) * h * (0.034f + i * 0.006f);
            }

            drawPixelGirl(canvas, centers[i] + tremble, h * 0.385f + bob + jump,
                    w * 0.0105f, i, celebrating || (reaching && reachStage >= 4 && i != 2));

            float scale = 1f;
            if (now < reelPulseUntil[i]) {
                float remaining = (reelPulseUntil[i] - now) / 520f;
                scale = 1f + 0.16f * (float) Math.sin((1f - remaining) * Math.PI);
            }
            drawNumberCard(canvas, centers[i], h * 0.545f + jump * 0.30f,
                    w, h, shown[i], i, scale);
            drawNameTag(canvas, centers[i], h * 0.675f, w, names[i]);
        }

        if (reaching) {
            paint.setAntiAlias(true);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(Color.rgb(255, 236, 115));
            paint.setTextSize(w * (0.045f + reachStage * 0.003f));
            String label = reachStage >= 4 ? "まだ止まらないッ！！" : "リーチ…！";
            canvas.drawText(label, w / 2f, h * 0.715f, paint);
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
        paint.setShadowLayer(w * 0.025f, 0, h * 0.008f, Color.argb(120, 0, 0, 0));
        paint.setColor(Color.rgb(252, 245, 255));
        canvas.drawRoundRect(card, w * 0.035f, w * 0.035f, paint);
        paint.clearShadowLayer();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(w * 0.008f);
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
        paint.setColor(Color.rgb(229, 213, 239));
        canvas.drawText(name, cx, y, paint);
    }

    private void drawSpinButton(Canvas canvas, int w, int h) {
        float bw = w * 0.64f;
        float bh = h * 0.105f;
        float cx = w / 2f;
        float cy = h * 0.845f;
        spinButton.set(cx - bw/2f, cy - bh/2f, cx + bw/2f, cy + bh/2f);

        paint.setAntiAlias(true);
        paint.setShadowLayer(w * 0.025f, 0, h * 0.009f, Color.argb(140, 0, 0, 0));
        if (spinning && reachTriggered) {
            float p = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 80.0);
            paint.setColor(Color.rgb(245, (int) (80 + p * 80), 124));
        } else {
            paint.setColor(spinning ? Color.rgb(122, 103, 141) : Color.rgb(242, 102, 171));
        }
        canvas.drawRoundRect(spinButton, bh/2f, bh/2f, paint);
        paint.clearShadowLayer();

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(w * 0.064f);
        paint.setColor(Color.WHITE);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = cy - (fm.ascent + fm.descent) / 2f;
        String label = spinning ? (reachTriggered ? "激アツ進行中…！" : "くるくる…") : "SPIN!";
        canvas.drawText(label, cx, baseline, paint);

        paint.setTextSize(w * 0.028f);
        paint.setColor(Color.rgb(200, 182, 216));
        canvas.drawText("リーチに入ったら、ここからが長い。", cx, h * 0.925f, paint);
    }

    private void updateGameState(long now) {
        if (!spinning) return;

        long e = now - spinStart;
        boolean reachSpin = target[0] == target[1];
        long firstStop = 650L;
        long secondStop = 1000L;
        long thirdStop = reachSpin ? 6000L : 1550L;

        updateReel(0, e, firstStop, now, 58L);
        updateReel(1, e, secondStop, now, 62L);

        if (e < thirdStop) {
            long interval = 55L;
            if (reachSpin && e >= secondStop) {
                if (e >= 5000L) interval = 175L;
                else if (e >= 4000L) interval = 125L;
                else if (e >= 2800L) interval = 92L;
                else interval = 68L;
            }
            shown[2] = (int) ((e / interval + 6L) % 10L);
        } else {
            shown[2] = target[2];
            if (!reelLocked[2]) {
                reelLocked[2] = true;
                reelPulseUntil[2] = now + 700L;
            }
        }

        if (reachSpin && !reachTriggered && e >= secondStop) {
            reachTriggered = true;
            reachStage = 1;
            triggerCutIn(now, 1000L, 1, "リーチ！！", "ここからやで…！ まだ止まらへん！");
        }

        if (reachSpin && reachStage < 2 && e >= 2200L) {
            reachStage = 2;
            triggerCutIn(now, 900L, 4, "まだ回るっ！", "ミオ、数字を離さないでーっ！");
        }

        if (reachSpin && reachStage < 3 && e >= 3500L) {
            reachStage = 3;
            triggerCutIn(now, 1000L, 5, "熱くなってきた！", "キラキラ増量！ これは期待してええやつ！");
        }

        if (reachSpin && reachStage < 4 && e >= 4800L) {
            reachStage = 4;
            triggerCutIn(now, 1050L, 6, "決めてぇぇぇ！！", "旦那はん、見てて！ ここで止まってーーっ！");
        }

        if (!resultTriggered && e >= thirdStop) {
            resultTriggered = true;
            spinning = false;
            boolean jackpot = target[0] == target[1] && target[1] == target[2];

            if (jackpot) {
                wins++;
                celebrationStart = now;
                celebrationUntil = now + 5200L;
                triggerCutIn(now, 2500L, 2, "大当たりィィィ！！！", "やったぁぁ！ 旦那はん、ほんまにすごーい！！");
            } else if (reachSpin) {
                triggerCutIn(now, 1500L, 3, "うわぁぁぁ惜しい！", "あとひとつやったぁ…！ 次、絶対いこ！");
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
                reelPulseUntil[index] = now + 600L;
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

        boolean huge = cutInType >= 5 || cutInType == 2;
        float top = huge ? h * 0.205f : h * 0.245f;
        float bottom = huge ? h * 0.445f : h * 0.405f;
        float slide = (1f - visible) * w * (cutInType % 2 == 0 ? -0.95f : 0.95f);

        int accent = cutInAccent(cutInType);

        canvas.save();
        canvas.translate(slide, 0f);
        if (cutInType == 6) {
            float wobble = (float) Math.sin(now / 40.0) * 0.018f;
            canvas.rotate(wobble * 180f / (float) Math.PI, w / 2f, (top + bottom) / 2f);
        }

        paint.setAntiAlias(true);
        paint.setColor(Color.argb(245, 24, 12, 40));
        canvas.drawRect(-w * 0.05f, top, w * 1.05f, bottom, paint);

        paint.setColor(accent);
        canvas.drawRect(0, top, w, top + h * 0.010f, paint);
        canvas.drawRect(0, bottom - h * 0.010f, w, bottom, paint);

        for (int i = 0; i < 12; i++) {
            float x = ((i * 97 + now / 8) % 1200L) / 1200f * w;
            paint.setColor(Color.argb(80, Color.red(accent), Color.green(accent), Color.blue(accent)));
            canvas.drawRect(x, top, x + w * 0.012f, bottom, paint);
        }

        int girlIndex = cutInType == 2 ? 0 : (cutInType == 3 ? 1 : 2);
        float girlScale = huge ? w * 0.0165f : w * 0.0135f;
        drawPixelGirl(canvas, w * 0.17f, (top + bottom) * 0.5f,
                girlScale, girlIndex, cutInType == 2 || cutInType >= 5);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setColor(accent);
        paint.setTextSize(w * (huge ? 0.078f : 0.067f));
        canvas.drawText(cutInTitle, w * 0.31f, top + h * (huge ? 0.095f : 0.067f), paint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(w * (huge ? 0.039f : 0.036f));
        canvas.drawText(cutInMessage, w * 0.31f, top + h * (huge ? 0.155f : 0.116f), paint);

        if (cutInType == 6 || cutInType == 2) {
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setTextSize(w * 0.18f);
            paint.setColor(Color.argb(85, 255, 255, 255));
            canvas.drawText("!!!", w * 0.98f, bottom - h * 0.015f, paint);
        }

        canvas.restore();

        if (cutInType >= 5 || cutInType == 2) {
            float flash = (float) Math.sin(t * Math.PI);
            paint.setColor(Color.argb((int) (90 * flash), 255, 255, 255));
            canvas.drawRect(0, 0, w, h, paint);
        }
    }

    private int cutInAccent(int type) {
        if (type == 2) return Color.rgb(255, 218, 74);
        if (type == 3) return Color.rgb(138, 197, 255);
        if (type == 4) return Color.rgb(143, 239, 255);
        if (type == 5) return Color.rgb(255, 134, 64);
        if (type == 6) return Color.rgb(255, 61, 128);
        return Color.rgb(255, 105, 181);
    }

    private void drawHypeEffects(Canvas canvas, int w, int h, long now, boolean victory) {
        float cx = victory ? w * 0.5f : w * 0.80f;
        float cy = h * 0.53f;
        int stage = victory ? 6 : Math.max(1, reachStage);
        int lines = victory ? 42 : 12 + stage * 5;

        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < lines; i++) {
            double a = i * (Math.PI * 2.0 / lines) + now / (victory ? 420.0 : 690.0);
            float inner = w * (0.11f + (i % 4) * 0.012f);
            float outer = w * (0.27f + stage * 0.018f + (i % 3) * 0.018f);
            float x1 = cx + (float) Math.cos(a) * inner;
            float y1 = cy + (float) Math.sin(a) * inner;
            float x2 = cx + (float) Math.cos(a) * outer;
            float y2 = cy + (float) Math.sin(a) * outer;
            int alpha = victory ? 135 : 45 + stage * 16;
            int c = i % 3 == 0 ? Color.rgb(255, 228, 92)
                    : (i % 3 == 1 ? Color.rgb(255, 77, 173) : Color.rgb(112, 229, 255));
            paint.setColor(Color.argb(alpha, Color.red(c), Color.green(c), Color.blue(c)));
            paint.setStrokeWidth(w * (0.003f + (i % 3) * 0.0015f));
            canvas.drawLine(x1, y1, x2, y2, paint);
        }
        paint.setStyle(Paint.Style.FILL);

        int particles = victory ? 48 : 10 + stage * 7;
        paint.setAntiAlias(false);
        for (int i = 0; i < particles; i++) {
            long seed = i * 173L + now / (victory ? 8L : 13L);
            float x = ((seed * 37L) % 1000L) / 1000f * w;
            float y = h * 0.18f + (((seed * 91L) % 1000L) / 1000f) * h * 0.58f;
            float s = w * (0.004f + (i % 5) * 0.0015f);
            int c = i % 3 == 0 ? Color.rgb(255, 231, 90)
                    : (i % 3 == 1 ? Color.rgb(255, 113, 194) : Color.rgb(125, 230, 255));
            block(canvas, x, y, s, s * 2f, c);
        }
        paint.setAntiAlias(true);

        float pulse = 0.5f + 0.5f * (float) Math.sin(now / (victory ? 48.0 : 85.0));
        int alpha = victory ? (int) (25 + pulse * 55) : (int) (8 + pulse * stage * 6);
        paint.setColor(Color.argb(Math.min(100, alpha), 255, victory ? 225 : 84, victory ? 125 : 195));
        canvas.drawRect(0, 0, w, h, paint);

        if (!victory && stage >= 3) {
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(w * (0.034f + stage * 0.004f));
            paint.setColor(Color.argb(180, 255, 241, 120));
            canvas.drawText(stage >= 4 ? "MAX CHANCE" : "CHANCE UP", w * 0.80f, h * 0.25f, paint);
        }
    }

    private void drawReelPulses(Canvas canvas, int w, int h, long now) {
        float[] centers = {w * 0.20f, w * 0.50f, w * 0.80f};
        paint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(true);
        for (int i = 0; i < 3; i++) {
            if (now >= reelPulseUntil[i]) continue;
            float left = Math.max(0f, (reelPulseUntil[i] - now) / 700f);
            float progress = 1f - left;
            float radius = w * (0.12f + progress * 0.10f);
            paint.setStrokeWidth(w * 0.010f * (1f - progress * 0.6f));
            paint.setColor(Color.argb((int) (180 * (1f - progress)),
                    255, i == 2 ? 220 : 153, i == 2 ? 95 : 225));
            canvas.drawCircle(centers[i], h * 0.545f, radius, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawSparkles(Canvas canvas, int w, int h, long now) {
        paint.setAntiAlias(false);
        for (int i = 0; i < 54; i++) {
            float phase = ((now / 9L + i * 71L) % 1000L) / 1000f;
            float x = ((i * 173) % 997) / 997f * w;
            float y = h * 0.15f + phase * h * 0.64f;
            float s = w * (0.006f + (i % 5) * 0.002f);
            int c = (i % 3 == 0) ? Color.rgb(255, 221, 90)
                    : (i % 3 == 1) ? Color.rgb(255, 134, 199)
                    : Color.rgb(135, 225, 255);
            block(canvas, x, y, s, s * 2f, c);
            block(canvas, x - s/2f, y + s/2f, s * 2f, s, c);
        }
        paint.setAntiAlias(true);
    }

    private float currentShake(long now, int w) {
        if (spinning && reachTriggered) {
            return w * (0.0015f + reachStage * 0.0012f);
        }
        if (now < celebrationUntil && now - celebrationStart < 1300L) {
            float fade = 1f - (now - celebrationStart) / 1300f;
            return w * 0.012f * Math.max(0f, fade);
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
