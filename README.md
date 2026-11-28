# Folio (Android, v0.8)

Open this folder in Android Studio (Koala or newer), let Gradle sync, then Run on your phone.
The first build downloads KaTeX into app/src/main/assets/katex so equations work offline (needs internet once).

## Release APK
1. Make a keystore (once):
   keytool -genkeypair -v -keystore folio.jks -keyalg RSA -keysize 2048 -validity 10000 -alias folio
2. Create keystore.properties next to settings.gradle.kts:
   storeFile=../folio.jks
   storePassword=YOUR_PASSWORD
   keyAlias=folio
   keyPassword=YOUR_PASSWORD
   (storeFile is relative to the app/ folder, or use an absolute path)
3. Build > Generate Signed App Bundle / APK in Android Studio, or run: gradlew assembleRelease
   The APK is in app/build/outputs/apk/release/.
Keep folio.jks and its passwords safe: you need the same key for every future update.
