package com.voiceassistant.services;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import androidx.core.app.NotificationCompat;

import com.voiceassistant.R;
import com.voiceassistant.activities.MainActivity;
import com.voiceassistant.utils.CommandProcessor;
import com.voiceassistant.utils.LanguageManager;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Background foreground service that continuously listens for voice commands.
 * Shows a persistent notification while running.
 */
public class VoiceListenerService extends Service implements TextToSpeech.OnInitListener {

    private static final String CHANNEL_ID = "voice_assistant_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int RESTART_DELAY_MS = 1500;

    private SpeechRecognizer speechRecognizer;
    private TextToSpeech tts;
    private CommandProcessor commandProcessor;
    private LanguageManager languageManager;
    private Handler handler;
    private boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        languageManager = new LanguageManager(this);
        commandProcessor = new CommandProcessor(this);
        commandProcessor.setFeedbackListener((msg, speak) -> {
            if (speak && tts != null) {
                tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "svc");
            }
        });
        tts = new TextToSpeech(this, this);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isRunning = true;
        startListening();
        return START_STICKY; // Auto-restart if killed
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            Locale locale = languageManager.getCurrentLocale();
            int result = tts.setLanguage(locale);
            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.ENGLISH);
            }
        }
    }

    private void startListening() {
        if (!isRunning) return;

        handler.post(() -> {
            if (speechRecognizer != null) {
                speechRecognizer.destroy();
            }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle p) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float r) {}
                @Override public void onBufferReceived(byte[] b) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onEvent(int t, Bundle p) {}

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        commandProcessor.processCommand(matches.get(0).toLowerCase().trim());
                    }
                    // Restart listening after delay
                    handler.postDelayed(() -> startListening(), RESTART_DELAY_MS);
                }

                @Override
                public void onError(int error) {
                    // Restart on error (most errors are temporary)
                    handler.postDelayed(() -> startListening(), RESTART_DELAY_MS);
                }

                @Override public void onPartialResults(Bundle p) {}
            });

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                    languageManager.getCurrentLanguageCode());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            try {
                speechRecognizer.startListening(intent);
            } catch (Exception e) {
                handler.postDelayed(() -> startListening(), RESTART_DELAY_MS * 2);
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Voice Assistant",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Voice assistant is running in background");
            channel.setSound(null, null);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent notifIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notifIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Voice Assistant Active")
                .setContentText("Listening for commands...")
                .setSmallIcon(R.drawable.ic_mic)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
