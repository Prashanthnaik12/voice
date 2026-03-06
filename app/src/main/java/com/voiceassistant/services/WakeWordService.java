package com.voiceassistant.services;

import android.app.*;
import android.content.*;
import android.media.AudioManager;
import android.os.*;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import androidx.core.app.NotificationCompat;

import com.voiceassistant.R;
import com.voiceassistant.activities.MainActivity;
import com.voiceassistant.utils.CommandProcessor;
import com.voiceassistant.utils.LanguageManager;
import com.voiceassistant.utils.WakeWordDetector;

import java.util.ArrayList;
import java.util.Locale;

/**
 * WakeWordService — Always running in background.
 *
 * STATE MACHINE:
 *   SLEEPING  → listening quietly for wake word only ("hi mama", "హాయ్ మామా", etc.)
 *   WOKEN     → says "చెప్పు మామా", then listens for actual command
 *   EXECUTING → processes & executes the command
 *   SLEEPING  → back to waiting
 */
public class WakeWordService extends Service implements TextToSpeech.OnInitListener {

    private static final String TAG = "WakeWordService";
    public static final String CHANNEL_ID = "nova_wake_channel";
    public static final int NOTIF_ID = 2001;

    // How long to wait for command after waking (ms)
    private static final int COMMAND_TIMEOUT_MS = 8000;
    // Delay before restarting listener after TTS finishes
    private static final int RESTART_DELAY_MS = 800;

    public enum State { SLEEPING, WOKEN, EXECUTING }

    private State state = State.SLEEPING;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private CommandProcessor commandProcessor;
    private LanguageManager languageManager;
    private WakeWordDetector wakeWordDetector;
    private Handler handler;
    private PowerManager.WakeLock wakeLock;
    private boolean ttsReady = false;
    private Runnable commandTimeoutRunnable;

    // ─── LIFECYCLE ────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        languageManager = new LanguageManager(this);
        wakeWordDetector = new WakeWordDetector();
        commandProcessor = new CommandProcessor(this);

        commandProcessor.setFeedbackListener((msg, speak) -> {
            Log.d(TAG, "Command feedback: " + msg);
            if (speak && ttsReady) {
                speakThenSleep(msg);
            } else {
                handler.postDelayed(this::startSleepListening, RESTART_DELAY_MS);
            }
        });

