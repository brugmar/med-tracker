# Med Tracker

A small Android app to track medicine intake.

- **Today tab** — one button (card) per medicine. Tap it, confirm or adjust the dosage,
  and the dose is logged with the current date and time. Each card shows the total amount
  taken today and the number of doses.
- **Stats tab** — pick a medicine and a day to see the total and every single dose that day
  (entries can be corrected or deleted if logged by mistake), plus bar charts for the last 7 and last 30 days.
- **Medicines tab** — add, edit, or delete medicines. A medicine has a name, a default
  dose amount, a unit (mg, tablet, ml, …), a custom color, and three quick-dose buttons shown
  while logging a dose. Colors can be picked from pastel presets or set with hex/RGB controls.
  It can also save a printable, black-and-white **PDF report** for a chosen number of days
  (default 30, current day excluded — quick presets 7/30/90 or any
  value up to 366): a summary with charts, then week-by-week sections with a bar chart,
  sum and per-day average, and a day-by-day dose table. Backup export/import uses Android's
  document picker, including Google Drive when available. Deleting a medicine keeps its history.

Data is stored locally on the phone in an SQLite database (Room). No internet, no accounts.

Tech stack: Kotlin, Jetpack Compose (Material 3), Room, single-activity architecture.
`minSdk 26` (Android 8.0+), `targetSdk 35`.

## Samsung Galaxy S25+ compatibility

The app is configured for modern Samsung phones such as the **Samsung Galaxy S25+ 5G
SM-S936**: it targets Android 15/API 35, supports Android 8.0+, uses density-independent
Compose layouts, includes `arm64-v8a` APK support, and declares the main activity as
resizable with keyboard resize behavior enabled. Main screens and dialogs use scrolling or
wrapping controls so medicine names, units, quick-dose chips, and report options remain
usable on the S25+ 6.7" display.

---

## Project layout

```
app/src/main/java/com/medtracker/app/
├── MainActivity.kt          # entry point
├── AppViewModel.kt          # state + DB queries exposed as Flows
├── data/
│   ├── Models.kt            # Medicine & DoseLog entities, formatting helpers
│   └── AppDatabase.kt       # Room DAO + database
├── report/
│   ├── ReportData.kt        # report model: weeks, sums, averages
│   └── ReportPdf.kt         # printable B/W PDF renderer (PdfDocument)
└── ui/
    ├── AppScaffold.kt       # bottom navigation (Today / Stats / Medicines)
    ├── MainScreen.kt        # medicine buttons + "take dose" dialog
    ├── MedicinesScreen.kt   # manage medicines, backups, reports
    ├── StatsScreen.kt       # per-day view + week/month charts
    ├── BarChart.kt          # dependency-free Canvas bar chart
    └── theme/Theme.kt
```

---

## Testing on a Mac

### Option A — Android Studio (recommended)

1. **Install Android Studio** (skip if already done):
   ```bash
   brew install --cask android-studio
   ```
   or download it from <https://developer.android.com/studio>.
2. Launch Android Studio. On first run a setup wizard appears — choose **Standard** setup.
   It will detect the SDK already installed at `~/Library/Android/sdk` and reuse it.
3. **Open** this `MedTracker` folder (File → Open…) and wait for the Gradle sync to finish.
4. Create a virtual phone: **Tools → Device Manager → “+” / Create Virtual Device** →
   pick e.g. *Pixel 8* → system image **API 35 (arm64)** → Finish.
5. Press the green **Run ▶** button. The emulator boots and the app starts.

### Option B — command line only (no Android Studio)

Everything needed is already installed by the setup in this repo
(JDK 21 in `~/Library/Java/JavaVirtualMachines`, SDK in `~/Library/Android/sdk`):

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export ANDROID_HOME="$HOME/Library/Android/sdk"

# Build the APK
./gradlew assembleDebug          # result: app/build/outputs/apk/debug/app-debug.apk

# Run the unit tests
./gradlew testDebugUnitTest

# Start the emulator (AVD "MedTracker_Phone" is already created)
$ANDROID_HOME/emulator/emulator -avd MedTracker_Phone &

# Install + launch the app on the running emulator
$ANDROID_HOME/platform-tools/adb wait-for-device
./gradlew installDebug
$ANDROID_HOME/platform-tools/adb shell am start -n com.medtracker.app/.MainActivity
```

If the AVD is missing, recreate it with:

```bash
"$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd \
  -n MedTracker_Phone -d pixel_7 \
  -k "system-images;android-35;google_apis;arm64-v8a"
```

---

## Installing on a real Android phone

### Option A — via USB cable (easiest, keeps app updatable)

1. On the phone enable **Developer options**: Settings → About phone → tap **Build number**
   7 times.
2. In Settings → System → Developer options, enable **USB debugging**.
3. Connect the phone to the Mac with a USB cable. On the phone confirm the
   **"Allow USB debugging?"** prompt (check *Always allow*).
4. Either press **Run ▶** in Android Studio with your phone selected as target, or:
   ```bash
   export JAVA_HOME=$(/usr/libexec/java_home -v 21)
   ./gradlew installDebug
   ```
5. "Med Tracker" appears in the phone's app list.

### Option B — copy the APK (no cable needed)

1. Take `app/build/outputs/apk/debug/app-debug.apk` and get it onto the phone any way you
   like: Google Drive, email to yourself, Telegram/WhatsApp "saved messages", etc.
2. Open the file on the phone (via the Files app or the download notification).
3. Android will warn about installing apps from unknown sources — allow your Files/browser
   app to **Install unknown apps** when prompted.
4. Tap **Install**. Done.

> Note: this is a *debug* build, fine for personal use. If you ever want to publish or
> share it more widely, build a signed release: `./gradlew assembleRelease` plus a signing
> config (Android Studio: Build → Generate Signed App Bundle / APK).

### Wireless install (Android 11+, no cable)

1. Phone: Developer options → **Wireless debugging** → enable → **Pair device with pairing code**.
2. Mac (same Wi-Fi):
   ```bash
   export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"
   adb pair <ip>:<pairing-port>    # enter the 6-digit code shown on the phone
   adb connect <ip>:<port>         # the port from the main Wireless debugging screen
   ./gradlew installDebug
   ```

---

## Troubleshooting

| Problem | Fix |
| --- | --- |
| `SDK location not found` | `echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties` |
| Gradle complains about the JDK | `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` (Android Studio uses its own bundled JDK, so this only matters on the command line) |
| `adb: no devices found` | Re-plug the cable, confirm the debugging prompt on the phone, try `adb kill-server && adb devices` |
| Phone refuses APK install | Settings → Apps → Special app access → Install unknown apps → allow for your file manager |
| Emulator slow to boot first time | Normal; subsequent boots are much faster (snapshot) |
