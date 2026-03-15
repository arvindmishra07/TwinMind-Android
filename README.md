# TwinMind Android App

A voice recording app with AI transcription and summary generation, built as a take-home assignment replicating the TwinMind app experience.

## 📱 Demo Video
> [▶️ Watch Demo Video](https://youtube.com/shorts/ew0kH2Uo8mI?feature=share)

## 🚀 Features

### 1. 🎙️ Robust Audio Recording
- Foreground service with persistent notification
- 30-second chunks with 2-second overlap for speech continuity
- Automatic chunk upload to transcription pipeline as each chunk is ready

### 2. 🤖 AI Transcription
- Google Gemini 2.5 Flash API for audio transcription
- Each 30s chunk transcribed independently and in correct order
- Saved to Room database as single source of truth
- Automatic retry on failure (up to 5 attempts)

### 3. 📝 AI Summary Generation
- Structured summary via Gemini 2.5 Flash API
- Real-time streaming — UI updates as response comes in
- 4 sections: Title, Summary, Action Items, Key Points
- Persists even if app is killed mid-generation (WorkManager)
- Graceful fallback if API is unavailable

## ⚡ Edge Cases Handled

| Edge Case | Handling |
|-----------|----------|
| 📞 Incoming/Outgoing phone calls | Auto pause → resume when call ends |
| 🔇 Audio focus loss | Pause with notification → resume on focus regain |
| 🎧 Bluetooth headset connect/disconnect | Continue recording, show notification |
| 🔌 Wired headset plug/unplug | Continue recording, show notification |
| 💾 Low storage | Graceful stop with error message |
| 💀 Process death | Session persisted in Room, FinalizeWorker resumes on restart |
| 🤫 Silent audio (10s) | Warning: "No audio detected - Check microphone" |
| 🔄 30s chunks + 2s overlap | Preserves speech continuity across boundaries |
| 🔒 Android 16 Live Updates | Lock screen timer, status, pause/stop actions |

## 🏗️ Architecture
```
com.twinmind
├── data
│   ├── local          # Room DB (entities, DAOs, database)
│   ├── remote         # Gemini REST API (Retrofit)
│   └── repository     # Single source of truth
├── di                 # Hilt dependency injection modules
├── domain
│   └── model          # Domain models
├── service            # RecordingService (foreground)
├── worker             # TranscriptionWorker, SummaryWorker, FinalizeWorker
├── ui
│   ├── dashboard      # Meeting list screen
│   ├── recording      # Active recording screen
│   ├── summary        # Summary display screen
│   ├── navigation     # NavGraph
│   └── theme          # TwinMind dark theme
└── util               # Constants, AudioUtils
```

## 🛠️ Tech Stack

| Technology | Usage |
|------------|-------|
| Kotlin | Primary language |
| Jetpack Compose | 100% declarative UI |
| MVVM | Architecture pattern |
| Hilt | Dependency injection |
| Room | Local database |
| Retrofit + OkHttp | REST API calls |
| WorkManager | Background processing + process death recovery |
| Coroutines + Flow | Async operations + streaming |
| Gemini 2.5 Flash | Transcription + Summary generation |

## 📋 Requirements

- Android 7.0+ (API 24)
- Microphone permission
- Internet connection (for AI features)
- Notification permission (Android 13+)

## 🔧 Setup & Build

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 11+

### Configuration
1. Clone the repository
```bash
git clone https://github.com/arvindmishra07/TwinMind-Android.git
```

2. Add your Gemini API key in `app/build.gradle.kts`:
```kotlin
buildConfigField("String", "GEMINI_API_KEY", "\"your_api_key_here\"")
```

3. Get a free API key at [aistudio.google.com/apikey](https://aistudio.google.com/apikey)

4. Build and run:
```bash
./gradlew assembleDebug
```

### Debug APK
Download the latest debug APK from the [Releases](https://github.com/arvindmishra07/TwinMind-Android/releases) section.

## 📦 Submission Checklist

- [x] Android APK (Debug Build)
- [x] Public GitHub Repository
- [x] Screen recording demo video

## 🎥 App Flow

1. **Dashboard** — View all past meetings, tap record button to start
2. **Recording Screen** — Live timer, waveform animation, status indicator, stop button
3. **Summary Screen** — AI-generated title, summary, action items, key points with streaming

## 📸 Screenshots

| Dashboard | Recording | Summary |
|-----------|-----------|---------|
| Meeting list with dark theme | Live recording with waveform | AI structured summary |