package com.zoeykl.simpleboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

public final class DictionaryActivity extends Activity {
    private WordStore store; private LinearLayout learnedList,correctionList;
    @Override protected void onCreate(Bundle b){super.onCreate(b);store=new WordStore(this);build();}
    private void build(){ScrollView sc=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(12),dp(16),dp(28));sc.addView(root);UiInsets.apply(sc);setContentView(sc);title(root,"Autocorrect & learned words");note(root,"Explicit corrections always beat automatic guesses. Tapping a suggestion in the keyboard also teaches that correction locally.");
        section(root,"Manual correction");EditText bad=field(root,"Typed text","teh"),good=field(root,"Replace with","the");button(root,"Save correction",v->{store.addCorrection(bad.getText().toString(),good.getText().toString());store.addWord(good.getText().toString());refresh();});
        section(root,"Add a word");EditText word=field(root,"Word","Etrius");button(root,"Add / trust word",v->{store.addWord(word.getText().toString());refresh();});
        section(root,"Learned words");note(root,"Tap a learned word to delete it.");learnedList=new LinearLayout(this);learnedList.setOrientation(LinearLayout.VERTICAL);root.addView(learnedList,new LinearLayout.LayoutParams(-1,-2));
        section(root,"Explicit corrections");note(root,"Tap a correction to delete it.");correctionList=new LinearLayout(this);correctionList.setOrientation(LinearLayout.VERTICAL);root.addView(correctionList,new LinearLayout.LayoutParams(-1,-2));refresh();}
    private void refresh(){if(learnedList==null)return;learnedList.removeAllViews();for(String s:store.learned(200)){TextView t=row(s);t.setOnClickListener(v->{String q=((TextView)v).getText().toString();int x=q.indexOf("  (");String w=x>0?q.substring(0,x):q;confirm("Delete learned word?",w,()->{store.deleteWord(w);refresh();});});learnedList.addView(t);}correctionList.removeAllViews();for(String s:store.corrections(200)){TextView t=row(s);t.setOnClickListener(v->{String q=((TextView)v).getText().toString();int x=q.indexOf("  →  ");String w=x>0?q.substring(0,x):q;confirm("Delete correction?",q,()->{store.deleteCorrection(w);refresh();});});correctionList.addView(t);}}
    private void confirm(String title,String msg,Runnable yes){new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->yes.run()).show();}
    private TextView row(String s){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.DKGRAY);t.setTextSize(16);t.setPadding(0,dp(7),0,dp(7));return t;}
    private EditText field(LinearLayout root,String label,String hint){note(root,label);EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);root.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    private Button button(LinearLayout root,String s,View.OnClickListener l){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setOnClickListener(l);root.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
    private void title(LinearLayout r,String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(25);t.setTextColor(Color.BLACK);r.addView(t);}private void section(LinearLayout r,String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(20);t.setTextColor(Color.rgb(90,30,120));t.setPadding(0,dp(18),0,dp(4));r.addView(t);}private void note(LinearLayout r,String s){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.DKGRAY);t.setPadding(0,dp(5),0,dp(5));r.addView(t);}private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
}
