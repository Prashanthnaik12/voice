package com.voiceassistant.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * WakeWordDetector
 *
 * Detects the wake word "Hi Mama" and its variants across all 4 languages.
 * Uses fuzzy matching to handle speech recognition inaccuracies.
 */
public class WakeWordDetector {

    // ─── WAKE WORD VARIANTS ───────────────────────────────────────────────────
    // All forms of "Hi Mama" across English, Telugu, Kannada, Hindi

    private static final Set<String> WAKE_WORDS = new HashSet<>(Arrays.asList(
        // English variants
        "hi mama",
        "hey mama",
        "hello mama",
        "hi mamma",
        "hey mamma",
        "hi mom",
        "hey mom",
        "ok mama",
        "okay mama",
        "nova",
        "hi nova",
        "hey nova",
        "ok nova",
        "okay nova",

        // Telugu variants — హాయ్ మామా
        "హాయ్ మామా",
        "హాయ్ మామ",
        "హే మామా",
        "హెలో మామా",
        "నోవా",
        "హాయ్ నోవా",

        // Telugu romanized
        "hay mama",
        "hai mama",
        "haay mama",
        "he mama",
        "hei mama",

        // Kannada variants — ಹಾಯ್ ಮಾಮಾ
        "ಹಾಯ್ ಮಾಮಾ",
        "ಹೇ ಮಾಮಾ",
        "ಹಲೋ ಮಾಮಾ",
        "ನೋವಾ",

        // Hindi variants — हाय मामा
        "हाय मामा",
        "हे मामा",
        "हेलो मामा",
        "ओके मामा",
        "नोवा",

        // Common mishearings / partial matches
        "hi mom a",
        "hi moma",
        "hi momma",
        "hi mama wake up",
        "wake up mama",
        "mama hi",
        "mama hey"
    ));

    // Substrings that alone indicate a wake word
    private static final String[] WAKE_SUBSTRINGS = {
        "hi mama", "hey mama", "hello mama", "ok mama", "okay mama",
        "hi nova", "hey nova", "ok nova", "okay nova",
        "హాయ్ మామా", "ಹಾಯ್ ಮಾಮಾ", "हाय मामा"
    };

    // ─── DETECTION ────────────────────────────────────────────────────────────

    public boolean isWakeWord(String heard) {
        if (heard == null || heard.trim().isEmpty()) return false;

        String normalized = heard.toLowerCase().trim();

        // 1. Exact match
        if (WAKE_WORDS.contains(normalized)) return true;

        // 2. Substring match (handles "hey mama how are you")
        for (String substring : WAKE_SUBSTRINGS) {
            if (normalized.contains(substring)) return true;
        }

        // 3. Fuzzy match for "hi mama" variants
        if (isFuzzyMatch(normalized)) return true;

        return false;
    }

    /**
     * Fuzzy match to handle speech recognition variations:
     * "hai mamma", "hey moma", "haay mama", etc.
     */
    private boolean isFuzzyMatch(String text) {
        // Check if starts with greeting sound
        boolean hasGreeting = text.startsWith("hi ") || text.startsWith("hey ")
                || text.startsWith("hai ") || text.startsWith("he ")
                || text.startsWith("ok ") || text.startsWith("okay ")
                || text.startsWith("hei ") || text.startsWith("hay ");

        if (!hasGreeting) return false;

        // Check if ends with or contains mama/mom variant
        boolean hasMama = text.contains("mama") || text.contains("mamma")
                || text.contains("moma") || text.contains("momma")
                || text.contains("mom") || text.contains("nova")
                || text.contains("nova");

        return hasMama;
    }

    /**
     * Check if a partial transcript likely contains the wake word.
     * More lenient — used for early detection.
     */
    public boolean isLikelyWakeWord(String partial) {
        if (partial == null || partial.length() < 3) return false;
        String norm = partial.toLowerCase().trim();
        return norm.contains("mama") || norm.contains("nova")
                || norm.contains("మామా") || norm.contains("ಮಾಮಾ") || norm.contains("मामा");
    }
}
