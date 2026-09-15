# Whats Translate Test

Prototype Android accessibility helper for WhatsApp / WhatsApp Business.

## What it does
- Floating `TR` button while WhatsApp is active.
- Reads visible WhatsApp accessibility text and prefers the lowest likely chat message on screen.
- Translates English → Hindi with Google ML Kit on-device translation.
- Lets you type a Hindi reply and translates Hindi → English.
- Closes its overlay before inserting the English reply into WhatsApp for better device compatibility.
- Never presses Send automatically.
- If automatic insertion fails, it copies the English translation to the clipboard so you can paste it manually.

## First install
1. Install the debug APK.
2. Open **Whats Translate Test**.
3. Enable its Accessibility service.
4. On Android 13+, a sideloaded APK may show Accessibility as restricted. Open **Settings → Apps → Whats Translate Test → ⋮ → Allow restricted settings**, then enable the Accessibility service.
5. Open a normal WhatsApp chat and tap `TR`.

## Translation notes
- First translation needs internet because ML Kit downloads the English/Hindi models.
- After the models are downloaded, translation can run locally.
- Hindi written in Devanagari is expected to work better than Roman-Hindi/Hinglish such as `kal main free hu` because the reply translator is configured as Hindi → English.

## Important test limitation
WhatsApp does not expose a supported public API for reading the currently displayed personal chat. This prototype uses Android Accessibility. WhatsApp UI/accessibility-tree changes can therefore affect message detection or insertion. Always review the detected source text and translated English before pressing Send.
