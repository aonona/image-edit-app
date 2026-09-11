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
    private long spinStart = 0L;
    private long celebrationUntil = 0L;

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

        drawBackground(canvas, w, h);
        updateGameState(now);
        drawHeader(canvas, w, h);
        drawMachine(canvas, w, h, now);
        drawSpinButton(canvas, w, h);

        if (now < cutInUntil) {
            drawCutIn(canvas, w, h, now);
        }
        if (now < celebrationUntil) {
            drawSparkles(canvas, w, h, now);
        }

        if (spinning || now < cutInUntil || now < celebrationUntil) {
            postInvalidateOnAnimation();
        }
    }

    private void drawBackground(Canvas canvas, int w, int h) {
        paint.setAntiAlias(true);
        paint.setShader(new LinearGradient(0, 0, 0, h,
                Color.rgb(41, 24, 64),
                Color.rgb(20, 12, 35),
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        paint.setColor(Color.argb(45, 255, 207, 236));
        float r = w * 0.32f;
        canvas.drawCircle(w * 0.12f, h * 0.16f, r, paint);
        paint.setColor(Color.argb(28, 132, 222, 255));
        canvas.drawCircle(w * 0.92f, h * 0.62f, r * 1.2f, paint);
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
        canvas.drawText("かわいい3人が数字をぎゅっ。揃ったら全力でお祝い！", w / 2f, h * 0.112f, paint);

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
        paint.setColor(Color.argb(225, 65, 42, 88));
        canvas.drawRoundRect(new RectF(left, top, right, bottom), w * 0.05f, w * 0.05f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(w * 0.008f);
        paint.setColor(Color.rgb(240, 169, 218));
        canvas.drawRoundRect(new RectF(left, top, right, bottom), w * 0.05f, w * 0.05f, paint);
        paint.setStyle(Paint.Style.FILL);

        float[] centers = {w * 0.20f, w * 0.50f, w * 0.80f};
        boolean celebrating = now < celebrationUntil;
        boolean reaching = spinning && reachTriggered;

        for (int i = 0; i < 3; i++) {
            float bob = (float) Math.sin((now / 210.0) + i * 1.5) * h * 0.004f;
            float tremble = (reaching && i == 2) ? (float) Math.sin(now / 22.0) * w * 0.007f : 0f;
            float jump = 0f;
            if (celebrating) {
                float t = (now % 520L) / 520f;
                jump = (float) -Math.abs(Math.sin(t * Math.PI)) * h * (0.025f + i * 0.004f);
            }
            drawPixelGirl(canvas, centers[i] + tremble, h * 0.385f + bob + jump, w * 0.0105f, i, celebrating);
            drawNumberCard(canvas, centers[i], h * 0.545f + jump * 0.30f, w, h, shown[i], i);
            drawNameTag(canvas, centers[i], h * 0.675f, w, names[i]);
        }

        if (spinning && reachTriggered) {
            paint.setAntiAlias(true);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(Color.rgb(255, 229, 121));
            paint.setTextSize(w * 0.046f);
            canvas.drawText("リーチ…！", w / 2f, h * 0.715f, paint);
        }
    }

    private void drawPixelGirl(Canvas canvas, float cx, float cy, float s, int index, boolean celebrate) {
        paint.setAntiAlias(false);
        int hair = hairColors[index];
        int outfit = outfitColors[index];
        int skin = Color.rgb(255, 220, 202);
        int dark = Color.rgb(51, 35, 58);

        // Hair silhouette
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

        // Face
        block(canvas, cx - 6*s, cy - 7*s, 12*s, 9*s, skin);
        block(canvas, cx - 4*s, cy - 4*s, 2*s, 2*s, dark);
        block(canvas, cx + 2*s, cy - 4*s, 2*s, 2*s, dark);
        if (celebrate) {
            block(canvas, cx - 2*s, cy, 4*s, 1.5f*s, Color.rgb(198, 74, 112));
        } else {
            block(canvas, cx - 1*s, cy, 2*s, 1*s, Color.rgb(198, 74, 112));
        }

        // Body and skirt
        block(canvas, cx - 5*s, cy + 2*s, 10*s, 9*s, outfit);
        block(canvas, cx - 7*s, cy + 10*s, 14*s, 5*s, mix(outfit, Color.WHITE));
        block(canvas, cx - 5*s, cy + 15*s, 3*s, 7*s, skin);
        block(canvas, cx + 2*s, cy + 15*s, 3*s, 7*s, skin);
        block(canvas, cx - 6*s, cy + 21*s, 5*s, 2*s, dark);
        block(canvas, cx + 1*s, cy + 21*s, 5*s, 2*s, dark);

        // Arms: normal = hugging the number; celebration = raised.
        if (celebrate) {
            block(canvas, cx - 9*s, cy + 2*s, 4*s, 3*s, skin);
            block(canvas, cx - 11*s, cy - 2*s, 3*s, 5*s, skin);
            block(canvas, cx + 5*s, cy + 2*s, 4*s, 3*s, skin);
            block(canvas, cx + 8*s, cy - 2*s, 3*s, 5*s, skin);
        } else {
            block(canvas, cx - 9*s, cy + 5*s, 5*s, 3*s, skin);
            block(canvas, cx + 4*s, cy + 5*s, 5*s, 3*s, skin);
        }

        // Tiny highlight pixels.
        block(canvas, cx - 5*s, cy - 9*s, 2*s, 1.5f*s, Color.argb(180, 255, 255, 255));
        paint.setAntiAlias(true);
    }

    private void drawNumberCard(Canvas canvas, float cx, float cy, int w, int h, int number, int index) {
        float cw = w * 0.235f;
        float ch = h * 0.135f;
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
        paint.setColor(spinning ? Color.rgb(122, 103, 141) : Color.rgb(242, 102, 171));
        canvas.drawRoundRect(spinButton, bh/2f, bh/2f, paint);
        paint.clearShadowLayer();

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(w * 0.064f);
        paint.setColor(Color.WHITE);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = cy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(spinning ? "くるくる…" : "SPIN!", cx, baseline, paint);

        paint.setTextSize(w * 0.028f);
        paint.setColor(Color.rgb(200, 182, 216));
        canvas.drawText("タップして3人を応援してね", cx, h * 0.925f, paint);
    }

    private void updateGameState(long now) {
        if (!spinning) return;
        long e = now - spinStart;
        long[] stop = {650L, 950L, 1350L};

        for (int i = 0; i < 3; i++) {
            if (e < stop[i]) {
                shown[i] = (int) ((e / 65L + i * 3L) % 10L);
            } else {
                shown[i] = target[i];
            }
        }

        if (!reachTriggered && e >= stop[1] && target[0] == target[1]) {
            reachTriggered = true;
            triggerCutIn(now, 900L, 1, "リーチ！", "お願いっ…揃って！");
        }

        if (!resultTriggered && e >= stop[2]) {
            resultTriggered = true;
            spinning = false;
            boolean jackpot = target[0] == target[1] && target[1] == target[2];
            if (jackpot) {
                wins++;
                celebrationUntil = now + 2300L;
                triggerCutIn(now, 1600L, 2, "大当たり！", "旦那はん、すごーい！");
            } else if (target[0] == target[1]) {
                triggerCutIn(now, 1050L, 3, "おしいっ！", "次こそ一緒に当てよっ♪");
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
        celebrationUntil = 0L;
        cutInUntil = 0L;
        spinStart = now;

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
        float enter = Math.min(1f, t / 0.18f);
        float exit = t > 0.82f ? Math.max(0f, (1f - t) / 0.18f) : 1f;
        float visible = Math.min(enter, exit);
        float slide = (1f - visible) * w * 0.85f;

        float top = h * 0.245f;
        float bottom = h * 0.405f;
        canvas.save();
        canvas.translate(slide, 0);

        int accent;
        if (cutInType == 2) accent = Color.rgb(255, 210, 80);
        else if (cutInType == 3) accent = Color.rgb(148, 196, 255);
        else accent = Color.rgb(255, 105, 181);

        paint.setAntiAlias(true);
        paint.setColor(Color.argb(240, 35, 20, 52));
        canvas.drawRect(0, top, w, bottom, paint);
        paint.setColor(accent);
        canvas.drawRect(0, top, w, top + h * 0.008f, paint);
        canvas.drawRect(0, bottom - h * 0.008f, w, bottom, paint);

        int girlIndex = cutInType == 2 ? 0 : (cutInType == 3 ? 1 : 2);
        drawPixelGirl(canvas, w * 0.17f, (top + bottom) * 0.5f, w * 0.0135f, girlIndex, cutInType == 2);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setColor(accent);
        paint.setTextSize(w * 0.070f);
        canvas.drawText(cutInTitle, w * 0.31f, top + h * 0.067f, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(w * 0.038f);
        canvas.drawText(cutInMessage, w * 0.31f, top + h * 0.118f, paint);
        canvas.restore();
    }

    private void drawSparkles(Canvas canvas, int w, int h, long now) {
        paint.setAntiAlias(false);
        for (int i = 0; i < 26; i++) {
            float phase = ((now / 12L + i * 71L) % 1000L) / 1000f;
            float x = ((i * 173) % 997) / 997f * w;
            float y = h * 0.18f + phase * h * 0.60f;
            float s = w * (0.006f + (i % 4) * 0.002f);
            int c = (i % 3 == 0) ? Color.rgb(255, 221, 90)
                    : (i % 3 == 1) ? Color.rgb(255, 134, 199)
                    : Color.rgb(135, 225, 255);
            block(canvas, x, y, s, s * 2f, c);
            block(canvas, x - s/2f, y + s/2f, s * 2f, s, c);
        }
        paint.setAntiAlias(true);
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
