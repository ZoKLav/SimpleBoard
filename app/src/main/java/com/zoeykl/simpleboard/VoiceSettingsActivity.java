package com.zoeykl.simpleboard;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.widget.*;
import java.util.List;

public final class VoiceSettingsActivity extends SettingsPageActivity {
    private static final int MODEL_FOLDER=78;
    private final Handler main=new Handler(Looper.getMainLooper());
    private TextView modelStatus,folderStatus;
    private Button scanButton,setupButton,cancelButton;
    private final Runnable refresh=new Runnable(){public void run(){updateStatus();main.postDelayed(this,750);}};
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);initPage(getString(R.string.nav_voice),getString(R.string.voice_intro));
        check(getString(R.string.voice_enable),"voice_enabled",true);
        section(getString(R.string.voice_models));text(getString(R.string.folder_intro));
        setupButton=button(getString(R.string.folder_choose),v->new AlertDialog.Builder(this)
            .setMessage(R.string.folder_help).setNegativeButton(R.string.cancel,null)
            .setPositiveButton(R.string.continue_action,(d,w)->pickFolder()).show());
        scanButton=button(getString(R.string.folder_scan),v->SharedVoiceFolder.scan(this));
        folderStatus=new TextView(this);root.addView(folderStatus,wide());
        cancelButton=button(getString(R.string.cancel),v->SharedVoiceFolder.cancel());
        modelStatus=new TextView(this);root.addView(modelStatus,wide());
        button(getString(R.string.model_choose),v->chooseModel());
        button(getString(R.string.model_remove),v->removeModel());
        section(getString(R.string.download_models));text(getString(R.string.download_help));
        for(WhisperCatalog.Model model:WhisperCatalog.MODELS)button(model.label,v->openBrowser(model.url()));
        section(getString(R.string.voice_language));
        final String[] codes={"auto","en","zh","hi","es","ar"};
        final String[] labels={getString(R.string.language_auto),"English","中文","हिन्दी","Español","العربية"};
        Spinner language=new Spinner(this);language.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));
        int selected=0;for(int i=0;i<codes.length;i++)if(codes[i].equals(Prefs.s(this,"whisper_language","auto")))selected=i;
        language.setSelection(selected);language.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,android.view.View v,int index,long id){Prefs.p(VoiceSettingsActivity.this).edit().putString("whisper_language",codes[index]).apply();}
            public void onNothingSelected(android.widget.AdapterView<?> p){}
        });root.addView(language,wide());
        text(getString(R.string.voice_note));text(getString(R.string.voice_controls));
        section(getString(R.string.microphone));text(getString(R.string.mic_help));
        button(getString(R.string.mic_grant),v->requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},77));
        button(getString(R.string.app_permissions),v->startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        updateStatus();
    }
    @Override protected void onResume(){super.onResume();main.removeCallbacks(refresh);main.post(refresh);}
    @Override protected void onPause(){main.removeCallbacks(refresh);super.onPause();}
    private void updateStatus(){
        if(modelStatus==null)return;String name=getString(R.string.model_choose),id=Prefs.s(this,SpeechModels.PREFERENCE,SpeechModels.BUILTIN);
        for(SpeechModels.Entry entry:SpeechModels.list(this))if(entry.id.equals(id))name=entry.name;
        modelStatus.setText(getString(R.string.model_selected)+name);
        folderStatus.setText(SharedVoiceFolder.status()==R.string.folder_intro&&SharedVoiceFolder.configured(this)?R.string.folder_ready:SharedVoiceFolder.status());
        boolean busy=SharedVoiceFolder.busy();scanButton.setEnabled(!busy&&SharedVoiceFolder.configured(this));setupButton.setEnabled(!busy);cancelButton.setEnabled(busy);
    }
    private void pickFolder(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        if(android.os.Build.VERSION.SDK_INT>=26)intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,DocumentsContract.buildDocumentUri("com.android.externalstorage.documents","primary:Documents"));
        try{startActivityForResult(intent,MODEL_FOLDER);}catch(android.content.ActivityNotFoundException e){Toast.makeText(this,R.string.no_browser,Toast.LENGTH_LONG).show();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==MODEL_FOLDER&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri tree=data.getData();
            try{
                if(!"com.android.externalstorage.documents".equals(tree.getAuthority())||!"primary:Documents/SimpleBoard/VoiceModels".equals(DocumentsContract.getTreeDocumentId(tree)))throw new IllegalArgumentException();
                // Persist read access only. Folder creation and downloads remain in the system picker/browser.
                getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION);
                SharedVoiceFolder.configure(this,tree);
            }catch(Exception e){Toast.makeText(this,R.string.folder_setup_error,Toast.LENGTH_LONG).show();}
            updateStatus();
        }
    }
    private void openBrowser(String url){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));}
        catch(android.content.ActivityNotFoundException e){Toast.makeText(this,R.string.no_browser,Toast.LENGTH_LONG).show();}
    }
    private void chooseModel(){
        List<SpeechModels.Entry> entries=SpeechModels.list(this);String[] names=new String[entries.size()];int chosen=0;
        String current=Prefs.s(this,SpeechModels.PREFERENCE,SpeechModels.BUILTIN);
        for(int i=0;i<names.length;i++){names[i]=entries.get(i).name;if(entries.get(i).id.equals(current))chosen=i;}
        new AlertDialog.Builder(this).setTitle(R.string.model_choose).setSingleChoiceItems(names,chosen,(dialog,index)->{
            Prefs.p(this).edit().putString(SpeechModels.PREFERENCE,entries.get(index).id).apply();dialog.dismiss();updateStatus();
        }).setNegativeButton(R.string.cancel,null).show();
    }
    private void removeModel(){
        List<SpeechModels.Entry> entries=SpeechModels.list(this);entries.remove(0);
        if(entries.isEmpty()){Toast.makeText(this,R.string.model_none,Toast.LENGTH_SHORT).show();return;}
        String[] names=new String[entries.size()];for(int i=0;i<names.length;i++)names[i]=entries.get(i).name;
        new AlertDialog.Builder(this).setTitle(R.string.model_remove).setItems(names,(dialog,index)->{
            SpeechModels.Entry entry=entries.get(index);
            new AlertDialog.Builder(this).setTitle(entry.name).setMessage(R.string.model_remove_info)
                .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.remove,(d,w)->{
                    try{SpeechModels.remove(this,entry);updateStatus();}catch(java.io.IOException e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
                }).show();
        }).setNegativeButton(R.string.cancel,null).show();
    }
}
