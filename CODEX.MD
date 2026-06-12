# MedTracker project notes for Codex

This file is a handoff brief for future Codex sessions working in this repo.
It summarizes the app, build setup, source map, important behavior, and known
working commands as of 2026-06-12.

Last refreshed by re-reading and updating the project on 2026-06-12.

## Project identity

- Name: Med Tracker / MedTracker.
- Path inspected: `/Users/m/claude code/MedTracker`.
- Android package/application id: `com.medtracker.app`.
- App type: single-activity native Android app.
- Purpose: track medicine intake locally on the phone.
- Data: stored only in local SQLite via Room. No accounts, no internet, no sync.
- Main tabs: Today, Stats, Medicines.

Important repo-state note: this directory did not contain a usable `.git`
repository when inspected. `git status --short` failed with "not a git
repository". Treat this as a plain workspace unless a future session sees a
real `.git` directory.

## Build and environment

- Gradle wrapper: Gradle `8.10.2`.
- Android Gradle Plugin: `8.7.3`.
- Kotlin: `2.0.21`.
- KSP: `2.0.21-1.0.27`.
- Compile SDK: `35`.
- Min SDK: `26`.
- Target SDK: `35`.
- Java/Kotlin target: JVM 17.
- README recommends JDK 21 for command-line runs on this machine:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export ANDROID_HOME="$HOME/Library/Android/sdk"
```

`local.properties` is ignored by Git and currently contains:

```properties
sdk.dir=/Users/m/Library/Android/sdk
```

If it is missing, recreate it with:

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

Common commands:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew installDebug
$ANDROID_HOME/platform-tools/adb shell am start -n com.medtracker.app/.MainActivity
```

Output APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Sandbox note for Codex: Gradle may need access to `~/.gradle/wrapper` and
`~/.gradle/caches`. In this session, `./gradlew testDebugUnitTest` first failed
inside the sandbox on a Gradle wrapper lock file, then passed when rerun with
normal filesystem access.

Verified in this session:

```text
./gradlew testDebugUnitTest
BUILD SUCCESSFUL in 2s
25 actionable tasks: 8 executed, 17 up-to-date
```

Only unit tests were run. No emulator or manual UI smoke test was run.

## File map

Top-level files:

- `settings.gradle.kts`: root project name `MedTracker`, includes `:app`, uses
  Google, Maven Central, and Gradle Plugin Portal repositories.
- `build.gradle.kts`: top-level plugin aliases only.
- `gradle/libs.versions.toml`: versions and dependency aliases.
- `app/build.gradle.kts`: Android application config and dependencies.
- `README.md`: user-facing setup, install, and troubleshooting notes.
- `.gitignore`: ignores Gradle, IDE, build, local SDK, Kotlin, and keystore files.

Main source tree:

```text
app/src/main/java/com/medtracker/app/
|-- MainActivity.kt
|-- AppViewModel.kt
|-- data/
|   |-- Models.kt
|   |-- Backup.kt
|   `-- AppDatabase.kt
`-- ui/
    |-- AppScaffold.kt
    |-- MainScreen.kt
    |-- MedicinesScreen.kt
    |-- StatsScreen.kt
    |-- BarChart.kt
    |-- EmptyState.kt
    |-- AppIcons.kt
    `-- theme/
        |-- Color.kt
        |-- Shape.kt
        |-- Theme.kt
        `-- Type.kt
```

Resources:

- `app/src/main/AndroidManifest.xml`: launcher activity `.MainActivity`,
  `android:allowBackup="true"`, label from `@string/app_name`.
- `app/src/main/res/values/strings.xml`: app label `Med Tracker`.
- `app/src/main/res/values/themes.xml`: no-action-bar platform theme; Compose
  draws the UI.
