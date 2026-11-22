# Pixel Sound & Vibration Quick Settings Tiles

A lightweight, open-source Android app designed specifically for **Google Pixel devices** (Pixel 6, 7, 8, 9, Fold, and newer running Android 12 through Android 15/16).

It restores two critical features that Google omitted from stock Pixel Quick Settings:
1. **Ringer Mode Pill**: Instant 1-tap cycling between **Ring** → **Vibrate** → **Silent** → **Ring** (no need to press physical volume buttons and tap the on-screen slider).
2. **Global Vibration Pill**: 1-tap master toggle for Pixel's **"Use vibration & haptics"** switch (`vibrate_on`), incoming call vibration, and touch haptic feedback.

---

## Why Google Pixel Needs This

Starting in Android 12 (Material You), Google redesigned the Quick Settings shade into large 2-column pills and **removed the standalone Sound / Vibrate toggle**. Pixel users were left with only two choices:
* Press a physical volume button, wait for the on-screen slider, and tap the tiny icon above it.
* Dig 4 levels deep into *Settings → Sound & vibration → Vibration & haptics* just to turn off haptics.

This app brings back native Quick Settings tiles that match Pixel's Material You design, typography, and tactile feedback.

---

## Pixel-Specific Architecture

### 1. Master "Use vibration & haptics" Switch (`vibrate_on`)
* On Pixel devices, turning off the top-level switch disables **all** tactile feedback (Gboard typing haptics, gesture navigation clicks, fingerprint unlock haptics, call vibration, and app notifications).
* This app toggles `Settings.System.putInt(resolver, "vibrate_on", ...)` in sync with `vibrate_when_ringing` and `haptic_feedback_enabled`.
* Automatically observes `Settings.System.getUriFor("vibrate_on")` so that if you toggle it inside Pixel's system Settings, the quick tile updates instantly.

### 2. DND Policy Access for Silent Mode
* Pixel devices strictly protect `AudioManager.RINGER_MODE_SILENT`. Calling it without `ACCESS_NOTIFICATION_POLICY` throws a `SecurityException`.
* The app provides a direct link to Pixel's *Do Not Disturb access* permission screen so it can switch into Silent mode seamlessly.

### 3. Crisp Pixel Haptics
* Uses Pixel's linear resonant actuator (LRA) via `VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)` to provide satisfying tactile clicks when toggling tiles.

### 4. Android 14 & 15 Background Policies
* Implements `startActivityAndCollapse(PendingIntent)` required by Pixel builds running Android 14+ to prevent background activity start restrictions.

---

## Required Permissions (Grant Once)

| Permission | Purpose on Pixel | Grant Method |
| :--- | :--- | :--- |
| **Do Not Disturb Access** (`ACCESS_NOTIFICATION_POLICY`) | Allows tile to enter **Silent** mode | Tap **Grant Access** in the app |
| **Modify System Settings** (`WRITE_SETTINGS`) | Allows tile to toggle **vibrate_on** & **haptics** | Tap **Grant Access** in the app |
| *(Optional)* **Write Secure Settings** | Deep system persistence across certain vendor builds | `adb shell pm grant com.example.quicktiles android.permission.WRITE_SECURE_SETTINGS` |

---

## How to Add Tiles to Your Pixel Quick Settings

1. Install and open **Pixel Quick Tiles** once to grant the two permissions.
2. Swipe down **twice** from the top of your screen to open the full Quick Settings grid.
3. Tap the **Pencil (Edit)** icon at the bottom-right corner of the quick settings shade.
4. Scroll down to the **Hold and drag to add tiles** section.
5. Locate **Ringer** and **Vibration**.
6. Touch and drag them up into your top 4 active tiles (so they appear on the very first pull-down swipe).
7. Tap the back arrow.

---

## Building and Installing

### In Android Studio
1. Open the project folder:
   `C:\Users\soumy\.gemini\antigravity\scratch\QuickTilesApp`
2. Connect your Pixel phone via USB (with **USB Debugging** enabled under *Developer options*).
3. Click **Run** (`Shift + F10`) to build and install the debug APK directly onto your Pixel.
