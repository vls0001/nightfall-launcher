# Nightfall

A custom Fire TV home screen: big glowing clock, a night lake wallpaper, and a frosted row of your favourite apps.

## Install (no computer needed)

1. On the Fire TV, install **Downloader** from the Appstore.
2. Settings → My Fire TV → Developer options → **Install unknown apps** → turn on for Downloader.
   (No Developer options? Settings → My Fire TV → About → click the device name 7 times.)
3. Open Downloader and enter:
   `https://github.com/vls0001/nightfall-launcher/raw/main/dist/Nightfall.apk`
4. Install, then open **Nightfall**.

## Make the Home button open Nightfall

1. Settings → My Fire TV → Developer options → **ADB debugging** → On.
2. In Nightfall, open the **Settings** tile (gear) → **Link the Home button**.
3. When the TV asks "Allow USB debugging?", tick **Always allow** and choose **OK**.

Nightfall connects to the TV's own debugging service to switch on its Home button helper, so no computer is needed.

## Using it

- **Hold OK** (or press ☰) on a tile to move it, remove it, or see app info.
- **All apps** tile: every installed app. Hold OK (or ☰) to pin or unpin one.
- **Settings** tile: Fire TV settings, Home button linking, removing Amazon's tracking apps (undoable), resetting the row.

## Build

On Ubuntu:

```
sudo apt install aapt apksigner zipalign dalvik-exchange android-sdk-platform-23 openjdk-21-jdk-headless
./build.sh
```

The APK lands in `build/Nightfall.apk`.

Clock and UI font: [Rubik](https://fonts.google.com/specimen/Rubik), SIL Open Font License.
