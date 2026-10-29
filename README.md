<p align="center">
  <a href="README.md"><img src="https://img.shields.io/badge/Sprache-Deutsch-black?style=for-the-badge" alt="Deutsch"></a>
  <a href="README-en.md"><img src="https://img.shields.io/badge/Language-English-blue?style=for-the-badge" alt="English"></a>
</p>

# 👻 Paranormal Toolkit

Eine Android-App für paranormale Untersuchungen – EVP-Aufnahmen, Spektralanalyse, eine echte **GhostBox** über Radio-Hardware und eine sensorgesteuerte **GhostApp**.

> **Ehrlich statt Fake:** Die GhostBox arbeitet ausschließlich mit echter Hardware. Ohne physische Antenne bzw. angeschlossenen Empfänger kommt **nichts** – es werden keine Geräusche, Wörter oder Signale simuliert.

---

## ✨ Features

### 🎙️ EVP Recorder
- Mono-Aufnahme als 16-bit-PCM-WAV (verlustfrei)
- Nutzt möglichst die unbearbeitete Mikrofonquelle (`UNPROCESSED`, Fallback `VOICE_RECOGNITION` / `MIC`); Rauschunterdrückung und automatische Verstärkung werden abgeschaltet, damit leise Geräusche nicht weggefiltert werden
- Einstellbare digitale Verstärkung (1x–20x, Standard 4x)
- Läuft als Foreground-Service weiter, damit lange Sessions nicht vom System beendet werden
- **Audio laden:** importiert beliebige Audiodateien, die das Gerät abspielen kann (MP3, M4A/AAC, OGG, FLAC, WAV, AMR …), und wandelt sie in Mono-WAV um

### 📊 Analyzer
- Wellenform und Spektrogramm (eigene FFT-Implementierung)
- Filter: Hochpass, Tiefpass, Bandpass mit Grenzfrequenz 100–8000 Hz
- Peak- oder RMS-Normalisierung, um leise Stellen hörbar zu machen
- Wiedergabe mit 0,25x–2x Geschwindigkeit – Tonhöhe bleibt erhalten
- Bearbeitetes Ergebnis als neue Aufnahme speichern (Original bleibt unverändert)
- Lange Aufnahmen werden für die Analyse automatisch heruntergerechnet, die Datei selbst wird nicht verändert

### 📻 GhostBox (Ghost Radio)
Sweept echte UKW-Frequenzen wie eine klassische Spirit Box. Zwei Signalwege:

| Modus | Hardware | Funktionsweise |
|---|---|---|
| **USB-SDR** | RTL-SDR-Dongle (RTL2832U) + Antenne über USB-OTG | Die App steuert den Dongle direkt, demoduliert FM selbst (inkl. De-Emphasis) und gibt den Ton live aus. Sweep über **87,5–108 MHz in 200-kHz-Schritten**, alle 250 ms ein Sprung. Frequenz auch manuell einstellbar. |
| **Companion FM** | Eingebauter UKW-Chip des Handys + FM-Radio-App (getestet mit der Xiaomi-Radio-App) | Die App schickt Standard-Medientasten (Play/Pause, Weiter, Zurück) an die laufende Radio-App – derselbe Weg wie Kopfhörer-Tasten. Automatischer Sweep mit ca. 3 s pro Sender, weil der Tuner Zeit zum Einrasten braucht. Der Ton kommt aus der Radio-App. |

⚠️ **Eine physische Antenne ist Pflicht.**
- **USB-SDR:** Antenne am Dongle.
- **Companion FM:** Kabelgebundene Kopfhörer müssen eingesteckt sein – das Kabel ist die Antenne. Außerdem muss UKW in der Radio-App tatsächlich eingeschaltet sein (nur App öffnen reicht nicht).

Fehlt Hardware oder Antenne, passiert schlicht nichts – es gibt kein simuliertes Ersatzsignal.

Unterstützte RTL-SDR-Dongles (die App bietet sich beim Einstecken automatisch an):
- Realtek `0bda:2832`, `0bda:2838` (R820T/R820T2)
- Dexatek-Rebrands `1d19:1101`–`1104`
- Terratec Cinergy T Stick Black, Terratec NOXON

### 💬 GhostApp
- Spricht Wörter per **Sprachausgabe (Text-to-Speech)**, ausgelöst durch Bewegungs- und Magnetfeldsensoren
- Die Sensoren bestimmen nur **wann** etwas passiert – **welches** Wort kommt, wird separat per `SecureRandom` gezogen, der nie Sensordaten sieht. So können Sensorwerte die Wortwahl weder absichtlich noch zufällig steuern
- Zusätzlich zufälliges Zeitfenster, damit der Auslöser nicht vorhersagbar ist
- Eingebaute Wortlisten auf **Deutsch** und **Englisch**
- Eigene Wortliste als Textdatei importierbar (ersetzt die eingebaute, jederzeit zurücksetzbar)
- Empfindlichkeit per Regler, Verlauf der gesprochenen Wörter (leerbar)
- Läuft bei ausgeschaltetem Bildschirm weiter; Sicherheitsabschaltung nach 8 Stunden, damit eine vergessene Session nicht den Akku leert

---

## 📱 Voraussetzungen

- Android **8.0 (API 26)** oder neuer
- Für GhostBox:
  - **USB-SDR:** USB-OTG-fähiges Handy, RTL-SDR-Dongle, Antenne
  - **Companion FM:** Handy mit eingebautem UKW-Chip, FM-Radio-App, die diesen nutzt, kabelgebundene Kopfhörer als Antenne

### Berechtigungen
| Berechtigung | Wofür |
|---|---|
| `RECORD_AUDIO` | EVP-Aufnahmen |
| `FOREGROUND_SERVICE*`, `POST_NOTIFICATIONS` | Aufnahmen/Sessions im Hintergrund |
| `WAKE_LOCK` | GhostApp bei ausgeschaltetem Bildschirm |
| USB Host (optional) | Zugriff auf RTL-SDR-Dongle |

---

## 🛠️ Build

Gebaut mit **Kotlin** und **Jetpack Compose** (Material 3), Java 17, compileSdk 36.

```bash
git clone https://github.com/justinsanjp/paranormal.git
cd paranormal
./gradlew assembleDebug
```

Die APK liegt danach unter `app/build/outputs/apk/debug/`.
Alternativ einfach in **Android Studio** öffnen und starten.

---

## 📂 Projektstruktur

```
app/src/main/java/com/paranormal/toolkit/
├── data/                 # Datenmodelle (Recording, SpokenWord, …)
├── features/
│   ├── recorder/         # EVP Recorder, WAV-Writer, Foreground-Service
│   ├── analyzer/         # Spektrogramm, FFT, Filter, Player
│   ├── ghostbox/         # RTL-SDR-Treiber, FM-Demodulator, Companion-FM
│   └── ghostapp/         # Sensor-Trigger, Wortliste, Service
├── navigation/           # Bottom-Navigation
└── ui/theme/             # Farben, Typografie, Theme
```

---

## ⚠️ Hinweis

Diese App ist ein Werkzeug für Hobby-Untersuchungen und zur Unterhaltung. Sie liefert keinen wissenschaftlichen Beweis für paranormale Phänomene. Was du hörst, ist echtes Radio, echtes Rauschen und echte Aufnahmen – die Interpretation liegt bei dir.

---

## 📄 Lizenz

Dieses Projekt steht unter der [MIT-Lizenz](LICENSE).
