package com.voiceassistant.utils;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Locale;

public class LanguageManager {
    private static final String PREF_LANGUAGE = "selected_language";
    private final SharedPreferences prefs;
    private int currentIndex;

    // Language codes for Android SpeechRecognizer
    private static final String[] LANGUAGE_CODES = {
        "en-IN",   // English (India)
        "te-IN",   // Telugu
        "kn-IN",   // Kannada
        "hi-IN"    // Hindi
    };

    private static final Locale[] LOCALES = {
        new Locale("en", "IN"),
        new Locale("te", "IN"),
        new Locale("kn", "IN"),
        new Locale("hi", "IN")
    };

    public LanguageManager(Context context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        currentIndex = prefs.getInt(PREF_LANGUAGE, 0);
    }

    public void setLanguage(int index) {
        currentIndex = index;
        prefs.edit().putInt(PREF_LANGUAGE, index).apply();
    }

    public int getCurrentLanguageIndex() { return currentIndex; }
    public String getCurrentLanguageCode() { return LANGUAGE_CODES[currentIndex]; }
    public Locale getCurrentLocale() { return LOCALES[currentIndex]; }
}
