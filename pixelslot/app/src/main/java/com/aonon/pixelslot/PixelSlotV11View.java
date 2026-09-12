package com.aonon.pixelslot;

import android.content.Context;
import android.graphics.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;

/** v0.11: full-resolution atlas cut-ins + dedicated banner art + tighter idle reel layout. */
public class PixelSlotV11View extends PixelSlotV9View {
    private static final int MOMO = 0, LUNA = 1, MIO = 2;
    private static final int ATLAS_W = 4495, ATLAS_H = 2823;
    private static final int FULL_W = 941, FULL_H = 1672;
    private static final int BANNER_X = 2823, BANNER_W = 1672, BANNER_H = 941;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private Bitmap atlas;

    private Field fStage, fSpinning, fReach, fPremiumStart, fPremiumUntil,
            fRevivalStart, fRevivalFlash, fCutStart, fCutUntil, fCutType,
            fCutTitle, fCutMessage, fCelebrationUntil, fShown;
    private Field fV9Girls, fV9Boards;
    private boolean ready;

    private int prevStage;
    private int activeFull = -1;
    private long activeFullStart, activeFullUntil;

    public PixelSlotV11View(Context context) {
        super(context);
        atlas = loadAtlas();
        try {
            Class<?> e = PixelSlotV5View.class;
            fStage = field(e, "reachStage");
            fSpinning = field(e, "spinning");
            fReach = field(e, "reach");
            fPremiumStart = field(e, "premiumStart");
            fPremiumUntil = field(e, "premiumUntil");
            fRevivalStart = field(e, "revivalStart");
            fRevivalFlash = field(e, "revivalFlashUntil");
            fCutStart = field(e, "cutStart");
            fCutUntil = field(e, "cutUntil");
            fCutType = field(e, "cutType");
            fCutTitle = field(e, "cutTitle");
            fCutMessage = field(e, "cutMessage");
            fCelebrationUntil = field(e, "celebrationUntil");
            fShown = field(e, "shown");

            Class<?> v9 = PixelSlotV9View.class;
            fV9Girls = field(v9, "girls");
            fV9Boards = field(v9, "boards");
            ready = atlas != null;
        } catch (Throwable t) {
            t.printStackTrace();
            ready = false;
        }
    }

