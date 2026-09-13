package com.aonon.pixelslot;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.lang.reflect.Field;

/**
 * v0.12: high-definition polish + longer, staged reach suspense.
 *
 * The v0.11 renderer already owns the full-resolution character/cut-in atlas.
 * This layer keeps that artwork, stretches the reach timeline without restarting
 * the locked reels, and adds native-resolution glass, edge bloom, energy rails,
 * impact flashes and a much longer final build-up.
 */
public class PixelSlotV12View extends PixelSlotV11View {
    private final Paint fx = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final RectF machine = new RectF();

    private Field fStage;
    private Field fSpinning;
    private Field fReach;
    private Field fSpinStart;
    private Field fCutUntil;
    private Field fCelebrationUntil;
    private Field fPremiumUntil;
    private Field fRevivalFlashUntil;

    private boolean ready;
    private boolean stretched3;
    private boolean stretched5;
    private boolean stretched6;
    private long impactStart;
    private int impactStage;

    public PixelSlotV12View(Context context) {
        super(context);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        try {
            Class<?> engine = PixelSlotV5View.class;
            fStage = field(engine, "reachStage");
            fSpinning = field(engine, "spinning");
            fReach = field(engine, "reach");
            fSpinStart = field(engine, "spinStart");
            fCutUntil = field(engine, "cutUntil");
            fCelebrationUntil = field(engine, "celebrationUntil");
            fPremiumUntil = field(engine, "premiumUntil");
            fRevivalFlashUntil = field(engine, "revivalFlashUntil");
            ready = true;
        } catch (Throwable t) {
            t.printStackTrace();
            ready = false;
        }
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private int stage() throws Exception { return fStage.getInt(this); }
    private boolean spinning() throws Exception { return fSpinning.getBoolean(this); }
    private boolean reach() throws Exception { return fReach.getBoolean(this); }
    private long value(Field f) throws Exception { return f.getLong(this); }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = System.currentTimeMillis();

        if (ready) {
            try {
                stretchReachIfNeeded(now);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        super.onDraw(canvas);

        if (!ready) return;
        try {
            drawHdPolish(canvas, now);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * Delay only after the first two reels are already locked.
     * Rewinding earlier than 2.8 s would make those reels animate again, so all
     * three holds are inserted at safe late-reach checkpoints.
     */
    private void stretchReachIfNeeded(long now) throws Exception {
        if (!spinning() || !reach()) {
            stretched3 = false;
            stretched5 = false;
            stretched6 = false;
            impactStage = 0;
            return;
        }

        int st = stage();
        if (st >= 3 && !stretched3) {
            addReachHold(now, 2200L, 1200L, 3);
            stretched3 = true;
        }
        if (st >= 5 && !stretched5) {
            addReachHold(now, 1500L, 1100L, 5);
            stretched5 = true;
        }
        if (st >= 6 && !stretched6) {
            addReachHold(now, 1200L, 1000L, 6);
            stretched6 = true;
        }
    }

    private void addReachHold(long now, long timelineHold, long cutHold, int st) throws Exception {
        fSpinStart.setLong(this, value(fSpinStart) + timelineHold);
        long cutUntil = value(fCutUntil);
        if (cutUntil > now) fCutUntil.setLong(this, cutUntil + cutHold);
        impactStart = now;
        impactStage = st;
    }

    private void drawHdPolish(Canvas c, long now) throws Exception {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        machine.set(w * .045f, h * .18f, w * .955f, h * .735f);
        drawGlass(c, w, h, now);

        boolean spin = spinning();
        boolean isReach = reach();
        int st = stage();

        if (spin && isReach) {
            drawTensionRail(c, w, h, now, st);
            if (st >= 3) {
                drawEdgeBloom(c, w, h, now, st);
                drawEnergyDust(c, w, h, now, st);
            }
            if (st >= 5) drawCrossBeams(c, w, h, now, st);
            if (st >= 6) drawLimitBreak(c, w, h, now, st);
            if (st >= 7) drawFinalHold(c, w, h, now);
        }

        if (impactStart > 0 && now - impactStart < 1050L) {
            drawImpact(c, w, h, now, impactStage);
        }

        long celebrationUntil = value(fCelebrationUntil);
        if (now < celebrationUntil) drawVictoryFrame(c, w, h, now);

        if (spin || now < celebrationUntil || now < value(fPremiumUntil)
                || now < value(fRevivalFlashUntil)
                || (impactStart > 0 && now - impactStart < 1050L)) {
            postInvalidateOnAnimation();
        }
    }

    private void drawGlass(Canvas c, int w, int h, long now) {
        // A faint native-resolution reflection makes the machine feel like a real panel
        // without covering the character art.
        LinearGradient sheen = new LinearGradient(
                machine.left, machine.top,
                machine.right, machine.bottom,
                new int[]{Color.argb(30,255,255,255), Color.argb(3,255,255,255), Color.argb(20,255,190,235)},
                new float[]{0f,.43f,1f}, Shader.TileMode.CLAMP);
        fx.setShader(sheen);
        fx.setStyle(Paint.Style.STROKE);
        fx.setStrokeWidth(Math.max(1.5f, w * .0022f));
        c.drawRoundRect(machine, w * .05f, w * .05f, fx);
        fx.setShader(null);
        fx.setStyle(Paint.Style.FILL);

        // Quiet corner LEDs. They stay restrained until the reach starts.
        float pulse = .55f + .45f * (float)Math.sin(now / 620.0);
        int a = (int)(32 + 18 * pulse);
        fx.setColor(Color.argb(a,255,210,240));
        float r = w * .006f;
        c.drawCircle(machine.left + w*.022f, machine.top + w*.022f, r, fx);
        c.drawCircle(machine.right - w*.022f, machine.top + w*.022f, r, fx);
    }

    private void drawTensionRail(Canvas c, int w, int h, long now, int st) {
        float left = w * .10f;
        float right = w * .90f;
        float y = h * .762f;
        float gap = w * .012f;
        float seg = (right - left - gap * 6f) / 7f;

        for (int i = 0; i < 7; i++) {
            float x1 = left + i * (seg + gap);
            RectF r = new RectF(x1, y, x1 + seg, y + h * .007f);
            boolean on = i < st;
            int alpha = on ? 215 : 35;
            if (on && i == Math.max(0, st - 1)) {
                alpha = 170 + (int)(75 * (.5f + .5f * Math.sin(now / 70.0)));
            }
            fx.setColor(on
                    ? Color.argb(alpha, st >= 6 ? 255 : 255, st >= 6 ? 224 : 110, st >= 6 ? 86 : 194)
                    : Color.argb(alpha, 220, 205, 230));
            c.drawRoundRect(r, h*.004f, h*.004f, fx);
        }
    }

    private void drawEdgeBloom(Canvas c, int w, int h, long now, int st) {
        int base = st >= 6 ? Color.rgb(255,222,80) : Color.rgb(255,78,180);
        float wave = .5f + .5f * (float)Math.sin(now / (st >= 6 ? 44.0 : 80.0));
        fx.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 4; i++) {
            float inset = w * (.004f + i * .007f);
            RectF r = new RectF(machine.left - inset, machine.top - inset,
                    machine.right + inset, machine.bottom + inset);
            fx.setStrokeWidth(w * (.0018f + (3-i)*.0015f));
            int alpha = Math.max(10, (int)((70 - i*13) * (.55f + wave*.45f)));
            fx.setColor(withAlpha(base, alpha));
            c.drawRoundRect(r, w*.052f + inset, w*.052f + inset, fx);
        }
        fx.setStyle(Paint.Style.FILL);
    }

    private void drawEnergyDust(Canvas c, int w, int h, long now, int st) {
        int count = 22 + st * 7;
        float cx = w * .5f;
        float cy = h * .47f;
        for (int i = 0; i < count; i++) {
            double a = i * 2.3999632297 + now / 1150.0;
            float cycle = ((i * 61 + now / 5) % 1000) / 1000f;
            float radius = w * (.18f + cycle * (.36f + st*.018f));
            float x = cx + (float)Math.cos(a) * radius;
            float y = cy + (float)Math.sin(a) * radius * .72f;
            int alpha = (int)((1f-cycle) * Math.min(185, 48 + st*18));
            int rr = st >= 6 ? 255 : 255;
            int gg = st >= 6 ? 220 : 125 + (i%3)*28;
            int bb = st >= 6 ? 82 : 205;
            fx.setColor(Color.argb(alpha, rr, gg, bb));
            float r = w * (.0017f + (i%4)*.0011f);
            c.drawCircle(x, y, r, fx);
        }
    }

    private void drawCrossBeams(Canvas c, int w, int h, long now, int st) {
        float phase = ((now % 1200L) / 1200f);
        int alpha = 20 + st * 4;
        fx.setStyle(Paint.Style.STROKE);
        fx.setStrokeWidth(w * .010f);
        fx.setColor(Color.argb(alpha,255,218,245));
        float offset = (phase - .5f) * w * .35f;
        c.drawLine(-w*.15f + offset, h*.30f, w*1.15f + offset, h*.64f, fx);
        c.drawLine(w*1.15f - offset, h*.30f, -w*.15f - offset, h*.64f, fx);
        fx.setStyle(Paint.Style.FILL);
    }

    private void drawLimitBreak(Canvas c, int w, int h, long now, int st) {
        float blink = Math.max(0f, (float)Math.sin(now / 48.0));
        fx.setColor(Color.argb((int)(18 + blink*34),255,245,186));
        c.drawRect(0,0,w,h,fx);

        // Gold/cyan chromatic edge gives the last stages a denser HD look.
        fx.setStyle(Paint.Style.STROKE);
        fx.setStrokeWidth(w*.004f);
        fx.setColor(Color.argb(120,255,215,70));
        c.drawRoundRect(new RectF(w*.018f,h*.105f,w*.982f,h*.795f),w*.045f,w*.045f,fx);
        fx.setStrokeWidth(w*.0022f);
        fx.setColor(Color.argb(95,92,238,255));
        c.drawRoundRect(new RectF(w*.026f,h*.112f,w*.974f,h*.788f),w*.043f,w*.043f,fx);
        fx.setStyle(Paint.Style.FILL);
    }

    private void drawFinalHold(Canvas c, int w, int h, long now) {
        float q = .5f + .5f * (float)Math.sin(now / 62.0);
        int gold = Color.rgb(255,226,92);
        drawOutlinedText(c, "FINAL", w*.5f, h*.805f, w*.052f,
                withAlpha(gold, 205 + (int)(50*q)), Paint.Align.CENTER);
    }

    private void drawImpact(Canvas c, int w, int h, long now, int st) {
        float t = clamp((now-impactStart)/1050f);
        float hit = (float)Math.sin(Math.min(1f,t/.26f) * Math.PI);
        float fade = 1f - t;
        int accent = st >= 6 ? Color.rgb(255,224,82) : st >= 5 ? Color.rgb(255,93,184) : Color.rgb(236,190,255);

        fx.setColor(withAlpha(accent, (int)(82*hit*fade)));
        c.drawRect(0,0,w,h,fx);

        fx.setStyle(Paint.Style.STROKE);
        for (int i=0;i<3;i++) {
            float r = w * (.16f + t*(.30f+i*.08f));
            fx.setStrokeWidth(w*(.010f-i*.0023f));
            fx.setColor(withAlpha(accent, (int)(180*fade/(i+1))));
            c.drawCircle(w*.5f,h*.47f,r,fx);
        }
        fx.setStyle(Paint.Style.FILL);

        String label = st >= 6 ? "限界突破――まだ続く！" : st >= 5 ? "激アツ継続ッ！" : "……まだ、止まらへん";
        drawOutlinedText(c,label,w*.5f,h*.785f,w*(st>=6?.041f:.037f),
                withAlpha(Color.WHITE,(int)(255*fade)),Paint.Align.CENTER);
    }

    private void drawVictoryFrame(Canvas c, int w, int h, long now) {
        float q=.5f+.5f*(float)Math.sin(now/46.0);
        fx.setStyle(Paint.Style.STROKE);
        fx.setStrokeWidth(w*.008f);
        fx.setColor(Color.argb((int)(110+90*q),255,226,85));
        c.drawRoundRect(new RectF(w*.025f,h*.11f,w*.975f,h*.79f),w*.055f,w*.055f,fx);
        fx.setStyle(Paint.Style.FILL);
    }

    private void drawOutlinedText(Canvas c, String s, float x, float y, float size, int color, Paint.Align align) {
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setTextSize(size);
        text.setTextAlign(align);
        text.setStyle(Paint.Style.STROKE);
        text.setStrokeWidth(Math.max(2f,size*.12f));
        text.setColor(Color.argb(Color.alpha(color),20,8,30));
        c.drawText(s,x,y,text);
        text.setStyle(Paint.Style.FILL);
        text.setColor(color);
        c.drawText(s,x,y,text);
    }

    private static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
