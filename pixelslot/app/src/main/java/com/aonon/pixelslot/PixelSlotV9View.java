package com.aonon.pixelslot;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import java.lang.reflect.*;

/** v0.9: same-scale supersampled girls + restored over-the-top effects. */
public class PixelSlotV9View extends PixelSlotV8View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final RectF spinRect = new RectF();
    private final Bitmap[] girls = new Bitmap[3];
    private final float[][] boards = new float[3][4];

    private Method updateMethod, startMethod;
    private Field fShown, fPulse, fCoins, fSpins, fWins, fStage, fSpinning, fReach, fPremium, fRevival;
    private Field fCelebrationEnd, fPremiumStart, fPremiumEnd, fRevivalStart, fRevivalFlash;
    private Field fCutStart, fCutEnd, fRewardStart, fRewardEnd, fCutTitle, fCutMessage, fCutType, fLastReward;
    private boolean ready;

    private static final String[] NAMES = {"モモ", "ルナ", "ミオ"};
    private static final float[][] OLD_BOARD = {
            {.60156f,.34286f,.89062f,.54286f},
            {.58886f,.38079f,.92573f,.59768f},
            {.27106f,.43852f,.83516f,.65369f}
    };
    private static final int HR_W = 1200;
    private static final int HR_H = 1600;
    private static final float HR_BASELINE = 1450f;
    private static final float HR_VISIBLE_HEIGHT = 1220f;

    public PixelSlotV9View(Context context) {
        super(context);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        try {
            Field g = PixelSlotV8View.class.getDeclaredField("girl");
            g.setAccessible(true);
            Bitmap[] raw = (Bitmap[]) g.get(this);
            for (int i = 0; i < 3; i++) girls[i] = normalize(raw[i], i);
            bindEngine();
            ready = true;
        } catch (Throwable t) {
            t.printStackTrace();
            ready = false;
        }
    }

    private Bitmap normalize(Bitmap src, int ch) {
        if (src == null) return null;
        Bitmap out = Bitmap.createBitmap(HR_W, HR_H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint q = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        float scale = HR_VISIBLE_HEIGHT / src.getHeight();
        float dw = src.getWidth() * scale;
        float dh = src.getHeight() * scale;
        float left = (HR_W - dw) / 2f;
        float top = HR_BASELINE - dh;
        c.drawBitmap(src, null, new RectF(left, top, left + dw, top + dh), q);
        float[] b = OLD_BOARD[ch];
        boards[ch][0] = (left + dw * b[0]) / HR_W;
        boards[ch][1] = (top + dh * b[1]) / HR_H;
        boards[ch][2] = (left + dw * b[2]) / HR_W;
        boards[ch][3] = (top + dh * b[3]) / HR_H;
        return out;
    }

    private void bindEngine() throws Exception {
        Class<?> c = PixelSlotV5View.class;
        updateMethod = method(c, "update", long.class);
        startMethod = method(c, "startSpin");
        fShown = field(c,"shown"); fPulse = field(c,"pulseUntil"); fCoins = field(c,"coins");
        fSpins = field(c,"spins"); fWins = field(c,"wins"); fStage = field(c,"reachStage");
        fSpinning = field(c,"spinning"); fReach = field(c,"reach"); fPremium = field(c,"premium");
        fRevival = field(c,"revivalActive"); fCelebrationEnd = field(c,"celebrationUntil");
        fPremiumStart = field(c,"premiumStart"); fPremiumEnd = field(c,"premiumUntil");
        fRevivalStart = field(c,"revivalStart"); fRevivalFlash = field(c,"revivalFlashUntil");
        fCutStart = field(c,"cutStart"); fCutEnd = field(c,"cutUntil");
        fRewardStart = field(c,"rewardStart"); fRewardEnd = field(c,"rewardUntil");
        fCutTitle = field(c,"cutTitle"); fCutMessage = field(c,"cutMessage");
        fCutType = field(c,"cutType"); fLastReward = field(c,"lastReward");
    }

    private static Field field(Class<?> c, String name) throws Exception {
        Field f = c.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private static Method method(Class<?> c, String name, Class<?>... args) throws Exception {
        Method m = c.getDeclaredMethod(name, args); m.setAccessible(true); return m;
    }
    private int i(Field f) throws Exception { return f.getInt(this); }
    private long l(Field f) throws Exception { return f.getLong(this); }
    private boolean z(Field f) throws Exception { return f.getBoolean(this); }
    private String s(Field f) throws Exception { return (String) f.get(this); }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        long now = System.currentTimeMillis();
        if (!ready) {
            c.drawColor(Color.rgb(18,11,30));
            text(c,"v0.9 renderer error",w/2f,h/2f,w*.05f,Color.WHITE,Paint.Align.CENTER);
            return;
        }
        try {
            updateMethod.invoke(this, now);
            drawBackground(c,w,h,now);
            drawHeader(c,w,h);

            float shake = shakeAmount(w,now);
            c.save();
            if (shake > 0f) c.translate((float)Math.sin(now/8.5)*shake,(float)Math.cos(now/11.0)*shake*.72f);
            drawMachine(c,w,h,now);
            drawButton(c,w,h);
            c.restore();

            int stage = i(fStage);
            if (z(fReach) && z(fSpinning) && stage >= 3) drawHype(c,w,h,now,stage);
            if (now < l(fCelebrationEnd)) drawCelebration(c,w,h,now,z(fPremium));
            if (now < l(fCutEnd)) drawCutin(c,w,h,now);
            if (now < l(fPremiumEnd)) drawPremium(c,w,h,now);
            if (z(fRevival)) drawRevival(c,w,h,now);
            if (now < l(fRevivalFlash)) {
                float t = Math.max(0f,Math.min(1f,(l(fRevivalFlash)-now)/900f));
                p.setColor(Color.argb((int)(220*t),255,235,255)); c.drawRect(0,0,w,h,p);
            }
            if (now < l(fRewardEnd)) drawReward(c,w,h,now);

            long[] pulse = (long[]) fPulse.get(this);
            if (z(fSpinning) || z(fRevival) || now<l(fCelebrationEnd) || now<l(fCutEnd) ||
                    now<l(fPremiumEnd) || now<l(fRewardEnd) || now<pulse[0] || now<pulse[1] || now<pulse[2]) {
                postInvalidateOnAnimation();
            }
        } catch (Throwable t) { t.printStackTrace(); }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction()==MotionEvent.ACTION_UP && spinRect.contains(e.getX(),e.getY())) {
            try { startMethod.invoke(this); invalidate(); } catch (Throwable t) { t.printStackTrace(); }
            return true;
        }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }

    private float shakeAmount(int w,long now) throws Exception {
        if (now < l(fCelebrationEnd)) return w * (z(fPremium) ? .020f : .013f);
        if (!z(fSpinning) || !z(fReach)) return 0f;
        int st=i(fStage); if(st<3)return 0f;
        float[] v={0f,0f,0f,.0025f,.005f,.009f,.015f,.023f};
        return w*v[Math.min(7,st)];
    }

    private void drawBackground(Canvas c,int w,int h,long now) throws Exception {
        int top=Color.rgb(30,20,46), bottom=Color.rgb(13,9,23); int st=i(fStage);
        if(z(fSpinning)&&st>=4) top=st>=6?Color.rgb(100,8,70):Color.rgb(70,14,78);
        if(now<l(fCelebrationEnd)){top=z(fPremium)?Color.rgb(98,62,4):Color.rgb(92,12,53);bottom=z(fPremium)?Color.rgb(24,12,0):Color.rgb(40,7,20);}
        p.setShader(new LinearGradient(0,0,0,h,top,bottom,Shader.TileMode.CLAMP)); c.drawRect(0,0,w,h,p); p.setShader(null);
        p.setColor(Color.argb(16,255,207,236));c.drawCircle(w*.1f,h*.15f,w*.29f,p);
        p.setColor(Color.argb(10,110,220,255));c.drawCircle(w*.94f,h*.66f,w*.34f,p);
    }

    private void drawHeader(Canvas c,int w,int h) throws Exception {
        text(c,"PIXEL SLOT",w/2f,h*.072f,w*.071f,Color.rgb(255,221,244),Paint.Align.CENTER);
        text(c,"静かな通常時。来た時だけ全部盛り。",w/2f,h*.107f,w*.026f,Color.rgb(190,176,204),Paint.Align.CENTER);
        text(c,"SPIN  "+i(fSpins),w*.055f,h*.15f,w*.029f,Color.rgb(235,226,244),Paint.Align.LEFT);
        coin(c,w*.408f,h*.135f,w*.021f);
        text(c,"COIN  "+i(fCoins),w*.54f,h*.15f,w*.031f,Color.rgb(255,226,105),Paint.Align.CENTER);
        text(c,"WIN  "+i(fWins),w*.945f,h*.15f,w*.029f,Color.rgb(235,226,244),Paint.Align.RIGHT);
    }

    private void drawMachine(Canvas c,int w,int h,long now) throws Exception {
        RectF box=new RectF(w*.045f,h*.18f,w*.955f,h*.735f);
        p.setColor(Color.argb(225,55,37,76)); c.drawRoundRect(box,w*.05f,w*.05f,p);
        int st=i(fStage);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(w*(z(fSpinning)&&st>=3?.006f+st*.0015f:.006f));
        if(z(fSpinning)&&st>=3){float q=.5f+.5f*(float)Math.sin(now/45.0);p.setColor(Color.rgb(255,(int)(80+q*165),(int)(65+q*170)));}
        else p.setColor(Color.rgb(184,124,174));
        c.drawRoundRect(box,w*.05f,w*.05f,p); p.setStyle(Paint.Style.FILL);

        float[] xs={w*.20f,w*.50f,w*.80f}; int[] nums=(int[])fShown.get(this); boolean win=now<l(fCelebrationEnd);
        for(int ch=0;ch<3;ch++){
            float bob=(float)Math.sin(now/360.0+ch)*h*.0014f;
            float jump=win?-(float)Math.abs(Math.sin((now%390)/390f*Math.PI))*h*(.025f+ch*.003f):0f;
            float trem=z(fReach)&&z(fSpinning)&&ch==2&&st>=3?(float)Math.sin(now/12.0)*w*(.003f+st*.0023f):0f;
            RectF d=drawGirl(c,girls[ch],xs[ch]+trem,h*.595f+bob+jump,w*.282f,h*.345f);
            drawDigit(c,d,ch,nums[ch]);
            text(c,NAMES[ch],xs[ch],h*.69f,w*.03f,Color.rgb(213,199,222),Paint.Align.CENTER);
        }
        if(z(fReach)&&z(fSpinning)){
            String[] q={"","……？","もしかして…","リーチ！！","まだ続く…！","激アツッ！！","限界突破…！","止まれぇぇぇ！！"};
            int s=Math.min(7,st); text(c,q[s],w/2f,h*.72f,w*(.033f+Math.max(0,s-1)*.006f),s<=2?Color.rgb(220,211,228):Color.rgb(255,236,102),Paint.Align.CENTER);
        }
    }

    private RectF drawGirl(Canvas c,Bitmap b,float cx,float baseline,float maxW,float maxH) {
        if(b==null)return new RectF(cx-maxW/2f,baseline-maxH,cx+maxW/2f,baseline);
        float scale=Math.min(maxW/b.getWidth(),maxH/b.getHeight()); float dw=b.getWidth()*scale,dh=b.getHeight()*scale;
        RectF d=new RectF(cx-dw/2f,baseline-dh,cx+dw/2f,baseline); c.drawBitmap(b,null,d,bp); return d;
    }

    private void drawDigit(Canvas c,RectF d,int ch,int number) {
        float[] b=boards[ch]; RectF r=new RectF(d.left+d.width()*b[0],d.top+d.height()*b[1],d.left+d.width()*b[2],d.top+d.height()*b[3]);
        float size=Math.min(r.height()*.72f,r.width()*.62f); p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));p.setTextSize(size);p.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm=p.getFontMetrics();float y=r.centerY()-(fm.ascent+fm.descent)/2f;
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2f,size*.055f));p.setColor(Color.WHITE);c.drawText(String.valueOf(number),r.centerX(),y,p);
        p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(69,43,103));c.drawText(String.valueOf(number),r.centerX(),y,p);
    }

    private void drawButton(Canvas c,int w,int h) throws Exception {
        float bw=w*.64f,bh=h*.105f,cx=w/2f,cy=h*.85f;spinRect.set(cx-bw/2f,cy-bh/2f,cx+bw/2f,cy+bh/2f);
        p.setColor(z(fSpinning)||z(fRevival)?Color.rgb(92,78,108):Color.rgb(219,83,148));p.setShadowLayer(w*.018f,0,h*.006f,Color.argb(110,0,0,0));c.drawRoundRect(spinRect,bh/2f,bh/2f,p);p.clearShadowLayer();
        String t=!z(fSpinning)&&!z(fRevival)?"SPIN!":i(fStage)<=1?"……":i(fStage)==2?"ん……？":i(fStage)<=4?"来てる…！":"うわあああ！！";
        text(c,t,cx,cy+h*.018f,w*.059f,Color.WHITE,Paint.Align.CENTER);text(c,"1 PLAY  10 COIN",cx,h*.93f,w*.027f,Color.rgb(177,163,190),Paint.Align.CENTER);
    }

    private void drawHype(Canvas c,int w,int h,long now,int st) {
        float cx=w*.5f,cy=h*.49f;int rays=22+st*10;float rot=now/600.0f;
        p.setStrokeWidth(w*(.0028f+st*.00045f));
        for(int k=0;k<rays;k++){
            double a=Math.PI*2*k/rays+rot;float r1=w*(.18f+(k%3)*.018f),r2=w*(.32f+st*.045f+(k%5)*.012f);
            p.setColor(Color.argb(Math.min(210,28+st*20),255,190+(k%2)*50,50+(k%3)*70));
            c.drawLine(cx+(float)Math.cos(a)*r1,cy+(float)Math.sin(a)*r1,cx+(float)Math.cos(a)*r2,cy+(float)Math.sin(a)*r2,p);
        }
        for(int k=0;k<2+st;k++){
            float phase=((now/8+k*137)%900)/900f;float r=w*(.08f+phase*(.20f+st*.055f));
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(w*(.003f+(1f-phase)*.006f));p.setColor(Color.argb((int)((1f-phase)*(45+st*19)),255,220,80));c.drawCircle(cx,cy,r,p);p.setStyle(Paint.Style.FILL);
        }
        int dots=st>=6?95:st>=5?60:32;
        for(int k=0;k<dots;k++){
            float x=((k*173+now/7)%1200)/1200f*w,y=((k*113+now/5)%1000)/1000f*h*.68f;
            p.setColor(Color.argb(Math.min(220,80+st*15),255,(120+k*9)%255,80+(k*13)%175));c.drawCircle(x,y,w*(.0025f+(k%4)*.0016f),p);
        }
        if(st>=5){float pulse=.5f+.5f*(float)Math.sin(now/55.0);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(w*(.008f+st*.001f));p.setColor(Color.argb((int)(70+90*pulse),255,70,110));c.drawRoundRect(new RectF(w*.018f,h*.16f,w*.982f,h*.755f),w*.055f,w*.055f,p);p.setStyle(Paint.Style.FILL);}
        if(st>=7){float f=.5f+.5f*(float)Math.sin(now/38.0);p.setColor(Color.argb((int)(50*f),255,245,210));c.drawRect(0,0,w,h,p);text(c,"!!!",w*.5f,h*.39f,w*.20f,Color.argb((int)(150+90*f),255,255,255),Paint.Align.CENTER);}
    }

    private void drawCelebration(Canvas c,int w,int h,long now,boolean premium) {
        float flash=.5f+.5f*(float)Math.sin(now/55.0);p.setColor(Color.argb((int)(premium?40+60*flash:22+38*flash),255,premium?220:80,premium?70:155));c.drawRect(0,0,w,h,p);
        for(int k=0;k<150;k++){
            float x=((k*173+now/(premium?7:10))%1500)/1500f*w,y=((k*97+now/(premium?5:7))%1700)/1700f*h;
            p.setColor(Color.argb(175,premium?255:(100+k*7)%255,premium?210:(90+k*11)%255,premium?50:150+(k*13)%105));c.drawCircle(x,y,w*(.0025f+(k%5)*.0016f),p);
        }
        for(int k=0;k<40;k++){float x=((k*241+now/5)%1200)/1200f*w,y=((k*151+now/4)%1400)/1400f*h;coin(c,x,y,w*(.0055f+(k%3)*.0018f));}
        for(int k=0;k<6;k++){float phase=((now/9+k*173)%1000)/1000f;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(w*(.004f+(1f-phase)*.007f));p.setColor(Color.argb((int)((1f-phase)*160),255,230,90));c.drawCircle(w*.5f,h*.49f,w*(.08f+phase*.64f),p);p.setStyle(Paint.Style.FILL);}
    }

    private void drawCutin(Canvas c,int w,int h,long now) throws Exception {
        long a=l(fCutStart),e=l(fCutEnd);float t=Math.min(1f,(now-a)/(float)Math.max(1L,e-a));float vis=Math.min(Math.min(1f,t/.12f),t>.88f?Math.max(0f,(1f-t)/.12f):1f);
        int type=i(fCutType),ch=(type==2||type==11)?0:(type==3?1:2),accent=cutColor(type);boolean big=type==2||type==6||type==9||type==10||type==11;
        float top=big?h*.135f:h*.205f,bottom=big?h*.515f:h*.435f,slide=(1f-vis)*(type%2==0?-w:w);
        if(big){p.setColor(Color.argb((int)(55*vis),255,255,255));c.drawRect(0,0,w,h,p);}
        c.save();c.translate(slide,0);p.setColor(Color.rgb(16,8,30));c.drawRect(0,top,w,bottom,p);p.setColor(accent);c.drawRect(0,top,w,top+h*.011f,p);c.drawRect(0,bottom-h*.011f,w,bottom,p);
        for(int k=0;k<(big?18:9);k++){float x=((k*83+now/5)%1000)/1000f*w;p.setColor(Color.argb(big?90:45,Color.red(accent),Color.green(accent),Color.blue(accent)));c.drawRect(x,top,x+w*.014f,bottom,p);}
        RectF portrait=new RectF(w*.015f,top+h*.008f,w*.315f,bottom-h*.008f);drawPortrait(c,girls[ch],portrait);
        text(c,s(fCutTitle),w*.345f,top+h*(big?.105f:.078f),w*(big?.078f:.061f),accent,Paint.Align.LEFT);oneLine(c,s(fCutMessage),w*.345f,top+h*(big?.171f:.135f),w*.035f,Color.WHITE,w*.60f);
        if(big)text(c,"!!!",w*.94f,bottom-h*.025f,w*.16f,Color.argb(100,255,255,255),Paint.Align.RIGHT);c.restore();
    }

    private int cutColor(int t){if(t==2||t==11)return Color.rgb(255,222,64);if(t==5)return Color.rgb(255,122,44);if(t==6||t==9)return Color.rgb(255,55,100);if(t==10)return Color.rgb(255,75,215);return Color.rgb(255,105,181);}

    private void drawPortrait(Canvas c,Bitmap b,RectF area) {
        if(b==null)return;Rect src=new Rect((int)(b.getWidth()*.25f),(int)(b.getHeight()*.08f),(int)(b.getWidth()*.75f),(int)(b.getHeight()*.62f));
        float sc=Math.max(area.width()/src.width(),area.height()/src.height()),dw=src.width()*sc,dh=src.height()*sc;RectF dst=new RectF(area.centerX()-dw/2f,area.centerY()-dh/2f,area.centerX()+dw/2f,area.centerY()+dh/2f);
        c.save();c.clipRect(area);c.drawBitmap(b,src,dst,bp);c.restore();
    }

    private void drawPremium(Canvas c,int w,int h,long now) throws Exception {
        long e=now-l(fPremiumStart);p.setColor(Color.argb(240,4,2,0));c.drawRect(0,0,w,h,p);
        if(e<900){text(c,"……",w/2f,h*.49f,w*.075f,Color.rgb(255,229,120),Paint.Align.CENTER);return;}
        float cx=w*.5f,cy=h*.42f;
        if(e<2200){float k=Math.min(1f,(e-900)/1300f);for(int j=0;j<4;j++){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(w*.004f);p.setColor(Color.argb((int)(175*k),255,225,90));c.drawCircle(cx,cy,w*(.02f+j*.045f*k),p);p.setStyle(Paint.Style.FILL);}text(c,"……来る。",cx,h*.58f,w*.038f,Color.rgb(255,230,140),Paint.Align.CENTER);return;}
        for(int j=0;j<64;j++){double a=Math.PI*2*j/64+now/700.0;p.setStrokeWidth(w*.005f);p.setColor(Color.argb(100,255,205+(j%2)*45,55));c.drawLine(cx+(float)Math.cos(a)*w*.10f,cy+(float)Math.sin(a)*w*.10f,cx+(float)Math.cos(a)*w*.75f,cy+(float)Math.sin(a)*w*.75f,p);}
        text(c,"PREMIUM",cx,h*.29f,w*.105f,Color.rgb(255,224,70),Paint.Align.CENTER);if(e>3300)text(c,"777",cx,h*.44f,w*.18f,Color.WHITE,Paint.Align.CENTER);
        if(e>4800){for(int j=0;j<3;j++)drawGirl(c,girls[j],w*(.20f+j*.30f),h*.82f,w*.235f,h*.30f);for(int j=0;j<90;j++){float x=((j*191+now/5)%1200)/1200f*w,y=((j*103+now/4)%1300)/1300f*h;p.setColor(Color.argb(185,255,200+(j%3)*18,45));c.drawCircle(x,y,w*(.003f+(j%5)*.0018f),p);}}
    }

    private void drawRevival(Canvas c,int w,int h,long now) throws Exception {
        long e=now-l(fRevivalStart);p.setColor(Color.argb(242,0,0,0));c.drawRect(0,0,w,h,p);
        if(e<700)text(c,"……",w/2f,h*.49f,w*.065f,Color.rgb(190,180,200),Paint.Align.CENTER);
        else {text(c,"……まだ。",w/2f,h*.49f,w*.068f,Color.rgb(235,215,245),Paint.Align.CENTER);float k=.5f+.5f*(float)Math.sin(now/90.0);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(w*.006f);p.setColor(Color.argb((int)(80+100*k),255,120,210));c.drawCircle(w/2f,h*.49f,w*(.12f+.05f*k),p);p.setStyle(Paint.Style.FILL);}
    }

    private void drawReward(Canvas c,int w,int h,long now) throws Exception {
        long elapsed=now-l(fRewardStart),dur=Math.max(1L,l(fRewardEnd)-l(fRewardStart));float k=(float)Math.sin(Math.min(1f,elapsed/(float)dur)*Math.PI),cy=h*.22f;
        for(int j=0;j<14;j++){double a=Math.PI*2*j/14+now/800.0;float rr=w*(.04f+.055f*k);coin(c,w*.45f+(float)Math.cos(a)*rr,cy+(float)Math.sin(a)*rr,w*.009f);}
        coin(c,w*.39f,cy,w*.03f*(1f+k*.3f));text(c,"+"+i(fLastReward),w*.44f,cy+w*.012f,w*.06f,Color.rgb(255,228,80),Paint.Align.LEFT);
    }

    private void coin(Canvas c,float x,float y,float r){p.setColor(Color.rgb(255,190,35));c.drawCircle(x,y,r,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1f,r*.18f));p.setColor(Color.rgb(255,235,125));c.drawCircle(x,y,r*.72f,p);p.setStyle(Paint.Style.FILL);}
    private void text(Canvas c,String t,float x,float y,float size,int color,Paint.Align align){p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));p.setTextAlign(align);p.setTextSize(size);p.setColor(color);c.drawText(t,x,y,p);}
    private void oneLine(Canvas c,String t,float x,float y,float size,int color,float max){p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));p.setTextSize(size);while(p.measureText(t)>max&&p.getTextSize()>15f)p.setTextSize(p.getTextSize()*.94f);p.setTextAlign(Paint.Align.LEFT);p.setColor(color);c.drawText(t,x,y,p);}
}
