package com.aonon.pixelslot;

import android.content.Context;
import android.graphics.*;
import android.util.Base64;
import android.view.MotionEvent;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;

/** v0.9: equal-scale supersampled character renderer + restored bombastic effects. */
public class PixelSlotV9View extends PixelSlotV5View {
  private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG), bp=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG|Paint.DITHER_FLAG);
  private final RectF spin=new RectF();
  private final Bitmap[] srcGirl=new Bitmap[3], girl=new Bitmap[3];
  private final float[][] board=new float[3][4];
  private Method update,start; private boolean ok;
  private Field shown,pulse,coins,spins,wins,stage,spinning,reach,premium,revival,celeEnd,premStart,premEnd,revStart,revFlash,cutStart,cutEnd,rewardStart,rewardEnd,cutTitle,cutMsg,cutType,lastReward;
  private static final String[] N={"モモ","ルナ","ミオ"};
  private static final int[] A={Color.rgb(255,126,170),Color.rgb(166,139,241),Color.rgb(92,218,220)};
  private static final float[][] OLD_B={{.60156f,.34286f,.89062f,.54286f},{.58886f,.38079f,.92573f,.59768f},{.27106f,.43852f,.83516f,.65369f}};
  private static final int HRW=1200, HRH=1600;
  private static final float HR_BASE=1450f, HR_CHAR_H=1220f;

  public PixelSlotV9View(Context c){
    super(c); setLayerType(LAYER_TYPE_SOFTWARE,null);
    srcGirl[0]=asset("v08c_momo_",1); srcGirl[1]=asset("v08c_luna_",1); srcGirl[2]=asset("v08c_mio_",1);
    for(int i=0;i<3;i++) girl[i]=normalize(srcGirl[i],i);
    reflect();
  }

  private Bitmap asset(String pre,int n){
    try{
      StringBuilder s=new StringBuilder();
      for(int i=0;i<n;i++)try(BufferedReader r=new BufferedReader(new InputStreamReader(getContext().getAssets().open(pre+i+".txt"),StandardCharsets.US_ASCII))){String x;while((x=r.readLine())!=null)s.append(x.trim());}
      byte[] d=Base64.decode(s.toString(),Base64.DEFAULT); return BitmapFactory.decodeByteArray(d,0,d.length);
    }catch(Exception e){e.printStackTrace();return null;}
  }

  private Bitmap normalize(Bitmap b,int ch){
    if(b==null)return null;
    Bitmap out=Bitmap.createBitmap(HRW,HRH,Bitmap.Config.ARGB_8888);
    Canvas c=new Canvas(out); Paint q=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG|Paint.DITHER_FLAG);
    float scale=HR_CHAR_H/b.getHeight(), dw=b.getWidth()*scale, dh=b.getHeight()*scale;
    float left=(HRW-dw)/2f, top=HR_BASE-dh;
    RectF dst=new RectF(left,top,left+dw,top+dh); c.drawBitmap(b,null,dst,q);
    float[] z=OLD_B[ch];
    board[ch][0]=(left+dw*z[0])/HRW; board[ch][1]=(top+dh*z[1])/HRH;
    board[ch][2]=(left+dw*z[2])/HRW; board[ch][3]=(top+dh*z[3])/HRH;
    return out;
  }

  private static Field f(Class<?> c,String n)throws Exception{Field x=c.getDeclaredField(n);x.setAccessible(true);return x;}
  private static Method m(Class<?> c,String n,Class<?>...a)throws Exception{Method x=c.getDeclaredMethod(n,a);x.setAccessible(true);return x;}
  private void reflect(){try{Class<?> c=PixelSlotV5View.class;update=m(c,"update",long.class);start=m(c,"startSpin");shown=f(c,"shown");pulse=f(c,"pulseUntil");coins=f(c,"coins");spins=f(c,"spins");wins=f(c,"wins");stage=f(c,"reachStage");spinning=f(c,"spinning");reach=f(c,"reach");premium=f(c,"premium");revival=f(c,"revivalActive");celeEnd=f(c,"celebrationUntil");premStart=f(c,"premiumStart");premEnd=f(c,"premiumUntil");revStart=f(c,"revivalStart");revFlash=f(c,"revivalFlashUntil");cutStart=f(c,"cutStart");cutEnd=f(c,"cutUntil");rewardStart=f(c,"rewardStart");rewardEnd=f(c,"rewardUntil");cutTitle=f(c,"cutTitle");cutMsg=f(c,"cutMessage");cutType=f(c,"cutType");lastReward=f(c,"lastReward");ok=true;}catch(Exception e){e.printStackTrace();}}
  private int I(Field x)throws Exception{return x.getInt(this);} private long L(Field x)throws Exception{return x.getLong(this);} private boolean Z(Field x)throws Exception{return x.getBoolean(this);} private String S(Field x)throws Exception{return (String)x.get(this);}

  @Override protected void onDraw(Canvas c){
    int w=getWidth(),h=getHeight(); long now=System.currentTimeMillis();
    if(!ok){c.drawColor(Color.rgb(18,11,30));text(c,"v0.9 renderer error",w/2f,h/2f,w*.05f,Color.WHITE,Paint.Align.CENTER);return;}
    try{
      update.invoke(this,now); bg(c,w,h,now); header(c,w,h);
      float sh=shake(w,now); c.save(); if(sh>0)c.translate((float)Math.sin(now/8.5)*sh,(float)Math.cos(now/11.0)*sh*.72f); machine(c,w,h,now); button(c,w,h); c.restore();
      if(Z(reach)&&Z(spinning)&&I(stage)>=3)hype(c,w,h,now,I(stage));
      if(now<L(celeEnd))celebrate(c,w,h,now,Z(premium));
      if(now<L(cutEnd))cutin(c,w,h,now);
      if(now<L(premEnd))premiumFx(c,w,h,now);
      if(Z(revival))revivalFx(c,w,h,now);
      if(now<L(revFlash)){float t=Math.max(0f,Math.min(1f,(L(revFlash)-now)/900f));p.setColor(Color.argb((int)(210*t),255,235,255));c.drawRect(0,0,w,h,p);}
      if(now<L(rewardEnd))reward(c,w,h,now);
      long[] pu=(long[])pulse.get(this);
      if(Z(spinning)||Z(revival)||now<L(celeEnd)||now<L(cutEnd)||now<L(premEnd)||now<L(rewardEnd)||now<pu[0]||now<pu[1]||now<pu[2])postInvalidateOnAnimation();
    }catch(Throwable e){e.printStackTrace();}
  }

  @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_UP&&spin.contains(e.getX(),e.getY())){try{start.invoke(this);invalidate();}catch(Exception x){x.printStackTrace();}return true;}return true;}
  @Override public boolean performClick(){super.performClick();return true;}

  private float shake(int w,long n)throws Exception{
    if(n<L(celeEnd))return w*(Z(premium)?.018f:.012f);
    if(!Z(spinning)||!Z(reach))return 0;
    int s=I(stage); if(s<3)return 0;
    float[] v={0,0,0,.0025f,.005f,.009f,.014f,.022f}; return w*v[Math.min(7,s)];
  }

  private void bg(Canvas c,int w,int h,long n)throws Exception{
    int t=Color.rgb(30,20,46),b=Color.rgb(13,9,23); int st=I(stage);
    if(Z(spinning)&&st>=4)t=st>=6?Color.rgb(100,8,70):Color.rgb(70,14,78);
    if(n<L(celeEnd)){t=Z(premium)?Color.rgb(98,62,4):Color.rgb(92,12,53);b=Z(premium)?Color.rgb(24,12,0):Color.rgb(40,7,20);}
    p.setShader(new LinearGradient(0,0,0,h,t,b,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
    p.setColor(Color.argb(16,255,207,236));c.drawCircle(w*.1f,h*.15f,w*.29f,p);p.setColor(Color.argb(10,110,220,255));c.drawCircle(w*.94f,h*.66f,w*.34f,p);
  }

  private void header(Canvas c,int w,int h)throws Exception{
    text(c,"PIXEL SLOT",w/2f,h*.072f,w*.071f,Color.rgb(255,221,244),Paint.Align.CENTER);
    text(c,"静かな通常時。来た時だけ全部盛り。",w/2f,h*.107f,w*.026f,Color.rgb(190,176,204),Paint.Align.CENTER);
    text(c,"SPIN  "+I(spins),w*.055f,h*.15f,w*.029f,Color.rgb(235,226,244),Paint.Align.LEFT);coin(c,w*.408f,h*.135f,w*.021f);
    text(c,"COIN  "+I(coins),w*.54f,h*.15f,w*.031f,Color.rgb(255,226,105),Paint.Align.CENTER);text(c,"WIN  "+I(wins),w*.945f,h*.15f,w*.029f,Color.rgb(235,226,244),Paint.Align.RIGHT);
  }

  private void machine(Canvas c,int w,int h,long n)throws Exception{
    RectF box=new RectF(w*.045f,h*.18f,w*.955f,h*.735f);p.setColor(Color.argb(225,55,37,76));c.drawRoundRect(box,w*.05f,w*.05f,p);
    int st=I(stage);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(w*(Z(spinning)&&st>=3?.006f+st*.0015f:.006f));
    if(Z(spinning)&&st>=3){float q=.5f+.5f*(float)Math.sin(n/45.0);p.setColor(Color.rgb(255,(int)(80+q*165),(int)(65+q*170)));}else p.setColor(Color.rgb(184,124,174));
    c.drawRoundRect(box,w*.05f,w*.05f,p);p.setStyle(Paint.Style.FILL);
    float[] x={w*.20f,w*.50f,w*.80f};int[] nums=(int[])shown.get(this);boolean win=n<L(celeEnd);
    for(int i=0;i<3;i++){
      float bob=(float)Math.sin(n/360.0+i)*h*.0014f,jump=win?-(float)Math.abs(Math.sin((n%390)/390f*Math.PI))*h*(.025f+i*.003f):0,trem=Z(reach)&&Z(spinning)&&i==2&&st>=3?(float)Math.sin(n/12.0)*w*(.003f+st*.0023f):0;
      RectF d=sprite(c,girl[i],x[i]+trem,h*.595f+bob+jump,w*.282f,h*.345f);digit(c,d,i,nums[i]);text(c,N[i],x[i],h*.69f,w*.03f,Color.rgb(213,199,222),Paint.Align.CENTER);
    }
    if(Z(reach)&&Z(spinning)){String[] q={"","……？","もしかして…","リーチ！！","まだ続く…！","激アツッ！！","限界突破…！","止まれぇぇぇ！！"};int s=Math.min(7,st);text(c,q[s],w/2f,h*.72f,w*(.033f+Math.max(0,s-1)*.006f),s<=2?Color.rgb(220,211,228):Color.rgb(255,236,102),Paint.Align.CENTER);}
  }

  private RectF sprite(Canvas c,Bitmap b,float cx,float base,float mw,float mh){
    if(b==null)return new RectF(cx-mw/2,base-mh,cx+mw/2,base);float s=Math.min(mw/b.getWidth(),mh/b.getHeight()),dw=b.getWidth()*s,dh=b.getHeight()*s;RectF d=new RectF(cx-dw/2,base-dh,cx+dw/2,base);c.drawBitmap(b,null,d,bp);return d;
  }

  private void digit(Canvas c,RectF d,int ch,int n){
    float[] z=board[ch];RectF r=new RectF(d.left+d.width()*z[0],d.top+d.height()*z[1],d.left+d.width()*z[2],d.top+d.height()*z[3]);float sz=Math.min(r.height()*.72f,r.width()*.62f);
    p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));p.setTextSize(sz);p.setTextAlign(Paint.Align.CENTER);Paint.FontMetrics fm=p.getFontMetrics();float y=r.centerY()-(fm.ascent+fm.descent)/2;
    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,sz*.055f));p.setColor(Color.WHITE);c.drawText(""+n,r.centerX(),y,p);p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(69,43,103));c.drawText(""+n,r.centerX(),y,p);
  }

  private void button(Canvas c,int w,int h)throws Exception{
    float bw=w*.64f,bh=h*.105f,cx=w/2f,cy=h*.85f;spin.set(cx-bw/2,cy-bh/2,cx+bw/2,cy+bh/2);p.setColor(Z(spinning)||Z(revival)?Color.rgb(92,78,108):Color.rgb(219,83,148));p.setShadowLayer(w*.018f,0,h*.006f,Color.argb(110,0,0,0));c.drawRoundRect(spin,bh/2,bh/2,p);p.clearShadowLayer();String s=!Z(spinning)&&!Z(revival)?"SPIN!":I(stage)<=1?"……":I(stage)==2?"ん……？":I(stage)<=4?"来てる…！":"うわあああ！！";text(c,s,cx,cy+h*.018f,w*.059f,Color.WHITE,Paint.Align.CENTER);text(c,"1 PLAY  10 COIN",cx,h*.93f,w*.027f,Color.rgb(177,163,190),Paint.Align.CENTER);
  }

  private void hype(Canvas c,int w,int h,long n,int st){
    float cx=w*.5f,cy=h*.49f;int rays=22+st*10;float rot=n/600.0;
    p.setStrokeWidth(w*(.0028f+st*.00045f));
    for(int i=0;i<rays;i++){double a=Math.PI*2*i/rays+rot;float r1=w*(.18f+(i%3)*.018f),r2=w*(.32f+st*.045f+(i%5)*.012f);int alpha=Math.min(210,28+st*20);p.setColor(Color.argb(alpha,255,190+(i%2)*50,50+(i%3)*70));c.drawLine(cx+(float)Math.cos(a)*r1,cy+(float)Math.sin(a)*r1,cx+(float)Math.cos(a)*r2,cy+(float)Math.sin(a)*r2,p);}
    int rings=2+st;for(int i=0;i<rings;i++){float phase=((n/8+i*137)%900)/900f;float r=w*(.08f+phase*(.20f+st*.055f));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(w*(.003f+(1-phase)*.006f));p.setColor(Color.argb((int)((1-phase)*(45+st*19)),255,220,80));c.drawCircle(cx,cy,r,p);p.setStyle(Paint.Style.FILL);}
    int dots=st>=6?95:st>=5?60:32;for(int i=0;i<dots;i++){float x=((i*173+n/7)%1200)/1200f*w,y=((i*113+n/5)%1000)/1000f*h*.68f;p.setColor(Color.argb(80+st*15,255,(120+i*9)%255,80+(i*13)%175));c.drawCircle(x,y,w*(.0025f+(i%4)*.0016f),p);}
    if(st>=5){float pulse=.5f+.5f*(float)Math.sin(n/55.0);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(w*(.008f+st*.001f));p.setColor(Color.argb((int)(70+90*pulse),255,70,110));c.drawRoundRect(new RectF(w*.018f,h*.16f,w*.982f,h*.755f),w*.055f,w*.055f,p);p.setStyle(Paint.Style.FILL);}
    if(st>=7){float f=.5f+.5f*(float)Math.sin(n/38.0);p.setColor(Color.argb((int)(45*f),255,245,210));c.drawRect(0,0,w,h,p);text(c,"!!!",w*.5f,h*.39f,w*.20f,Color.argb((int)(150+90*f),255,255,255),Paint.Align.CENTER);}
  }

  private void celebrate(Canvas c,int w,int h,long n,boolean pr){
    float flash=.5f+.5f*(float)Math.sin(n/55.0);p.setColor(Color.argb((int)(pr?38+55*flash:20+35*flash),pr?255:255,pr?220:80,pr?70:155));c.drawRect(0,0,w,h,p);
    for(int i=0;i<150;i++){float x=((i*173+n/(pr?7:10))%1500)/1500f*w,y=((i*97+n/(pr?5:7))%1700)/1700f*h;p.setColor(Color.argb(170,pr?255:(100+i*7)%255,pr?210:(90+i*11)%255,pr?50:(150+i*13)%105+150));c.drawCircle(x,y,w*(.0025f+(i%5)*.0016f),p);}
    for(int i=0;i<36;i++){float x=((i*241+n/5)%1200)/1200f*w,y=((i*151+n/4)%1400)/1400f*h;p.setColor(Color.rgb(255,188+(i%3)*20,35));c.drawCircle(x,y,w*(.006f+(i%3)*.002f),p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(w*.0018f);p.setColor(Color.rgb(255,240,130));c.drawCircle(x,y,w*(.004f+(i%3)*.0015f),p);p.setStyle(Paint.Style.FILL);}
    float cx=w*.5f,cy=h*.49f;for(int k=0;k<5;k++){float phase=((n/9+k*173)%1000)/1000f;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(w*(.004f+(1-phase)*.007f));p.setColor(Color.argb((int)((1-phase)*150),255,230,90));c.drawCircle(cx,cy,w*(.08f+phase*.62f),p);p.setStyle(Paint.Style.FILL);}
  }

  private void cutin(Canvas c,int w,int h,long n)throws Exception{
    long a=L(cutStart),e=L(cutEnd);float t=Math.min(1f,(n-a)/(float)Math.max(1,e-a)),vis=Math.min(Math.min(1,t/.12f),t>.88f?Math.max(0,(1-t)/.12f):1);int type=I(cutType),ch=(type==2||type==11)?0:(type==3?1:2),ac=cutColor(type);boolean big=type==2||type==6||type==9||type==10||type==11;float top=big?h*.135f:h*.205f,bot=big?h*.515f:h*.435f,slide=(1-vis)*(type%2==0?-w:w);
    if(big){p.setColor(Color.argb((int)(50*vis),255,255,255));c.drawRect(0,0,w,h,p);}c.save();c.translate(slide,0);p.setColor(Color.rgb(16,8,30));c.drawRect(0,top,w,bot,p);p.setColor(ac);c.drawRect(0,top,w,top+h*.011f,p);c.drawRect(0,bot-h*.011f,w,bot,p);
    for(int i=0;i<(big?18:9);i++){float x=((i*83+n/5)%1000)/1000f*w;p.setColor(Color.argb(big?90:45,Color.red(ac),Color.green(ac),Color.blue(ac)));c.drawRect(x,top,x+w*.014f,bot,p);}
    RectF ar=new RectF(w*.015f,top+h*.008f,w*.315f,bot-h*.008f);portrait(c,girl[ch],ar);text(c,S(cutTitle),w*.345f,top+h*(big?.105f:.078f),w*(big?.078f:.061f),ac,Paint.Align.LEFT);oneLine(c,S(cutMsg),w*.345f,top+h*(big?.171f:.135f),w*.035f,Color.WHITE,w*.60f);if(big)text(c,"!!!",w*.94f,bot-h*.025f,w*.16f,Color.argb(100,255,255,255),Paint.Align.RIGHT);c.restore();
  }

  private int cutColor(int t){if(t==2||t==11)return Color.rgb(255,222,64);if(t==5)return Color.rgb(255,122,44);if(t==6||t==9)return Color.rgb(255,55,100);if(t==10)return Color.rgb(255,75,215);return Color.rgb(255,105,181);}

  private void portrait(Canvas c,Bitmap b,RectF a){if(b==null)return;Rect src=new Rect((int)(b.getWidth()*.25f),(int)(b.getHeight()*.10f),(int)(b.getWidth()*.75f),(int)(b.getHeight()*.62f));float s=Math.max(a.width()/src.width(),a.height()/src.height()),dw=src.width()*s,dh=src.height()*s;RectF d=new RectF(a.centerX()-dw/2,a.centerY()-dh/2,a.centerX()+dw/2,a.centerY()+dh/2);c.save();c.clipRect(a);c.drawBitmap(b,src,d,bp);c.restore();}

  private void premiumFx(Canvas c,int w,int h,long n)throws Exception{
    long e=n-L(premStart);p.setColor(Color.argb(238,4,2,0));c.drawRect(0,0,w,h,p);
    if(e<900){text(c,"……",w/2f,h*.49f,w*.075f,Color.rgb(255,229,120),Paint.Align.CENTER);return;}
    float cx=w*.5f,cy=h*.42f;if(e<2200){float k=Math.min(1,(e-900)/1300f);for(int i=0;i<4;i++){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(w*.004f);p.setColor(Color.argb((int)(170*k),255,225,90));c.drawCircle(cx,cy,w*(.02f+i*.045f*k),p);p.setStyle(Paint.Style.FILL);}text(c,"……来る。",cx,h*.58f,w*.038f,Color.rgb(255,230,140),Paint.Align.CENTER);return;}
    int rays=64;for(int i=0;i<rays;i++){double a=Math.PI*2*i/rays+n/700.0;p.setStrokeWidth(w*.005f);p.setColor(Color.argb(95,255,205+(i%2)*45,55));c.drawLine(cx+(float)Math.cos(a)*w*.10f,cy+(float)Math.sin(a)*w*.10f,cx+(float)Math.cos(a)*w*.75f,cy+(float)Math.sin(a)*w*.75f,p);}
    text(c,"PREMIUM",cx,h*.29f,w*.105f,Color.rgb(255,224,70),Paint.Align.CENTER);if(e>3300)text(c,"777",cx,h*.44f,w*.18f,Color.WHITE,Paint.Align.CENTER);
    if(e>4800){for(int i=0;i<3;i++)sprite(c,girl[i],w*(.20f+i*.30f),h*.82f,w*.235f,h*.30f);for(int i=0;i<80;i++){float x=((i*191+n/5)%1200)/1200f*w,y=((i*103+n/4)%1300)/1300f*h;p.setColor(Color.argb(180,255,200+(i%3)*18,45));c.drawCircle(x,y,w*(.003f+(i%5)*.0018f),p);}}
  }

  private void revivalFx(Canvas c,int w,int h,long n)throws Exception{long e=n-L(revStart);p.setColor(Color.argb(242,0,0,0));c.drawRect(0,0,w,h,p);if(e<700)text(c,"……",w/2f,h*.49f,w*.065f,Color.rgb(190,180,200),Paint.Align.CENTER);else{text(c,"……まだ。",w/2f,h*.49f,w*.068f,Color.rgb(235,215,245),Paint.Align.CENTER);float k=.5f+.5f*(float)Math.sin(n/90.0);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(w*.006f);p.setColor(Color.argb((int)(80+100*k),255,120,210));c.drawCircle(w/2f,h*.49f,w*(.12f+.05f*k),p);p.setStyle(Paint.Style.FILL);}}

  private void reward(Canvas c,int w,int h,long n)throws Exception{long e=n-L(rewardStart),dur=Math.max(1,L(rewardEnd)-L(rewardStart));float k=(float)Math.sin(Math.min(1,e/(float)dur)*Math.PI),cy=h*.22f;for(int i=0;i<14;i++){double a=Math.PI*2*i/14+n/800.0;float rr=w*(.04f+.055f*k);coin(c,w*.45f+(float)Math.cos(a)*rr,cy+(float)Math.sin(a)*rr,w*.009f);}coin(c,w*.39f,cy,w*.03f*(1+k*.3f));text(c,"+"+I(lastReward),w*.44f,cy+w*.012f,w*.06f,Color.rgb(255,228,80),Paint.Align.LEFT);}
  private void coin(Canvas c,float x,float y,float r){p.setColor(Color.rgb(255,190,35));c.drawCircle(x,y,r,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(r*.18f);p.setColor(Color.rgb(255,235,125));c.drawCircle(x,y,r*.72f,p);p.setStyle(Paint.Style.FILL);}
  private void text(Canvas c,String s,float x,float y,float size,int color,Paint.Align al){p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));p.setTextAlign(al);p.setTextSize(size);p.setColor(color);c.drawText(s,x,y,p);}
  private void oneLine(Canvas c,String s,float x,float y,float size,int color,float max){p.setTextSize(size);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));while(p.measureText(s)>max&&p.getTextSize()>15)p.setTextSize(p.getTextSize()*.94f);p.setTextAlign(Paint.Align.LEFT);p.setColor(color);c.drawText(s,x,y,p);}
}
