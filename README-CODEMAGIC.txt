Whats Translate Test - Codemagic build

Repository root must contain:
  app/
  build.gradle.kts
  settings.gradle.kts
  gradle.properties
  codemagic.yaml

Codemagic workflow:
  WhatsApp Translator Debug APK

The workflow explicitly installs Gradle 8.9 and uses JDK 17, then runs:
  gradle clean assembleDebug

Expected artifact:
  app/build/outputs/apk/debug/app-debug.apk

Mobile setup after installing APK:
1. Open Whats Translate Test.
2. Tap Enable Accessibility Service.
3. Enable Whats Translate Test in Android Accessibility settings.
4. Open WhatsApp or WhatsApp Business.
5. Floating TR button should appear only while WhatsApp is in front.
6. Tap TR to translate the latest visible message to Hindi.
7. Type Hindi reply, tap Translate + Insert, review English, then press Send yourself.

Important prototype limitation:
- The app heuristically chooses the latest visible text from WhatsApp's accessibility tree.
  WhatsApp can change its UI at any time, so message detection may need adjustment after testing.
- Hindi script is the supported input for Hindi -> English. Hinglish/Roman Hindi may work inconsistently.
