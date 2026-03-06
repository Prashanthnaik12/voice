package com.voiceassistant.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.KeyEvent;

import com.voiceassistant.R;
import com.voiceassistant.models.CustomContact;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CommandProcessor - Heart of the voice assistant.
 * Handles ALL voice commands in Telugu, Kannada, Hindi, and English.
 */
public class CommandProcessor {

    public interface FeedbackListener {
        void onFeedback(String message, boolean speak);
    }

    private final Context context;
    private FeedbackListener feedbackListener;
    private int currentLanguage = 0; // 0=English, 1=Telugu, 2=Kannada, 3=Hindi
    private SharedPreferences prefs;

    // ─── MULTILINGUAL COMMAND MAPS ────────────────────────────────────────────

    // Custom greetings: trigger phrase → response phrase
    private final Map<String, String> customGreetings = new HashMap<>();

    // Open app triggers
    private final String[][] openAppTriggers = {
        // English
        {"open", "launch", "start", "run"},
        // Telugu
        {"తెరవు", "తెరువు", "ప్రారంభించు", "స్టార్ట్"},
        // Kannada
        {"ತೆರೆ", "ಪ್ರಾರಂಭಿಸು", "ಶುರು ಮಾಡು", "ಓಪನ್"},
        // Hindi
        {"खोलो", "खोलें", "शुरू करो", "चलाओ", "ओपन"}
    };

    // Music play triggers
    private final String[][] playMusicTriggers = {
        {"play", "play music", "play song", "start music"},
        {"పాట వేసు", "సంగీతం వేయి", "ప్లే చేయి", "పాడు"},
        {"ಹಾಡು ಹಾಕು", "ಸಂಗೀತ ಪ್ಲೇ ಮಾಡು", "ಮ್ಯೂಸಿಕ್"},
        {"गाना बजाओ", "म्यूजिक चलाओ", "गाना चलाओ", "प्ले करो"}
    };

    // Next song triggers
    private final String[][] nextSongTriggers = {
        {"next", "next song", "skip", "forward"},
        {"తదుపరి", "తర్వాత పాట", "నెక్స్ట్"},
        {"ಮುಂದಿನ", "ಮುಂದಿನ ಹಾಡು", "ನೆಕ್ಸ್ಟ್"},
        {"अगला", "अगला गाना", "नेक्स्ट"}
    };

    // Previous song triggers
    private final String[][] prevSongTriggers = {
        {"previous", "back", "prev song", "go back"},
        {"వెనుకటి", "మునుపటి పాట", "బ్యాక్"},
        {"ಹಿಂದಿನ", "ಹಿಂದಿನ ಹಾಡು", "ಬ್ಯಾಕ್"},
        {"पिछला", "पुराना गाना", "वापस जाओ"}
    };

    // Pause music triggers
    private final String[][] pauseMusicTriggers = {
        {"pause", "stop music", "mute music"},
        {"ఆపు", "పాజ్ చేయి", "ఆపివేయి"},
        {"ನಿಲ್ಲಿಸು", "ಪಾಸ್ ಮಾಡು"},
        {"रुको", "बंद करो", "रोको"}
    };

    // Call triggers
    private final String[][] callTriggers = {
        {"call", "dial", "phone", "ring"},
        {"కాల్ చేయి", "ఫోన్ చేయి", "డయల్"},
        {"ಕಾಲ್ ಮಾಡು", "ಫೋನ್ ಮಾಡು"},
        {"कॉल करो", "फोन करो", "डायल करो"}
    };

    // Volume up triggers
    private final String[][] volUpTriggers = {
        {"volume up", "increase volume", "louder"},
        {"వాల్యూమ్ పెంచు", "శబ్దం పెంచు"},
        {"ವಾಲ್ಯೂಮ್ ಹೆಚ್ಚಿಸು"},
        {"आवाज बढ़ाओ", "वॉल्यूम बढ़ाओ"}
    };

    // Volume down triggers
    private final String[][] volDownTriggers = {
        {"volume down", "decrease volume", "quieter"},
        {"వాల్యూమ్ తగ్గించు", "శబ్దం తగ్గించు"},
        {"ವಾಲ್ಯೂಮ್ ಕಡಿಮೆ ಮಾಡು"},
        {"आवाज कम करो", "वॉल्यूम कम करो"}
    };

