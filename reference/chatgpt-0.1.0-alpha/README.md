# THETA Home Hatch — Android alpha

Native Android project (Java 17, Android framework Views; no WebView).

## Scope

- Default HOME candidate; first launch works as an ordinary app until the user selects it as Home.
- Narrow windows show a clock, photo frame, music-app entry point and configurable favorite apps. Wide windows show the file workspace. Settings allow an explicit layout override.
- Storage Access Framework folder grants, expandable folder tree, file viewing, text/image preview, sharing and export through the system picker.
- Full-height tab rail. No brand header or drawer title.
- Capture icons at the bottom of the tree: tap to browse, hold to create. Recordings run in a microphone foreground service with a visible notification and Stop action. Camera capture delegates to the installed camera app. Text notes and pressure-aware handwriting are saved as actual files.
- Monochrome app icons and per-app glyph overrides; stack glyph configured independently.
- Android widget hosting with the system binding/configuration flow.
- Bookmarks and Hatch distribution/web links can be registered manually. Existing THETA-series app protocols are not yet known or integrated.

## Storage

Capture files initially live in the app-private `Capture` directory. Use **書き出す** to copy an item to a user-selected folder. Uninstalling the app removes its private captures. User-selected SAF folders remain outside the app and are not deleted.

## Build

Use Android SDK Platform 36 and Android Gradle Plugin 8.13.0 with Gradle 8.14.3 / Java 17 or 21. Adjust `local.properties` to your SDK. Run `gradle :app:assembleDebug`. Debug builds are for personal testing, not a Play Store release.

## Verification still required

APK build/signature checks do not replace device testing. Test both Z Fold5 screens, rotation and process recreation, SAF permission revocation, camera-app output, denied microphone permissions, notification Stop, S Pen input and widget configuration on real devices. Cover music controls, arbitrary icon-pack imports, cross-device sync and automatic Hatch package installation are not implemented.
