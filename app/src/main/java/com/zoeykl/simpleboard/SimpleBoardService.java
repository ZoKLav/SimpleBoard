package com.zoeykl.simpleboard;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.inputmethodservice.InputMethodService;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Locale;

public final class SimpleBoardService extends InputMethodService implements SimpleKeyboardView.Listener, SuggestionStrip.Listener {
    private SimpleKeyboardView keyboard;
    private SuggestionStrip suggestions;
    private WordStore store;
    private AutocorrectEngine autocorrect;
    private NeuralPredictions neural;
    private boolean shifted;
    private boolean capsLocked;
    private long lastShiftTap;
    private boolean secureField;
    private int layoutMode=0; // 0 alpha, 1 symbols, 2 number, 3 phone, 4 emoji
    private long lastSpaceTime;
    private String lastAutoOriginal,lastAutoCorrected;
    private long lastAutoTime;
    private OfflineSpeech speech;
    private boolean voiceActive,voiceStarted,voiceFinishing;
    private LinearLayout voicePanel;
    private android.widget.TextView voiceStatus;
    private int voiceSession;
    private boolean privateField;
    private String keepOriginal;
    private String pendingWord;
    private String[] pendingContext;
    private boolean pendingAccepted;
    private String pendingSnapshot;
    private final android.os.Handler suggestionHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.concurrent.ThreadPoolExecutor suggestionWorker=new java.util.concurrent.ThreadPoolExecutor(
        1,1,0,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.LinkedBlockingQueue<Runnable>(),
        r->new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);r.run();},"SimpleBoard-suggestions"));
    private long suggestionGeneration;
    private java.util.concurrent.Future<?> suggestionTask;
    private boolean serviceDestroyed;
    private final Runnable delayedSuggestions=this::computeSuggestions;
    private void cancelSuggestions(){
        suggestionGeneration++;suggestionHandler.removeCallbacks(delayedSuggestions);
        if(suggestionTask!=null){suggestionTask.cancel(false);suggestionTask=null;suggestionWorker.purge();}
    }



    @Override public void onCreate(){super.onCreate();store=new WordStore(this);autocorrect=new AutocorrectEngine(this,store);neural=new NeuralPredictions(this);speech=new OfflineSpeech(this);}

    @Override public View onCreateInputView(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        suggestions=new SuggestionStrip(this);suggestions.setListener(this);
        voicePanel=new LinearLayout(this);voicePanel.setOrientation(LinearLayout.VERTICAL);
        voicePanel.setBackgroundColor(Prefs.boardBg(this));voicePanel.setVisibility(View.GONE);
        voiceStatus=new android.widget.TextView(this);voiceStatus.setPadding(16,12,16,8);voiceStatus.setTextSize(16);
        voiceStatus.setTextColor(Prefs.textColor(this));voiceStatus.setMaxLines(4);
        voicePanel.addView(voiceStatus,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout voiceActions=new LinearLayout(this);
        android.widget.Button done=new android.widget.Button(this);done.setText(R.string.done);done.setOnClickListener(v->finishVoice());
        android.widget.Button cancel=new android.widget.Button(this);cancel.setText(R.string.cancel);cancel.setOnClickListener(v->{stopVoice();updateSuggestions();});
        voiceActions.addView(done,new LinearLayout.LayoutParams(0,-2,1));voiceActions.addView(cancel,new LinearLayout.LayoutParams(0,-2,1));
        voicePanel.addView(voiceActions);root.addView(voicePanel,new LinearLayout.LayoutParams(-1,-2));
        keyboard=new SimpleKeyboardView(this);keyboard.setListener(this);
        root.addView(suggestions,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(keyboard,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
        refreshUi();return root;
    }

    @Override public void onStartInput(EditorInfo attribute,boolean restarting){
        cancelSuggestions();flushLearning();if(neural!=null)neural.reset();stopVoice();super.onStartInput(attribute,restarting);secureField=isSecure(attribute==null?0:attribute.inputType);shifted=false;capsLocked=false;lastShiftTap=0;keepOriginal=null;lastSpaceTime=0;privateField=isPrivate(attribute);lastAutoOriginal=null;lastAutoCorrected=null;
        int cls=attribute==null?InputType.TYPE_CLASS_TEXT:(attribute.inputType&InputType.TYPE_MASK_CLASS);
        if(cls==InputType.TYPE_CLASS_PHONE)layoutMode=3;else if(cls==InputType.TYPE_CLASS_NUMBER||cls==InputType.TYPE_CLASS_DATETIME)layoutMode=2;else layoutMode=0;
        updateAutoShift();refreshUi();
    }

    @Override public void onStartInputView(EditorInfo info,boolean restarting){super.onStartInputView(info,restarting);if(keyboard!=null)keyboard.refreshPrefs();refreshUi();updateSuggestions();}
    @Override public void onFinishInputView(boolean finishingInput){cancelSuggestions();if(neural!=null)neural.idle();flushLearning();stopVoice();super.onFinishInputView(finishingInput);}
    @Override public void onFinishInput(){cancelSuggestions();if(neural!=null)neural.idle();flushLearning();super.onFinishInput();stopVoice();}
    @Override public void onDestroy(){serviceDestroyed=true;cancelSuggestions();if(neural!=null)neural.close();discardLearning();stopVoice();if(speech!=null)speech.close();suggestionWorker.execute(()->{if(store!=null)store.close();});suggestionWorker.shutdown();super.onDestroy();}

    private void refreshUi(){
        if(keyboard!=null){switch(layoutMode){case 1:keyboard.setRows(LayoutFactory.symbols(this));break;case 2:keyboard.setRows(LayoutFactory.numpad(this,false));break;case 3:keyboard.setRows(LayoutFactory.numpad(this,true));break;case 4:keyboard.setRows(LayoutFactory.emoji(this));break;default:keyboard.setRows(LayoutFactory.alphabet(this,shifted,capsLocked));break;}}
        if(suggestions!=null){suggestions.refresh();boolean suggestionWords=Prefs.b(this,"suggestions",true)&&!privateField&&(layoutMode==0);boolean stripVoice=Prefs.b(this,"voice_enabled",true)&&!secureField&&(layoutMode==0);boolean show=suggestionWords||stripVoice;suggestions.setVisibility(show?View.VISIBLE:View.GONE);if(!suggestionWords)suggestions.setSuggestions(new ArrayList<String>());}
    }

    @Override public void onKey(KeySpec key){
        cancelSuggestions();if(neural!=null)neural.typing();
        if(key.action!=KeySpec.Action.VOICE)stopVoice();
        feedback();
        switch(key.action){
            case TEXT: handleText(key.output); break;
            case SPACE: handleSpace(); break;
            case DELETE: handleDelete(); break;
            case ENTER: handleEnter(); break;
            case SHIFT:{long now=System.currentTimeMillis();if(now-lastShiftTap<450){capsLocked=!capsLocked;shifted=capsLocked;}else if(capsLocked){capsLocked=false;shifted=false;}else shifted=!shifted;lastShiftTap=now;refreshUi();updateSuggestions();break;}
            case SYMBOLS:layoutMode=1;shifted=false;refreshUi();break;
            case ALPHA:layoutMode=0;updateAutoShift();refreshUi();updateSuggestions();break;
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
        cancelSuggestions();if(neural!=null)neural.typing();stopVoice();feedback();if(text==null)return;
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
        handleText(text);
    }

    private void handleText(String text){
        InputConnection ic=getCurrentInputConnection();if(ic==null||text==null)return;
        if(isWordish(text)){flushLearning();ic.commitText(text,1);if(shifted&&!capsLocked){shifted=false;updateAutoShift();refreshUi();}updateSuggestions();}
        else{flushLearning();finishCurrentWord(true);ic.commitText(text,1);sealLearning();if(".".equals(text)||"!".equals(text)||"?".equals(text))updateAutoShift();refreshUi();updateSuggestions();}
    }

    private void handleSpace(){
        flushLearning();
        InputConnection ic=getCurrentInputConnection();if(ic==null)return;long now=System.currentTimeMillis();CharSequence before=ic.getTextBeforeCursor(4,0);
        if(Prefs.b(this,"double_space_period",true)&&now-lastSpaceTime<700&&before!=null&&before.length()>=2&&before.charAt(before.length()-1)==' '&&Character.isLetterOrDigit(before.charAt(before.length()-2))){ic.deleteSurroundingText(1,0);ic.commitText(". ",1);lastSpaceTime=0;sealLearning();updateAutoShift();refreshUi();updateSuggestions();return;}
        finishCurrentWord(true);ic.commitText(" ",1);sealLearning();lastSpaceTime=now;updateAutoShift();refreshUi();updateSuggestions();
    }

    private void handleEnter(){flushLearning();finishCurrentWord(true);sealLearning();flushLearning();InputConnection ic=getCurrentInputConnection();if(ic==null)return;if(!sendDefaultEditorAction(true))sendKey(KeyEvent.KEYCODE_ENTER);updateAutoShift();refreshUi();updateSuggestions();}

    private void handleDelete(){
        discardLearning();
        InputConnection ic=getCurrentInputConnection();if(ic==null)return;CharSequence selected=ic.getSelectedText(0);if(selected!=null&&selected.length()>0){ic.commitText("",1);lastSpaceTime=0;updateAutoShift();refreshUi();updateSuggestions();return;}
        if(lastAutoCorrected!=null&&System.currentTimeMillis()-lastAutoTime<5000){CharSequence before=ic.getTextBeforeCursor(lastAutoCorrected.length()+2,0);String suffix=lastAutoCorrected+" ";if(before!=null&&before.toString().endsWith(suffix)){ic.deleteSurroundingTextInCodePoints(suffix.codePointCount(0,suffix.length()),0);ic.commitText(lastAutoOriginal,1);autocorrect.reject(lastAutoOriginal);keepOriginal=lastAutoOriginal;lastAutoCorrected=null;lastAutoOriginal=null;lastSpaceTime=0;updateAutoShift();refreshUi();updateSuggestions();return;}}
        ic.deleteSurroundingTextInCodePoints(1,0);lastSpaceTime=0;updateAutoShift();refreshUi();updateSuggestions();
    }

    private void finishCurrentWord(boolean allowAutocorrect){
        if(privateField||!atWordEnd())return;String word=currentWord();if(word.isEmpty())return;boolean kept=word.equals(keepOriginal);keepOriginal=null;String corrected=allowAutocorrect&&!kept?autocorrect.correction(word):null;
        if(corrected!=null&&!corrected.equals(word)){replaceCurrentWord(word,corrected);lastAutoOriginal=word;lastAutoCorrected=corrected;lastAutoTime=System.currentTimeMillis();}
        else if(!kept)stageLearning(word,contextBefore(word),false);
    }

    private String currentWord(){
        InputConnection ic=getCurrentInputConnection();if(ic==null)return "";CharSequence selection=ic.getSelectedText(0);if(selection!=null&&selection.length()>0)return "";CharSequence cs=ic.getTextBeforeCursor(80,0);if(cs==null)return "";String s=cs.toString();int i=s.length();while(i>0){int cp=s.codePointBefore(i);if(Character.isLetterOrDigit(cp)||cp=='\''||cp=='-')i-=Character.charCount(cp);else break;}return s.substring(i);
    }
    private void replaceCurrentWord(String old,String replacement){InputConnection ic=getCurrentInputConnection();if(ic==null)return;ic.deleteSurroundingTextInCodePoints(old.codePointCount(0,old.length()),0);ic.commitText(replacement,1);}

    @Override public void onSuggestion(String text){
        InputConnection ic=getCurrentInputConnection();String old=currentWord();
        if(ic==null||privateField||!atWordEnd()||hasSelection()||text==null||text.isEmpty())return;
        if(old.isEmpty()&&!Prefs.b(this,"next_word_predictions",true))return;
        flushLearning();stopVoice();String[] previous=contextBefore(old);
        if(old.isEmpty()){
            CharSequence before=ic.getTextBeforeCursor(1,0);
            if(before!=null&&before.length()>0&&!Character.isWhitespace(before.charAt(0)))ic.commitText(" ",1);
            ic.commitText(text,1);
        }else replaceCurrentWord(old,text);
        stageLearning(text,previous,true);ic.commitText(" ",1);sealLearning();
        lastAutoCorrected=null;lastSpaceTime=0;updateAutoShift();refreshUi();updateSuggestions();
    }
    @Override public void onVoice(){startVoice();}
    private void updateSuggestions(){
        cancelSuggestions();
        if(!serviceDestroyed)suggestionHandler.postDelayed(delayedSuggestions,80);
    }
    private void computeSuggestions(){
        if(suggestions==null||suggestions.getVisibility()!=View.VISIBLE)return;
        if(privateField||layoutMode!=0||hasSelection()||!Prefs.b(this,"suggestions",true)||!atWordEnd()){
            if(neural!=null)neural.reset();suggestions.setSuggestions(new ArrayList<String>());return;
        }
        String w=currentWord();String[] previous=contextBefore(w);
        final long ticket=suggestionGeneration;
        final boolean capitalize=shifted,caps=capsLocked;
        suggestionTask=suggestionWorker.submit(()->{
            java.util.List<String> local=w.isEmpty()?autocorrect.predict(previous,capitalize,caps):autocorrect.suggestions(w,previous);
            suggestionHandler.post(()->{
                if(serviceDestroyed||ticket!=suggestionGeneration||privateField||voiceActive)return;
                suggestions.setSuggestions(local);
                requestNeuralSuggestions(w);
            });
        });
    }
    private void requestNeuralSuggestions(String w){
        if(neural==null)return;
        if(!Prefs.b(this,"next_word_predictions",true)||!Prefs.b(this,"neural_predictions",true)||voiceActive){neural.reset();return;}
        final String context=neuralContext(w);
        if(context.isEmpty()){neural.reset();return;}
        neural.request(context,scores->{
            if(privateField||layoutMode!=0||hasSelection()||!atWordEnd()||voiceActive||suggestions.getVisibility()!=View.VISIBLE||!Prefs.b(this,"suggestions",true)||!Prefs.b(this,"next_word_predictions",true)||!Prefs.b(this,"neural_predictions",true))return;
            String current=currentWord();if(!context.equals(neuralContext(current)))return;
            java.util.List<String> words=PredictionBlend.combine(scores,store.personalBias(contextBefore(current),current),store.blockedWords(),current,3);
            if(words.isEmpty())return;
            java.util.List<String> display=new java.util.ArrayList<>();if(!current.isEmpty())display.add(current);
            for(String word:words){
                if(display.size()>=3)break;
                if(capsLocked||(!current.isEmpty()&&current.length()>1&&current.equals(current.toUpperCase(Locale.US))))word=word.toUpperCase(Locale.US);
                else if((current.isEmpty()&&shifted)||(!current.isEmpty()&&Character.isUpperCase(current.charAt(0))))word=Character.toUpperCase(word.charAt(0))+word.substring(1);
                else if(word.equals("i")||word.startsWith("i'"))word="I"+word.substring(1);
                if(!display.contains(word))display.add(word);
            }
            // Preserve existing alternatives when the cached neural list has no useful completion.
            if(current.isEmpty()||display.size()>1)suggestions.setSuggestions(display);
        });
    }
    private String neuralContext(String current){String before=surroundingBefore();if(before==null)return "";if(!current.isEmpty()&&before.endsWith(current))before=before.substring(0,before.length()-current.length());return before.trim();}
    private boolean hasSelection(){InputConnection ic=getCurrentInputConnection();if(ic==null)return true;CharSequence selected=ic.getSelectedText(0);return selected!=null&&selected.length()>0;}
    private String surroundingBefore(){InputConnection ic=getCurrentInputConnection();if(ic==null)return null;CharSequence before=ic.getTextBeforeCursor(256,0);return before==null?null:before.toString();}
    private String[] contextBefore(String current){return LocalLanguageModel.context(surroundingBefore(),current);}
    private void stageLearning(String word,String[] previous,boolean accepted){
        pendingWord=word;pendingContext=previous;pendingAccepted=accepted;pendingSnapshot=null;
    }
    private void sealLearning(){if(pendingWord!=null)pendingSnapshot=surroundingBefore();}
    private void discardLearning(){pendingWord=null;pendingContext=null;pendingSnapshot=null;}
    private void flushLearning(){
        if(pendingWord!=null&&!privateField&&pendingSnapshot!=null&&pendingSnapshot.equals(surroundingBefore())) {
            final String word=pendingWord;final String[] previous=pendingContext.clone();final boolean accepted=pendingAccepted;
            suggestionWorker.execute(()->autocorrect.learn(word,previous,accepted));
        }
        discardLearning();
    }

    private void updateAutoShift(){
        if(capsLocked){shifted=true;return;}if(layoutMode!=0){shifted=false;return;}if(!Prefs.b(this,"auto_caps",true)){shifted=false;return;}InputConnection ic=getCurrentInputConnection();if(ic==null)return;int caps=ic.getCursorCapsMode(TextUtils.CAP_MODE_SENTENCES);shifted=caps!=0;
    }
    private boolean isSecure(int inputType){int cls=inputType&InputType.TYPE_MASK_CLASS;int var=inputType&InputType.TYPE_MASK_VARIATION;if(cls==InputType.TYPE_CLASS_NUMBER)return var==InputType.TYPE_NUMBER_VARIATION_PASSWORD;if(cls!=InputType.TYPE_CLASS_TEXT)return false;return var==InputType.TYPE_TEXT_VARIATION_PASSWORD||var==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD||var==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;}
    private boolean isWordish(String s){if(s==null||s.isEmpty())return false;int cp=s.codePointAt(0);return Character.isLetterOrDigit(cp)||cp=='\''||cp=='-';}

    private void feedback(){if(keyboard!=null&&Prefs.b(this,"vibrate",true))keyboard.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);if(Prefs.b(this,"sound",false)){AudioManager a=(AudioManager)getSystemService(Context.AUDIO_SERVICE);if(a!=null)a.playSoundEffect(SoundEffectConstants.CLICK,1f);}}
    private void sendKey(int code){InputConnection ic=getCurrentInputConnection();if(ic==null)return;long t=android.os.SystemClock.uptimeMillis();ic.sendKeyEvent(new KeyEvent(t,t,KeyEvent.ACTION_DOWN,code,0));ic.sendKeyEvent(new KeyEvent(t,t,KeyEvent.ACTION_UP,code,0));}
    private void pasteClipboard(){discardLearning();android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(cm!=null&&cm.hasPrimaryClip()&&cm.getPrimaryClip()!=null&&cm.getPrimaryClip().getItemCount()>0){CharSequence s=cm.getPrimaryClip().getItemAt(0).coerceToText(this);InputConnection ic=getCurrentInputConnection();if(ic!=null&&s!=null)ic.commitText(s,1);}}

    private void startVoice(){
        if(voiceActive){finishVoice();return;}
        cancelSuggestions();discardLearning();
        if(secureField){toast("Voice is disabled in password fields");return;}
        if(!Prefs.b(this,"voice_enabled",true)){toast("Voice key is disabled in settings");return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            toast("Grant microphone permission in Voice settings, then tap the microphone again");
            Intent settings=new Intent(this,VoiceSettingsActivity.class);settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(settings);return;
        }
        final InputConnection connection=getCurrentInputConnection();if(connection==null)return;
        final int session=++voiceSession;voiceActive=true;
        showVoiceStatus(getString(R.string.voice_preparing));
        // Release the prediction model before loading ASR to avoid holding both models in RAM.
        neural.suspendForVoice(()->{
            if(!voiceActive||session!=voiceSession)return;
            voiceStarted=true;
            speech.start(new OfflineSpeech.Listener(){
                private boolean active(){return voiceActive&&session==voiceSession;}
                public void status(String text){if(active())showVoiceStatus(text);}
                public void partial(String text){
                    if(active()&&!voiceFinishing&&Prefs.b(SimpleBoardService.this,"voice_partial",true))showVoiceStatus(text.isEmpty()?"Listening offline · Tap Done to insert":text);
                }
                public void result(String text){
                    if(!active())return;
                    stopVoice();
                    if(!text.isEmpty())connection.commitText(text+" ",1);else toast("No speech recognized. Check microphone access and try again.");
                    updateAutoShift();refreshUi();updateSuggestions();
                }
                public void error(String text){if(active()){stopVoice();toast(text);updateSuggestions();}}
            });
        });
    }
    private void finishVoice(){if(voiceActive){if(!voiceStarted){stopVoice();updateSuggestions();return;}voiceFinishing=true;speech.finish();showVoiceStatus(getString(R.string.voice_finishing));}}
    private void showVoiceStatus(String text){if(voiceStatus!=null){voiceStatus.setText(text);voicePanel.setVisibility(View.VISIBLE);}}
    private void stopVoice(){
        voiceSession++;voiceActive=false;voiceStarted=false;voiceFinishing=false;if(speech!=null)speech.cancel();
        if(voicePanel!=null){voicePanel.setVisibility(View.GONE);voiceStatus.setText("");}
    }
    private boolean atWordEnd(){InputConnection ic=getCurrentInputConnection();if(ic==null)return false;CharSequence after=ic.getTextAfterCursor(1,0);return after==null||after.length()==0||!isWordish(after.toString());}
    private boolean isPrivate(EditorInfo info){
        if(info==null)return true;
        if(isSecure(info.inputType)||(info.imeOptions&EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)!=0)return true;
        int type=info.inputType;
        if((type&InputType.TYPE_MASK_CLASS)!=InputType.TYPE_CLASS_TEXT)return true;
        int variation=type&InputType.TYPE_MASK_VARIATION;
        return (type&InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)!=0||variation==InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS||variation==InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS||variation==InputType.TYPE_TEXT_VARIATION_URI;
    }
    @Override public void onUpdateSelection(int oldStart,int oldEnd,int newStart,int newEnd,int candidatesStart,int candidatesEnd){
        super.onUpdateSelection(oldStart,oldEnd,newStart,newEnd,candidatesStart,candidatesEnd);
        if(voiceActive&&(oldStart!=newStart||oldEnd!=newEnd))stopVoice();
        updateSuggestions();
    }

    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
