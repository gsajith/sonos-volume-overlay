# Sonos volume overlay

A small Android TV app for the Nvidia Shield. It shows a volume bar in the top right corner whenever the Sonos Beam's volume or mute state changes.

Samsung TVs show no volume number for third-party soundbars on HDMI ARC/eARC. This app asks the Beam for its volume over the local network four times a second (port 1400, no account needed) and draws the bar itself. Changes from the Shield remote, the TV remote and the Sonos app all show up.

## Build

```sh
brew install --cask android-commandlinetools
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
yes | sdkmanager --sdk_root=$ANDROID_HOME --licenses
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

## Install on the Shield

1. On the Shield, open Settings > Device Preferences > About and press Build 7 times to unlock developer options.
2. In Settings > Device Preferences > Developer options, turn on Network debugging. It shows the Shield's IP.
3. From the Mac:

```sh
adb connect <shield-ip>:5555        # accept the prompt on the TV
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell appops set com.gsajith.sonosvolume SYSTEM_ALERT_WINDOW allow
adb shell am start -n com.gsajith.sonosvolume/.MainActivity --es ip 192.168.0.174
```

The last command saves the Beam's IP and starts the app. After that the app starts itself whenever the Shield boots.

Reserve 192.168.0.174 for the Beam in your router's DHCP settings so the address doesn't change. If it does change, rerun the last command with the new IP.

## Troubleshooting

```sh
adb logcat -s SonosVolume
```

The log shows a missing IP, a missing overlay permission, or a Beam that can't be reached. To confirm the Beam answers on a given IP, open `http://<ip>:1400/xml/device_description.xml` from the Mac.
