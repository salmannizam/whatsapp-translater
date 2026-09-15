package com.coderlala.whatstranslate

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Rect
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var enToHi: Translator
    private lateinit var hiToEn: Translator

    private val whatsappPackages = setOf("com.whatsapp", "com.whatsapp.w4b")
    private val downloadConditions = DownloadConditions.Builder().build()

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

        // Warm up both models. Translation helpers below also re-check this,
        // so first use remains safe if the download is still in progress.
        enToHi.downloadModelIfNeeded(downloadConditions)
        hiToEn.downloadModelIfNeeded(downloadConditions)

        if (getWhatsAppRoot() != null) showBubble()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return

        if (pkg in whatsappPackages) {
            showBubble()
            updateLastVisibleMessage()
            return
        }

        // Our own overlay and the on-screen keyboard can generate accessibility
        // events while the translator panel is open. Do not tear the panel down
        // just because one of those windows temporarily has focus.
        if (pkg == packageName || panel != null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            hideBubble()
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        closePanel()
        hideBubble()
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
            size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = dp(12)
        }

        runCatching { wm.addView(button, params) }
            .onSuccess { bubble = button }
    }

    private fun hideBubble() {
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
    }

    private fun showPanelAndTranslate() {
        closePanel()

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
            text = if (lastCandidate.isBlank()) {
                "No WhatsApp message detected yet."
            } else {
                "English:\n$lastCandidate"
            }
            setTextColor(Color.DKGRAY)
            textSize = 15f
        }

        val translated = TextView(this).apply {
            text = if (lastCandidate.isBlank()) "Hindi: —" else "Hindi: preparing translation…"
            setTextColor(Color.BLACK)
            textSize = 17f
            setPadding(0, dp(8), 0, dp(8))
        }

        val reply = EditText(this).apply {
            hint = "Type reply in Hindi (Hinglish may vary)"
            minLines = 2
            maxLines = 5
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

        val maxWidth = resources.displayMetrics.widthPixels - dp(24)
        val panelWidth = minOf(dp(360), maxWidth)
        val params = WindowManager.LayoutParams(
            panelWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        runCatching { wm.addView(container, params) }
            .onFailure {
                Toast.makeText(this, "Could not open translator panel", Toast.LENGTH_LONG).show()
                return
            }
        panel = container

        if (lastCandidate.isNotBlank()) {
            translateAfterModelReady(
                translator = enToHi,
                input = lastCandidate,
                onSuccess = { translated.text = "Hindi:\n$it" },
                onFailure = { translated.text = "Hindi translation failed: ${it.message ?: "unknown error"}" }
            )
        }

        insert.setOnClickListener {
            val raw = reply.text.toString().trim()
            if (raw.isBlank()) {
                Toast.makeText(this, "Type your reply first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            insert.isEnabled = false
            insert.text = "Translating…"

            translateAfterModelReady(
                translator = hiToEn,
                input = raw,
                onSuccess = { english ->
                    // Remove our focusable overlay first. This lets WhatsApp become
                    // the active interactive window again before we search for its
                    // message editor. It is more reliable across Android vendors.
                    closePanel()
                    mainHandler.postDelayed({
                        val ok = insertIntoWhatsApp(english)
                        if (ok) {
                            Toast.makeText(this, "Inserted. Review it, then press Send.", Toast.LENGTH_LONG).show()
                        } else {
                            copyToClipboard(english)
                            Toast.makeText(
                                this,
                                "Could not insert automatically. English reply copied — paste it in WhatsApp.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }, 250)
                },
                onFailure = {
                    insert.isEnabled = true
                    insert.text = "Translate + Insert into WhatsApp"
                    Toast.makeText(this, "Reply translation failed: ${it.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                }
            )
        }

        close.setOnClickListener { closePanel() }

        // Put cursor in the reply box so the keyboard appears with one tap less.
        reply.requestFocus()
        reply.postDelayed({
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(reply, InputMethodManager.SHOW_IMPLICIT)
        }, 250)
    }

    private fun translateAfterModelReady(
        translator: Translator,
        input: String,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        translator.downloadModelIfNeeded(downloadConditions)
            .addOnSuccessListener {
                translator.translate(input)
                    .addOnSuccessListener(onSuccess)
                    .addOnFailureListener(onFailure)
            }
            .addOnFailureListener(onFailure)
    }

    private fun closePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
    }

    private fun updateLastVisibleMessage() {
        val root = getWhatsAppRoot() ?: return
        val screenHeight = resources.displayMetrics.heightPixels
        val candidates = mutableListOf<MessageCandidate>()
        collectMessageCandidates(root, candidates, screenHeight)

        // In a normal chat, the newest visible bubble is the lowest useful
        // non-editable text above the composer. This is much safer than using
        // accessibility-tree traversal order, which varies by WhatsApp version.
        candidates
            .filter { isLikelyChatMessage(it.text) }
            .maxByOrNull { it.bottom }
            ?.text
            ?.let { lastCandidate = it }
    }

    private data class MessageCandidate(val text: String, val bottom: Int)

    private fun collectMessageCandidates(
        node: AccessibilityNodeInfo?,
        out: MutableList<MessageCandidate>,
        screenHeight: Int
    ) {
        if (node == null) return

        // Never treat the WhatsApp composer (or another editable field) as a
        // received message candidate.
        if (!node.isEditable) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val centerY = bounds.centerY()
            val inChatBody = centerY > (screenHeight * 0.10f) && centerY < (screenHeight * 0.92f)

            if (inChatBody && !bounds.isEmpty) {
                node.text?.toString()?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { out.add(MessageCandidate(it, bounds.bottom)) }

                // Content descriptions sometimes contain the complete message
                // when the visible text node is split. Keep them as a fallback.
                node.contentDescription?.toString()?.trim()
                    ?.takeIf { it.isNotBlank() && it != node.text?.toString()?.trim() }
                    ?.let { out.add(MessageCandidate(it, bounds.bottom)) }
            }
        }

        for (i in 0 until node.childCount) {
            collectMessageCandidates(node.getChild(i), out, screenHeight)
        }
    }

    private fun isLikelyChatMessage(text: String): Boolean {
        if (text.length < 2 || text.length > 1500) return false

        val lower = text.lowercase()
        val blocked = listOf(
            "type a message",
            "message",
            "send",
            "emoji",
            "camera",
            "attach",
            "voice message",
            "video call",
            "voice call",
            "online",
            "typing…",
            "typing...",
            "whatsapp",
            "search",
            "more options"
        )

        if (blocked.any { lower == it || lower.startsWith("$it ") }) return false
        if (text.matches(Regex("^\\d{1,2}:\\d{2}(\\s?[ap]m)?$", RegexOption.IGNORE_CASE))) return false
        return true
    }

    private fun insertIntoWhatsApp(text: String): Boolean {
        val root = getWhatsAppRoot() ?: return false
        val edit = findEditable(root) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Translated reply", text))
    }

    private fun getWhatsAppRoot(): AccessibilityNodeInfo? {
        // A focusable accessibility overlay may become rootInActiveWindow.
        // Search all interactive windows and deliberately choose WhatsApp.
        windows.forEach { window ->
            val root = window.root ?: return@forEach
            if (root.packageName?.toString() in whatsappPackages) return root
        }

        val active = rootInActiveWindow
        return if (active?.packageName?.toString() in whatsappPackages) active else null
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
