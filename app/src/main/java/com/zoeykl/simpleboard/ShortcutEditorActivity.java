package com.zoeykl.simpleboard;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ShortcutEditorActivity extends Activity {
    private EditText keyField,popupField;
    private LinearLayout list;
    @Override protected void onCreate(Bundle b){super.onCreate(b);build();}
    private void build(){
        ScrollView sc=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(12),dp(16),dp(24));sc.addView(root);UiInsets.apply(sc);setContentView(sc);
        title(root,"Key-hold shortcuts / popup editor");
        note(root,"Tap a key below or type its visible label. Separate choices with |. Example: key S, popup 🙂|😏|💀. Built-in actions can be mixed in too: @numpad, @settings, @voice, @paste, @left, @right, @emoji, @enter, @tab. Saving an empty popup disables the built-in popup for that key.");
        keyField=field(root,"Key label","s");popupField=field(root,"Popup entries","🙂");
        section(root,"Tap a key to edit");
        pickerRow(root,new String[]{"q","w","e","r","t","y","u","i","o","p"});
        pickerRow(root,new String[]{"a","s","d","f","g","h","j","k","l"});
        pickerRow(root,new String[]{"z","x","c","v","b","n","m"});
        pickerRow(root,new String[]{"?123",",",".","space","🎤","↵"});
        pickerRow(root,new String[]{"1","2","3","4","5","6","7","8","9","0","@","#","$","%","&","*","-","+","!","?"});
        Button save=button(root,"Save popup",v->{String k=keyField.getText().toString();String p=popupField.getText().toString();if(k.trim().isEmpty()){toast("Enter a key label");return;}Prefs.p(this).edit().putString(Prefs.popupKey(k),p).apply();toast("Saved");refreshList();});
        button(root,"Delete popup for this key",v->{String k=keyField.getText().toString();Prefs.p(this).edit().remove(Prefs.popupKey(k)).apply();toast("Deleted");refreshList();});
        section(root,"Current custom popups");list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list,new LinearLayout.LayoutParams(-1,-2));refreshList();
    }

    private void pickerRow(LinearLayout root,String[] keys){
        HorizontalScrollView hs=new HorizontalScrollView(this);LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);hs.addView(row);
        for(String k:keys){Button b=new Button(this);b.setText(k);b.setAllCaps(false);b.setMinWidth(dp(44));b.setPadding(dp(7),0,dp(7),0);b.setOnClickListener(v->selectKey(k));row.addView(b,new LinearLayout.LayoutParams(-2,dp(46)));}
        root.addView(hs,new LinearLayout.LayoutParams(-1,-2));
    }
    private void selectKey(String k){keyField.setText(k);String pk=Prefs.popupKey(k);String v=Prefs.p(this).contains(pk)?Prefs.s(this,pk,""):builtIn(k);popupField.setText(v);popupField.requestFocus();popupField.setSelection(popupField.length());}
    private String builtIn(String label){String l=label.toLowerCase(java.util.Locale.US);if("a".equals(l))return "á|à|â|ä|ã|å|æ";if("e".equals(l))return "é|è|ê|ë";if("i".equals(l))return "í|ì|î|ï";if("o".equals(l))return "ó|ò|ô|ö|õ|ø|œ";if("u".equals(l))return "ú|ù|û|ü";if("c".equals(l))return "ç";if("n".equals(l))return "ñ";if("y".equals(l))return "ÿ";if("?123".equals(label))return "@numpad";if(",".equals(label))return "@settings";if(".".equals(label))return "?|!|…";return "";}

    private void refreshList(){if(list==null)return;list.removeAllViews();List<String> rows=new ArrayList<>();for(Map.Entry<String,?> e:Prefs.p(this).getAll().entrySet())if(e.getKey().startsWith("popup_")&&e.getValue() instanceof String&&!((String)e.getValue()).isEmpty())rows.add(e.getKey().substring(6)+"  →  "+e.getValue());Collections.sort(rows);if(rows.isEmpty())note(list,"No custom popups yet.");for(String s:rows){TextView t=new TextView(this);t.setText(s);t.setTextSize(16);t.setTextColor(Color.DKGRAY);t.setPadding(0,dp(7),0,dp(7));t.setOnClickListener(v->{String line=((TextView)v).getText().toString();int x=line.indexOf("  →  ");if(x>0){keyField.setText(line.substring(0,x));popupField.setText(line.substring(x+5));}});list.addView(t,new LinearLayout.LayoutParams(-1,-2));}}
    private EditText field(LinearLayout root,String label,String hint){TextView l=new TextView(this);l.setText(label);l.setTextColor(Color.DKGRAY);l.setPadding(0,dp(10),0,0);root.addView(l);EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);root.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    private Button button(LinearLayout root,String text,View.OnClickListener l){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setOnClickListener(l);root.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
    private void title(LinearLayout root,String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(25);t.setTextColor(Color.BLACK);root.addView(t);}
    private void section(LinearLayout root,String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(20);t.setTextColor(Color.rgb(90,30,120));t.setPadding(0,dp(18),0,dp(4));root.addView(t);}
    private void note(LinearLayout root,String s){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.DKGRAY);t.setPadding(0,dp(6),0,dp(6));root.addView(t);}
    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