    // Flashlight triggers
    private final String[][] flashlightOnTriggers = {
        {"torch on", "flashlight on", "turn on torch", "light on"},
        {"టార్చ్ వేయి", "వెలుతురు వేయి"},
        {"ಟಾರ್ಚ್ ಹಾಕು", "ದೀಪ ಹಾಕು"},
        {"टॉर्च चालू", "लाइट चालू करो"}
    };

    private final String[][] flashlightOffTriggers = {
        {"torch off", "flashlight off", "turn off torch", "light off"},
        {"టార్చ్ ఆపు"},
        {"ಟಾರ್ಚ್ ಆಫ್"},
        {"टॉर्च बंद", "लाइट बंद करो"}
    };

    // ─── RESPONSE MESSAGES ────────────────────────────────────────────────────

    private final String[][] responses_calling = {
        {"Calling %s", "Dialing %s now"},
        {"%s కి కాల్ చేస్తున్నాను"},
        {"%s ಗೆ ಕಾಲ್ ಮಾಡುತ್ತಿದ್ದೇನೆ"},
        {"%s को कॉल कर रहा हूं"}
    };

    private final String[][] responses_music_play = {
        {"Playing music"},
        {"సంగీతం ప్లే చేస్తున్నాను"},
        {"ಸಂಗೀತ ಪ್ಲೇ ಮಾಡುತ್ತಿದ್ದೇನೆ"},
        {"गाना बजा रहा हूं"}
    };

    private final String[][] responses_next = {
        {"Next song"},
        {"తదుపరి పాట"},
        {"ಮುಂದಿನ ಹಾಡು"},
        {"अगला गाना"}
    };

    private final String[][] responses_prev = {
        {"Previous song"},
        {"వెనుకటి పాట"},
        {"ಹಿಂದಿನ ಹಾಡು"},
        {"पिछला गाना"}
    };

    private final String[][] responses_pause = {
        {"Music paused"},
        {"సంగీతం ఆపాను"},
        {"ಸಂಗೀತ ನಿಲ್ಲಿಸಿದ್ದೇನೆ"},
        {"गाना रोक दिया"}
    };

    private final String[][] responses_vol_up = {
        {"Volume increased"},
        {"వాల్యూమ్ పెంచాను"},
        {"ವಾಲ್ಯೂಮ್ ಹೆಚ್ಚಿಸಿದ್ದೇನೆ"},
        {"आवाज बढ़ा दी"}
    };

    private final String[][] responses_vol_down = {
        {"Volume decreased"},
        {"వాల్యూమ్ తగ్గించాను"},
        {"ವಾಲ್ಯೂಮ್ ಕಡಿಮೆ ಮಾಡಿದ್ದೇನೆ"},
        {"आवाज कम कर दी"}
    };

    private final String[][] responses_app_open = {
        {"Opening %s"},
        {"%s తెరుస్తున్నాను"},
        {"%s ತೆರೆಯುತ್ತಿದ್ದೇನೆ"},
        {"%s खोल रहा हूं"}
    };

    private final String[][] responses_not_understood = {
        {"Sorry, I didn't understand. Please try again."},
        {"క్షమించండి, అర్థం కాలేదు. మళ్లీ ప్రయత్నించండి."},
        {"ಕ್ಷಮಿಸಿ, ಅರ್ಥವಾಗಲಿಲ್ಲ. ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ."},
        {"माफ करो, समझ नहीं आया। फिर से कोशिश करो।"}
    };

    // ─── APP NAME MAPPINGS ────────────────────────────────────────────────────

