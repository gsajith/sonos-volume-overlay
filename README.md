# Sonos volume overlay

An Android TV app that shows your Sonos soundbar's volume on screen.

Some TVs show no volume number for a third-party soundbar on HDMI ARC or eARC. Samsung is the common one. You press volume, the sound changes, and the screen shows nothing or a bare plus or minus. This app runs on an Android TV box like the Nvidia Shield and fixes that. Whenever the soundbar's volume or mute changes, it draws a small bar with the number in the top right corner. The bar fades after 2 seconds.

The app reads the volume from the speaker itself, so it catches every change: the TV remote, the box's remote and the Sonos app.

Not affiliated with Sonos or Nvidia.

## What you need

- An Android TV or Google TV box on Android 9 or later. Tested on an Nvidia Shield.
- A Sonos speaker on the same network. Tested on a Sonos Beam.
- A computer with `adb`, Java 17 or later and the Android SDK, to build and install the app.

## Install with a coding agent

Open this repo in your coding agent and ask it to install the app on your TV. [AGENTS.md](AGENTS.md) walks it through every step. Expect two things from you: turning on network debugging on the TV box, and accepting a prompt on the TV.

## Install by hand

1. Build the app:

   ```sh
   echo "sdk.dir=/path/to/android/sdk" > local.properties
   ./gradlew assembleDebug
   ```

   On macOS, `brew install --cask android-commandlinetools` installs the SDK at `/opt/homebrew/share/android-commandlinetools`. Accept its licenses with `yes | sdkmanager --sdk_root=<sdk path> --licenses` before the first build.

2. Turn on network debugging on the TV box. On a Shield, open Settings > Device Preferences > About and press Build 7 times. Then turn on Developer options > Network debugging. That row shows the box's IP.

3. Find your speaker's IP in your router's device list. To check you have the right one, open `http://<speaker-ip>:1400/xml/device_description.xml` in a browser. It names the room and model.

4. Install, grant the overlay permission and start the app:

   ```sh
   adb connect <box-ip>:5555        # accept the prompt on the TV
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell appops set com.gsajith.sonosvolume SYSTEM_ALERT_WINDOW allow
   adb shell am start -n com.gsajith.sonosvolume/.MainActivity --es ip <speaker-ip>
   ```

   Change the volume. The bar should appear.

5. Reserve the speaker's IP in your router's DHCP settings so it never changes. If it does change, rerun the last command with the new IP.

6. Turn network debugging back off. The app starts by itself when the box boots.

## Limits

- The bar shows only while the Android TV box is the TV's active input. Apps built into the TV and other HDMI devices get no bar.
- It follows one speaker. In a home theater set with a sub or surrounds, use the soundbar's IP.
- The app has no launcher icon. You set it up over `adb`.

## Troubleshooting

```sh
adb logcat -s SonosVolume
```

The log says when the speaker IP is missing, the overlay permission is missing or the speaker can't be reached.

## How it works

Sonos speakers answer UPnP requests on port 1400 on your home network, with no account or cloud involved. A foreground service sends `GetVolume` and `GetMute` requests to the speaker every 250 ms. When either value changes, the service draws the bar in an overlay window, which the `SYSTEM_ALERT_WINDOW` permission allows. A boot receiver restarts the service after a reboot. The code is four small Java classes in [`app/src/main/java/com/gsajith/sonosvolume`](app/src/main/java/com/gsajith/sonosvolume).

## License

MIT. See [LICENSE](LICENSE).
