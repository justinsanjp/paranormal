<p align="center">
  <a href="README.md"><img src="https://img.shields.io/badge/Sprache-Deutsch-black?style=for-the-badge" alt="Deutsch"></a>
  <a href="README-en.md"><img src="https://img.shields.io/badge/Language-English-blue?style=for-the-badge" alt="English"></a>
</p>

# 👻 Paranormal Toolkit

An Android app for paranormal investigations – EVP recording, spectral analysis, a real **GhostBox** driven by radio hardware, and a sensor-triggered **GhostApp**.

> **Honest, not fake:** The GhostBox works exclusively with real hardware. Without a physical antenna or connected receiver you get **nothing** – no sounds, words or signals are simulated.

---

## ✨ Features

### 🎙️ EVP Recorder
- Mono recording as 16-bit PCM WAV (lossless)
- Uses the unprocessed microphone source where available (`UNPROCESSED`, falling back to `VOICE_RECOGNITION` / `MIC`); noise suppression and automatic gain control are switched off so faint sounds aren't filtered out
- Adjustable digital gain (1x–20x, default 4x)
- Runs as a foreground service so long sessions aren't killed by the system
- **Load audio:** imports any audio file the device can play (MP3, M4A/AAC, OGG, FLAC, WAV, AMR …) and converts it to mono WAV

### 📊 Analyzer
- Waveform and spectrogram (custom FFT implementation)
- Filters: high-pass, low-pass, band-pass with a 100–8000 Hz cutoff
- Peak or RMS normalization to bring out quiet passages
- Playback at 0.25x–2x speed – pitch is preserved
- Save the processed result as a new recording (the original stays untouched)
- Long recordings are automatically downsampled for analysis; the file itself is not changed

### 📻 GhostBox (Ghost Radio)
Sweeps real FM frequencies like a classic spirit box. Two signal paths:

| Mode | Hardware | How it works |
|---|---|---|
| **USB SDR** | RTL-SDR dongle (RTL2832U) + antenna via USB OTG | The app drives the dongle directly, demodulates FM itself (incl. de-emphasis) and plays the audio live. Sweeps **87.5–108 MHz in 200 kHz steps**, one hop every 250 ms. Frequency can also be set manually. |
| **Companion FM** | The phone's built-in FM chip + an FM radio app (tested with Xiaomi's radio app) | The app sends standard media key events (play/pause, next, previous) to the running radio app – the same path headset buttons use. Automatic sweep at roughly 3 s per station, since the tuner needs time to lock. Audio comes from the radio app. |

⚠️ **A physical antenna is required.**
- **USB SDR:** antenna attached to the dongle.
- **Companion FM:** wired headphones must be plugged in – the cable is the antenna. FM must also actually be switched on in the radio app (just opening the app isn't enough).

If hardware or antenna is missing, simply nothing happens – there is no simulated fallback signal.

Supported RTL-SDR dongles (the app offers itself automatically when one is plugged in):
- Realtek `0bda:2832`, `0bda:2838` (R820T/R820T2)
- Dexatek rebrands `1d19:1101`–`1104`
- Terratec Cinergy T Stick Black, Terratec NOXON

### 💬 GhostApp
- Speaks words via **text-to-speech**, triggered by motion and magnetic field sensors
- Sensors only decide **when** something happens – **which** word is spoken is drawn separately from a `SecureRandom` that never sees sensor data, so sensor readings can't steer the word choice, deliberately or accidentally
- An additional randomized time window keeps the trigger unpredictable
- Built-in word lists in **German** and **English**
- Import your own word list as a text file (replaces the built-in one, can be reset any time)
- Sensitivity slider and a history of spoken words (clearable)
- Keeps running with the screen off; safety shutdown after 8 hours so a forgotten session doesn't drain the battery

---

## 📱 Requirements

- Android **8.0 (API 26)** or newer
- For GhostBox:
  - **USB SDR:** USB-OTG-capable phone, RTL-SDR dongle, antenna
  - **Companion FM:** phone with a built-in FM chip, an FM radio app that uses it, wired headphones as antenna

### Permissions
| Permission | Used for |
|---|---|
| `RECORD_AUDIO` | EVP recordings |
| `FOREGROUND_SERVICE*`, `POST_NOTIFICATIONS` | Recordings/sessions in the background |
| `WAKE_LOCK` | GhostApp with the screen off |
| USB host (optional) | Access to the RTL-SDR dongle |

---

## 🛠️ Build

Built with **Kotlin** and **Jetpack Compose** (Material 3), Java 17, compileSdk 36.

```bash
git clone https://github.com/justinsanjp/paranormal.git
cd paranormal
./gradlew assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/`.
Alternatively, just open the project in **Android Studio** and run it.

> Note: the app's UI is currently in German.

---

## 📂 Project structure

```
app/src/main/java/com/paranormal/toolkit/
├── data/                 # Data models (Recording, SpokenWord, …)
├── features/
│   ├── recorder/         # EVP recorder, WAV writer, foreground service
│   ├── analyzer/         # Spectrogram, FFT, filters, player
│   ├── ghostbox/         # RTL-SDR driver, FM demodulator, companion FM
│   └── ghostapp/         # Sensor trigger, word bank, service
├── navigation/           # Bottom navigation
└── ui/theme/             # Colors, typography, theme
```

---

## ⚠️ Disclaimer

This app is a tool for hobby investigations and entertainment. It does not provide scientific proof of paranormal phenomena. What you hear is real radio, real noise and real recordings – the interpretation is up to you.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