    private final Map<String, String> appNameMap = new HashMap<String, String>() {{
        // English names
        put("whatsapp", "com.whatsapp");
        put("youtube", "com.google.android.youtube");
        put("camera", "com.android.camera");
        put("camera2", "com.android.camera2");
        put("gallery", "com.android.gallery3d");
        put("settings", "com.android.settings");
        put("calculator", "com.android.calculator2");
        put("chrome", "com.android.chrome");
        put("google", "com.google.android.googlequicksearchbox");
        put("maps", "com.google.android.apps.maps");
        put("gmail", "com.google.android.gm");
        put("spotify", "com.spotify.music");
        put("instagram", "com.instagram.android");
        put("facebook", "com.facebook.katana");
        put("twitter", "com.twitter.android");
        put("phone", "com.android.dialer");
        put("contacts", "com.android.contacts");
        put("messages", "com.android.mms");
        put("clock", "com.android.deskclock");
        put("alarm", "com.android.deskclock");
        put("calendar", "com.android.calendar");
        put("photos", "com.google.android.apps.photos");
        put("files", "com.android.documentsui");
        put("play store", "com.android.vending");
        put("netflix", "com.netflix.mediaclient");
        put("amazon", "com.amazon.mShop.android.shopping");
        put("flipkart", "com.flipkart.android");
        put("phonepe", "com.phonepe.app");
        put("paytm", "net.one97.paytm");
        put("gpay", "com.google.android.apps.nbu.paisa.user");
        // Telugu app names
        put("కెమెరా", "com.android.camera");
        put("సెట్టింగ్స్", "com.android.settings");
        put("గ్యాలరీ", "com.android.gallery3d");
        put("కాలిక్యులేటర్", "com.android.calculator2");
        // Kannada app names
        put("ಕ್ಯಾಮೆರಾ", "com.android.camera");
        put("ಸೆಟ್ಟಿಂಗ್ಸ್", "com.android.settings");
        put("ಗ್ಯಾಲರಿ", "com.android.gallery3d");
        // Hindi app names
        put("कैमरा", "com.android.camera");
        put("सेटिंग्स", "com.android.settings");
        put("गैलरी", "com.android.gallery3d");
        put("यूट्यूब", "com.google.android.youtube");
        put("व्हाट्सएप", "com.whatsapp");
    }};

    // ─── CONSTRUCTOR ─────────────────────────────────────────────────────────

    public CommandProcessor(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences("voice_assistant", Context.MODE_PRIVATE);
        loadCustomGreetings();
        loadDefaultGreetings();
    }

    public void setFeedbackListener(FeedbackListener listener) {
        this.feedbackListener = listener;
    }

    public void updateLanguage(int languageIndex) {
        this.currentLanguage = languageIndex;
    }

    // ─── CUSTOM GREETINGS ─────────────────────────────────────────────────────

    private void loadDefaultGreetings() {
        // Built-in: "hi mama" → "చెప్పు మామా" (Telugu for "Tell me uncle/bro")
        if (!customGreetings.containsKey("hi mama")) {
            customGreetings.put("hi mama", "చెప్పు మామా");
            customGreetings.put("హాయ్ మామా", "చెప్పు మామా");
            customGreetings.put("hey mama", "చెప్పు మామా");
            customGreetings.put("hello mama", "చెప్పు మామా");
        }
    }

