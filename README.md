# Nightfall

A custom Fire TV home screen: big glowing clock, an animated night lake (twinkling stars, drifting
water, moonlight), a frosted row of your favourite apps with a gliding focus glow, live weather, and a
row of recently opened apps.

## Install (no computer needed)

1. On the Fire TV, install **Downloader** from the Appstore.
2. Settings → My Fire TV → Developer options → **Install unknown apps** → turn on for Downloader.
   (No Developer options? Settings → My Fire TV → About → click the device name 7 times.)
3. Open Downloader and enter:
   `https://github.com/vls0001/nightfall-launcher/raw/main/dist/Nightfall.apk`
4. Install, then open **Nightfall**.

## Make Nightfall your home screen

1. Settings → My Fire TV → Developer options → **ADB debugging** → On.
2. In Nightfall, open the **Settings** tile (gear) → **Home screen**, then pick:
   - **Link the Home button**: Nightfall opens whenever you press Home; Amazon's home stays installed as a backup.
   - **Replace Amazon home completely**: switches Amazon's home screen off so Nightfall is the TV's real home screen.
     Undo any time with **Restore Amazon home**. If the TV ever gets stuck, hold **Back + Right** on the remote
     for 10 seconds to factory reset.
3. When the TV asks "Allow USB debugging?", tick **Always allow** and choose **OK**.

Nightfall connects to the TV's own debugging service (127.0.0.1:5555) to make these changes, so no computer is needed.

## Updates

Settings → **Check for updates** downloads the newest version from this repo and installs it.

## Using it

- **Hold OK** (or press ☰) on a tile to move it, remove it, or see app info.
- **All apps** tile: every installed app. Hold OK (or ☰) to pin or unpin one.
- **Recent row** under the apps: the last apps you opened. Hold OK on one to pin it.
- **Settings** tile: Fire TV settings, home screen takeover, removing Amazon's tracking apps (undoable), clock
  format, weather on/off, recent row on/off, updates, and resetting the row.

Weather uses your internet address for a rough location (ipapi.co, falling back to ipwho.is) and
open-meteo.com for the forecast. Turn it off in Settings if you'd rather not.

## Build

On Ubuntu:

```
sudo apt install aapt apksigner zipalign dalvik-exchange android-sdk-platform-23 openjdk-21-jdk-headless
./build.sh
```

The APK lands in `build/Nightfall.apk`. Releases: copy it to `dist/`, and bump `VERSION_CODE` in
`build.sh` and `versionCode` in `dist/version.json` so the in-app updater sees it.

To see the real screens without a TV, run `tools/screenshots/run.sh` (see its README).

Clock and UI font: [Rubik](https://fonts.google.com/specimen/Rubik), SIL Open Font License.