- `app/src/main/res/values/colors.xml`: launcher background color `#00696B`.
- `app/src/main/res/drawable/ic_launcher_foreground.xml`: vector capsule pill
  launcher foreground.
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`: adaptive icon.

Tests:

- `app/src/test/java/com/medtracker/app/FormatTest.kt`: JUnit tests for
  `formatAmount` and `parseAmount`.
- No instrumentation tests or Compose UI tests currently exist.

Generated/noisy dirs that may exist locally and should usually be ignored while
searching:

```text
.gradle/
.kotlin/
app/build/
```

Good search command:

```bash
rg --files -g '!app/build' -g '!.gradle' -g '!.kotlin'
```

## Dependencies

Production dependencies:

- AndroidX Core KTX `1.15.0`.
- Activity Compose `1.9.3`.
- Lifecycle runtime KTX and ViewModel Compose `2.8.7`.
- Compose BOM `2024.12.01`.
- Compose UI, UI graphics, UI tooling preview.
- Material 3.
- Room runtime and KTX `2.6.1`.
- Room compiler through KSP.

Debug/test dependencies:

- Compose UI tooling for debug.
- JUnit `4.13.2`.

No navigation library, DI framework, networking library, charting library, or
serialization library is present. The app uses manual tab state, direct Room
access from the ViewModel, and a custom Canvas chart.

## Architecture

`MainActivity.kt`

- Single `ComponentActivity`.
- Creates `AppViewModel` with `ViewModelProvider(this)`.
- Calls `enableEdgeToEdge()`.
- Sets content to `MedTrackerTheme { MedTrackerApp(viewModel) }`.
- Calls `viewModel.refreshToday()` in `onResume()` so an app left open across
  midnight keeps "today" correct.

`AppViewModel.kt`

- Extends `AndroidViewModel` to access `Application`.
- Gets DAO from `AppDatabase.get(application).dao()`.
- Exposes Room Flows as `StateFlow` with `stateIn` and
  `SharingStarted.WhileSubscribed(5_000)`.
- Holds `today` as a private `MutableStateFlow<LocalDate>`.
- Holds stats UI state as public `MutableStateFlow`s:
  `statsMedicineId` and `statsDay`.
- Actions are coroutine launches in `viewModelScope`.

Data flow:

- `medicines`: sorted list from Room.
- `todayStats`: logs from today's local day boundary, grouped by `medicineId`,
  returning total amount and dose count per medicine.
- `statsDayLogs`: logs for selected medicine on selected day.
- `statsRangeLogs`: logs for selected medicine over the last 30 days anchored to
  `today`; used by both 7-day and 30-day charts.

## Data model and persistence

Room database:

- Class: `AppDatabase`.
- File name: `medtracker.db`.
- Version: `4`.
- `exportSchema = false`.
- Migration `1 -> 2` recreates the `medicines` table with three quick-dose
  preset columns and backfills existing rows as 0.5x, 1x, and 2x their
  `defaultAmount`.
- Migration `2 -> 3` adds optional `dailyMaxAmount`.
- Migration `3 -> 4` adds optional `colorKey`.

Entities:

```kotlin
Medicine(
    id: Long = 0,
    name: String,
    defaultAmount: Double,
    unit: String,
    presetAmount1: Double = defaultAmount * 0.5,
    presetAmount2: Double = defaultAmount,
    presetAmount3: Double = defaultAmount * 2,
    dailyMaxAmount: Double? = null,
    colorKey: String? = null
)

DoseLog(
    id: Long = 0,
    medicineId: Long,
    medicineName: String,
    amount: Double,
    unit: String,
    timestamp: Long
)
```

Important persistence behavior:

- `DoseLog` snapshots `medicineName` and `unit` when a dose is logged. This keeps
  entry rows readable after a medicine is renamed or deleted.
- There are no foreign keys. Deleting a `Medicine` does not delete `DoseLog`
  rows.
- The current UI only lists active medicines in the Stats selector. Logs for a
  deleted medicine remain in the DB, but are not selectable through the current
  UI once the medicine row is gone.
- Day boundaries use `ZoneId.systemDefault()`.
- Timestamps are epoch milliseconds from `System.currentTimeMillis()`.
- `Medicine.presetAmounts()` returns the three quick-dose amounts displayed as
  chips in the dose logging dialog.
- `Medicine.colorKey` stores a user-selected custom color as `#RRGGBB`. Older
  palette keys such as `teal` or `coral` are still understood. If it is null,
  the UI falls back to the old stable id-based accent.
- `Backup.kt` encodes/decodes JSON backups with format
  `com.medtracker.app.backup`, version `1`. Backups include all medicines,
  quick-dose presets, daily max, chosen color, and all dose logs, including logs
  for medicines deleted from the active list.
