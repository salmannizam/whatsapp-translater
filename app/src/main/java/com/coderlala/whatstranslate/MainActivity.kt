package com.coderlala.whatstranslate

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Whats Translate Test\n\n" +
                "1. Tap the button below and enable Whats Translate Test in Accessibility.\n" +
                "2. Open WhatsApp or WhatsApp Business.\n" +
                "3. Tap the floating TR button.\n" +
                "4. The latest visible message is translated English → Hindi.\n" +
                "5. Type your reply in Hindi and tap Translate + Insert.\n" +
                "6. Review the English text and press Send yourself.\n\n" +
                "First translation needs internet to download Google's on-device language model. " +
                "After the model is downloaded, translation can run locally.\n\n" +
                "Note: Roman-Hindi/Hinglish such as ‘kal main free hu’ may be less accurate than Hindi script.\n\n" +
                "If Android blocks Accessibility for this sideloaded APK (common on Android 13+), open App info → three-dot menu → Allow restricted settings, then enable Accessibility.\n\n" +
                "This test app never presses Send automatically."
            textSize = 17f
        })

        root.addView(Button(this).apply {
            text = "Enable Accessibility Service"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })

        setContentView(root)
    }
}
