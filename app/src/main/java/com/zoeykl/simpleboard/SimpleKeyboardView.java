package com.zoeykl.simpleboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class SimpleKeyboardView extends View {
    public interface Listener { void onKey(KeySpec key); void onPopupText(String text); }
    private static final class KeyBox { KeySpec key; RectF rect; KeyBox(KeySpec k,RectF r){key=k;rect=r;} }

    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private List<List<KeySpec>> rows=new ArrayList<>();
    private final List<KeyBox> boxes=new ArrayList<>();
    private Listener listener;
    private KeyBox pressed;
    private boolean popupActive=false;
    private String[] popupEntries=new String[0];
    private RectF popupRect=new RectF();
    private int popupSelected=-1;
    private Runnable longPressRunnable;
    private Runnable repeatRunnable;
    private Bitmap background;
    private ThemeConfig theme;
    private float contentLeft,contentRight,contentBottom;
    private int navInsetLeft,navInsetRight,navInsetBottom;

    public SimpleKeyboardView(Context c){ super(c); setFocusable(false); refreshPrefs(); }
    public void setListener(Listener l){listener=l;}
    public void setRows(List<List<KeySpec>> r){rows=r==null?new ArrayList<List<KeySpec>>():r;requestLayout();invalidate();}
    public void refreshPrefs(){
        theme=ThemeConfig.read(getContext());
        background=null;
        if(theme.backgroundPath!=null&&!theme.backgroundPath.isEmpty()){
            try{File f=new File(theme.backgroundPath);if(f.isFile())background=BitmapFactory.decodeFile(f.getAbsolutePath());}catch(Exception ignored){}
        }
        requestLayout(); invalidate();
    }

    @Override protected void onMeasure(int wSpec,int hSpec){
        int w=MeasureSpec.getSize(wSpec);
        int base=dp(getResources().getConfiguration().orientation==2?235:275);
        int desired=(int)(base*(Prefs.i(getContext(),"keyboard_height",100)/100f))+navInsetBottom;
        setMeasuredDimension(w,resolveSize(desired,hSpec));
    }

    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets){
        int left=0,right=0,bottom=0;
        if(Build.VERSION.SDK_INT>=30){
            android.graphics.Insets nav=insets.getInsets(WindowInsets.Type.navigationBars());
            left=nav.left;right=nav.right;bottom=nav.bottom;
        }else{
            left=insets.getSystemWindowInsetLeft();right=insets.getSystemWindowInsetRight();bottom=insets.getSystemWindowInsetBottom();
        }
        if(left!=navInsetLeft||right!=navInsetRight||bottom!=navInsetBottom){
            navInsetLeft=left;navInsetRight=right;navInsetBottom=bottom;requestLayout();invalidate();
        }
        return insets;
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        theme=ThemeConfig.read(getContext());
        paint.setStyle(Paint.Style.FILL);paint.setColor(theme.board);c.drawRect(0,0,getWidth(),getHeight(),paint);
        if(background!=null)drawCenterCrop(c,background,new RectF(0,0,getWidth(),getHeight()));
        layoutBoxes();
        for(KeyBox b:boxes)drawKey(c,b);
        if(pressed!=null&&Prefs.b(getContext(),"key_preview",true)&&!popupActive)drawPreview(c,pressed);
        if(popupActive)drawPopup(c);
    }

    private void drawCenterCrop(Canvas c,Bitmap b,RectF dst){
        float scale=Math.max(dst.width()/b.getWidth(),dst.height()/b.getHeight());
        float sw=dst.width()/scale,sh=dst.height()/scale;float sx=(b.getWidth()-sw)/2f,sy=(b.getHeight()-sh)/2f;
        c.drawBitmap(b,new android.graphics.Rect((int)sx,(int)sy,(int)(sx+sw),(int)(sy+sh)),dst,paint);
    }

    private void layoutBoxes(){
        boxes.clear(); if(rows.isEmpty())return;
        int mode=Prefs.i(getContext(),"one_hand_mode",0); float one=Prefs.i(getContext(),"one_hand_width",76)/100f;
        float safeLeft=navInsetLeft,safeRight=getWidth()-navInsetRight;
        contentLeft=safeLeft;contentRight=safeRight;contentBottom=Math.max(0,getHeight()-navInsetBottom);
        float safeWidth=Math.max(0,safeRight-safeLeft);
        if(mode==1){contentRight=safeLeft+safeWidth*one;} else if(mode==2){contentLeft=safeRight-safeWidth*one;}
        float gap=dp(theme.gap);
        int lastRow=rows.size()-1;
        boolean bottomHasSpace=false;
        for(KeySpec k:rows.get(lastRow))if(k.kind==KeySpec.Kind.SPACE){bottomHasSpace=true;break;}
        float spaceScale=bottomHasSpace?Math.max(1f,Prefs.i(getContext(),"spacebar_height",125)/100f):1f;
        float totalUnits=(rows.size()-1)+spaceScale;
        float normalRowH=contentBottom/Math.max(1f,totalUnits);
        float bandTop=0;
        for(int ri=0;ri<rows.size();ri++){
            List<KeySpec> row=rows.get(ri);float total=0;for(KeySpec k:row)total+=Math.max(.1f,k.weight);
            boolean tallBottom=ri==lastRow&&bottomHasSpace;
            float bandH=tallBottom?normalRowH*spaceScale:normalRowH;
            float bandBottom=bandTop+bandH;
            float x=contentLeft;float usable=contentRight-contentLeft;
            for(KeySpec k:row){
                float kw=usable*(k.weight/total);
                float top=bandTop+gap,bottom=bandBottom-gap;
                if(tallBottom&&k.kind!=KeySpec.Kind.SPACE){
                    float extra=Math.max(0,(bandH-normalRowH)/2f);
                    top=bandTop+extra+gap;
                    bottom=bandBottom-extra-gap;
                }
                RectF r=new RectF(x+gap,top,x+kw-gap,bottom);boxes.add(new KeyBox(k,r));x+=kw;
            }
            bandTop=bandBottom;
        }
    }

    private void drawKey(Canvas c,KeyBox b){
        int color=theme.key;
        if(b.key.kind==KeySpec.Kind.FUNCTION)color=theme.function;else if(b.key.kind==KeySpec.Kind.SPACE)color=theme.space;else if(b.key.kind==KeySpec.Kind.ACTION)color=theme.accent;
        if(pressed==b&&!popupActive)color=blend(color,theme.accent,0.28f);
        paint.setStyle(Paint.Style.FILL);paint.setColor(color);c.drawRoundRect(b.rect,dp(theme.radius),dp(theme.radius),paint);
        if(theme.borderWidth>0){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(theme.borderWidth));paint.setColor(theme.border);c.drawRoundRect(b.rect,dp(theme.radius),dp(theme.radius),paint);}
        paint.setStyle(Paint.Style.FILL);paint.setColor(theme.text);paint.setTextAlign(Paint.Align.CENTER);
        float size=sp(20)*(theme.textScale/100f); if(b.key.label.length()>4)size*=0.72f; paint.setTextSize(size);
        Paint.FontMetrics fm=paint.getFontMetrics();float cy=b.rect.centerY()-(fm.ascent+fm.descent)/2f;c.drawText(b.key.label,b.rect.centerX(),cy,paint);
        String pop=popupRaw(b.key.label);
        if(!pop.isEmpty()){paint.setTextAlign(Paint.Align.RIGHT);paint.setTextSize(sp(8));paint.setColor(theme.hint);String first=popupDisplay(pop.split("\\|")[0]);c.drawText(first,b.rect.right-dp(4),b.rect.top+dp(10),paint);}
    }

    private void drawPreview(Canvas c,KeyBox b){
        if(b.key.action!=KeySpec.Action.TEXT)return;
        float w=Math.max(dp(52),b.rect.width()*1.15f),h=dp(54);float cx=b.rect.centerX();float left=clamp(cx-w/2,contentLeft,contentRight-w);float top=Math.max(0,b.rect.top-h-dp(4));RectF r=new RectF(left,top,left+w,top+h);
        paint.setStyle(Paint.Style.FILL);paint.setColor(theme.popupBg);c.drawRoundRect(r,dp(10),dp(10),paint);paint.setTextAlign(Paint.Align.CENTER);paint.setColor(theme.popupText);paint.setTextSize(sp(27));Paint.FontMetrics fm=paint.getFontMetrics();c.drawText(b.key.label,r.centerX(),r.centerY()-(fm.ascent+fm.descent)/2,paint);
    }

    private void showPopup(){
        if(pressed==null)return; String raw=popupRaw(pressed.key.label); if(raw.trim().isEmpty())return;
        popupEntries=raw.split("\\|"); if(popupEntries.length==0)return; popupActive=true;popupSelected=Prefs.b(getContext(),"popup_autoselect",true)?0:-1;
        int cols=Math.max(1,Math.min(theme.popupColumns,popupEntries.length));int rowsCount=(popupEntries.length+cols-1)/cols;
        float cellW=dp(48)*(theme.popupScale/100f),cellH=dp(50)*(theme.popupScale/100f);float w=cols*cellW,h=rowsCount*cellH;
        float left=clamp(pressed.rect.centerX()-w/2,contentLeft,contentRight-w);float above=pressed.rect.top-h-dp(5),below=pressed.rect.bottom+dp(5);float top;
        if("below".equals(theme.popupPosition))top=below;else if("above".equals(theme.popupPosition))top=above;else top=above<0?below:above;
        top=clamp(top,0,Math.max(0,contentBottom-h));popupRect.set(left,top,left+w,top+h); performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);invalidate();
    }

    private void drawPopup(Canvas c){
        int cols=Math.max(1,Math.min(theme.popupColumns,popupEntries.length));int rowsCount=(popupEntries.length+cols-1)/cols;float cw=popupRect.width()/cols,ch=popupRect.height()/rowsCount;
        float rad="square".equals(theme.popupShape)?0:("pill".equals(theme.popupShape)?Math.min(cw,ch)/2f:dp(theme.popupRadius));
        paint.setStyle(Paint.Style.FILL);paint.setColor(theme.popupBg);c.drawRoundRect(popupRect,rad,rad,paint);
        if(theme.popupBorderWidth>0){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(theme.popupBorderWidth));paint.setColor(theme.popupBorder);c.drawRoundRect(popupRect,rad,rad,paint);paint.setStyle(Paint.Style.FILL);}
        float inset=dp(theme.popupGap)/2f;
        for(int i=0;i<popupEntries.length;i++){int col=i%cols,row=i/cols;RectF cell=new RectF(popupRect.left+col*cw+inset,popupRect.top+row*ch+inset,popupRect.left+(col+1)*cw-inset,popupRect.top+(row+1)*ch-inset);if(i==popupSelected){paint.setColor(theme.accent);c.drawRoundRect(cell,rad,rad,paint);}paint.setTextAlign(Paint.Align.CENTER);paint.setColor(theme.popupText);paint.setTextSize(sp(22)*(theme.popupTextScale/100f));Paint.FontMetrics fm=paint.getFontMetrics();c.drawText(popupDisplay(popupEntries[i]),cell.centerX(),cell.centerY()-(fm.ascent+fm.descent)/2,paint);}
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        float x=e.getX(),y=e.getY();
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                cancelTimers();popupActive=false;popupSelected=-1;pressed=find(x,y);
                if(pressed!=null){int delay=Prefs.i(getContext(),"hold_delay",350);longPressRunnable=this::showPopup;handler.postDelayed(longPressRunnable,delay);if(pressed.key.action==KeySpec.Action.DELETE){repeatRunnable=new Runnable(){public void run(){if(pressed!=null&&!popupActive&&listener!=null)listener.onKey(pressed.key);handler.postDelayed(this,70);}};handler.postDelayed(repeatRunnable,500);}invalidate();return true;}
                return false;
            case MotionEvent.ACTION_MOVE:
                if(popupActive){popupSelected=popupIndex(x,y);invalidate();}return true;
            case MotionEvent.ACTION_UP:
                cancelTimers();
                if(popupActive){int idx=popupSelected;if(idx>=0&&idx<popupEntries.length&&listener!=null)listener.onPopupText(popupEntries[idx]);}
                else if(pressed!=null&&listener!=null)listener.onKey(pressed.key);
                pressed=null;popupActive=false;popupEntries=new String[0];invalidate();return true;
            case MotionEvent.ACTION_CANCEL:
                cancelTimers();pressed=null;popupActive=false;invalidate();return true;
        }
        return true;
    }


    private String popupRaw(String label){
        String key=Prefs.popupKey(label);
        if(Prefs.p(getContext()).contains(key))return Prefs.s(getContext(),key,"");
        String l=label.toLowerCase(java.util.Locale.US);
        if("a".equals(l))return "á|à|â|ä|ã|å|æ";
        if("e".equals(l))return "é|è|ê|ë";
        if("i".equals(l))return "í|ì|î|ï";
        if("o".equals(l))return "ó|ò|ô|ö|õ|ø|œ";
        if("u".equals(l))return "ú|ù|û|ü";
        if("c".equals(l))return "ç";
        if("n".equals(l))return "ñ";
        if("y".equals(l))return "ÿ";
        if("?123".equals(label))return "@numpad";
        if(",".equals(label))return "@settings";
        if(".".equals(label))return "?|!|…";
        return "";
    }
    private String popupDisplay(String s){
        if("@numpad".equals(s))return "123";if("@settings".equals(s))return "⚙";if("@voice".equals(s))return "🎤";if("@paste".equals(s))return "📋";if("@left".equals(s))return "←";if("@right".equals(s))return "→";if("@emoji".equals(s))return "☺";if("@enter".equals(s))return "↵";if("@tab".equals(s))return "⇥";return s;
    }

    private int popupIndex(float x,float y){
        if(!popupRect.contains(x,y))return Prefs.b(getContext(),"popup_cancel_outside",true)?-1:popupSelected;
        int cols=Math.max(1,Math.min(theme.popupColumns,popupEntries.length));int rowsCount=(popupEntries.length+cols-1)/cols;float cw=popupRect.width()/cols,ch=popupRect.height()/rowsCount;
        int col=Math.min(cols-1,Math.max(0,(int)((x-popupRect.left)/cw)));int row=Math.min(rowsCount-1,Math.max(0,(int)((y-popupRect.top)/ch)));int idx=row*cols+col;return idx<popupEntries.length?idx:popupSelected;
    }
    private KeyBox find(float x,float y){for(KeyBox b:boxes)if(b.rect.contains(x,y))return b;return null;}
    private void cancelTimers(){if(longPressRunnable!=null)handler.removeCallbacks(longPressRunnable);if(repeatRunnable!=null)handler.removeCallbacks(repeatRunnable);longPressRunnable=null;repeatRunnable=null;}
    private int blend(int a,int b,float t){int aa=(a>>>24)&255,ar=(a>>16)&255,ag=(a>>8)&255,ab=a&255,ba=(b>>>24)&255,br=(b>>16)&255,bg=(b>>8)&255,bb=b&255;return ((int)(aa+(ba-aa)*t)<<24)|((int)(ar+(br-ar)*t)<<16)|((int)(ag+(bg-ag)*t)<<8)|(int)(ab+(bb-ab)*t);}
    private float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
    private float sp(float x){return x*getResources().getDisplayMetrics().scaledDensity;}
}
