package com.aonon.pixelslot;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public class PixelSlotV5View extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Random rnd = new Random();
    private final RectF spinRect = new RectF();
    private final int[] shown = {2,5,8};
    private final int[] target = {2,5,8};
    private final boolean[] locked = {false,false,false};
    private final long[] pulseUntil = {0,0,0};
    private final Bitmap[][] girls = new Bitmap[3][2];
    private Bitmap coin, coinBurst, premiumReward;
    private final SharedPreferences prefs;

    private static final int COST=10, NORMAL_REWARD=120, REVIVAL_REWARD=220, PREMIUM_REWARD=500;
    private int coins, spins=0, wins=0, reachStage=0, lastReward=0;
    private boolean spinning=false, reach=false, result=false, premium=false, revival=false;
    private boolean premiumShown=false, revivalActive=false, revivalDone=false;
    private long spinStart=0, celebrationStart=0, celebrationUntil=0;
    private long premiumStart=0, premiumUntil=0, revivalStart=0, revivalFlashUntil=0;
    private long cutStart=0, cutUntil=0, rewardStart=0, rewardUntil=0;
    private String cutTitle="", cutMessage="";
    private int cutType=0;

    private final int[] accent={Color.rgb(255,158,194),Color.rgb(190,166,255),Color.rgb(132,229,224)};
    private final String[] names={"モモ","ルナ","ミオ"};

    public PixelSlotV5View(Context c){
        super(c);
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        prefs=c.getSharedPreferences("pixelslot_state",Context.MODE_PRIVATE);
        coins=prefs.getInt("coins",1000);
        loadAssets();
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        int w=getWidth(), h=getHeight(); long now=System.currentTimeMillis();
        update(now);
        drawBg(c,w,h,now);
        c.save();
        float sh=shake(now,w);
        if(sh>0)c.translate((float)Math.sin(now/10.0)*sh,(float)Math.cos(now/14.0)*sh*.7f);
        drawHeader(c,w,h); drawMachine(c,w,h,now); drawButton(c,w,h,now); c.restore();
        if(reach&&spinning&&reachStage>=3) drawHype(c,w,h,now,false);
        if(now<celebrationUntil){drawHype(c,w,h,now,true);drawSparkles(c,w,h,now);if(premium)drawPremiumBanner(c,w,h,now);}
        drawPulses(c,w,h,now);
        if(now<cutUntil)drawCut(c,w,h,now);
        if(now<premiumUntil)drawPremium(c,w,h,now);
        if(revivalActive)drawRevival(c,w,h,now);
        if(now<revivalFlashUntil)drawRevivalFlash(c,w,h,now);
        if(now<rewardUntil)drawReward(c,w,h,now);
        if(spinning||revivalActive||now<celebrationUntil||now<cutUntil||now<premiumUntil||now<revivalFlashUntil||now<rewardUntil||now<pulseUntil[0]||now<pulseUntil[1]||now<pulseUntil[2]) postInvalidateOnAnimation();
    }

    private void drawBg(Canvas c,int w,int h,long now){
        int top=Color.rgb(30,20,46), bottom=Color.rgb(13,9,23);
        if(spinning&&reachStage>=4)top=Color.rgb(72,14,78);
        if(now<celebrationUntil){top=premium?Color.rgb(88,55,5):Color.rgb(82,17,54);bottom=premium?Color.rgb(28,15,2):Color.rgb(48,13,20);}
        p.setShader(new LinearGradient(0,0,0,h,top,bottom,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
        p.setColor(Color.argb(16,255,207,236));c.drawCircle(w*.1f,h*.15f,w*.29f,p);
        p.setColor(Color.argb(10,132,222,255));c.drawCircle(w*.94f,h*.65f,w*.33f,p);
    }

    private void drawHeader(Canvas c,int w,int h){
        p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setColor(Color.rgb(255,221,244));p.setTextSize(w*.071f);c.drawText("PIXEL SLOT",w/2f,h*.072f,p);
        p.setColor(Color.rgb(190,176,204));p.setTextSize(w*.026f);c.drawText("静かな通常時。来た時だけ全部盛り。",w/2f,h*.107f,p);
        p.setTextSize(w*.029f);p.setColor(Color.rgb(235,226,244));p.setTextAlign(Paint.Align.LEFT);c.drawText("SPIN  "+spins,w*.055f,h*.15f,p);
        drawBitmap(c,coin,w*.405f,h*.135f,w*.052f,w*.052f,255);
        p.setTextAlign(Paint.Align.CENTER);p.setColor(Color.rgb(255,226,105));p.setTextSize(w*.031f);c.drawText("COIN  "+coins,w*.54f,h*.15f,p);
        p.setTextAlign(Paint.Align.RIGHT);p.setColor(Color.rgb(235,226,244));c.drawText("WIN  "+wins,w*.945f,h*.15f,p);
    }

    private void drawMachine(Canvas c,int w,int h,long now){
        RectF box=new RectF(w*.045f,h*.18f,w*.955f,h*.735f);
        p.setColor(Color.argb(225,55,37,76));c.drawRoundRect(box,w*.05f,w*.05f,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(w*(reachStage>=3&&spinning?.010f:.006f));
        if(reachStage>=3&&spinning){float q=.5f+.5f*(float)Math.sin(now/55.0);p.setColor(Color.rgb(255,(int)(100+q*120),(int)(80+q*150)));}else p.setColor(Color.rgb(184,124,174));
        c.drawRoundRect(box,w*.05f,w*.05f,p);p.setStyle(Paint.Style.FILL);
        float[] xs={w*.20f,w*.50f,w*.80f}; boolean win=now<celebrationUntil;
        for(int i=0;i<3;i++){
            float bob=(float)Math.sin(now/360.0+i*1.5)*h*.0018f, trem=0, jump=0;
            if(reach&&spinning&&i==2&&reachStage>=3)trem=(float)Math.sin(now/12.0)*w*(.003f+reachStage*.0028f);
            if(win)jump=-(float)Math.abs(Math.sin((now%390)/390f*Math.PI))*h*(.026f+i*.004f);
            RectF r=drawGirl(c,xs[i]+trem,h*.425f+bob+jump,w*.29f,h*.36f,i,win?1:0);
            if(!win)drawDigit(c,r,i,shown[i],w); else drawCard(c,xs[i],h*.57f+jump*.15f,w,h,shown[i],i,.68f);
            p.setTextAlign(Paint.Align.CENTER);p.setTextSize(w*.03f);p.setColor(Color.rgb(213,199,222));c.drawText(names[i],xs[i],h*.69f,p);
        }
        if(reach&&spinning){String[] t={"","……？","もしかして…","リーチ！！","まだ続く…！","激アツッ！！","限界突破…！","止まれぇぇぇ！！"};p.setTextAlign(Paint.Align.CENTER);p.setTextSize(w*(.033f+Math.max(0,reachStage-1)*.006f));p.setColor(reachStage<=2?Color.rgb(220,211,228):Color.rgb(255,236,102));c.drawText(t[Math.min(7,reachStage)],w/2f,h*.72f,p);}
    }

    private void drawButton(Canvas c,int w,int h,long now){
        float bw=w*.64f,bh=h*.105f,cx=w/2f,cy=h*.85f;spinRect.set(cx-bw/2,cy-bh/2,cx+bw/2,cy+bh/2);
        p.setShadowLayer(w*.018f,0,h*.006f,Color.argb(110,0,0,0));
        p.setColor(spinning||revivalActive?Color.rgb(92,78,108):Color.rgb(219,83,148));c.drawRoundRect(spinRect,bh/2,bh/2,p);p.clearShadowLayer();
        String s=!spinning&&!revivalActive?"SPIN!":reachStage<=1?"……":reachStage==2?"ん……？":reachStage<=4?"来てる…！":"うわあああ！！";
        p.setTextAlign(Paint.Align.CENTER);p.setColor(Color.WHITE);p.setTextSize(w*.059f);Paint.FontMetrics f=p.getFontMetrics();c.drawText(s,cx,cy-(f.ascent+f.descent)/2,p);
        p.setTextSize(w*.027f);p.setColor(Color.rgb(177,163,190));c.drawText("1 PLAY  10 COIN",cx,h*.93f,p);
    }

    private void update(long now){
        if(revivalActive){long e=now-revivalStart;if(!revivalDone&&e>=1700){revivalDone=true;revivalActive=false;shown[2]=target[0];pulseUntil[2]=now+1200;revivalFlashUntil=now+900;wins++;award(REVIVAL_REWARD,now,false);celebrationStart=now;celebrationUntil=now+7600;cut(now,3100,10,"復活ッッッ！！！","まだ終わってへん！ 揃ったぁぁぁ！！");}return;}
        if(!spinning)return;
        long e=now-spinStart;boolean r=target[0]==target[1];long s1=1000,s2=2800,s3=premium?16600:(r?13600:4300);
        reel(0,e,s1,now,58);reel(1,e,s2,now,62);
        if(e<s3){long iv=55;if(r&&e>=s2){if(e>=12800)iv=350;else if(e>=11200)iv=270;else if(e>=9500)iv=195;else if(e>=7700)iv=145;else if(e>=6000)iv=110;else if(e>=4300)iv=88;else iv=70;}shown[2]=(int)((e/iv+6)%10);}else{shown[2]=target[2];if(!locked[2]){locked[2]=true;pulseUntil[2]=now+1000;}}
        if(r&&!reach&&e>=s2){reach=true;reachStage=1;cut(now,850,7,"……ん？","今、ふたり揃った……？");}
        if(r&&reachStage<2&&e>=4300){reachStage=2;cut(now,1000,8,"もしかして…","ミオだけ、まだ回ってる……");}
        if(r&&reachStage<3&&e>=6000){reachStage=3;cut(now,1200,1,"リーチ！！","ここからやで旦那はん……！");}
        if(r&&reachStage<4&&e>=7700){reachStage=4;cut(now,1250,4,"まだ終わらないッ！","まだ回る……まだ止まらへん！！");}
        if(premium&&!premiumShown&&e>=8500){premiumShown=true;premiumStart=now;premiumUntil=now+7200;cutUntil=0;}
        if(r&&reachStage<5&&e>=9500){reachStage=5;if(!premium)cut(now,1350,5,"激アツッ！！！","これ来てる！ 全部光ってるぅぅ！！");}
        if(r&&reachStage<6&&e>=11200){reachStage=6;if(!premium)cut(now,1400,9,"限界突破ッ！！！","ここまで来たら、もう止められへん！！");}
        if(r&&reachStage<7&&e>=12800){reachStage=7;if(!premium)cut(now,1550,6,"止まれぇぇぇぇ！！！","旦那はん見てて！ ここで止まってぇぇ！！");}
        if(!result&&e>=s3){result=true;spinning=false;if(revival){revivalActive=true;revivalStart=now;cutUntil=0;return;}boolean jp=target[0]==target[1]&&target[1]==target[2];if(jp){wins++;award(premium?PREMIUM_REWARD:NORMAL_REWARD,now,premium);celebrationStart=now;celebrationUntil=now+(premium?9200:7000);cut(now,premium?4000:3400,premium?11:2,premium?"PREMIUM 777！！！":"大当たりィィィィ！！！！！",premium?"旦那はん、これは特別やでーーっ！！":"やったぁぁぁ！ 旦那はん最強ーーーっ！！");}else if(r)cut(now,1900,3,"うわぁぁぁ惜しい！！","そこまで行ったのにぃ！ 次、絶対いこ！！");}
    }

    private void reel(int i,long e,long stop,long now,long iv){if(e<stop)shown[i]=(int)((e/iv+i*3)%10);else{shown[i]=target[i];if(!locked[i]){locked[i]=true;pulseUntil[i]=now+800;}}}

    private void startSpin(){
        if(spinning||revivalActive)return;long now=System.currentTimeMillis();
        if(coins<COST){cut(now,1800,3,"コインが足りへん…！","あと "+(COST-coins)+" COIN 必要やで");invalidate();return;}
        coins-=COST;saveCoins();spins++;spinning=true;reach=false;result=false;reachStage=0;spinStart=now;celebrationUntil=cutUntil=premiumUntil=revivalFlashUntil=rewardUntil=0;premium=false;premiumShown=false;revival=false;revivalActive=false;revivalDone=false;
        for(int i=0;i<3;i++){locked[i]=false;pulseUntil[i]=0;}
        float x=rnd.nextFloat();
        if(x<.03f){premium=true;target[0]=target[1]=target[2]=7;}
        else if(x<.08f){revival=true;int n=rnd.nextInt(10);target[0]=target[1]=n;do target[2]=rnd.nextInt(10);while(target[2]==n);}
        else if(x<.14f){int n=rnd.nextInt(10);target[0]=target[1]=target[2]=n;}
        else if(x<.44f){int n=rnd.nextInt(10);target[0]=target[1]=n;do target[2]=rnd.nextInt(10);while(target[2]==n);}
        else{target[0]=rnd.nextInt(10);do target[1]=rnd.nextInt(10);while(target[1]==target[0]);target[2]=rnd.nextInt(10);}
        performClick();invalidate();
    }

    private void cut(long now,long d,int type,String title,String msg){cutStart=now;cutUntil=now+d;cutType=type;cutTitle=title;cutMessage=msg;}

    private void drawCut(Canvas c,int w,int h,long now){
        float d=Math.max(1,cutUntil-cutStart),t=Math.min(1,(now-cutStart)/d),vis=Math.min(Math.min(1,t/.13f),t>.86f?Math.max(0,(1-t)/.14f):1);boolean whisper=cutType==7||cutType==8,huge=cutType==2||cutType==6||cutType==9||cutType==10||cutType==11;float top=h*(whisper?.285f:huge?.175f:.235f),bot=h*(whisper?.395f:huge?.475f:.415f),slide=(1-vis)*w*(whisper?.18f:(cutType%2==0?-.95f:.95f));int ac=cutAccent(cutType);
        c.save();c.translate(slide,0);p.setColor(Color.argb(whisper?185:248,21,10,36));c.drawRect(-w*.05f,top,w*1.05f,bot,p);p.setColor(ac);float edge=h*(whisper?.004f:.011f);c.drawRect(0,top,w,top+edge,p);c.drawRect(0,bot-edge,w,bot,p);
        int gi=(cutType==2||cutType==11)?0:(cutType==3?1:2);drawGirl(c,w*.16f,(top+bot)/2,w*(whisper?.17f:huge?.25f:.20f),h*(whisper?.14f:huge?.25f:.19f),gi,huge?1:0);
        p.setTextAlign(Paint.Align.LEFT);p.setColor(ac);p.setTextSize(w*(whisper?.05f:huge?.078f:.064f));c.drawText(cutTitle,w*.30f,top+h*(whisper?.05f:huge?.11f:.075f),p);p.setColor(Color.WHITE);p.setTextSize(w*(whisper?.029f:huge?.036f:.033f));c.drawText(cutMessage,w*.30f,top+h*(whisper?.088f:huge?.177f:.128f),p);c.restore();
        if(huge){p.setColor(Color.argb((int)(105*Math.sin(t*Math.PI)),255,cutType==11?225:255,cutType==11?80:255));c.drawRect(0,0,w,h,p);}
    }
    private int cutAccent(int x){if(x==11)return Color.rgb(255,221,61);if(x==10)return Color.rgb(255,80,205);if(x==9)return Color.rgb(255,65,65);if(x==6)return Color.rgb(255,42,112);if(x==5)return Color.rgb(255,122,44);if(x==4)return Color.rgb(139,236,255);if(x==3)return Color.rgb(138,197,255);if(x==2)return Color.rgb(255,222,64);return x>=7?Color.rgb(215,195,240):Color.rgb(255,105,181);}

    private void drawPremium(Canvas c,int w,int h,long now){
        long e=now-premiumStart;p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        if(e<1200){p.setColor(Color.BLACK);c.drawRect(0,0,w,h,p);float q=e/1200f,s=w*(.006f+q*.015f);p.setColor(Color.rgb(255,222,82));c.drawRect(w/2-s/2,h*.5f-s*2,w/2+s/2,h*.5f+s*2,p);c.drawRect(w/2-s*2,h*.5f-s/2,w/2+s*2,h*.5f+s/2,p);return;}
        p.setShader(new LinearGradient(0,0,0,h,Color.rgb(52,31,3),Color.rgb(7,5,1),Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);float q=.5f+.5f*(float)Math.sin(now/55.0);
        if(e<2800){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(w*.007f);p.setColor(Color.argb((int)(120+q*100),255,220,78));c.drawCircle(w/2f,h*.47f,w*(.07f+q*.04f),p);p.setStyle(Paint.Style.FILL);p.setTextSize(w*.045f);p.setColor(Color.rgb(255,231,150));c.drawText("……？",w/2f,h*.62f,p);return;}
        rays(c,w,h,now);p.setColor(Color.rgb(255,225,83));p.setTextSize(w*.125f);c.drawText("PREMIUM",w/2f,h*.25f,p);if(e<4600)return;
        p.setTextSize(w*.275f);p.setColor(Color.WHITE);c.drawText("777",w/2f,h*.48f,p);p.setTextSize(w*.043f);p.setColor(Color.rgb(255,229,135));c.drawText("プレミア確定",w/2f,h*.56f,p);if(e<6100)return;
        drawBitmap(c,premiumReward,w*.5f,h*.62f,w*.37f,h*.27f,230);float[] xs={w*.22f,w*.5f,w*.78f};for(int i=0;i<3;i++)drawGirl(c,xs[i],h*.76f,w*.23f,h*.24f,i,1);p.setTextSize(w*.035f);p.setColor(Color.rgb(255,239,173));c.drawText("旦那はん、これは特別やで。",w/2f,h*.92f,p);
    }
    private void rays(Canvas c,int w,int h,long now){p.setStyle(Paint.Style.STROKE);for(int i=0;i<44;i++){double a=i*Math.PI*2/44+now/520.;float in=w*.1f,out=w*(.36f+(i%3)*.04f);p.setStrokeWidth(w*(.0025f+(i%3)*.001f));p.setColor(Color.argb(100+(i%3)*25,255,218,i%2==0?72:150));c.drawLine(w/2f+(float)Math.cos(a)*in,h*.47f+(float)Math.sin(a)*in,w/2f+(float)Math.cos(a)*out,h*.47f+(float)Math.sin(a)*out,p);}p.setStyle(Paint.Style.FILL);}
    private void drawPremiumBanner(Canvas c,int w,int h,long now){float q=.5f+.5f*(float)Math.sin(now/55.);p.setColor(Color.argb(220,33,20,2));c.drawRect(0,h*.115f,w,h*.205f,p);p.setColor(Color.rgb(255,220,66));c.drawRect(0,h*.115f,w,h*.123f,p);c.drawRect(0,h*.197f,w,h*.205f,p);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(w*(.052f+q*.006f));p.setColor(Color.WHITE);c.drawText("★ PREMIUM JACKPOT ★",w/2f,h*.175f,p);}

    private void drawRevival(Canvas c,int w,int h,long now){long e=now-revivalStart;if(e<800)return;float a=e<1080?(e-800)/280f:1;p.setColor(Color.argb((int)(255*Math.min(1,a)),0,0,0));c.drawRect(0,0,w,h,p);if(e>1200){float q=Math.min(1,(e-1200)/400f);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(w*.043f);p.setColor(Color.argb((int)(180*q),235,220,240));c.drawText("……まだ。",w/2f,h*.52f,p);}}
    private void drawRevivalFlash(Canvas c,int w,int h,long now){float x=Math.max(0,(revivalFlashUntil-now)/900f),pr=1-x;p.setColor(Color.argb((int)(210*(1-pr)),255,120,230));c.drawRect(0,0,w,h,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(w*.014f*(1-pr*.6f));p.setColor(Color.argb((int)(220*(1-pr)),255,255,255));c.drawCircle(w*.8f,h*.55f,w*(.12f+pr*.35f),p);p.setStyle(Paint.Style.FILL);}

    private void drawHype(Canvas c,int w,int h,long now,boolean victory){int stage=victory?8:Math.max(3,reachStage),lines=victory?110:stage*14,parts=victory?150:stage*15;float cx=victory?w*.5f:w*.8f,cy=h*.53f;p.setStyle(Paint.Style.STROKE);for(int i=0;i<lines;i++){double a=i*Math.PI*2/lines+now/(victory?240.:Math.max(250,820-stage*70));float in=w*(.09f+(i%4)*.01f),out=w*(.20f+stage*.032f+(i%3)*.024f);p.setStrokeWidth(w*(.002f+(i%4)*.0013f));int cc=i%3==0?Color.rgb(255,234,78):i%3==1?Color.rgb(255,61,164):Color.rgb(103,228,255);p.setColor(Color.argb(victory?165:Math.min(190,stage*25),Color.red(cc),Color.green(cc),Color.blue(cc)));c.drawLine(cx+(float)Math.cos(a)*in,cy+(float)Math.sin(a)*in,cx+(float)Math.cos(a)*out,cy+(float)Math.sin(a)*out,p);}p.setStyle(Paint.Style.FILL);for(int i=0;i<parts;i++){long s=i*173+now/7;float x=((s*37)%1000)/1000f*w,y=h*.13f+(((s*91)%1000)/1000f)*h*.66f,sz=w*(.004f+(i%5)*.0017f);p.setColor(i%3==0?Color.rgb(255,231,72):i%3==1?Color.rgb(255,92,187):Color.rgb(104,229,255));c.drawRect(x,y,x+sz,y+sz*2,p);}if(stage>=5){float q=.5f+.5f*(float)Math.sin(now/45.);p.setColor(Color.argb((int)(20+q*65),255,victory?220:60,victory?90:175));c.drawRect(0,0,w,h,p);}}
    private void drawSparkles(Canvas c,int w,int h,long now){for(int i=0;i<(premium?150:110);i++){float ph=((now/6+i*71)%1000)/1000f,x=((i*173)%997)/997f*w,y=h*.1f+ph*h*.75f,s=w*(.004f+(i%6)*.002f);p.setColor(premium?(i%2==0?Color.rgb(255,224,72):Color.WHITE):(i%3==0?Color.rgb(255,221,70):i%3==1?Color.rgb(255,110,196):Color.rgb(120,229,255)));c.drawRect(x,y,x+s,y+s*2,p);}}
    private void drawPulses(Canvas c,int w,int h,long now){float[] xs={w*.2f,w*.5f,w*.8f};p.setStyle(Paint.Style.STROKE);for(int i=0;i<3;i++)if(now<pulseUntil[i]){float pr=1-Math.max(0,(pulseUntil[i]-now)/1000f);p.setStrokeWidth(w*.008f*(1-pr*.6f));p.setColor(Color.argb((int)(190*(1-pr)),255,i==2?221:150,i==2?80:225));c.drawCircle(xs[i],h*.55f,w*(.11f+pr*.18f),p);}p.setStyle(Paint.Style.FILL);}
    private float shake(long now,int w){if(spinning&&reach&&reachStage>2){float[] a={0,0,0,.0014f,.0028f,.005f,.0072f,.010f};return w*a[Math.min(7,reachStage)];}if(now<celebrationUntil&&now-celebrationStart<1800){float f=1-(now-celebrationStart)/1800f;return w*(premium?.020f:.017f)*Math.max(0,f);}return 0;}

    private void award(int amount,long now,boolean prem){coins+=amount;lastReward=amount;rewardStart=now;rewardUntil=now+(prem?3800:2900);saveCoins();}
    private void saveCoins(){prefs.edit().putInt("coins",coins).apply();}
    private void drawReward(Canvas c,int w,int h,long now){float d=Math.max(1,rewardUntil-rewardStart),t=Math.min(1,(now-rewardStart)/d),fade=t<.15f?t/.15f:t>.82f?(1-t)/.18f:1;drawBitmap(c,premium?premiumReward:coinBurst,w*.5f,h*.68f,premium?w*.62f:w*.56f,h*.32f,(int)(190*Math.max(0,fade)));for(int i=0;i<(premium?28:18);i++){float ph=(t*(1.3f+(i%5)*.08f)+i*.137f)%1,x=((i*197)%997)/997f*w+(float)Math.sin(now/75.+i)*w*.02f,y=h*(.32f+ph*.52f),s=w*(.03f+(i%4)*.008f);drawBitmap(c,coin,x,y,s,s,(int)(220*Math.max(0,fade)));}p.setTextAlign(Paint.Align.CENTER);p.setTextSize(w*(premium?.10f:.084f));p.setColor(Color.argb((int)(255*Math.max(0,fade)),255,235,103));c.drawText("+"+lastReward+" COIN",w/2f,h*.73f,p);}

    private void drawCard(Canvas c,float cx,float cy,int w,int h,int n,int i,float scale){float cw=w*.235f*scale,ch=h*.135f*scale;RectF r=new RectF(cx-cw/2,cy-ch/2,cx+cw/2,cy+ch/2);p.setColor(Color.rgb(247,241,250));c.drawRoundRect(r,w*.025f,w*.025f,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(w*.005f);p.setColor(accent[i]);c.drawRoundRect(r,w*.025f,w*.025f,p);p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));p.setTextSize(w*.105f*scale);p.setColor(Color.rgb(52,34,68));Paint.FontMetrics f=p.getFontMetrics();c.drawText(String.valueOf(n),cx,cy-(f.ascent+f.descent)/2,p);}

    private RectF drawGirl(Canvas c,float cx,float cy,float maxW,float maxH,int who,int state){Bitmap b=girls[who][state];if(b==null)return new RectF(cx-maxW/2,cy-maxH/2,cx+maxW/2,cy+maxH/2);float s=Math.min(maxW/b.getWidth(),maxH/b.getHeight()),dw=b.getWidth()*s,dh=b.getHeight()*s;RectF d=new RectF(cx-dw/2,cy-dh/2,cx+dw/2,cy+dh/2);c.drawBitmap(b,null,d,bp);return d;}
    private void drawDigit(Canvas c,RectF r,int who,int n,int w){float ax=who==0?.745f:who==1?.735f:.555f,ay=who==2?.455f:.445f,x=r.left+r.width()*ax,y=r.top+r.height()*ay;p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));p.setTextSize(Math.min(w*.09f,r.height()*.18f));p.setColor(Color.rgb(69,43,103));Paint.FontMetrics f=p.getFontMetrics();c.drawText(String.valueOf(n),x,y-(f.ascent+f.descent)/2,p);}
    private void drawBitmap(Canvas c,Bitmap b,float cx,float cy,float mw,float mh,int alpha){if(b==null)return;float s=Math.min(mw/b.getWidth(),mh/b.getHeight()),dw=b.getWidth()*s,dh=b.getHeight()*s;bp.setAlpha(alpha);c.drawBitmap(b,null,new RectF(cx-dw/2,cy-dh/2,cx+dw/2,cy+dh/2),bp);bp.setAlpha(255);}

    private void loadAssets(){Bitmap s=loadSheet();if(s==null)return;girls[0][0]=crop(s,16,3,137,169);girls[1][0]=crop(s,185,2,139,173);girls[2][0]=crop(s,370,25,109,150);girls[0][1]=crop(s,13,175,144,175);girls[1][1]=crop(s,180,175,150,175);girls[2][1]=crop(s,359,175,135,175);coin=crop(s,43,424,89,71);coinBurst=crop(s,178,359,160,147);premiumReward=crop(s,342,366,163,140);}
    private Bitmap loadSheet(){int[] ids={R.raw.assets_00,R.raw.assets_01,R.raw.assets_02,R.raw.assets_03,R.raw.assets_04,R.raw.assets_05,R.raw.assets_06,R.raw.assets_07,R.raw.assets_08};ByteArrayOutputStream all=new ByteArrayOutputStream();try{for(int id:ids){InputStream in=getResources().openRawResource(id);ByteArrayOutputStream txt=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)txt.write(buf,0,n);in.close();String b64=new String(txt.toByteArray(),StandardCharsets.US_ASCII).replaceAll("\\s+","");byte[] chunk=Base64.decode(b64,Base64.DEFAULT);all.write(chunk,0,chunk.length);}byte[] d=all.toByteArray();return BitmapFactory.decodeByteArray(d,0,d.length);}catch(IOException|IllegalArgumentException e){return null;}}
    private Bitmap crop(Bitmap b,int x,int y,int w,int h){int sx=Math.max(0,Math.min(x,b.getWidth()-1)),sy=Math.max(0,Math.min(y,b.getHeight()-1)),sw=Math.max(1,Math.min(w,b.getWidth()-sx)),sh=Math.max(1,Math.min(h,b.getHeight()-sy));return Bitmap.createBitmap(b,sx,sy,sw,sh);}

    @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_UP){if(spinRect.contains(e.getX(),e.getY()))startSpin();return true;}return true;}
    @Override public boolean performClick(){super.performClick();return true;}
}
