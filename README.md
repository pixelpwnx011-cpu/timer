# Geneo Toolbox — Floating Overlay App

A floating "chat-head" style toolbox for Geneo smart boards. Shows a small
draggable bubble on top of every app; tapping it pops open an animated menu
with **Stopwatch**, **Timer**, and **Calculator**; each tool opens as its own
small draggable window with a close (✕) button. The bubble auto-starts on
every boot once you've completed setup a single time.

## What's included
- Full Android Studio (Kotlin) project, ready to open and build.
- No paid dependencies — only AndroidX + Material.

## How to build the APK

1. Install **Android Studio** (Hedgehog/Iguana or newer — free from
   developer.android.com).
2. Open Android Studio → **Open** → select the `GeneoOverlay` folder (the one
   containing `settings.gradle`).
3. Let Gradle sync (first sync downloads the Gradle 8.4 + AGP 8.2 toolchain —
   needs internet once).
4. Build the APK:
   - Menu: **Build → Build Bundle(s) / APK(s) → Build APK(s)**
   - Or in a terminal inside the project folder: `./gradlew assembleDebug`
5. The APK appears at:
   `app/build/outputs/apk/debug/app-debug.apk`
6. Copy that APK to the Geneo smart board (USB, ADB, or a file-share app)
   and install it (enable "install unknown apps" for that source first).

To produce a signed **release** APK for wider deployment, use
**Build → Generate Signed Bundle / APK**, create/select a keystore, and
build the `release` variant.

## First-time setup on the board (one-time only)
1. Open the **Geneo Toolbox** app.
2. Tap **"Allow display over other apps"** → grant the permission for Geneo
   Toolbox in the system settings screen that opens → go back to the app.
3. Tap **"Enable Floating Toolbox"**.
4. The bubble appears on screen immediately, and will now also reappear
   automatically after every future reboot — no need to open the app again.

## Using it
- **Single tap** the bubble → menu of 3 tools pops open with a bounce
  animation.
- **Tap the bubble again** → menu closes with a matching animation.
- **Drag** the bubble anywhere; release and it snaps to the nearest screen
  edge.
- Tap **Stopwatch / Timer / Calculator** in the menu → that tool opens as
  its own floating card (the menu auto-closes).
- Each tool card has a **header you can drag** to reposition it, and an
  **✕ button** top-right to close it.
- Multiple tools can be open at once; each remembers its own state
  independently while open.

## Project structure
```
app/src/main/java/com/geneo/smartboard/overlay/
 ├─ MainActivity.kt        – one-time setup screen (permission + enable)
 ├─ BootReceiver.kt        – restarts the overlay after every reboot
 ├─ OverlayService.kt      – foreground service: bubble, animated menu,
 │                           tool windows, drag/snap logic
 ├─ DragHelper.kt          – shared tap-vs-drag touch handling
 ├─ StopwatchController.kt – stopwatch tick/lap logic
 ├─ TimerController.kt     – countdown timer logic + vibration on finish
 ├─ CalculatorController.kt– 4-function calculator logic
 └─ Prefs.kt               – remembers "setup completed" for auto-boot-start
```

## Get an APK automatically from GitHub (no Android Studio needed)

This repo includes a GitHub Actions workflow (`.github/workflows/build.yml`)
that builds the APK for you in the cloud every time you push.

1. Create a new **empty** repository on GitHub (don't initialize it with a
   README/license — this project already has one).
2. Push this project to it:
   ```bash
   cd GeneoOverlay
   git init
   git add .
   git commit -m "Initial commit — Geneo floating toolbox"
   git branch -M main
   git remote add origin https://github.com/<your-username>/<your-repo>.git
   git push -u origin main
   ```
3. On GitHub, open the **Actions** tab. A workflow run called **"Build APK"**
   starts automatically — wait for the green checkmark (2–4 minutes).
4. Click into that run → scroll to **Artifacts** → download
   **`GeneoOverlay-debug-apk`**. It's a zip containing `app-debug.apk`,
   ready to install on the smart board.
   - A `GeneoOverlay-release-apk-unsigned` artifact is also produced; it's
     **unsigned**, so install the debug one unless you set up signing (see
     below).
5. Every future push to `main` rebuilds it automatically — just re-download
   the latest artifact from the newest run.

### Getting a downloadable Release instead of an Actions artifact
Artifacts expire after 90 days and require a GitHub login to download. For a
permanent link anyone can grab without logging in, push a version tag:
```bash
git tag v1.0
git push origin v1.0
```
This triggers the same workflow but also attaches the APKs to a proper
**GitHub Release** (visible on the repo's "Releases" sidebar) with a stable
download link.

### Signing the release APK (optional, for production rollout)
The `assembleRelease` build in CI is unsigned, so Android will refuse to
install it until it's signed. Easiest path: install the **debug APK** for
now (it works identically, just isn't optimized/signed) — fine for a
smart-board internal tool. To properly sign release builds later, generate a
keystore, add it as GitHub **Secrets**, and extend `app/build.gradle` with a
`signingConfigs` block referencing those secrets — ask if you'd like this
set up.

## Notes for customizing for Geneo branding
- Colors live in `app/src/main/res/values/colors.xml` (currently a blue/
  purple palette) — swap in Geneo's exact brand hex codes there.
- The launcher/bubble logo is a simple placeholder vector
  (`res/drawable/ic_launcher.xml`, `bg_bubble.xml` + `ic_bubble_grid.xml`) —
  replace with Geneo's actual logo asset for production.
- `applicationId` is `com.geneo.smartboard.overlay` in `app/build.gradle` —
  change to Geneo's real package name before publishing/deploying at scale.