- Changing database schemas requires incrementing the Room version and adding a
  migration or choosing a deliberate destructive dev-only strategy.

DAO queries:

- `medicines()`: all medicines ordered by `name COLLATE NOCASE`.
- `logsBetween(start, end)`: all logs in a time range, newest first.
- `logsForMedicineBetween(medicineId, start, end)`: selected medicine logs in a
  time range, newest first.
- `updateLog(log)`: updates a historical dose entry, currently used to correct
  its timestamp from the Stats screen.

Formatting helpers in `Models.kt`:

- `formatAmount(Double)`: renders whole values without `.0`, otherwise rounds to
  at most two decimal places and strips trailing zeros.
- `parseAmount(String)`: trims, accepts comma or dot decimal separators, returns
  `Double?`.
- `LocalDate.startMillis()`: local start-of-day epoch millis.
- `Long.toLocalDate()`: epoch millis to local date.
- `Long.toTimeString()`: `HH:mm`.
- `parseTime(String)`: accepts `H:mm` or `HH:mm` 24-hour time and returns
  `LocalTime?`.
- `LocalDate.atTimeMillis(LocalTime)`: selected local date plus a time converted
  to epoch millis.

Use these helpers for any dosage/date UI instead of reimplementing formatting.

## UI behavior

Root UI: `ui/AppScaffold.kt`

- `MedTrackerApp` owns local tab state with `rememberSaveable`.
- Uses Material 3 `Scaffold` with top app bar, snackbar host, and bottom
  navigation.
- Tabs:
  - `0`: Today / `MainScreen`.
  - `1`: Stats / `StatsScreen`.
  - `2`: Medicines / `MedicinesScreen`.
- Content transitions with `Crossfade`.

Today screen: `ui/MainScreen.kt`

- Empty state if there are no medicines, with an action to switch to Medicines.
- Header shows a time-of-day greeting and today's date.
- Summary card shows how many medicines have at least one log today.
- Each medicine card shows:
  - Monogram avatar using stable medicine accent.
  - Medicine name.
  - Default dose.
  - Today's total/count if logged today, otherwise an add icon.
- Tapping a medicine opens `TakeDoseDialog`.
- Dose dialog:
  - Starts with the default amount.
  - Shows the three saved quick-dose chips from the selected medicine.
  - Allows manual decimal input.
  - Has plus/minus step buttons that adjust the amount by one default dose.
    The minus button is disabled if subtracting would make the amount zero or
    negative.
  - The minus control is a drawn `Box` bar inside a `FilledTonalIconButton`
    because the currently used Material icon set does not include a core minus
    icon.
  - Requires amount > 0.
  - Logs the dose and shows a snackbar.

Medicines screen: `ui/MedicinesScreen.kt`

- Uses an inner transparent `Scaffold` with an extended FAB.
- Empty state if there are no medicines.
- Add/edit dialog captures name, default dose, unit, a custom color, and three
  quick-dose preset amounts. The medicine form shows a compact color summary;
  tapping Choose opens a separate color chooser with 8 pastel preset swatches
  plus hex input and RGB sliders.
- For new medicines, quick-dose fields auto-fill from the default dose as 0.5x,
  1x, and 2x until the user edits a quick dose.
- Valid only when name and unit are non-blank and all dose amounts parse to > 0.
- Backup actions live at the top of the screen. Export creates a JSON document
  through Android's Storage Access Framework. Import opens a JSON/text document,
  asks for confirmation, and replaces all local medicines and dose history.
  Google Drive works through the system picker when the Drive app/provider is
  available; there is no direct Google Drive API dependency.
- Each existing medicine row has a report action. It opens a PDF report dialog
  with 7/30/90 day presets or any whole number from 1 to 366; the report excludes
  the current day and saves through Android's Storage Access Framework.
- Delete dialog warns that prior dose history stays in storage.
- Existing rows show report, edit, and delete icon buttons.

Stats screen: `ui/StatsScreen.kt`

- Empty state if there are no medicines.
- Maintains a valid selected medicine: if selection is missing or deleted, it
  selects the first current medicine.
- Medicine filter chips are horizontally scrollable and colored by medicine
  accent.
