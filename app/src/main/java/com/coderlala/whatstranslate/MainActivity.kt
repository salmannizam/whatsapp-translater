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
            text = "Whats Translate Test\n\n1. Enable the accessibility service.\n2. Open WhatsApp.\n3. Tap the floating TR button.\n4. The latest visible message is translated English → Hindi.\n5. Type Hindi/Hinglish and tap Translate + Insert.\n6. Review it and press Send yourself.\n\nThe app does NOT auto-send messages."
            textSize = 18f
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
