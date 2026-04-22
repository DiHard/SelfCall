# SelfCall — Android

Kotlin + Jetpack Compose + LiveKit Android SDK.

Three screens, matching the React app:
1. **Name** — enter display name (saved to `SharedPreferences`)
2. **Rooms** — list of 5 fixed rooms from `GET /rooms`
3. **Call** — `RoomScope` + `VideoTrackView` from `livekit-android-compose-components`

## Requirements

- Android Studio **Ladybug (2024.2)** or newer
- JDK 17 (bundled with Android Studio)
- Test device / emulator running Android 8.0+ (API 26+)
- Real camera/microphone strongly recommended for WebRTC testing

## First run

1. Open the `android/` folder in Android Studio (not the repo root).
2. Wait for Gradle sync — AS will fetch the wrapper JAR, all plugins, and deps
   (first sync: 5–10 min depending on network).
3. If sync fails with "gradle-wrapper.jar not found", run once from the
   `android/` folder:
   ```
   gradle wrapper --gradle-version 8.11.1
   ```
   (using any locally installed Gradle ≥ 8, or the one bundled with AS at
   `<AS>/plugins/gradle/lib/gradle-*`).

4. Plug in a phone in developer mode, click ▶ Run.

## Backend URL

Defaults to `https://api.if-x.ru` (production).

Override locally by adding to `~/.gradle/gradle.properties` or project-local
`android/gradle.properties`:

```
SELFCALL_API_BASE=http://10.0.2.2:8000   # emulator -> host localhost
# or
SELFCALL_API_BASE=http://192.168.X.X:8000  # real device, host's LAN IP
```

Note: for non-HTTPS URLs you'll also need to allow cleartext traffic
(add `android:usesCleartextTraffic="true"` in `AndroidManifest.xml`
`<application>` tag for debug builds).

## Versions

Key dep versions are in `gradle/libs.versions.toml`. If LiveKit compose
components and the base SDK fall out of sync during a future upgrade,
bump `livekitCompose` to the version matched in
[LiveKit's release notes](https://github.com/livekit/components-android/releases).
