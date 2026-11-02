# AGENTS.md

An Android TV app, four Java classes with no library dependencies, that draws a Sonos speaker's volume over whatever is on screen. [README.md](README.md) explains what it does for users. This file covers installing it on someone's TV and changing the code.

## Installing for a user

Work through the steps in order. Each ends on a check you can run. Move on only once it passes.

### 1. Build

Needs Java 17+, `adb` and an Android SDK. Write the SDK path to `local.properties` as `sdk.dir=<path>`. On macOS without an SDK, `brew install --cask android-commandlinetools` puts one at `/opt/homebrew/share/android-commandlinetools`. Then accept the licenses with `yes | sdkmanager --sdk_root=<path> --licenses`. After that, Gradle downloads the SDK platform it needs during the build.

Done when `./gradlew assembleDebug` writes `app/build/outputs/apk/debug/app-debug.apk`.

### 2. Find the speaker

Sonos speakers serve `http://<ip>:1400/xml/device_description.xml`, which contains `<roomName>` and `<modelName>`. To find them, request that URL from every host in `arp -a` with a 1 second timeout. On macOS, `dns-sd -B _sonos._tcp` confirms speakers exist but gives names, not IPs.

If several speakers answer, ask the user which one is connected to the TV. In a home theater set (soundbar plus sub or surrounds), use the soundbar.

Done when this returns `<CurrentVolume>N</CurrentVolume>`:

```sh
curl -s http://<speaker-ip>:1400/MediaRenderer/RenderingControl/Control \
  -H 'Content-Type: text/xml; charset="utf-8"' \
  -H 'SOAPACTION: "urn:schemas-upnp-org:service:RenderingControl:1#GetVolume"' \
  -d '<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:GetVolume xmlns:u="urn:schemas-upnp-org:service:RenderingControl:1"><InstanceID>0</InstanceID><Channel>Master</Channel></u:GetVolume></s:Body></s:Envelope>'
```

For `SetVolume`, use the same request with `GetVolume` replaced in both places and `<DesiredVolume>N</DesiredVolume>` added after `<Channel>`.

### 3. Connect to the TV box

Ask the user to turn on network debugging. On a Shield, that's Settings > Device Preferences > About, press Build 7 times, then Developer options > Network debugging. That row shows the box's IP. You can also find the box with `dns-sd -B _androidtvremote2._tcp` and resolve the name it lists.

Run `adb connect <box-ip>:5555`. The box shows as `unauthorized` until the user accepts a prompt on the TV. Ask them to tick "Always allow from this computer", then poll `adb devices`.

Done when `adb devices` lists the box as `device`.

### 4. Install and start

Select the box with `export ANDROID_SERIAL=<box-ip>:5555`. This is more reliable than `-s`: in zsh, `adb $S` with `S="-s <serial>"` passes the flag as one word, and adb rejects it.

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell appops set com.gsajith.sonosvolume SYSTEM_ALERT_WINDOW allow
adb shell am start -n com.gsajith.sonosvolume/.MainActivity --es ip <speaker-ip>
```

Done when `adb shell dumpsys activity services com.gsajith.sonosvolume` shows `isForeground=true`.

### 5. Verify on screen

Ask the user before you change their volume. Read the current volume, set it 2 higher, wait 1 second, run `adb exec-out screencap -p > shot.png`, then set it back. Shield screenshots are 3840 wide, so scale them down before viewing. The bar sits against the left edge, about 73% of the way down.

Done when the screenshot shows the bar with the new number and the volume is back where it started.

### 6. Hand off

Tell the user to reserve the speaker's IP in their router's DHCP settings and to turn network debugging off. The app restarts itself on boot and doesn't need debugging left on.

## Troubleshooting

`adb logcat -s SonosVolume` logs a missing IP, a missing overlay permission or an unreachable speaker.

- `adb install -r` stops the service. Rerun the `am start` command after every reinstall.
- `MainActivity` saves the `ip` extra only if it's a plain IPv4 address. Any other value is dropped, and the service logs that no IP is saved.
- Other Android TV boxes should work the same way, but only the Shield has been tested.

## Changing the code

- `MainActivity`: invisible entry point. Saves the `ip` extra and starts the service.
- `VolumeOverlayService`: foreground service. Polls the speaker every 250 ms and shows the bar on a change.
- `VolumeBarView`: draws the bar.
- `BootReceiver`: starts the service after a reboot.

The Android framework covers everything the app does. Build with the framework APIs rather than adding a library. There are no tests. Check changes with `./gradlew assembleDebug`, then by installing on a device.

Commit messages and PR titles follow Conventional Commits (`type(scope): description`). `npm install` sets up a commitlint hook that checks each commit.

## Agent skills

### Issue tracker

Issues live in GitHub Issues on gsajith/sonos-volume-overlay, managed with the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
