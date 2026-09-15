package com.coderlala.whatstranslate

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

class WhatsTranslateAccessibilityService : AccessibilityService() {
    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var panel: View? = null
    private var lastCandidate: String = ""

    private lateinit var enToHi: Translator
    private lateinit var hiToEn: Translator

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        enToHi = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.HINDI)
                .build()
        )
        hiToEn = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.HINDI)
                .setTargetLanguage(TranslateLanguage.ENGLISH)
                .build()
        )

        val conditions = DownloadConditions.Builder().build()
        enToHi.downloadModelIfNeeded(conditions)
        hiToEn.downloadModelIfNeeded(conditions)
        showBubble()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return
        updateLastVisibleMessage()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        bubble?.let { runCatching { wm.removeView(it) } }
        panel?.let { runCatching { wm.removeView(it) } }
        if (::enToHi.isInitialized) enToHi.close()
        if (::hiToEn.isInitialized) hiToEn.close()
        super.onDestroy()
    }

    private fun showBubble() {
        if (bubble != null) return
        val button = TextView(this).apply {
            text = "TR"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(25, 118, 210))
            }
            setOnClickListener {
                updateLastVisibleMessage()
                showPanelAndTranslate()
            }
        }
        val size = dp(56)
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = dp(12)
        }
        wm.addView(button, params)
        bubble = button
    }

    private fun showPanelAndTranslate() {
        panel?.let { runCatching { wm.removeView(it) }; panel = null }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(1), Color.LTGRAY)
            }
        }

        val source = TextView(this).apply {
            text = if (lastCandidate.isBlank()) "No WhatsApp message detected yet." else "English:\n$lastCandidate"
            setTextColor(Color.DKGRAY)
            textSize = 15f
        }
        val translated = TextView(this).apply {
            text = "Hindi: translating…"
            setTextColor(Color.BLACK)
            textSize = 17f
            setPadding(0, dp(8), 0, dp(8))
        }
        val reply = EditText(this).apply {
            hint = "Type reply in Hindi/Hinglish"
            minLines = 2
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
        }
        val insert = Button(this).apply { text = "Translate + Insert into WhatsApp" }
        val close = Button(this).apply { text = "Close" }

        container.addView(source)
        container.addView(translated)
        container.addView(reply)
        container.addView(insert)
        container.addView(close)

        val params = WindowManager.LayoutParams(
            dp(330), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            0,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        wm.addView(container, params)
        panel = container

        if (lastCandidate.isBlank()) {
            translated.text = "Hindi: —"
        } else {
            enToHi.translate(lastCandidate)
                .addOnSuccessListener { translated.text = "Hindi:\n$it" }
                .addOnFailureListener { translated.text = "Hindi translation failed: ${it.message ?: "unknown error"}" }
        }

        insert.setOnClickListener {
            val raw = reply.text.toString().trim()
            if (raw.isBlank()) {
                Toast.makeText(this, "Type your reply first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            insert.isEnabled = false
            insert.text = "Translating…"
            hiToEn.translate(raw)
                .addOnSuccessListener { english ->
                    val ok = insertIntoWhatsApp(english)
                    if (ok) {
                        Toast.makeText(this, "Inserted. Review and press Send.", Toast.LENGTH_LONG).show()
                        closePanel()
                    } else {
                        Toast.makeText(this, "Could not find WhatsApp message box", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Reply translation failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
                .addOnCompleteListener {
                    insert.isEnabled = true
                    insert.text = "Translate + Insert into WhatsApp"
                }
        }

        close.setOnClickListener { closePanel() }
    }

    private fun closePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
    }

    private fun updateLastVisibleMessage() {
        val root = rootInActiveWindow ?: return
        val items = mutableListOf<String>()
        collectText(root, items)
        lastCandidate = items.asReversed().firstOrNull { isLikelyChatMessage(it) } ?: lastCandidate
    }

    private fun collectText(node: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(out::add)
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(out::add)
        for (i in 0 until node.childCount) collectText(node.getChild(i), out)
    }

    private fun isLikelyChatMessage(text: String): Boolean {
        if (text.length < 2 || text.length > 1500) return false
        val lower = text.lowercase()
        val blocked = listOf(
            "type a message", "message", "send", "emoji", "camera", "attach",
            "voice message", "video call", "voice call", "online", "typing…",
            "whatsapp", "search", "more options"
        )
        if (blocked.any { lower == it || lower.startsWith("$it ") }) return false
        if (text.matches(Regex("^\\d{1,2}:\\d{2}(\\s?[ap]m)?$", RegexOption.IGNORE_CASE))) return false
        return true
    }

    private fun insertIntoWhatsApp(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val edit = findEditable(root) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable && node.isEnabled) return node
        for (i in 0 until node.childCount) {
            val found = findEditable(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
