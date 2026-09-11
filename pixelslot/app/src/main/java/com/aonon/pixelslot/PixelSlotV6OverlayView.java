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

public class PixelSlotV6OverlayView extends PixelSlotV5View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private Bitmap[][] bitmaps;
    private Field fShown, fSpinning, fReachTriggered, fReachStage;
    private Field fCelebrationUntil, fCutInStart, fCutInUntil, fCutInTitle, fCutInMessage, fCutInType;
    private Field fPremiumRevealStart, fPremiumRevealUntil, fRevivalActive;
    private boolean ready;

    private static final int[][][] CROPS = {
            { {16, 0, 231, 340}, {5, 38, 230, 305}, {12, 28, 238, 315} },
            { {22, 0, 235, 340}, {4, 86, 241, 340}, {18, 88, 276, 340} },
            { {36, 50, 195, 340}, {36, 35, 198, 340}, {10, 145, 220, 340} }
    };

    private static final float[][] SIGNS = {
            {0.567f, 0.324f, 0.925f, 0.565f},
            {0.545f, 0.359f, 0.958f, 0.626f},
            {0.189f, 0.414f, 0.899f, 0.679f}
    };

    private static final int[] ACCENTS = {
            Color.rgb(255, 126, 170), Color.rgb(166, 139, 241), Color.rgb(92, 218, 220)
    };

    public PixelSlotV6OverlayView(Context context) {
        super(context);
        initReflection();
    }

    private void initReflection() {
        try {
            Class<?> c = PixelSlotV5View.class;
            Field fb = field(c, "characterBitmaps");
            bitmaps = (Bitmap[][]) fb.get(this);
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

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!ready) return;
        try {
            long now = System.currentTimeMillis();
            int w = getWidth(), h = getHeight();
            boolean premium = now < gl(fPremiumRevealUntil);
            boolean revival = gb(fRevivalActive);

            if (!premium && !revival) {
                drawCorrectedReels(canvas, w, h, now);
            }
            if (now < gl(fCutInUntil)) {
                drawCorrectedCutIn(canvas, w, h, now);
            }
            if (premium && now - gl(fPremiumRevealStart) > 5200L) {
                drawCorrectedPremiumTrio(canvas, w, h, now);
            }
        } catch (Throwable ignored) {
        }
    }

    private void drawCorrectedReels(Canvas canvas, int w, int h, long now) throws Exception {
        float[] cx = {w * 0.19f, w * 0.50f, w * 0.81f};
        float panelTop = h * 0.305f;
        float panelBottom = h * 0.635f;
        float colHalf = w * 0.135f;

        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(55, 37, 76));
        for (float x : cx) {
            canvas.drawRect(x - colHalf, panelTop, x + colHalf, panelBottom, p);
        }

        int[] shown = (int[]) fShown.get(this);
        boolean spinning = gb(fSpinning);
        boolean reaching = spinning && gb(fReachTriggered);
        int reachStage = gi(fReachStage);
        boolean celebrating = now < gl(fCelebrationUntil);
        float baseline = h * 0.575f;

        for (int i = 0; i < 3; i++) {
            int state = celebrating ? 2 : ((reaching && i == 2 && reachStage >= 2) ? 1 : 0);
            float jump = 0f;
            if (celebrating) {
                float t = (now % 390L) / 390f;
                jump = (float) -Math.abs(Math.sin(t * Math.PI)) * h * (0.022f + i * 0.003f);
            }
            float tremble = 0f;
            if (reaching && i == 2 && reachStage >= 3) {
                tremble = (float) Math.sin(now / 18.0) * w * Math.min(0.014f, 0.0025f + (reachStage - 2) * 0.0024f);
            }

            RectF dst = drawSprite(canvas, i, state, cx[i] + tremble, baseline + jump,
                    w * 0.245f, h * 0.285f);
            if (state == 0) {
                drawDigitOnSign(canvas, dst, i, shown[i], w);
            } else {
                drawSimpleCard(canvas, cx[i], h * 0.575f + jump * 0.2f, shown[i], i, w, h);
            }
        }
    }

    private RectF drawSprite(Canvas canvas, int ch, int state, float cx, float baseline,
                             float maxW, float maxH) {
        Bitmap b = bitmaps[ch][state];
        if (b == null) return new RectF(cx-maxW/2f, baseline-maxH, cx+maxW/2f, baseline);
        int[] c = CROPS[ch][state];
        Rect src = new Rect(clamp(c[0],0,b.getWidth()-1), clamp(c[1],0,b.getHeight()-1),
                clamp(c[2],1,b.getWidth()), clamp(c[3],1,b.getHeight()));
        float scale = Math.min(maxW / src.width(), maxH / src.height());
        float dw = src.width() * scale, dh = src.height() * scale;
        RectF dst = new RectF(cx - dw/2f, baseline - dh, cx + dw/2f, baseline);
        canvas.drawBitmap(b, src, dst, bp);
        return dst;
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private void drawDigitOnSign(Canvas canvas, RectF sprite, int ch, int number, int w) {
        float[] s = SIGNS[ch];
        RectF r = new RectF(sprite.left + sprite.width()*s[0], sprite.top + sprite.height()*s[1],
                sprite.left + sprite.width()*s[2], sprite.top + sprite.height()*s[3]);
        String text = String.valueOf(number);
        float size = Math.min(r.height()*0.82f, r.width()*0.80f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        p.setTextSize(size);
        Paint.FontMetrics fm = p.getFontMetrics();
        float y = r.centerY() - (fm.ascent + fm.descent)/2f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, size*0.07f));
        p.setColor(Color.WHITE);
        canvas.drawText(text, r.centerX(), y, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(69,43,103));
        canvas.drawText(text, r.centerX(), y, p);
    }

    private void drawSimpleCard(Canvas canvas, float cx, float cy, int number, int ch, int w, int h) {
        float cw = w*0.115f, chh = h*0.067f;
        RectF r = new RectF(cx-cw/2f, cy-chh/2f, cx+cw/2f, cy+chh/2f);
        p.setColor(Color.rgb(250,246,251));
        canvas.drawRoundRect(r, w*0.018f, w*0.018f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w*0.004f);
        p.setColor(ACCENTS[ch]);
        canvas.drawRoundRect(r, w*0.018f, w*0.018f, p);
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        p.setTextSize(w*0.070f);
        p.setColor(Color.rgb(69,43,103));
        Paint.FontMetrics fm = p.getFontMetrics();
        canvas.drawText(String.valueOf(number), cx, cy-(fm.ascent+fm.descent)/2f, p);
    }

    private void drawCorrectedCutIn(Canvas canvas, int w, int h, long now) throws Exception {
        long start = gl(fCutInStart), until = gl(fCutInUntil);
        float duration = Math.max(1f, until-start);
        float t = Math.min(1f, (now-start)/duration);
        float enter = Math.min(1f, t/0.14f);
        float exit = t > 0.84f ? Math.max(0f, (1f-t)/0.16f) : 1f;
        float vis = Math.min(enter, exit);
        int type = gi(fCutInType);
        String title = gs(fCutInTitle), msg = gs(fCutInMessage);
        boolean whisper = type==7 || type==8;
        boolean huge = type==2 || type==6 || type==9 || type==10 || type==11;
        int girl = (type==2 || type==11) ? 0 : (type==3 ? 1 : 2);
        int state = (type==2 || type==6 || type==10 || type==11) ? 2 : 1;
        int accent = accent(type);
        float top = whisper ? h*0.275f : (huge ? h*0.145f : h*0.205f);
        float bottom = whisper ? h*0.405f : (huge ? h*0.505f : h*0.425f);
        float slide = (1f-vis) * (type%2==0 ? -w*0.95f : w*0.95f);

        canvas.save();
        canvas.translate(slide, 0);
        p.setColor(Color.rgb(18,10,33));
        canvas.drawRect(0, top, w, bottom, p);
        p.setColor(accent);
        canvas.drawRect(0, top, w, top+h*0.009f, p);
        canvas.drawRect(0, bottom-h*0.009f, w, bottom, p);

        RectF portrait = new RectF(w*0.035f, top+h*0.018f, w*0.285f, bottom-h*0.018f);
        drawPortrait(canvas, girl, state, portrait);

        float tx = w*0.325f;
        p.setTextAlign(Paint.Align.LEFT);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setColor(accent);
        p.setTextSize(w*(whisper?0.050f:huge?0.075f:0.061f));
        canvas.drawText(title, tx, top+h*(whisper?0.055f:huge?0.098f:0.076f), p);
        p.setColor(Color.WHITE);
        p.setTextSize(w*(whisper?0.030f:huge?0.037f:0.034f));
        canvas.drawText(msg, tx, top+h*(whisper?0.096f:huge?0.161f:0.128f), p);
        canvas.restore();
    }

    private void drawPortrait(Canvas canvas, int ch, int state, RectF area) {
        Bitmap b = bitmaps[ch][state];
        if (b==null) return;
        int[] c = CROPS[ch][state];
        int l=c[0], t=c[1], r=c[2], bot=c[3];
        int sw=r-l, sh=bot-t;
        Rect src = new Rect(l + (int)(sw*0.10f), t,
                r - (int)(sw*0.10f), t + (int)(sh*0.72f));
        float scale = Math.min(area.width()/src.width(), area.height()/src.height());
        float dw=src.width()*scale, dh=src.height()*scale;
        RectF dst = new RectF(area.centerX()-dw/2f, area.centerY()-dh/2f,
                area.centerX()+dw/2f, area.centerY()+dh/2f);
        canvas.drawBitmap(b, src, dst, bp);
    }

    private int accent(int type) {
        if (type==2 || type==11) return Color.rgb(255,222,64);
        if (type==3) return Color.rgb(138,197,255);
        if (type==4) return Color.rgb(139,236,255);
        if (type==5) return Color.rgb(255,122,44);
        if (type==6) return Color.rgb(255,42,112);
        if (type==7 || type==8) return Color.rgb(215,195,240);
        if (type==9) return Color.rgb(255,65,65);
        if (type==10) return Color.rgb(255,80,205);
        return Color.rgb(255,105,181);
    }

    private void drawCorrectedPremiumTrio(Canvas canvas, int w, int h, long now) {
        p.setColor(Color.rgb(18,11,2));
        canvas.drawRect(0, h*0.61f, w, h*0.86f, p);
        float[] xs={w*0.22f,w*0.50f,w*0.78f};
        for (int i=0;i<3;i++) {
            float jump=(float)-Math.abs(Math.sin(now/310.0+i))*h*0.012f;
            drawSprite(canvas,i,2,xs[i],h*0.82f+jump,w*0.23f,h*0.20f);
        }
    }
}
