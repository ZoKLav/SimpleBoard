package com.zoeykl.simpleboard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class ThemeActivity extends Activity {
    private static final int EXPORT_SB=1,EXPORT_HB=2,IMPORT=3;
    @Override protected void onCreate(Bundle b){super.onCreate(b);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(14),dp(16),dp(24));UiInsets.apply(root);setContentView(root);title(root,"Theme import / export");note(root,"SimpleBoard .simpleboardtheme files preserve all visual sizing/popup settings and can include the board background image. HeliBoard JSON import/export maps the compatible color fields.");button(root,"Export SimpleBoard theme",v->create("application/zip","SimpleBoard.simpleboardtheme",EXPORT_SB));button(root,"Export HeliBoard-compatible colors JSON",v->create("application/json","SimpleBoard-HeliBoard.json",EXPORT_HB));button(root,"Import SimpleBoard theme or HeliBoard JSON",v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,IMPORT);});}
    private void create(String type,String name,int req){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType(type);i.putExtra(Intent.EXTRA_TITLE,name);i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,req);}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;try{if(req==EXPORT_SB)writeSimple(data.getData());else if(req==EXPORT_HB)writeText(data.getData(),ThemeConfig.read(this).toHeliBoardJson().toString(2));else if(req==IMPORT)importTheme(data.getData());}catch(Exception e){toast("Theme operation failed: "+e.getMessage());}}
    private void writeSimple(Uri uri)throws Exception{OutputStream raw=getContentResolver().openOutputStream(uri);ZipOutputStream z=new ZipOutputStream(raw);z.putNextEntry(new ZipEntry("theme.json"));byte[] j=ThemeConfig.read(this).toJson().toString(2).getBytes(StandardCharsets.UTF_8);z.write(j);z.closeEntry();String bg=Prefs.s(this,"background_path","");if(!bg.isEmpty()&&new File(bg).isFile()){z.putNextEntry(new ZipEntry("background.img"));FileInputStream in=new FileInputStream(bg);copy(in,z);in.close();z.closeEntry();}z.finish();z.close();toast("Theme exported");}
    private void writeText(Uri uri,String text)throws Exception{OutputStream out=getContentResolver().openOutputStream(uri);out.write(text.getBytes(StandardCharsets.UTF_8));out.close();toast("Theme exported");}
    private void importTheme(Uri uri)throws Exception{InputStream in=getContentResolver().openInputStream(uri);byte[] all=readAll(in);if(in!=null)in.close();if(all.length>=2&&all[0]=='P'&&all[1]=='K'){File temp=new File(getCacheDir(),"theme.zip");FileOutputStream f=new FileOutputStream(temp);f.write(all);f.close();ZipInputStream z=new ZipInputStream(new FileInputStream(temp));JSONObject json=null;ZipEntry e;while((e=z.getNextEntry())!=null){if("theme.json".equals(e.getName()))json=new JSONObject(new String(readAll(z),StandardCharsets.UTF_8));else if("background.img".equals(e.getName())){File bg=new File(getFilesDir(),"keyboard_background");FileOutputStream o=new FileOutputStream(bg);copy(z,o);o.close();Prefs.p(this).edit().putString("background_path",bg.getAbsolutePath()).apply();}z.closeEntry();}z.close();if(json==null)throw new Exception("No theme.json in archive");ThemeConfig.fromJson(this,json).apply(this);}else{JSONObject json=new JSONObject(new String(all,StandardCharsets.UTF_8));ThemeConfig.fromJson(this,json).apply(this);}toast("Theme imported");}
    private static byte[] readAll(InputStream in)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();if(in!=null)copy(in,b);return b.toByteArray();}private static void copy(InputStream in,OutputStream out)throws Exception{byte[] buf=new byte[32768];int n;while((n=in.read(buf))>0)out.write(buf,0,n);}
    private Button button(LinearLayout r,String s,View.OnClickListener l){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setOnClickListener(l);r.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}private void title(LinearLayout r,String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(25);t.setTextColor(Color.BLACK);r.addView(t);}private void note(LinearLayout r,String s){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.DKGRAY);t.setPadding(0,dp(7),0,dp(12));r.addView(t);}private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
