package com.zoeykl.simpleboard;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Build;
import android.os.IBinder;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.inputmethodservice.InputMethodService;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SimpleBoardService extends InputMethodService implements SimpleKeyboardView.Listener, SuggestionStrip.Listener {
    private SimpleKeyboardView keyboard;
    private SuggestionStrip suggestions;
    private WordStore store;
    private AutocorrectEngine autocorrect;
    private EditorInfo editor;
    private boolean shifted;
    private boolean capsLocked;
    private long lastShiftTap;
    private boolean secureField;
    private int layoutMode=0; // 0 alpha, 1 symbols, 2 number, 3 phone, 4 emoji
    private long lastSpaceTime;
    private String lastAutoOriginal,lastAutoCorrected;
    private long lastAutoTime;
    private SpeechRecognizer recognizer;
    private boolean voiceComposing;

    @Override public void onCreate(){super.onCreate();store=new WordStore(this);autocorrect=new AutocorrectEngine(this,store);}

    @Override public View onCreateInputView(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        suggestions=new SuggestionStrip(this);suggestions.setListener(this);
        keyboard=new SimpleKeyboardView(this);keyboard.setListener(this);
        root.addView(suggestions,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(keyboard,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
        refreshUi();return root;
    }

    @Override public void onStartInput(EditorInfo attribute,boolean restarting){
        super.onStartInput(attribute,restarting);editor=attribute;secureField=isSecure(attribute==null?0:attribute.inputType);shifted=false;capsLocked=false;lastShiftTap=0;voiceComposing=false;lastAutoOriginal=null;lastAutoCorrected=null;
        int cls=attribute==null?InputType.TYPE_CLASS_TEXT:(attribute.inputType&InputType.TYPE_MASK_CLASS);
        if(cls==InputType.TYPE_CLASS_PHONE)layoutMode=3;else if(cls==InputType.TYPE_CLASS_NUMBER||cls==InputType.TYPE_CLASS_DATETIME)layoutMode=2;else layoutMode=0;
        updateAutoShift();refreshUi();
    }

    @Override public void onStartInputView(EditorInfo info,boolean restarting){super.onStartInputView(info,restarting);editor=info;if(keyboard!=null)keyboard.refreshPrefs();refreshUi();updateSuggestions();}
    @Override public void onFinishInput(){super.onFinishInput();stopVoice();}
    @Override public void onDestroy(){stopVoice();if(store!=null)store.close();super.onDestroy();}

    private void refreshUi(){
        if(keyboard!=null){switch(layoutMode){case 1:keyboard.setRows(LayoutFactory.symbols(this));break;case 2:keyboard.setRows(LayoutFactory.numpad(this,false));break;case 3:keyboard.setRows(LayoutFactory.numpad(this,true));break;case 4:keyboard.setRows(LayoutFactory.emoji(this));break;default:keyboard.setRows(LayoutFactory.alphabet(this,shifted,capsLocked));break;}}
        if(suggestions!=null){suggestions.refresh();boolean suggestionWords=Prefs.b(this,"suggestions",true)&&!secureField&&(layoutMode==0);boolean stripVoice=Prefs.b(this,"voice_enabled",true)&&Prefs.i(this,"voice_key_location",0)==1&&!secureField&&(layoutMode==0);boolean show=suggestionWords||stripVoice;suggestions.setVisibility(show?View.VISIBLE:View.GONE);if(!suggestionWords)suggestions.setSuggestions(new ArrayList<String>());}
    }

    @Override public void onKey(KeySpec key){
        feedback();
        switch(key.action){
            case TEXT: handleText(key.output); break;
            case SPACE: handleSpace(); break;
            case DELETE: handleDelete(); break;
            case ENTER: handleEnter(); break;
            case SHIFT:{long now=System.currentTimeMillis();if(now-lastShiftTap<450){capsLocked=!capsLocked;shifted=capsLocked;}else if(capsLocked){capsLocked=false;shifted=false;}else shifted=!shifted;lastShiftTap=now;refreshUi();break;}
            case SYMBOLS:layoutMode=1;shifted=false;refreshUi();break;
            case ALPHA:layoutMode=0;updateAutoShift();refreshUi();break;
            case NUMPAD:layoutMode=2;refreshUi();break;
            case EMOJI:layoutMode=layoutMode==4?0:4;refreshUi();break;
            case VOICE:startVoice();break;
            case SETTINGS:{Intent i=new Intent(this,SettingsActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);break;}
            case LEFT:sendKey(KeyEvent.KEYCODE_DPAD_LEFT);break;
            case RIGHT:sendKey(KeyEvent.KEYCODE_DPAD_RIGHT);break;
            case PASTE:pasteClipboard();break;
            default:break;
        }
    }

    @Override public void onPopupText(String text){
        feedback();if(text==null)return;
        if(text.startsWith("@")){
            if("@numpad".equals(text)){layoutMode=2;refreshUi();return;}
            if("@settings".equals(text)){Intent i=new Intent(this,SettingsActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);return;}
            if("@voice".equals(text)){startVoice();return;}
            if("@paste".equals(text)){pasteClipboard();return;}
            if("@left".equals(text)){sendKey(KeyEvent.KEYCODE_DPAD_LEFT);return;}
            if("@right".equals(text)){sendKey(KeyEvent.KEYCODE_DPAD_RIGHT);return;}
            if("@emoji".equals(text)){layoutMode=4;refreshUi();return;}
            if("@enter".equals(text)){handleEnter();return;}
            if("@tab".equals(text)){sendKey(KeyEvent.KEYCODE_TAB);return;}
        }
        InputConnection ic=getCurrentInputConnection();if(ic!=null){ic.commitText(text,1);if(isWordish(text))updateSuggestions();else{shifted=false;refreshUi();updateSuggestions();}}
    }

    private void handleText(String text){
        InputConnection ic=getCurrentInputConnection();if(ic==null||text==null)return;
        if(isWordish(text)){ic.commitText(text,1);if(shifted&&!capsLocked){shifted=false;updateAutoShift();refreshUi();}updateSuggestions();}
        else{finishCurrentWord(true);ic.commitText(text,1);if(".".equals(text)||"!".equals(text)||"?".equals(text))updateAutoShift();refreshUi();updateSuggestions();}
    }

    private void handleSpace(){
        InputConnection ic=getCurrentInputConnection();if(ic==null)return;long now=System.currentTimeMillis();CharSequence before=ic.getTextBeforeCursor(4,0);
        if(Prefs.b(this,"double_space_period",true)&&now-lastSpaceTime<700&&before!=null&&before.length()>=2&&before.charAt(before.length()-1)==' '&&Character.isLetterOrDigit(before.charAt(before.length()-2))){ic.deleteSurroundingText(1,0);ic.commitText(". ",1);lastSpaceTime=0;updateAutoShift();refreshUi();updateSuggestions();return;}
        finishCurrentWord(true);ic.commitText(" ",1);lastSpaceTime=now;updateAutoShift();refreshUi();updateSuggestions();
    }

    private void handleEnter(){finishCurrentWord(true);InputConnection ic=getCurrentInputConnection();if(ic==null)return;if(!sendDefaultEditorAction(true))sendKey(KeyEvent.KEYCODE_ENTER);updateAutoShift();refreshUi();updateSuggestions();}

    private void handleDelete(){
        InputConnection ic=getCurrentInputConnection();if(ic==null)return;CharSequence selected=ic.getSelectedText(0);if(selected!=null&&selected.length()>0){ic.commitText("",1);lastSpaceTime=0;updateAutoShift();refreshUi();updateSuggestions();return;}
        if(lastAutoCorrected!=null&&System.currentTimeMillis()-lastAutoTime<5000){CharSequence before=ic.getTextBeforeCursor(lastAutoCorrected.length()+2,0);String suffix=lastAutoCorrected+" ";if(before!=null&&before.toString().endsWith(suffix)){ic.deleteSurroundingTextInCodePoints(suffix.codePointCount(0,suffix.length()),0);ic.commitText(lastAutoOriginal,1);store.addWord(lastAutoOriginal);lastAutoCorrected=null;lastAutoOriginal=null;updateSuggestions();return;}}
        ic.deleteSurroundingTextInCodePoints(1,0);lastSpaceTime=0;updateAutoShift();refreshUi();updateSuggestions();
    }

    private void finishCurrentWord(boolean allowAutocorrect){
        if(secureField)return;String word=currentWord();if(word.isEmpty())return;String corrected=allowAutocorrect?autocorrect.correction(word):null;
        if(corrected!=null&&!corrected.equals(word)){replaceCurrentWord(word,corrected);autocorrect.learn(corrected);lastAutoOriginal=word;lastAutoCorrected=corrected;lastAutoTime=System.currentTimeMillis();}
        else autocorrect.learn(word);
    }

    private String currentWord(){
        InputConnection ic=getCurrentInputConnection();if(ic==null)return "";CharSequence cs=ic.getTextBeforeCursor(80,0);if(cs==null)return "";String s=cs.toString();int i=s.length();while(i>0){int cp=s.codePointBefore(i);if(Character.isLetterOrDigit(cp)||cp=='\''||cp=='-')i-=Character.charCount(cp);else break;}return s.substring(i);
    }
    private void replaceCurrentWord(String old,String replacement){InputConnection ic=getCurrentInputConnection();if(ic==null)return;ic.deleteSurroundingTextInCodePoints(old.codePointCount(0,old.length()),0);ic.commitText(replacement,1);}

    @Override public void onSuggestion(String text){String old=currentWord();if(old.isEmpty()||text==null||text.isEmpty())return;replaceCurrentWord(old,text);autocorrect.manualCorrection(old,text);updateSuggestions();}
    @Override public void onVoice(){startVoice();}
    private void updateSuggestions(){if(suggestions==null||suggestions.getVisibility()!=View.VISIBLE)return;String w=currentWord();suggestions.setSuggestions(w.isEmpty()?new ArrayList<String>():autocorrect.suggestions(w));}

    private void updateAutoShift(){
        if(capsLocked){shifted=true;return;}if(layoutMode!=0){shifted=false;return;}if(!Prefs.b(this,"auto_caps",true)){shifted=false;return;}InputConnection ic=getCurrentInputConnection();if(ic==null)return;int caps=ic.getCursorCapsMode(TextUtils.CAP_MODE_SENTENCES);shifted=caps!=0;
    }
    private boolean isSecure(int inputType){int cls=inputType&InputType.TYPE_MASK_CLASS;int var=inputType&InputType.TYPE_MASK_VARIATION;if(cls==InputType.TYPE_CLASS_NUMBER)return var==InputType.TYPE_NUMBER_VARIATION_PASSWORD;if(cls!=InputType.TYPE_CLASS_TEXT)return false;return var==InputType.TYPE_TEXT_VARIATION_PASSWORD||var==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD||var==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;}
    private boolean isWordish(String s){if(s==null||s.isEmpty())return false;int cp=s.codePointAt(0);return Character.isLetterOrDigit(cp)||cp=='\''||cp=='-';}

    private void feedback(){if(keyboard!=null&&Prefs.b(this,"vibrate",true))keyboard.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);if(Prefs.b(this,"sound",false)){AudioManager a=(AudioManager)getSystemService(Context.AUDIO_SERVICE);if(a!=null)a.playSoundEffect(SoundEffectConstants.CLICK,1f);}}
    private void sendKey(int code){InputConnection ic=getCurrentInputConnection();if(ic==null)return;long t=android.os.SystemClock.uptimeMillis();ic.sendKeyEvent(new KeyEvent(t,t,KeyEvent.ACTION_DOWN,code,0));ic.sendKeyEvent(new KeyEvent(t,t,KeyEvent.ACTION_UP,code,0));}
    private void pasteClipboard(){android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(cm!=null&&cm.hasPrimaryClip()&&cm.getPrimaryClip()!=null&&cm.getPrimaryClip().getItemCount()>0){CharSequence s=cm.getPrimaryClip().getItemAt(0).coerceToText(this);InputConnection ic=getCurrentInputConnection();if(ic!=null&&s!=null)ic.commitText(s,1);}}

    private void startVoice(){
        if(!Prefs.b(this,"voice_enabled",true)){toast("Voice key is disabled in SimpleBoard settings");return;}
        int mode=Prefs.i(this,"voice_implementation",0); // 0 auto, 1 external IME, 2 inline recognizer
        if(mode==0){
            String id=resolveExternalVoiceIme(true);
            if(id!=null&&switchToExternalIme(id))return;
            startInlineVoice();
            return;
        }
        if(mode==1){
            String id=resolveExternalVoiceIme(false);
            if(id==null){toast("No external voice keyboard is selected or enabled");openVoiceSettings();return;}
            if(!switchToExternalIme(id)){toast("Could not switch to the selected voice keyboard");openVoiceSettings();}
            return;
        }
        startInlineVoice();
    }

    private void startInlineVoice(){
        if(!SpeechRecognizer.isRecognitionAvailable(this)){toast("Android reports no inline speech recognizer");openVoiceSettings();return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){toast("Inline voice typing needs SimpleBoard microphone permission");openVoiceSettings();return;}
        stopVoice();
        try{
            String configured=Prefs.s(this,"voice_provider","");
            if(!configured.isEmpty()){
                ComponentName c=ComponentName.unflattenFromString(configured);
                if(c==null)throw new IllegalArgumentException("Invalid recognition provider");
                recognizer=SpeechRecognizer.createSpeechRecognizer(this,c);
            }else recognizer=SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new VoiceListener());
            Intent in=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            in.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            in.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,Prefs.i(this,"voice_max_results",3));
            if(Prefs.b(this,"voice_partial",true))in.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
            if(Prefs.b(this,"voice_offline",false))in.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
            String lang=voiceLanguage();if(lang!=null&&!lang.isEmpty())in.putExtra(RecognizerIntent.EXTRA_LANGUAGE,lang);
            recognizer.startListening(in);
        }catch(Exception e){stopVoice();toast("Voice recognizer unavailable: "+safeMessage(e));openVoiceSettings();}
    }

    private String resolveExternalVoiceIme(boolean voiceOnly){
        InputMethodManager imm=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(imm==null)return null;
        List<InputMethodInfo> infos=imm.getEnabledInputMethodList();
        String configured=Prefs.s(this,"voice_external_ime","");
        if(!configured.isEmpty())for(InputMethodInfo info:infos)if(configured.equals(info.getId()))return configured;
        for(InputMethodInfo info:infos){
            if(info==null||getPackageName().equals(info.getPackageName()))continue;
            if(!voiceOnly||isLikelyVoiceIme(info))return info.getId();
        }
        return null;
    }

    private boolean isLikelyVoiceIme(InputMethodInfo info){
        CharSequence lab=info.loadLabel(getPackageManager());
        String hay=((lab==null?"":lab.toString())+" "+info.getId()+" "+info.getPackageName()).toLowerCase(Locale.US);
        if(hay.contains("voice")||hay.contains("speech")||hay.contains("dictat"))return true;
        for(int n=0;n<info.getSubtypeCount();n++){InputMethodSubtype st=info.getSubtypeAt(n);if(st!=null&&"voice".equalsIgnoreCase(st.getMode()))return true;}
        return false;
    }

    @SuppressWarnings("deprecation")
    private boolean switchToExternalIme(String id){
        try{
            if(Build.VERSION.SDK_INT>=28){switchInputMethod(id);return true;}
            InputMethodManager imm=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(imm==null)return false;
            IBinder token=getWindow()!=null&&getWindow().getWindow()!=null?getWindow().getWindow().getAttributes().token:null;
            if(token==null)return false;
            imm.setInputMethod(token,id);return true;
        }catch(Exception e){return false;}
    }

    private void openVoiceSettings(){Intent i=new Intent(this,VoiceSettingsActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);}
    private String safeMessage(Exception e){String m=e.getMessage();return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m;}
    private String voiceLanguage(){String mode=Prefs.s(this,"voice_language_mode","system");if("explicit".equals(mode))return Prefs.s(this,"voice_language_tag","en-US");if("keyboard".equals(mode))return "en-US";return Locale.getDefault().toLanguageTag();}
    private void stopVoice(){if(recognizer!=null){try{recognizer.cancel();recognizer.destroy();}catch(Exception ignored){}recognizer=null;}voiceComposing=false;}
    private final class VoiceListener implements RecognitionListener {
        public void onReadyForSpeech(Bundle b){toast("Listening…");}public void onBeginningOfSpeech(){}public void onRmsChanged(float r){}public void onBufferReceived(byte[] b){}public void onEndOfSpeech(){}
        public void onError(int error){voiceComposing=false;toast("Voice recognition: "+voiceErrorName(error));stopVoice();}
        public void onResults(Bundle b){ArrayList<String> r=b==null?null:b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);InputConnection ic=getCurrentInputConnection();if(ic!=null){if(r!=null&&!r.isEmpty()){String s=r.get(0);ic.commitText(s,1);if(!s.endsWith(" "))ic.commitText(" ",1);}else if(voiceComposing)ic.finishComposingText();}voiceComposing=false;if(recognizer!=null){try{recognizer.destroy();}catch(Exception ignored){}recognizer=null;}updateSuggestions();}
        public void onPartialResults(Bundle b){if(!Prefs.b(SimpleBoardService.this,"voice_partial",true))return;ArrayList<String> r=b==null?null:b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);InputConnection ic=getCurrentInputConnection();if(ic!=null&&r!=null&&!r.isEmpty()){ic.setComposingText(r.get(0),1);voiceComposing=true;}}
        public void onEvent(int eventType,Bundle params){}
    }

    private String voiceErrorName(int error){
        switch(error){
            case SpeechRecognizer.ERROR_AUDIO:return "audio error";
            case SpeechRecognizer.ERROR_CLIENT:return "client error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:return "microphone permission denied";
            case SpeechRecognizer.ERROR_NETWORK:return "network error";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:return "network timeout";
            case SpeechRecognizer.ERROR_NO_MATCH:return "nothing recognized";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:return "recognizer busy";
            case SpeechRecognizer.ERROR_SERVER:return "recognition service error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:return "no speech heard";
            default:return "error "+error;
        }
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
