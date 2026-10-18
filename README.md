# Swish

**Swish** is an Android gesture-control app. Point your index finger at the front camera, draw a shape in the air, close your hand into a fist, and Swish recognises the shape and launches the action bound to it — opening an app, a website, or the dialer.

Hand tracking runs on-device with **Google MediaPipe**, and the drawn shape is classified by a small custom **TensorFlow Lite** model. Nothing is sent to a server.

<p align="center">
  <a href="https://github.com/samay312006/swish/releases/latest/download/Swish.apk"><img alt="Download APK" src="https://img.shields.io/badge/Download-Swish.apk-3DDC84?style=for-the-badge&logo=android&logoColor=white"></a>
  &nbsp;
  <a href="https://github.com/samay312006/swish/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/samay312006/swish?style=for-the-badge"></a>
</p>

> **Just want to use it?** [Download `Swish.apk`](https://github.com/samay312006/swish/releases/latest/download/Swish.apk) and follow [Install the app](#install-the-app-no-android-studio-needed). No Android Studio, no building.

---

## Table of contents

- [Features](#features)
- [How it works](#how-it-works)
- [Gestures](#gestures)
- [Tech stack](#tech-stack)
- [Install the app (no Android Studio needed)](#install-the-app-no-android-studio-needed)
- [Requirements](#requirements)
- [Build from source](#build-from-source)
- [Using the app](#using-the-app)
- [Customising](#customising)
- [Project structure](#project-structure)
- [Troubleshooting](#troubleshooting)
- [Known limitations](#known-limitations)

---

## Features

- **Real-time hand tracking** from the front camera (CameraX + MediaPipe Hand Landmarker, 30–60 fps target).
- **Air drawing** — a pointing hand draws a glowing neon trail with a particle effect on screen.
- **Explicit start/stop state machine** — pointing starts a stroke, a fist ends it, an open palm pauses.
- **On-device gesture classification** with a TensorFlow Lite model (5 shapes).
- **Gesture → action mapping** — launch Netflix, Spotify, WhatsApp, a website, or the dialer.
- **Floating launcher bubble** — a draggable overlay button that stays on top of other apps and opens the drawing screen from anywhere, kept alive by a foreground service.

## How it works

```
Front camera (CameraX, RGBA_8888)
        │
        ▼
HandTracker ── MediaPipe Hand Landmarker (21 landmarks, 1 hand, LIVE_STREAM)
        │
        ▼
MainActivity state machine
   ├─ POINTING  (index extended, other fingers folded) → add smoothed fingertip point
   ├─ FIST      (all fingers folded)                   → stroke finished → classify
   └─ IDLE      (anything else)                        → pause, nothing recorded
        │
        ▼
GesturePathManager ── EMA smoothing, rolling buffer (60 pts), bounding-box normalisation to [0,1]
        │
        ▼
GestureClassifier ── resample to 64 evenly spaced points → 128 floats → gesture_model.tflite
        │              (prediction accepted only if confidence > 60%)
        ▼
executeGestureAction() ── fires an Intent (open app / URL / dialer)
```

Key details:

- **Finger state** is decided by distance from the wrist: a finger counts as extended when its tip is farther from the wrist than its PIP joint.
- **Debouncing** — a stroke only starts after 5 consecutive "pointing" frames, so brief misdetections don't start a drawing.
- **Jitter reduction** — the fingertip position is smoothed with an exponential moving average, and points closer than 0.005 (normalised units) to the previous one are dropped.
- **Size invariance** — the path is normalised to its bounding box, so a small circle and a large circle look the same to the model.

## Gestures

| Shape drawn | Action |
|-------------|--------|
| `circle` | Opens https://www.google.com in the browser |
| `heart`  | Opens the dialer with a pre-filled number (see [Customising](#customising)) |
| `N`      | Opens **Netflix** (`com.netflix.mediaclient`) |
| `M`      | Opens **Spotify** (`com.spotify.music`) |
| `W`      | Opens **WhatsApp** (`com.whatsapp`) |

If the target app isn't installed you'll see an "App not installed on this device!" toast. Low-confidence predictions are reported as `Unknown` and do nothing.

## Tech stack

| Area | Library / tool | Version |
|------|----------------|---------|
| Language | Kotlin | 2.0.21 |
| Build | Android Gradle Plugin / Gradle wrapper | 9.0.1 / 9.2.1 |
| Camera | AndroidX CameraX (core, camera2, lifecycle, view) | 1.3.1 |
| Hand tracking | MediaPipe Tasks Vision (`hand_landmarker.task`) | 0.10.14 |
| Classification | TensorFlow Lite | 2.16.1 |
| UI | AppCompat, Material Components, ConstraintLayout | 1.6.1 / 1.11.0 / 2.2.1 |

## Install the app (no Android Studio needed)

1. **On your Android phone**, open this link: **[Swish.apk](https://github.com/samay312006/swish/releases/latest/download/Swish.apk)**. You can also find it under the [latest release](https://github.com/samay312006/swish/releases/latest) → *Assets*.
2. Open the downloaded file. If Android says installs from this source aren't allowed, tap **Settings**, turn on **Allow from this source** for your browser or file manager, and go back.
3. Tap **Install**. If Google Play Protect warns about an unknown app, tap **More details ▸ Install anyway**. That warning appears for any app that isn't from the Play Store.
4. Open **Gesture System** (that's the app's name on your home screen) and grant the permissions it asks for. See [step 7](#7-grant-permissions-on-first-launch) for what each one does.

**Requirements:** Android **8.0 or newer** with a front camera. Everything runs on the phone, so no account or internet connection is needed.

**Updating:** download the new `Swish.apk` and install it over the old one. There's no need to uninstall first.

---

## Requirements

> Only needed if you want to **build Swish yourself**. To just use the app, see [Install the app](#install-the-app-no-android-studio-needed).


**Development machine**

- **Android Studio** — a recent release that supports **AGP 9.0** (update Android Studio if Gradle sync complains about the plugin version).
- **JDK 21** — the Gradle daemon is configured for JDK 21 (`gradle/gradle-daemon-jvm.properties`). Android Studio's bundled JBR works; Gradle can also download it automatically via the Foojay toolchain resolver.
- **Android SDK Platform 34** (installed through the SDK Manager).
- Internet access for the first Gradle sync (dependencies are fetched from Google Maven and Maven Central).

**Device**

- Android **8.0 (API 26)** or newer. Target SDK is 34 (Android 14).
- A **front-facing camera** (declared as required in the manifest).
- A **physical device is strongly recommended**. Emulators usually have a poor or virtual camera and are too slow for real-time hand tracking.

## Build from source

### 1. Clone the repository

```bash
git clone https://github.com/samay312006/swish.git
cd swish
```

### 2. Open the project in Android Studio

1. Launch Android Studio → **File ▸ Open…** → select the cloned `swish` folder (the one containing `settings.gradle.kts`).
2. Wait for **Gradle sync** to finish. The first sync downloads Gradle 9.2.1, AGP and all dependencies, which can take several minutes.
3. If prompted, install any missing SDK components (Android SDK Platform 34, Build Tools).

Android Studio creates `local.properties` with your SDK path automatically. It is git-ignored and should not be committed.

### 3. Check the model files

Both ML models ship in the repo under `app/src/main/assets/`:

| File | Purpose |
|------|---------|
| `hand_landmarker.task` (~7.8 MB) | MediaPipe hand landmark model |
| `gesture_model.tflite` (~44 KB) | Custom shape classifier |

No download is needed. If either file is missing the app can't track hands or classify shapes.

### 4. (Optional) Set the "heart" contact number

Open `app/src/main/java/com/example/swish/MainActivity.kt` and change the placeholder at the bottom of the class:

```kotlin
companion object {
    private const val HEART_CONTACT_NUMBER = "0000000000"
}
```

The app uses `ACTION_DIAL`, so it only **pre-fills** the dialer. You still press call yourself, and no phone permission is needed.

### 5. Prepare your phone

1. Enable **Developer options**: *Settings ▸ About phone* → tap **Build number** seven times.
2. Enable **USB debugging** in *Settings ▸ System ▸ Developer options*.
3. Connect the phone by USB (or pair over Wi-Fi with **Wireless debugging**) and accept the debugging prompt.

### 6. Build and run

- In Android Studio, pick your device in the toolbar and press **Run ▶** (`Shift+F10`).
- Or from the command line:

  ```bash
  # macOS / Linux
  ./gradlew installDebug

  # Windows
  gradlew.bat installDebug
  ```

  The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### 7. Grant permissions on first launch

| Permission | Why it's needed | How it's granted |
|------------|-----------------|------------------|
| Camera | Hand tracking | System prompt on first launch |
| Display over other apps | Floating launcher bubble | Swish opens the settings page. Toggle it on, then return to the app |
| Notifications (Android 13+) | Shows the foreground-service notification ("Swish is Active") | Not requested by the app. Enable it in *Settings ▸ Apps ▸ Gesture System ▸ Notifications* if you want to see it (the service runs either way) |

The app's launcher label is **Gesture System**.

## Using the app

1. Open **Gesture System**. The overlay bubble starts, and a dimmed, see-through drawing screen appears over whatever is underneath. The camera feed itself is hidden. Your detected hand landmarks show as red dots.
2. Hold your hand in front of the **front camera**, about 30–60 cm away, in good lighting.
3. **Point** with your index finger (other fingers curled). After a few frames a neon green trail starts following your fingertip.
4. **Draw** one of the [supported shapes](#gestures) in the air, smoothly and at a moderate speed.
5. **Make a fist** to finish. Swish shows `Recognized: <shape>`, runs the bound action and closes the drawing screen.
6. To draw again from anywhere, tap the **floating bubble**. Drag it to move it around the screen.

Tips:

- An open palm pauses tracking without ending the stroke.
- Very short strokes (≤ 15 points) are rejected with "Gesture too short to recognize".
- Use `adb logcat -s GestureClassifier` to see the model's confidence for every class after each gesture.

## Customising

### Change what a gesture does

Edit `executeGestureAction()` in `MainActivity.kt`. To launch a different app, call `openApp("<package.name>")`. Because of Android 11+ package-visibility rules, you **must also** add the package to the `<queries>` block in `AndroidManifest.xml`:

```xml
<queries>
    <package android:name="com.example.yourapp" />
</queries>
```

### Add or retrain gestures

The classifier (`GestureClassifier.kt`) expects:

- **Input:** a `float32` tensor of **128** values: 64 `(x, y)` points, interleaved `x0, y0, x1, y1, …`, normalised to `[0, 1]` and resampled to be evenly spaced along the path.
- **Output:** a `float32` tensor of shape `[1, N]` with class probabilities.
- **Labels:** `listOf("heart", "M", "N", "W", "circle")`. This order **must exactly match** the label order used when the model was trained.

To add a gesture:

1. Collect samples. `OverlayView.triggerSaveGesture()` appends the current path to `swish_training_data.csv` in the app's external files directory (`/sdcard/Android/data/com.example.swish/files/`). Each row is `label,x0,y0,x1,y1,…`. Set `currentTrainingLabel` to the shape you're recording and call `triggerSaveGesture()` when the stroke ends (it isn't wired to the fist event by default).
2. Pull the CSV with `adb pull /sdcard/Android/data/com.example.swish/files/swish_training_data.csv`.
3. Train a model with the input/output shapes above (the original was trained in Google Colab), resampling each sample to 64 points the same way `resamplePath()` does.
4. Export it to TFLite, replace `app/src/main/assets/gesture_model.tflite`, update `labels` in `GestureClassifier.kt`, and add a branch in `executeGestureAction()`.

### Tuning knobs

| Setting | Location | Default |
|---------|----------|---------|
| Confidence threshold | `GestureClassifier.classify()` | `0.60` |
| Frames before a stroke starts | `MainActivity.pointingStartThreshold` | `5` |
| Fingertip smoothing (EMA) | `MainActivity.emaAlpha` | `0.4` |
| Path buffer length | `GesturePathManager.bUFFERLIMIT` | `60` points |
| Hand detection confidence | `HandTracker` | `0.5` |

## Project structure

```
swish/
├── app/
│   ├── build.gradle.kts                 # Module config: SDK levels, dependencies
│   └── src/main/
│       ├── AndroidManifest.xml          # Permissions, overlay service, <queries>
│       ├── assets/
│       │   ├── hand_landmarker.task     # MediaPipe hand model
│       │   └── gesture_model.tflite     # Custom shape classifier
│       ├── java/com/example/swish/
│       │   ├── MainActivity.kt          # Camera setup, hand-state machine, actions
│       │   ├── HandTracker.kt           # MediaPipe Hand Landmarker wrapper
│       │   ├── GesturePathManager.kt    # Smoothing, buffering, normalisation
│       │   ├── GestureClassifier.kt     # Resampling + TFLite inference
│       │   ├── OverlayView.kt           # Neon trail, particles, landmarks, CSV export
│       │   └── FloatingWidgetService.kt # Draggable overlay bubble (foreground service)
│       └── res/
│           ├── layout/activity_main.xml          # Camera preview + overlay
│           └── layout/layout_floating_widget.xml # Floating bubble
├── gradle/libs.versions.toml            # Version catalog
├── build.gradle.kts
└── settings.gradle.kts
```

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Gradle sync fails with an AGP / plugin version error | Update Android Studio to a version that supports AGP 9.0, or check *Settings ▸ Build Tools ▸ Gradle ▸ Gradle JDK* is set to JDK 21. |
| `SDK location not found` | Open the project once in Android Studio (it writes `local.properties`), or create it with `sdk.dir=/path/to/Android/sdk`. |
| No red landmark dots appear | Check that the camera permission was granted, and that no other app is using the camera. (The camera preview is intentionally invisible.) |
| Trail never starts | Curl your middle, ring and little fingers fully. The pointing pose must be clear. Improve the lighting. |
| Always `Unknown` | Draw larger, smoother shapes and finish with a clear fist. Check confidences with `adb logcat -s GestureClassifier`. |
| "App not installed on this device!" | Install the target app, or remap the gesture (and update `<queries>`). |
| Floating bubble doesn't appear | Grant *Display over other apps* for Gesture System, then reopen the app. |
| Service stops in the background | Some manufacturers (Xiaomi, Oppo, Samsung, …) kill background services. Disable battery optimisation for the app. |

## Known limitations

- Only **one hand** is tracked at a time (for speed).
- The model recognises a fixed set of **5 shapes**. Anything else is either `Unknown` or misclassified as the closest match.
- The drawing screen closes after each recognised gesture. Reopen it from the floating bubble.
- The training-data export in `OverlayView` is a developer tool and isn't connected to the UI.
- `app/build.gradle.kts` declares Appium/Selenium test dependencies, but no UI tests have been written yet.
