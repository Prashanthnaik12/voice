# NOVA Voice Assistant 🎤
### Offline Android Voice Assistant | Telugu · Kannada · Hindi · English

---

## 📱 Features

| Feature | Commands |
|---|---|
| **Open Apps** | "Open WhatsApp" / "WhatsApp తెరవు" / "व्हाट्सएप खोलो" |
| **Play Music** | "Play music" / "పాట వేసు" / "ಹಾಡು ಹಾಕು" / "गाना बजाओ" |
| **Next Song** | "Next" / "తదుపరి" / "ಮುಂದಿನ" / "अगला" |
| **Previous Song** | "Previous" / "వెనుకటి" / "ಹಿಂದಿನ" / "पिछला" |
| **Pause Music** | "Pause" / "ఆపు" / "ನಿಲ್ಲಿಸು" / "रुको" |
| **Call by Name** | "Call Mama" / "Mama కి కాల్ చేయి" / "Mama को कॉल करो" |
| **Volume Up** | "Volume up" / "వాల్యూమ్ పెంచు" / "आवाज बढ़ाओ" |
| **Volume Down** | "Volume down" / "వాల్యూమ్ తగ్గించు" / "आवाज कम करो" |
| **Torch** | "Torch on/off" / "టార్చ్ వేయి/ఆపు" |
| **Custom Greeting** | "Hi Mama" → **"చెప్పు మామా"** ✅ |

---

## 🚀 How to Build & Install

### Prerequisites
- Android Studio (Hedgehog or newer)
- Android SDK 26+
- A physical Android phone (recommended for microphone)

### Steps

1. **Open in Android Studio**
   ```
   File → Open → Select the VoiceAssistant folder
   ```

2. **Sync Gradle**
   ```
   Click "Sync Now" when prompted
   ```

3. **Build & Run**
   ```
   Run → Run 'app'  (or press Shift+F10)
   ```

4. **Grant Permissions** when prompted:
   - 🎤 Microphone (required)
   - 📞 Phone/Call (for calling)
   - 👥 Contacts (for contact lookup)

---

## 🔧 Offline Setup (Very Important!)

For the app to work **100% offline**, you must download the offline speech packs:

### Download Offline Telugu / Kannada / Hindi voices:
1. Go to **Settings → General Management → Language → Text-to-Speech**
2. Tap **Google Text-to-Speech Engine → Settings**
3. Download: **Telugu, Kannada, Hindi** language packs
4. For Speech Recognition, go to **Settings → Apps → Google app → Language**
5. Download offline recognition for each language

### Enable Offline Recognition (Samsung/Pixel):
```
Settings → Accessibility → Voice Assistant → Language → Download offline pack
```

---

## 📞 Custom Contacts (Call by Nickname)

Say **"Call Mama"** and NOVA will call your mama:

1. Open app → Tap **"Custom Contacts"**
2. Add entry:
   - Name: `mama`
   - Number: `+91XXXXXXXXXX`
   - Nicknames: `అమ్మ, amma, అమ్మగారు`
3. Now say: **"Call mama"** or **"Mama కి కాల్ చేయి"**

---

## 👋 Custom Greetings

Built-in: **"Hi Mama"** → **"చెప్పు మామా"**

Add your own:
1. Open app → Tap **"Custom Contacts"**
2. Add greeting:
   - Trigger: `hey bro`
   - Response: `ఏంటి మావా!`
3. Say **"Hey bro"** → NOVA says **"ఏంటి మావా!"**

---

## 🔁 Background Listening

Enable the **"Background Listening"** toggle to have NOVA listen for commands even when the app is closed. It will auto-start after phone reboot too.

---

## 📂 Project Structure

```
VoiceAssistant/
├── app/src/main/
│   ├── AndroidManifest.xml          ← Permissions & components
│   ├── java/com/voiceassistant/
│   │   ├── activities/
│   │   │   ├── MainActivity.java    ← Main UI + speech recognition
│   │   │   ├── ContactsActivity.java← Custom contacts & greetings
│   │   │   └── SettingsActivity.java
│   │   ├── services/
│   │   │   └── VoiceListenerService.java ← Background listening
│   │   ├── receivers/
│   │   │   └── BootReceiver.java    ← Auto-start on boot
│   │   └── utils/
│   │       ├── CommandProcessor.java ← ALL command logic (Telugu/Kannada/Hindi/English)
│   │       └── LanguageManager.java  ← Language switching
│   └── res/
│       ├── layout/                  ← UI layouts
│       ├── drawable/                ← Icons, backgrounds
│       └── values/                  ← strings, styles, colors
└── build.gradle
```

---

## 🌐 Supported Languages

| Language | Code | TTS | Speech |
|---|---|---|---|
| English | en-IN | ✅ | ✅ |
| Telugu తెలుగు | te-IN | ✅* | ✅* |
| Kannada ಕನ್ನಡ | kn-IN | ✅* | ✅* |
| Hindi हिंदी | hi-IN | ✅ | ✅ |

*Requires offline pack download (see setup above)

---

## 🔊 Apps You Can Open by Voice

WhatsApp, YouTube, Camera, Gallery, Settings, Calculator, Chrome, Google, Maps, Gmail, Spotify, Instagram, Facebook, Phone, Contacts, Messages, Clock, Calendar, Photos, Files, Play Store, Netflix, PhonePe, Paytm, GPay + more!

---

## 💡 Tips

- Speak clearly and at normal speed
- Works best in quiet environments
- If command not recognized, try in another language
- Add nicknames in multiple languages for contacts
- The app uses Android's built-in speech engine — no cloud, no internet!