    private static Field field(Class<?> c, String name) throws Exception {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private Bitmap loadAtlas() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(3_000_000);
            byte[] buf = new byte[64 * 1024];
            for (int i = 0; i < 7; i++) {
                try (InputStream in = getContext().getAssets().open("v11_atlas_" + i + ".bin")) {
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
            }
            byte[] all = out.toByteArray();
            Bitmap b = BitmapFactory.decodeByteArray(all, 0, all.length);
            if (b != null) bp.setFilterBitmap(true);
            return b;
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    private int iv(Field f) throws Exception { return f.getInt(this); }
    private long lv(Field f) throws Exception { return f.getLong(this); }
    private boolean bv(Field f) throws Exception { return f.getBoolean(this); }
    private String sv(Field f) throws Exception { Object x = f.get(this); return x == null ? "" : String.valueOf(x); }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (!ready) return;

        long now = System.currentTimeMillis();
        try {
            boolean spinning = bv(fSpinning);
            boolean reach = bv(fReach);
            int stage = iv(fStage);
            long celebrationUntil = lv(fCelebrationUntil);

            if (!spinning && now >= celebrationUntil && now >= lv(fCutUntil)
                    && now >= lv(fPremiumUntil) && now >= lv(fRevivalFlash)) {
                drawIdleMachineFix(c);
            }

            if (spinning && reach && stage >= 3) drawExtraHype(c, now, stage);

            if (spinning && reach) {
                if (stage >= 5 && prevStage < 5) startFull(LUNA, now, 2150L);
                else if (stage >= 3 && prevStage < 3) startFull(MOMO, now, 1750L);
                prevStage = stage;
            } else if (!spinning) {
                prevStage = 0;
            }

            long premiumStart = lv(fPremiumStart);
            long premiumUntil = lv(fPremiumUntil);
            long revivalStart = lv(fRevivalStart);

            long cutUntil = lv(fCutUntil);
            if (now < cutUntil) {
                drawBanner(c, chooseBanner(iv(fCutType)), lv(fCutStart), cutUntil, now,
                        sv(fCutTitle), sv(fCutMessage));
            }

            if (premiumStart > 0 && now >= premiumStart && now < premiumStart + 3400L && now < premiumUntil) {
                drawFull(c, LUNA, premiumStart, Math.min(premiumUntil, premiumStart + 3400L), now,
                        "PREMIUM", "777  確定", true);
            } else if (revivalStart > 0 && now >= revivalStart && now < revivalStart + 2500L) {
                drawFull(c, MIO, revivalStart, revivalStart + 2500L, now,
                        "復活ッ！！", "まだ終わってへん！", true);
            } else if (activeFull >= 0 && now < activeFullUntil) {
                if (activeFull == MOMO) drawFull(c, MOMO, activeFullStart, activeFullUntil, now,
                        "CHANCE!", "モモが押すでっ！", false);
                else drawFull(c, LUNA, activeFullStart, activeFullUntil, now,
                        "激アツッ！！", "月光チャンス", false);
            } else {
                activeFull = -1;
            }

            if (spinning || now < cutUntil || activeFull >= 0 || now < premiumUntil || now < lv(fRevivalFlash)) {
                postInvalidateOnAnimation();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void startFull(int ch, long now, long duration) {
        activeFull = ch;
        activeFullStart = now;
        activeFullUntil = now + duration;
    }

    private int chooseBanner(int type) {
        if (type == 3 || type == 6 || type == 9) return LUNA;
        if (type == 4 || type == 7 || type == 8) return MIO;
        return MOMO;
    }

    private Rect fullSrc(int ch) {
        int left = FULL_W * ch;
        return new Rect(left, 0, left + FULL_W, FULL_H);
    }

    private Rect bannerSrc(int ch) {
        int top = BANNER_H * ch;
        return new Rect(BANNER_X, top, BANNER_X + BANNER_W, top + BANNER_H);
    }

    private void drawIdleMachineFix(Canvas c) throws Exception {
        int w = getWidth(), h = getHeight();
        RectF box = new RectF(w * .045f, h * .18f, w * .955f, h * .735f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(255, 55, 37, 76));
        c.drawRoundRect(box, w * .05f, w * .05f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w * .006f);
        p.setColor(Color.rgb(184, 124, 174));
        c.drawRoundRect(box, w * .05f, w * .05f, p);
        p.setStyle(Paint.Style.FILL);

        Bitmap[] girls = (Bitmap[]) fV9Girls.get(this);
        float[][] boards = (float[][]) fV9Boards.get(this);
        int[] nums = (int[]) fShown.get(this);
        float[] xs = {w * .20f, w * .50f, w * .80f};
        String[] names = {"モモ", "ルナ", "ミオ"};

        for (int ch = 0; ch < 3; ch++) {
            RectF d = drawGirl(c, girls[ch], xs[ch], h * .555f, w * .292f, h * .355f);
            drawDigit(c, d, boards[ch], nums[ch]);
            drawText(c, names[ch], xs[ch], h * .655f, w * .030f,
                    Color.rgb(213, 199, 222), Paint.Align.CENTER, false);
        }
    }

    private RectF drawGirl(Canvas c, Bitmap b, float cx, float baseline, float maxW, float maxH) {
        if (b == null) return new RectF(cx-maxW/2, baseline-maxH, cx+maxW/2, baseline);
        float scale = Math.min(maxW / b.getWidth(), maxH / b.getHeight());
        float dw = b.getWidth() * scale, dh = b.getHeight() * scale;
        RectF dst = new RectF(cx-dw/2, baseline-dh, cx+dw/2, baseline);
        c.drawBitmap(b, null, dst, bp);
        return dst;
    }

    private void drawDigit(Canvas c, RectF d, float[] b, int number) {
        RectF r = new RectF(d.left+d.width()*b[0], d.top+d.height()*b[1],
                d.left+d.width()*b[2], d.top+d.height()*b[3]);
        float size = Math.min(r.height()*.72f, r.width()*.62f);
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        p.setTextSize(size);
        p.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = p.getFontMetrics();
        float y = r.centerY() - (fm.ascent + fm.descent) / 2f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, size*.055f));
        p.setColor(Color.WHITE);
        c.drawText(String.valueOf(number), r.centerX(), y, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(69,43,103));
        c.drawText(String.valueOf(number), r.centerX(), y, p);
    }

    private void drawExtraHype(Canvas c, long now, int stage) {
        int w = getWidth(), h = getHeight();
        float cx = w*.5f, cy = h*.46f;
        int accent = stage >= 6 ? Color.rgb(255, 235, 90) : Color.rgb(255, 85, 178);

        p.setStyle(Paint.Style.STROKE);
        int rings = 2 + Math.max(0, stage-3);
        for (int i = 0; i < rings; i++) {
            float ph = ((now/8f + i*150f) % 650f) / 650f;
            p.setStrokeWidth(w * (.003f + (1f-ph)*.007f));
            p.setColor(withAlpha(accent, (int)((1f-ph) * Math.min(190, 60+stage*18))));
            c.drawCircle(cx, cy, w*(.10f + ph*(.48f+stage*.025f)), p);
        }
        p.setStyle(Paint.Style.FILL);

        int particles = 18 + stage*8;
        for (int i = 0; i < particles; i++) {
            double a = i * 2.399963 + now/700.0;
            float rr = w * (.15f + ((i*37 + now/9)%420)/1000f);
            float x = cx + (float)Math.cos(a)*rr;
            float y = cy + (float)Math.sin(a)*rr*.78f;
            p.setColor(Color.argb(Math.min(210, 30+stage*22), 255, 215-(i%3)*35, 90+(i%4)*35));
            c.drawCircle(x, y, w*(.0025f+(i%4)*.0015f), p);
        }

        if (stage >= 6) {
            float blink = Math.max(0f, (float)Math.sin(now/38.0));
            p.setColor(Color.argb((int)(58*blink), 255,255,255));
            c.drawRect(0,0,w,h,p);
        }
    }

    private void drawBanner(Canvas c, int ch, long start, long until, long now, String title, String message) {
        int w = getWidth(), h = getHeight();
        float t = clamp((now-start)/(float)Math.max(1L, until-start));
        float enter = smooth(Math.min(1f, t/.12f));
        float exit = t > .86f ? smooth(Math.max(0f,(1f-t)/.14f)) : 1f;
        float vis = Math.min(enter, exit);
        float top = h*.145f, bottom = h*.495f;
        RectF dst = new RectF(0, top, w, bottom);

        c.save();
        c.clipRect(dst);
        c.translate((1f-enter) * -w*.18f, 0);
        drawCover(c, atlas, bannerSrc(ch), dst, bp, .50f);
        c.restore();

        int accent = accent(ch);
        LinearGradient g = new LinearGradient(w*.33f, 0, w, 0,
                Color.argb(20,0,0,0), Color.argb((int)(205*vis),6,3,18), Shader.TileMode.CLAMP);
        p.setShader(g); c.drawRect(w*.30f, top, w, bottom, p); p.setShader(null);
        p.setColor(withAlpha(accent, (int)(245*vis)));
        c.drawRect(0, top, w, top+h*.009f, p);
        c.drawRect(0, bottom-h*.009f, w, bottom, p);

        drawText(c, title, w*.36f, top+h*.085f, w*.060f, accent, Paint.Align.LEFT, true);
        drawText(c, oneLine(message, 20), w*.36f, top+h*.150f, w*.031f, Color.WHITE, Paint.Align.LEFT, true);
    }

    private void drawFull(Canvas c, int ch, long start, long until, long now,
                          String title, String subtitle, boolean special) {
        int w = getWidth(), h = getHeight();
        float t = clamp((now-start)/(float)Math.max(1L, until-start));
        float enter = smooth(Math.min(1f,t/.14f));
        float exit = t>.82f ? smooth(Math.max(0f,(1f-t)/.18f)) : 1f;
        float vis = Math.min(enter, exit);
        int accent = accent(ch);

        p.setColor(Color.argb((int)(195*vis),4,2,12)); c.drawRect(0,0,w,h,p);

        float slide = (1f-enter) * (ch==LUNA ? w*.14f : -w*.14f);
        c.save();
        c.translate(slide,0);
        float zoom = 1.07f - .07f*enter;
        c.scale(zoom,zoom,w/2f,h/2f);
        drawCover(c, atlas, fullSrc(ch), new RectF(0,0,w,h), bp, .46f);
        c.restore();

        float impact = (float)Math.sin(Math.min(1f,t/.22f)*Math.PI);
        p.setColor(withAlpha(accent,(int)(75*impact*vis))); c.drawRect(0,0,w,h,p);

        float plateTop = special ? h*.70f : h*.735f;
        LinearGradient g = new LinearGradient(0,plateTop,0,h,
                Color.argb(15,0,0,0), Color.argb((int)(230*vis),5,2,14), Shader.TileMode.CLAMP);
        p.setShader(g); c.drawRect(0,plateTop,w,h,p); p.setShader(null);
        p.setColor(withAlpha(accent,(int)(245*vis)));
        c.drawRect(0,plateTop,w,plateTop+h*.009f,p);

        float pulse = 1f+.03f*(float)Math.sin(now/58.0);
        drawText(c,title,w/2f,h*(special?.82f:.835f),w*(special?.092f:.079f)*pulse,
                Color.WHITE,Paint.Align.CENTER,true);
        drawText(c,subtitle,w/2f,h*(special?.875f:.89f),w*(special?.042f:.037f),
                accent,Paint.Align.CENTER,true);

        if (special) {
            p.setStyle(Paint.Style.STROKE);
            for (int i=0;i<5;i++) {
                float ph=((now/7f+i*150f)%700f)/700f;
                p.setStrokeWidth(w*(.003f+(1f-ph)*.007f));
                p.setColor(withAlpha(accent,(int)((1f-ph)*160*vis)));
                c.drawCircle(w/2f,h*.45f,w*(.10f+ph*.62f),p);
            }
            p.setStyle(Paint.Style.FILL);
        }
    }

    private static void drawCover(Canvas c, Bitmap bmp, Rect src, RectF dst, Paint paint, float focusY) {
        if (bmp == null) return;
        float target = dst.width()/dst.height();
        float source = src.width()/(float)src.height();
        Rect crop = new Rect(src);
        if (source < target) {
            int newH = Math.round(src.width()/target);
            int maxShift = src.height()-newH;
            int top = src.top + Math.round(maxShift*clamp(focusY));
            crop.top = top; crop.bottom = top+newH;
        } else if (source > target) {
            int newW = Math.round(src.height()*target);
            int left = src.left + (src.width()-newW)/2;
            crop.left=left; crop.right=left+newW;
        }
        c.drawBitmap(bmp,crop,dst,paint);
    }

    private int accent(int ch) {
        return ch==MOMO ? Color.rgb(255,86,166) : ch==LUNA ? Color.rgb(183,119,255) : Color.rgb(67,226,221);
    }

    private void drawText(Canvas c,String text,float x,float y,float size,int color,Paint.Align align,boolean bold) {
        p.setShader(null); p.setStyle(Paint.Style.FILL); p.setTextAlign(align);
        p.setTypeface(Typeface.create(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL));
        p.setTextSize(size); p.setColor(color);
        p.setShadowLayer(Math.max(2f,size*.09f),0,size*.035f,Color.argb(220,0,0,0));
        c.drawText(text==null?"":text,x,y,p); p.clearShadowLayer();
    }

    private static String oneLine(String s,int max) {
        if (s==null) return "";
        String x=s.replace('\n',' ').replace('\r',' ').trim();
        return x.length()>max ? x.substring(0,max)+"…" : x;
    }
    private static int withAlpha(int color,int a) { return Color.argb(Math.max(0,Math.min(255,a)),Color.red(color),Color.green(color),Color.blue(color)); }
    private static float clamp(float x) { return Math.max(0f,Math.min(1f,x)); }
    private static float smooth(float x) { x=clamp(x); return x*x*(3f-2f*x); }
}