    private void loadCustomGreetings() {
        try {
            String json = prefs.getString("custom_greetings", "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                customGreetings.put(
                    obj.getString("trigger").toLowerCase(),
                    obj.getString("response")
                );
            }
        } catch (Exception e) {
            // Ignore parse errors
        }
    }

    public void addCustomGreeting(String trigger, String response) {
        customGreetings.put(trigger.toLowerCase(), response);
        saveCustomGreetings();
    }

    private void saveCustomGreetings() {
        try {
            JSONArray arr = new JSONArray();
            for (Map.Entry<String, String> entry : customGreetings.entrySet()) {
                JSONObject obj = new JSONObject();
                obj.put("trigger", entry.getKey());
                obj.put("response", entry.getValue());
                arr.put(obj);
            }
            prefs.edit().putString("custom_greetings", arr.toString()).apply();
        } catch (Exception e) {
            // Ignore
        }
    }

    // ─── MAIN COMMAND PROCESSOR ───────────────────────────────────────────────

    public void processCommand(String command) {
        command = command.toLowerCase().trim();

        // 1. Check custom greetings first
        if (handleCustomGreeting(command)) return;

        // 2. Check call command
        if (handleCallCommand(command)) return;

        // 3. Check music commands
        if (handleMusicCommand(command)) return;

        // 4. Check volume commands
        if (handleVolumeCommand(command)) return;

        // 5. Check flashlight commands
        if (handleFlashlightCommand(command)) return;

        // 6. Check open app commands
        if (handleOpenAppCommand(command)) return;

        // 7. Not understood
        feedback(responses_not_understood[currentLanguage][0], true);
    }

    // ─── HANDLERS ─────────────────────────────────────────────────────────────

    private boolean handleCustomGreeting(String command) {
        for (Map.Entry<String, String> entry : customGreetings.entrySet()) {
            if (command.contains(entry.getKey()) || command.equals(entry.getKey())) {
                feedback(entry.getValue(), true);
                return true;
            }
        }
        return false;
    }

    private boolean handleCallCommand(String command) {
        boolean isCallCommand = false;
        for (String trigger : callTriggers[currentLanguage]) {
            if (command.contains(trigger)) { isCallCommand = true; break; }
        }
        // Also check other languages
        if (!isCallCommand) {
            for (String[] langTriggers : callTriggers) {
                for (String trigger : langTriggers) {
                    if (command.contains(trigger)) { isCallCommand = true; break; }
                }
                if (isCallCommand) break;
            }
        }
        if (!isCallCommand) return false;

        // Extract name/number from command
        String target = extractCallTarget(command);
        if (target == null || target.isEmpty()) {
            feedback("Who should I call?", true);
            return true;
        }

        // Check if it's a number
        if (target.matches("[0-9+\\-\\s]+")) {
            makeCall(target.replaceAll("[\\s\\-]", ""), target);
        } else {
            // Look up contact by name
            String number = findContactNumber(target);
            if (number != null) {
                makeCall(number, target);
            } else {
                // Check custom contacts
                number = findCustomContact(target);
                if (number != null) {
                    makeCall(number, target);
                } else {
                    feedback("Contact " + target + " not found", true);
                }
            }
        }
        return true;
    }

    private String extractCallTarget(String command) {
        // Remove call trigger words and extract the target
        String[] removeWords = {"call", "dial", "phone", "ring",
                "కాల్ చేయి", "ఫోన్ చేయి", "కాల్ చేయి",
                "ಕಾಲ್ ಮಾಡು", "ಫೋನ್ ಮಾಡು",
                "कॉल करो", "फोन करो"};
        for (String word : removeWords) {
            command = command.replace(word, "").trim();
        }
        return command.trim();
    }

    private void makeCall(String number, String displayName) {
        String msg = String.format(responses_calling[currentLanguage][0], displayName);
        feedback(msg, true);
        try {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + number));
            callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(callIntent);
        } catch (Exception e) {
            feedback("Cannot make call. Check permissions.", true);
        }
    }

    private String findContactNumber(String name) {
        try {
            Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
            String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            };
            String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                    + " LIKE ?";
            String[] selectionArgs = {"%" + name + "%"};

            Cursor cursor = context.getContentResolver().query(
                    uri, projection, selection, selectionArgs, null);

            if (cursor != null && cursor.moveToFirst()) {
                String number = cursor.getString(cursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.NUMBER));
                cursor.close();
                return number;
            }
        } catch (Exception e) {
            // Permission denied
        }
        return null;
    }

    private String findCustomContact(String name) {
        try {
            String json = prefs.getString("custom_contacts", "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String contactName = obj.getString("name").toLowerCase();
                if (contactName.contains(name) || name.contains(contactName)) {
                    return obj.getString("number");
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private boolean handleMusicCommand(String command) {
        // Play music
        for (String trigger : playMusicTriggers[currentLanguage]) {
            if (command.contains(trigger)) {
                playMusic();
                return true;
            }
        }
        // Next
        for (String trigger : nextSongTriggers[currentLanguage]) {
            if (command.contains(trigger)) {
                nextSong();
                return true;
            }
        }
        // Previous
        for (String trigger : prevSongTriggers[currentLanguage]) {
            if (command.contains(trigger)) {
                prevSong();
                return true;
            }
        }
        // Pause
        for (String trigger : pauseMusicTriggers[currentLanguage]) {
            if (command.contains(trigger)) {
                pauseMusic();
                return true;
            }
        }
        // Also check other language triggers
        for (int lang = 0; lang < 4; lang++) {
            if (lang == currentLanguage) continue;
            for (String t : playMusicTriggers[lang]) { if (command.contains(t)) { playMusic(); return true; } }
            for (String t : nextSongTriggers[lang]) { if (command.contains(t)) { nextSong(); return true; } }
            for (String t : prevSongTriggers[lang]) { if (command.contains(t)) { prevSong(); return true; } }
            for (String t : pauseMusicTriggers[lang]) { if (command.contains(t)) { pauseMusic(); return true; } }
        }
        return false;
    }

    private void playMusic() {
        feedback(responses_music_play[currentLanguage][0], true);
        sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        // Open default music app
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setType("audio/*");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { context.startActivity(intent); } catch (Exception e) { /* No music app */ }
    }

    private void nextSong() {
        feedback(responses_next[currentLanguage][0], true);
        sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT);
    }

    private void prevSong() {
        feedback(responses_prev[currentLanguage][0], true);
        sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    }

    private void pauseMusic() {
        feedback(responses_pause[currentLanguage][0], true);
        sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE);
    }

    private void sendMediaKey(int keyCode) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        }
    }

    private boolean handleVolumeCommand(String command) {
        // Volume up
        for (String[] langTriggers : volUpTriggers) {
            for (String trigger : langTriggers) {
                if (command.contains(trigger)) {
                    adjustVolume(true);
                    return true;
                }
            }
        }
        // Volume down
        for (String[] langTriggers : volDownTriggers) {
            for (String trigger : langTriggers) {
                if (command.contains(trigger)) {
                    adjustVolume(false);
                    return true;
                }
            }
        }
        return false;
    }

    private void adjustVolume(boolean increase) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    increase ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI);
        }
        feedback(increase ? responses_vol_up[currentLanguage][0]
                : responses_vol_down[currentLanguage][0], true);
    }

    private boolean handleFlashlightCommand(String command) {
        for (String[] langTriggers : flashlightOnTriggers) {
            for (String t : langTriggers) {
                if (command.contains(t)) {
                    toggleFlashlight(true);
                    return true;
                }
            }
        }
        for (String[] langTriggers : flashlightOffTriggers) {
            for (String t : langTriggers) {
                if (command.contains(t)) {
                    toggleFlashlight(false);
                    return true;
                }
            }
        }
        return false;
    }

    private void toggleFlashlight(boolean on) {
        try {
            Intent intent = new Intent("android.intent.action.TOGGLE_FLASHLIGHT");
            intent.putExtra("android.intent.extra.FLASH_STATE", on ? 1 : 0);
            context.sendBroadcast(intent);
            feedback(on ? "Flashlight on" : "Flashlight off", true);
        } catch (Exception e) {
            // Use camera manager API instead
            feedback(on ? "Flashlight on" : "Flashlight off", true);
        }
    }

    private boolean handleOpenAppCommand(String command) {
        boolean isOpenCommand = false;
        for (String[] langTriggers : openAppTriggers) {
            for (String trigger : langTriggers) {
                if (command.contains(trigger)) { isOpenCommand = true; break; }
            }
            if (isOpenCommand) break;
        }

        // Look for app name in command
        String appName = null;
        String packageName = null;

        for (Map.Entry<String, String> entry : appNameMap.entrySet()) {
            if (command.contains(entry.getKey())) {
                appName = entry.getKey();
                packageName = entry.getValue();
                break;
            }
        }

        if (packageName != null) {
            openApp(packageName, appName);
            return true;
        }

        // If "open" detected but no specific app found, try to extract app name
        if (isOpenCommand) {
            feedback("Which app should I open?", true);
            return true;
        }

        return false;
    }

    private void openApp(String packageName, String displayName) {
        String msg = String.format(responses_app_open[currentLanguage][0], displayName);
        feedback(msg, true);
        try {
            PackageManager pm = context.getPackageManager();
            Intent intent = pm.getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } else {
                feedback(displayName + " is not installed", true);
            }
        } catch (Exception e) {
            feedback("Could not open " + displayName, true);
        }
    }

    // ─── UTILITY ──────────────────────────────────────────────────────────────

    private void feedback(String message, boolean speak) {
        if (feedbackListener != null) {
            feedbackListener.onFeedback(message, speak);
        }
    }

    public Map<String, String> getCustomGreetings() {
        return customGreetings;
    }
}