- Day card:
  - Previous/next day buttons.
  - Next day disabled once the selected day is today.
  - Shows "Today", "Yesterday", or a formatted date.
  - Shows total amount and dose count.
  - Shows each dose with time, amount/unit, edit-time action, and delete action.
  - Edit-time dialog accepts a 24-hour `HH:mm` value and rewrites only the time
    portion of the log timestamp, keeping the selected stats day as the date.
- Charts:
  - Last 7 days and last 30 days.
  - Both use the selected medicine's last 30 days of logs.
  - Daily totals are grouped by local date.
  - Chart totals include today; the displayed average and dashed average line
    exclude today, so 7-day charts average the previous 6 days and 30-day
    charts average the previous 29 days.
  - 7-day chart shows values above bars; 30-day chart labels today and every
    fifth day counting backwards.
- PDF report generation lives in the Medicines tab, not on Stats.

Chart: `ui/BarChart.kt`

- Custom Compose `Canvas`, no chart dependency.
- One bar per value.
- Rounded-top bars with vertical gradients.
- Highlights `highlightIndex`, defaulting to today (`values.lastIndex`).
- Draws a dashed average line when average > 0. Callers can pass
  `averageValue` to override the default average of all chart values.
- Shows centered empty text if all values are zero.

Shared UI:

- `EmptyState.kt`: centered icon badge, title, body, optional action.
- `AppIcons.kt`: custom vector `Pill` and `Chart` icons, black paths tinted by
  `Icon`.
- `MedicineAvatar` lives in `MainScreen.kt` and is reused by other screens.

## Theme and design conventions

Theme files:

- `Theme.kt`: `MedTrackerTheme`, dynamic color off by default.
- `Color.kt`: custom light/dark Material 3 color schemes and medicine accents.
- `Type.kt`: custom typography based on system font.
- `Shape.kt`: rounded Material shapes.

Design conventions to preserve:

- Material 3 components.
- Soft teal brand palette with honey tertiary and per-medicine accent colors.
- User-selected medicine color lives in `medicine.colorKey` as `#RRGGBB`; null
  falls back to the stable legacy accent chosen by `medicine.id`.
- Rounded surfaces using `MaterialTheme.shapes.large`.
- Keep UI offline and task-focused; avoid adding account/network assumptions.
- Prefer existing private composable style in each screen file.
- For new icons, prefer existing Material icons when available; use custom
  `ImageVector` only for app-specific marks.

## Known caveats and gotchas

- Do not search generated files unless needed. `app/build` exists locally and can
  produce enormous, irrelevant search output.
- No dependency injection exists. The ViewModel directly obtains the singleton
  Room database.
- No navigation component exists. Tab switching is local integer state in
  `MedTrackerApp`.
- Room migrations currently cover quick-dose presets (`1 -> 2`), daily maximums
  (`2 -> 3`), and medicine colors (`3 -> 4`). Any future schema change needs
  explicit migration work.
- `DoseLog.amount` is `Double`, and display rounds to two decimals. Be careful if
  adding features that require exact fractional arithmetic.
- Logs for deleted medicines are preserved but not exposed by the current Stats
  picker.
- `StatsScreen` day totals label the total with the currently selected
  medicine's current unit, while individual historical rows use each log's
  snapshotted unit.
- Dates and day grouping use the device's current time zone.
- `formatAmount` is covered by unit tests; UI behavior is not covered by tests.
- Some UI source files intentionally contain typographic punctuation in display
  strings and comments. Keep new edits ASCII by default, but do not churn
  existing user-visible copy just to normalize punctuation.

## Suggested workflow for future changes

1. Start by reading `README.md`, this file, and the files related to the target
   feature.
2. Use `rg --files -g '!app/build' -g '!.gradle' -g '!.kotlin'` for source lists.
3. Keep changes scoped to the single-module Compose/Room architecture unless the
   requested feature clearly needs more structure.
4. Reuse `formatAmount`, `parseAmount`, and date helpers.
5. If changing persistence, plan the Room migration first.
6. Run at least:

```bash
./gradlew testDebugUnitTest
```

For UI-affecting changes, also build or install:

```bash
./gradlew assembleDebug
./gradlew installDebug
```

An emulator described in `README.md` may be available as `MedTracker_Phone`.
