# Human Weapon OS — Android APK + Samsung Galaxy Watch 4 Classic (EEPD) Sync

This Android project packages **Human Weapon OS** into a native Android `.apk` with an on-device **Android Health Connect** bridge (`MainActivity.kt`) that reads biometrics synced from your **Samsung Galaxy Watch 4 Classic (EEPD)** via **Samsung Health**.

---

## Step 1: Link Your Galaxy Watch 4 Classic (EEPD) to Health Connect on Your Phone

Samsung's official architecture routes Galaxy Watch 4 Classic data through **Samsung Health** into Android's on-device **Health Connect** hub:

1. Ensure your **Galaxy Watch 4 Classic (EEPD)** is connected via Bluetooth in the **Galaxy Wearable** app and open **Samsung Health** on your phone (pull down on the home screen to sync your watch).
2. In **Samsung Health**, tap the three-dot menu (`⋮`) in the top right -> **Settings** -> **Health Connect**.
3. Tap **App permissions** -> **Samsung Health**, and toggle **Allow all** (or enable **Steps**, **Sleep**, **Heart rate**, **Exercise**, and **Active calories burned**) under Write permissions.

---

## Step 2: Build the `.apk` (2 Easy Ways)

### Option A: Build in Android Studio (Desktop)
1. Unzip `human-weapon-os-android.zip` and open the folder in **Android Studio** (Ladybug / Koala or newer).
2. Let Gradle sync, then click **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.
3. Copy `app/build/outputs/apk/debug/app-debug.apk` to your Android phone and tap it to install.

### Option B: Build in the Cloud on GitHub Actions (No Android Studio Needed)
1. Create a free repository on GitHub and upload the contents of this zip folder.
2. Open the **Actions** tab in your GitHub repository and click **Build Human Weapon OS Android APK -> Run workflow**.
3. In ~2 minutes, download `human-weapon-os-debug-apk` from the workflow summary page and install `app-debug.apk` on your phone.

---

## Step 3: Using Watch Sync Inside the App
1. Open **Human Weapon OS** on your phone and tap **`↻ Sync Galaxy Watch 4 Classic`** (or open the **Watch Sync** tab).
2. On first tap, Android's **Health Connect** permission sheet will appear—grant Read access to **Human Weapon OS**.
3. Your Watch 4 Classic **Steps**, **Sleep hours**, **Average/Resting Heart Rate**, **Active Calories**, and **Workout Minutes** will populate automatically and auto-check your daily habits:
   - **Steps >= 8,000:** Auto-checks **Evening walk**
   - **Sleep >= 7.0 hours:** Auto-fills Night Check-in Sleep hours and auto-checks **Sleep by 10 PM**
   - **Workout >= 25 minutes:** Auto-checks **Morning training**
