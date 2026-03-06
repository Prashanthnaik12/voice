package com.voiceassistant.activities;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.voiceassistant.R;
import com.voiceassistant.services.VoiceListenerService;
import com.voiceassistant.services.WakeWordService;
import com.voiceassistant.utils.CommandProcessor;
import com.voiceassistant.utils.LanguageManager;
import java.util.*;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS
    };

    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private CommandProcessor commandProcessor;
    private LanguageManager languageManager;
    private ImageButton btnMic;
    private TextView tvStatus, tvTranscript, tvLastCommand, tvWakeStatus;
    private Spinner spinnerLanguage;
    private Switch switchWakeWord, switchBackground;
    private LinearLayout waveformContainer;
    private View[] waveformBars;
    private boolean isListening = false;
    private boolean ttsReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
        initLanguageManager();
        initCommandProcessor();
        initTTS();
        initWaveform();
        checkPermissions();
        setupLanguageSpinner();
        setupSwitches();
    }

    private void initViews() {
        btnMic = findViewById(R.id.btnMic);
        tvStatus = findViewById(R.id.tvStatus);
        tvTranscript = findViewById(R.id.tvTranscript);
        tvLastCommand = findViewById(R.id.tvLastCommand);
        tvWakeStatus = findViewById(R.id.tvWakeStatus);
        spinnerLanguage = findViewById(R.id.spinnerLanguage);
        switchWakeWord = findViewById(R.id.switchWakeWord);
        switchBackground = findViewById(R.id.switchBackground);
        waveformContainer = findViewById(R.id.waveformContainer);
        btnMic.setOnClickListener(v -> toggleListening());
        View bs = findViewById(R.id.btnSettings);
        if (bs != null) bs.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        View bc = findViewById(R.id.btnContacts);
        if (bc != null) bc.setOnClickListener(v -> startActivity(new Intent(this, ContactsActivity.class)));
    }

    private void initLanguageManager() { languageManager = new LanguageManager(this); }

    private void initCommandProcessor() {
        commandProcessor = new CommandProcessor(this);
        commandProcessor.setFeedbackListener((message, speak) -> {
            runOnUiThread(() -> tvLastCommand.setText(message));
            if (speak && ttsReady) textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "response");
        });
    }

    private void initTTS() { textToSpeech = new TextToSpeech(this, this); }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true;
            updateTTSLanguage();
            speak(getString(R.string.welcome_message));
        }
    }

    private void updateTTSLanguage() {
        Locale locale = languageManager.getCurrentLocale();
        int r = textToSpeech.setLanguage(locale);
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED)
            textToSpeech.setLanguage(Locale.ENGLISH);
        textToSpeech.setSpeechRate(0.9f);
        textToSpeech.setPitch(1.0f);
    }

    private void setupLanguageSpinner() {
        String[] languages = {"English", "తెలుగు (Telugu)", "ಕನ್ನಡ (Kannada)", "हिंदी (Hindi)"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, languages);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLanguage.setAdapter(adapter);
        spinnerLanguage.setSelection(languageManager.getCurrentLanguageIndex());
        spinnerLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                languageManager.setLanguage(pos);
                updateTTSLanguage();
                commandProcessor.updateLanguage(pos);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void setupSwitches() {
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);

        // Wake Word Switch
        boolean wakeEnabled = prefs.getBoolean("wake_word_enabled", false);
        switchWakeWord.setChecked(wakeEnabled);
        updateWakeStatus(wakeEnabled);
        switchWakeWord.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putBoolean("wake_word_enabled", checked).apply();
            if (checked) {
                if (!checkPermissionsGranted()) { checkPermissions(); switchWakeWord.setChecked(false); return; }
                startForegroundService(new Intent(this, WakeWordService.class));
                Toast.makeText(this, "👋 Say \"Hi Mama\" anytime to wake NOVA!", Toast.LENGTH_LONG).show();
                speak("Wake word activated. Say Hi Mama anytime!");
            } else {
                stopService(new Intent(this, WakeWordService.class));
            }
            updateWakeStatus(checked);
        });

        // Background Switch
        switchBackground.setChecked(prefs.getBoolean("background_service", false));
        switchBackground.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putBoolean("background_service", checked).apply();
            if (checked) startForegroundService(new Intent(this, VoiceListenerService.class));
            else stopService(new Intent(this, VoiceListenerService.class));
        });
    }

    private void updateWakeStatus(boolean active) {
        if (tvWakeStatus == null) return;
        if (active) {
            tvWakeStatus.setText("💤 Sleeping — say \"Hi Mama\" to wake");
            tvWakeStatus.setTextColor(0xFF10B981);
        } else {
            tvWakeStatus.setText("Wake word disabled");
            tvWakeStatus.setTextColor(0xFF475569);
        }
    }

    private void initWaveform() {
        if (waveformContainer == null) return;
        waveformBars = new View[12];
        for (int i = 0; i < 12; i++) {
            View bar = new View(this);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(12, dpToPx(8));
            p.setMargins(3, 0, 3, 0);
            bar.setLayoutParams(p);
            bar.setBackgroundResource(R.drawable.waveform_bar);
            waveformContainer.addView(bar);
            waveformBars[i] = bar;
        }
    }

    private void animateWaveform(boolean active) {
        if (waveformBars == null) return;
        Handler h = new Handler();
        Random random = new Random();
        if (active) {
            Runnable r = new Runnable() {
                @Override public void run() {
                    if (!isListening) return;
                    for (View bar : waveformBars) {
                        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) bar.getLayoutParams();
                        lp.height = dpToPx(8 + random.nextInt(60));
                        bar.setLayoutParams(lp);
                    }
                    h.postDelayed(this, 120);
                }
            };
            h.post(r);
        } else {
            for (View bar : waveformBars) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) bar.getLayoutParams();
                lp.height = dpToPx(8);
                bar.setLayoutParams(lp);
            }
        }
    }

    private int dpToPx(int dp) { return (int)(dp * getResources().getDisplayMetrics().density); }

    private void toggleListening() { if (isListening) stopListening(); else startListening(); }

    private void startListening() {
        if (!checkPermissionsGranted()) { checkPermissions(); return; }
        isListening = true;
        tvStatus.setText(R.string.listening);
        tvTranscript.setText("");
        btnMic.setImageResource(R.drawable.ic_mic_active);
        animateWaveform(true);
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle p) { tvStatus.setText(R.string.speak_now); }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float r) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEndOfSpeech() { tvStatus.setText(R.string.processing); }
            @Override public void onEvent(int t, Bundle p) {}
            @Override public void onError(int error) { stopListening(); tvStatus.setText(getErrorMessage(error)); }
            @Override public void onResults(Bundle results) {
                stopListening();
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String cmd = matches.get(0).toLowerCase().trim();
                    tvTranscript.setText("\"" + cmd + "\"");
                    commandProcessor.processCommand(cmd);
                }
            }
            @Override public void onPartialResults(Bundle p) {
                ArrayList<String> partial = p.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (partial != null && !partial.isEmpty()) tvTranscript.setText("\"" + partial.get(0) + "\"");
            }
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageManager.getCurrentLanguageCode());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        speechRecognizer.startListening(intent);
    }

    private void stopListening() {
        isListening = false;
        if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
        runOnUiThread(() -> { tvStatus.setText(R.string.tap_to_speak); btnMic.setImageResource(R.drawable.ic_mic); animateWaveform(false); });
    }

    private String getErrorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH: return getString(R.string.no_match);
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return getString(R.string.timeout);
            default: return getString(R.string.tap_to_speak);
        }
    }

    public void speak(String text) {
        if (ttsReady && textToSpeech != null) textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "main");
    }

    private boolean checkPermissionsGranted() {
        for (String p : REQUIRED_PERMISSIONS)
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }

    private void checkPermissions() { ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE); }

    @Override
    public void onRequestPermissionsResult(int rc, @NonNull String[] perms, @NonNull int[] results) { super.onRequestPermissionsResult(rc, perms, results); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (textToSpeech != null) { textToSpeech.stop(); textToSpeech.shutdown(); }
    }
}
