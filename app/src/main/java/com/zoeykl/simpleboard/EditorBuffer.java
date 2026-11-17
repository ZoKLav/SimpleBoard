package com.zoeykl.simpleboard;

import android.os.Build;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.SurroundingText;
import java.util.ArrayDeque;

/** Small per-editor window, updated with our edits and discarded on external cursor movement. */
final class EditorBuffer {
    private InputConnection connection;
    private String before="",after="";
    private int start=-1,end=-1;
    private boolean loaded,selected;
    private final ArrayDeque<Integer> expected=new ArrayDeque<>();
    void bind(InputConnection value,int selectionStart,int selectionEnd){
        connection=value;start=selectionStart;end=selectionEnd;loaded=false;expected.clear();
    }
    void invalidate(){loaded=false;expected.clear();}
    private void read(){
        if(loaded||connection==null)return;
        if(Build.VERSION.SDK_INT>=31){
            SurroundingText text=connection.getSurroundingText(256,80,0);
            if(text!=null){
                int a=text.getSelectionStart(),b=text.getSelectionEnd();CharSequence full=text.getText();
                if(a>=0&&b>=a&&b<=full.length()){
                    before=full.subSequence(0,a).toString();after=full.subSequence(b,full.length()).toString();selected=a!=b;
                    if(text.getOffset()>=0){start=text.getOffset()+a;end=text.getOffset()+b;}
                    loaded=true;return;
                }
            }
        }
        CharSequence a=connection.getTextBeforeCursor(256,0),b=connection.getTextAfterCursor(80,0),selection=connection.getSelectedText(0);
        before=a==null?"":a.toString();after=b==null?"":b.toString();selected=selection!=null&&selection.length()>0;loaded=true;
    }
    String before(){read();return before;}
    boolean selected(){read();return selected;}
    boolean wordEnd(){read();return after.isEmpty()||!wordChar(after.codePointAt(0));}
    static boolean wordChar(int cp){return Character.isLetterOrDigit(cp)||cp=='\''||cp=='\u2019'||cp=='-';}
    private void remember(){if(start>=0){expected.addLast(start);while(expected.size()>32)expected.removeFirst();}}
    void commit(String text){
        read();if(!connection.commitText(text,1)){invalidate();return;}before+=text;
        if(before.length()>256)before=before.substring(before.length()-256);
        if(start>=0)start+=text.length();end=start;selected=false;remember();
    }
    void deleteBefore(int codePoints){
        read();int count=Math.min(codePoints,before.codePointCount(0,before.length()));
        int cut=before.offsetByCodePoints(before.length(),-count),units=before.length()-cut;
        if(!connection.deleteSurroundingTextInCodePoints(codePoints,0)){invalidate();return;}before=before.substring(0,cut);
        if(start>=0)start=Math.max(0,start-units);end=start;remember();
    }
    /** Returns true only for a cursor/selection change not accounted for by our own edits. */
    boolean selectionChanged(int newStart,int newEnd){
        if(newStart==newEnd&&expected.contains(newStart)){
            while(!expected.isEmpty()&&expected.removeFirst()!=newStart){}
            return false;
        }
        if(newStart==start&&newEnd==end)return false;
        start=newStart;end=newEnd;invalidate();return true;
    }
    boolean sentenceStart(){
        String text=before();if(text.isEmpty())return true;
        int i=text.length()-1;boolean space=false;
        while(i>=0&&Character.isWhitespace(text.charAt(i))){if(text.charAt(i)=='\n')return true;space=true;i--;}
        if(i<0)return true;
        while(i>=0&&"\"')]}\u201d\u2019".indexOf(text.charAt(i))>=0)i--;
        return space&&i>=0&&".!?".indexOf(text.charAt(i))>=0;
    }
    void clear(){connection=null;before=after="";start=end=-1;loaded=false;expected.clear();}
}
