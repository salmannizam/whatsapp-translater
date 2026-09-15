# Whats Translate Test

Prototype Android accessibility overlay for WhatsApp.

## What it does
- Floating `TR` bubble while WhatsApp is open.
- Reads the latest visible WhatsApp text when you tap the bubble.
- Translates English -> Hindi with Google ML Kit on-device translation.
- Lets you type a Hindi/Hinglish reply.
- Translates Hindi -> English and inserts it into the WhatsApp compose field.
- Does **not** press Send.

## Build
Open this folder in Android Studio and let Gradle sync.

Then use:

```bash
./gradlew assembleDebug
```

APK output:

`app/build/outputs/apk/debug/app-debug.apk`

## Test
1. Install the APK.
2. Open it and tap **Enable Accessibility Service**.
3. Enable **Whats Translate Test**.
4. Open WhatsApp chat.
5. Tap the `TR` floating bubble.
6. First translation may need internet briefly to download ML Kit language models.
7. Type Hindi/Hinglish reply and tap **Translate + Insert into WhatsApp**.
8. Review the English text and manually tap WhatsApp Send.

## Important prototype limitation
WhatsApp's accessibility hierarchy can vary by app version/device. The current detector heuristically chooses the last visible text. For production, add more robust message-bubble detection and device/version testing.
# whatsapp-translater