        tts = new TextToSpeech(this, this);
        acquireWakeLock();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification("Sleeping... say 'Hi Mama' to wake me"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        if (state == State.SLEEPING) {
            handler.postDelayed(this::startSleepListening, 1000);
        }
        return START_STICKY;
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true;
            Locale locale = languageManager.getCurrentLocale();
            int result = tts.setLanguage(locale);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(new Locale("te", "IN")); // fallback to Telugu
            }
            tts.setSpeechRate(0.95f);
            tts.setPitch(1.05f);
            // Start listening after TTS is ready
            handler.postDelayed(this::startSleepListening, 1500);
        }
    }

    // ─── SLEEP LISTENING (Wake Word Mode) ────────────────────────────────────

    /**
     * In sleep mode, we quietly listen for ONLY the wake word.
     * Battery efficient — short bursts, then restart.
     */
    private void startSleepListening() {
        state = State.SLEEPING;
        updateNotification("💤 Sleeping... say \"Hi Mama\" to wake");
        Log.d(TAG, "Starting sleep listening (wake word mode)");
        startRecognizing(false);
    }

    // ─── COMMAND LISTENING (After Wake) ───────────────────────────────────────

    /**
     * After wake word detected: speak response, then listen for command.
     */
    private void handleWakeWord() {
        if (state != State.SLEEPING) return;
        state = State.WOKEN;
        Log.d(TAG, "WAKE WORD DETECTED!");
        updateNotification("🟢 Awake! Listening for command...");

        // Vibrate to signal wake
        vibrate();

        // Speak the greeting
        if (ttsReady) {
            speak("చెప్పు మామా", () -> {
                // After speaking, listen for command
                handler.postDelayed(this::startCommandListening, 400);
            });
        } else {
            handler.postDelayed(this::startCommandListening, 600);
        }
    }

    private void startCommandListening() {
        if (state != State.WOKEN) return;
        Log.d(TAG, "Listening for command...");

        // Set timeout — if no command in 8s, go back to sleep
        cancelCommandTimeout();
        commandTimeoutRunnable = () -> {
            Log.d(TAG, "Command timeout — going back to sleep");
            speak("పర్లేదు మామా", () -> handler.postDelayed(this::startSleepListening, 400));
        };
        handler.postDelayed(commandTimeoutRunnable, COMMAND_TIMEOUT_MS);

        startRecognizing(true);
    }

    // ─── CORE SPEECH RECOGNIZER ───────────────────────────────────────────────

    private void startRecognizing(boolean isCommandMode) {
        handler.post(() -> {
            destroyRecognizer();

            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {

                @Override public void onReadyForSpeech(Bundle p) {
                    Log.d(TAG, "Ready for speech (commandMode=" + isCommandMode + ")");
                }

                @Override public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION);

                    if (matches == null || matches.isEmpty()) {
                        restartAfterResult(isCommandMode);
                        return;
                    }

                    String heard = matches.get(0).toLowerCase().trim();
                    Log.d(TAG, "Heard: \"" + heard + "\" (commandMode=" + isCommandMode + ")");

                    if (!isCommandMode) {
                        // SLEEP MODE — only check for wake word
                        if (wakeWordDetector.isWakeWord(heard)) {
                            handleWakeWord();
                        } else {
                            // Not wake word, keep sleeping
                            handler.postDelayed(WakeWordService.this::startSleepListening,
                                    RESTART_DELAY_MS);
                        }
                    } else {
                        // COMMAND MODE — process the command
                        cancelCommandTimeout();
                        state = State.EXECUTING;

                        // Check if it's ANOTHER wake word (ignore)
                        if (wakeWordDetector.isWakeWord(heard)) {
                            speak("చెప్పు మామా, ఏం కావాలి?", () ->
                                    handler.postDelayed(WakeWordService.this::startCommandListening, 400));
                            return;
                        }

                        Log.d(TAG, "Processing command: " + heard);
                        commandProcessor.processCommand(heard);
                        // commandProcessor will call feedback → speakThenSleep
                    }
                }

                @Override public void onError(int error) {
                    Log.d(TAG, "Speech error: " + error);
                    // Most errors are recoverable — just restart
                    if (isCommandMode && state == State.WOKEN) {
                        cancelCommandTimeout();
                        handler.postDelayed(WakeWordService.this::startSleepListening,
                                RESTART_DELAY_MS);
                    } else {
                        handler.postDelayed(WakeWordService.this::startSleepListening,
                                RESTART_DELAY_MS);
                    }
                }

                @Override public void onPartialResults(Bundle partial) {
                    // In sleep mode, check partial results for wake word (faster detection)
                    if (!isCommandMode) {
                        ArrayList<String> partials = partial.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION);
                        if (partials != null && !partials.isEmpty()) {
                            String partialText = partials.get(0).toLowerCase().trim();
                            if (wakeWordDetector.isWakeWord(partialText)) {
                                Log.d(TAG, "Wake word detected in partial: " + partialText);
                                destroyRecognizer();
                                handleWakeWord();
                            }
                        }
                    }
                }

                @Override public void onBeginningOfSpeech() {}
                @Override public void onEndOfSpeech() {}
                @Override public void onRmsChanged(float r) {}
                @Override public void onBufferReceived(byte[] b) {}
                @Override public void onEvent(int t, Bundle p) {}
            });

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

            // In sleep mode, accept all 4 languages simultaneously
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN");
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN,te-IN,hi-IN,kn-IN");
            intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);

            if (isCommandMode) {
                // Command mode: listen for full sentence
                intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L);
                intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
            } else {
                // Sleep mode: short bursts
                intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L);
                intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L);
            }

            try {
                recognizer.startListening(intent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to start listening: " + e.getMessage());
                handler.postDelayed(WakeWordService.this::startSleepListening, 2000);
            }
        });
    }

    private void restartAfterResult(boolean wasCommandMode) {
        if (wasCommandMode) {
            handler.postDelayed(this::startSleepListening, RESTART_DELAY_MS);
        } else {
            handler.postDelayed(this::startSleepListening, RESTART_DELAY_MS);
        }
    }

    // ─── TTS HELPERS ─────────────────────────────────────────────────────────

    private void speak(String text, Runnable onDone) {
        if (!ttsReady || tts == null) {
            if (onDone != null) handler.postDelayed(onDone, 500);
            return;
        }
        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "utt");
        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override public void onStart(String id) {}
            @Override public void onDone(String id) {
                if (onDone != null) handler.post(onDone);
            }
            @Override public void onError(String id) {
                if (onDone != null) handler.post(onDone);
            }
        });
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "utt");
    }

    private void speakThenSleep(String text) {
        speak(text, () -> handler.postDelayed(this::startSleepListening, 600));
    }

    // ─── UTILITY ─────────────────────────────────────────────────────────────

    private void vibrate() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(
                        new long[]{0, 80, 50, 80}, -1));
            } else {
                vibrator.vibrate(new long[]{0, 80, 50, 80}, -1);
            }
        }
    }

    private void cancelCommandTimeout() {
        if (commandTimeoutRunnable != null) {
            handler.removeCallbacks(commandTimeoutRunnable);
            commandTimeoutRunnable = null;
        }
    }

    private void destroyRecognizer() {
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) {}
            try { recognizer.destroy(); } catch (Exception ignored) {}
            recognizer = null;
        }
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NOVA:WakeLock");
            wakeLock.acquire(); // Keep CPU running for background listening
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "NOVA Wake Word", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Listening for 'Hi Mama' wake word");
            channel.setSound(null, null);
            channel.enableLights(false);
            channel.enableVibration(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🎤 NOVA Voice Assistant")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_mic)
                .setContentIntent(pi)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(text));
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroyed");
        cancelCommandTimeout();
        destroyRecognizer();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
