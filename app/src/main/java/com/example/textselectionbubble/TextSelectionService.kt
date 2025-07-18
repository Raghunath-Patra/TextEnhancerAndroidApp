package com.example.textselectionbubble

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.textselectionbubble.data.UserSessionManager
import com.example.textselectionbubble.data.models.EnhancementType
import com.example.textselectionbubble.data.network.ApiResult
import com.example.textselectionbubble.data.network.NetworkModule
import com.example.textselectionbubble.data.repository.TextEnhancementRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.math.abs

class TextSelectionService : AccessibilityService() {

    companion object {
        private const val TAG = "TextSelectionService"
        private const val SELECTION_DELAY_MS = 300L
        private const val PREFS_NAME = "TextSelectionBubble"
        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        private const val KEY_SERVICE_ACTIVE = "service_active"
    }

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var selectedText = ""
    private var selectedNode: AccessibilityNodeInfo? = null
    private var selectionStart = -1
    private var selectionEnd = -1
    private var delayJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var sharedPrefs: SharedPreferences

    // Bubble position tracking
    private var bubbleX = 100
    private var bubbleY = 200

    // API and enhancement
    private lateinit var sessionManager: UserSessionManager
    private lateinit var textEnhancementRepository: TextEnhancementRepository
    private var isEnhancing = false
    private var enhancedText = ""
    private var selectedEnhancementType = EnhancementType.GENERAL

    // Keep references for style buttons
    private var enhancementButtons: List<Button> = emptyList()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service Connected")

        // Preferences
        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean(KEY_SERVICE_ACTIVE, true).apply()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        sessionManager = UserSessionManager(applicationContext)
        textEnhancementRepository = TextEnhancementRepository(NetworkModule.apiService)

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        serviceInfo = info
        Log.d(TAG, "Service configured and ready for text selection events")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isMonitoringEnabled()) return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            handleTextSelectionChanged(event)
        }
    }

    private fun isMonitoringEnabled(): Boolean {
        return sharedPrefs.getBoolean(KEY_MONITORING_ENABLED, false) // Change to 'false' if you want opt-in
    }

    private fun handleTextSelectionChanged(event: AccessibilityEvent) {
        Log.d(TAG, "Text selection changed - From: ${event.fromIndex}, To: ${event.toIndex}")
        cancelPendingBubble()

        val sourceNode = event.source ?: run {
            Log.d(TAG, "Source node is null")
            hideBubble()
            return
        }

        selectedNode = sourceNode
        val newSelectedText = extractSelectedText(sourceNode, event.fromIndex, event.toIndex)
        Log.d(TAG, "Extracted text: '$newSelectedText'")

        val hasValidSelection = newSelectedText != null &&
                newSelectedText.trim().isNotEmpty() &&
                newSelectedText.trim().length > 1 &&
                event.fromIndex >= 0 &&
                event.toIndex > event.fromIndex

        if (hasValidSelection) {
            selectedText = newSelectedText!!.trim()
            selectionStart = event.fromIndex
            selectionEnd = event.toIndex
            Log.d(TAG, "Valid selection: '$selectedText' (${selectionStart}-${selectionEnd})")
            scheduleShowBubble()
        } else {
            Log.d(TAG, "Invalid/no selection, hiding bubble")
            hideBubble()
        }
    }

    private fun extractSelectedText(node: AccessibilityNodeInfo, fromIndex: Int, toIndex: Int): String? {
        return try {
            val nodeText = node.text
            if (nodeText != null && fromIndex >= 0 && toIndex > fromIndex &&
                fromIndex < nodeText.length && toIndex <= nodeText.length
            ) {
                val selected = nodeText.subSequence(fromIndex, toIndex).toString()
                Log.d(TAG, "Direct extraction: '$selected'")
                return selected
            }
            // Clipboard fallback (rarely used)
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val clipText = clipData.getItemAt(0).text?.toString()
                if (clipText != null && clipText.trim().isNotEmpty() && clipText.length < 1000) {
                    Log.d(TAG, "Clipboard fallback: '$clipText'")
                    return clipText
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting selected text", e)
            null
        }
    }

    private fun cancelPendingBubble() {
        delayJob?.cancel()
        delayJob = null
    }

    private fun scheduleShowBubble() {
        delayJob = serviceScope.launch {
            delay(SELECTION_DELAY_MS)
            showBubble()
        }
    }

    private fun showBubble() {
        hideBubble()

        val inflater = LayoutInflater.from(this)
        bubbleView = inflater.inflate(R.layout.bubble_layout, null)
        val currentBubbleView = bubbleView ?: return

        val selectedTextView = currentBubbleView.findViewById<TextView>(R.id.tvSelectedText)
        val transformedTextView = currentBubbleView.findViewById<TextView>(R.id.tvTransformedText)
        val enhanceButton = currentBubbleView.findViewById<Button>(R.id.btnEnhance)
        val copyButton = currentBubbleView.findViewById<Button>(R.id.btnCopy)
        val replaceButton = currentBubbleView.findViewById<Button>(R.id.btnReplace)
        val closeButton = currentBubbleView.findViewById<Button>(R.id.btnClose)

        // Enhancement style buttons
        val generalButton = currentBubbleView.findViewById<Button>(R.id.btnGeneral)
        val professionalButton = currentBubbleView.findViewById<Button>(R.id.btnProfessional)
        val casualButton = currentBubbleView.findViewById<Button>(R.id.btnCasual)
        val conciseButton = currentBubbleView.findViewById<Button>(R.id.btnConcise)
        val detailedButton = currentBubbleView.findViewById<Button>(R.id.btnDetailed)

        enhancementButtons = listOf(generalButton, professionalButton, casualButton, conciseButton, detailedButton)

        // Reset UI
        selectedTextView.text = "Selected: ${selectedText.take(50)}${if (selectedText.length > 50) "..." else ""}"
        transformedTextView.text = "Select enhancement style and tap ✨ to enhance text"
        enhanceButton.isEnabled = true
        disableActionButtons(copyButton, replaceButton)
        enhancedText = ""
        selectedEnhancementType = EnhancementType.GENERAL
        updateEnhancementButtonStates()

        // Setup button handlers
        setupEnhancementTypeButtons()
        setupActionButtons(transformedTextView, copyButton, replaceButton, enhanceButton)
        closeButton.setOnClickListener { hideBubble() }

        // Window params and draggable
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleX
            y = bubbleY
        }
        makeBubbleDraggable(currentBubbleView, params)

        try {
            windowManager?.addView(currentBubbleView, params)
            Log.d(TAG, "Bubble shown successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show bubble", e)
        }
    }

    private fun setupActionButtons(
        transformedTextView: TextView,
        copyButton: Button,
        replaceButton: Button,
        enhanceButton: Button
    ) {
        enhanceButton.setOnClickListener {
            if (isEnhancing) return@setOnClickListener
            enhanceText(transformedTextView, copyButton, replaceButton, enhanceButton)
        }

        copyButton.setOnClickListener {
            val textToCopy = if (enhancedText.isNotEmpty()) enhancedText else selectedText
            copyToClipboard(textToCopy)
            showButtonFeedback(copyButton, "✓", R.color.success_color)
            Toast.makeText(this, "Copied enhanced text!", Toast.LENGTH_SHORT).show()
        }

        replaceButton.setOnClickListener {
            val textToReplace = if (enhancedText.isNotEmpty()) enhancedText else selectedText
            replaceButton.text = "⏳"
            if (replaceSelectedText(textToReplace)) {
                showButtonFeedback(replaceButton, "✓", R.color.success_color)
                Toast.makeText(this, "Text replaced successfully!", Toast.LENGTH_SHORT).show()
                serviceScope.launch {
                    delay(1000)
                    hideBubble()
                }
            } else {
                copyToClipboard(textToReplace)
                showButtonFeedback(replaceButton, "📋", R.color.warning_color)
                Toast.makeText(this, "Copied to clipboard - manual paste needed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showButtonFeedback(button: Button, text: String, colorRes: Int) {
        val originalText = button.text
        val originalColor = button.currentTextColor
        button.text = text
        button.setTextColor(ContextCompat.getColor(this, colorRes))
        serviceScope.launch {
            delay(1000)
            button.text = originalText
            button.setTextColor(originalColor)
        }
    }

    private fun setupEnhancementTypeButtons() {
        val enhancementTypes = listOf(
            EnhancementType.GENERAL,
            EnhancementType.PROFESSIONAL,
            EnhancementType.CASUAL,
            EnhancementType.CONCISE,
            EnhancementType.DETAILED
        )
        enhancementButtons.forEachIndexed { idx, button ->
            button.setOnClickListener {
                selectedEnhancementType = enhancementTypes[idx]
                updateEnhancementButtonStates()
            }
        }
    }

    private fun updateEnhancementButtonStates() {
        val enhancementTypes = listOf(
            EnhancementType.GENERAL,
            EnhancementType.PROFESSIONAL,
            EnhancementType.CASUAL,
            EnhancementType.CONCISE,
            EnhancementType.DETAILED
        )
        enhancementButtons.forEachIndexed { idx, button ->
            val isSelected = enhancementTypes[idx] == selectedEnhancementType
            button.isSelected = isSelected
            if (isSelected) {
                button.setBackgroundResource(R.drawable.enhancement_button_selected)
                button.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            } else {
                button.setBackgroundResource(R.drawable.enhancement_button_unselected)
                button.setTextColor(ContextCompat.getColor(this, R.color.enhancement_selected))
            }
        }
    }

    private fun enableActionButtons(copyButton: Button, replaceButton: Button) {
        copyButton.isEnabled = true
        replaceButton.isEnabled = true
        copyButton.setBackgroundResource(R.drawable.enhanced_action_button)
        replaceButton.setBackgroundResource(R.drawable.enhanced_action_button)
        copyButton.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        replaceButton.setTextColor(ContextCompat.getColor(this, android.R.color.white))
    }

    private fun disableActionButtons(copyButton: Button, replaceButton: Button) {
        copyButton.isEnabled = false
        replaceButton.isEnabled = false
        copyButton.setBackgroundResource(R.drawable.disabled_action_button)
        replaceButton.setBackgroundResource(R.drawable.disabled_action_button)
        copyButton.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        replaceButton.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
    }

    private fun enhanceText(
        transformedTextView: TextView,
        copyButton: Button,
        replaceButton: Button,
        enhanceButton: Button
    ) {
        serviceScope.launch {
            try {
                val accessToken = sessionManager.getAccessToken().first()
                if (accessToken == null) {
                    transformedTextView.text = "Please log in to enhance text"
                    return@launch
                }
                isEnhancing = true
                enhanceButton.isEnabled = false
                enhanceButton.text = "⏳"
                transformedTextView.text = "Enhancing (${selectedEnhancementType.value})..."
                when (val result = textEnhancementRepository.enhanceText(
                    accessToken,
                    selectedText,
                    selectedEnhancementType
                )) {
                    is ApiResult.Success -> {
                        enhancedText = result.data.enhancedText
                        transformedTextView.text = enhancedText
                        enableActionButtons(copyButton, replaceButton)
                        sessionManager.updateUserUsage(
                            tokensUsedToday = result.data.tokensUsedToday,
                            tokensRemaining = result.data.tokensRemainingToday,
                            lastUsageDate = null
                        )
                        Toast.makeText(
                            this@TextSelectionService,
                            "Enhanced! (${result.data.tokensUsedThisRequest} tokens)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    is ApiResult.Error -> {
                        transformedTextView.text = "⚠️ ${result.message}"
                        if (result.message.contains("token limit", true) || result.message.contains("tokens", true)) {
                            enhancedText = transformText(selectedText, selectedEnhancementType)
                            transformedTextView.text = "Enhanced (offline): $enhancedText"
                            enableActionButtons(copyButton, replaceButton)
                        }
                    }

                    is ApiResult.Loading -> {} // Already handled
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error enhancing text", e)
                transformedTextView.text = "⚠️ Enhancement failed - using offline mode"
                enhancedText = transformText(selectedText, selectedEnhancementType)
                transformedTextView.text = "Enhanced (offline): $enhancedText"
                enableActionButtons(copyButton, replaceButton)
            } finally {
                isEnhancing = false
                enhanceButton.isEnabled = true
                enhanceButton.text = "✨"
            }
        }
    }

    private fun makeBubbleDraggable(view: View, params: WindowManager.LayoutParams) {
        view.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false
            private val dragThreshold = 15

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - initialTouchX
                        val deltaY = event.rawY - initialTouchY
                        if (!isDragging && (abs(deltaX) > dragThreshold || abs(deltaY) > dragThreshold)) {
                            isDragging = true
                        }
                        if (isDragging) {
                            params.x = initialX + deltaX.toInt()
                            params.y = initialY + deltaY.toInt()
                            bubbleX = params.x
                            bubbleY = params.y
                            try {
                                windowManager?.updateViewLayout(view, params)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error updating bubble position", e)
                            }
                        }
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) return false
                        bubbleX = params.x
                        bubbleY = params.y
                        isDragging = false
                        Log.d(TAG, "Bubble position saved: x=$bubbleX, y=$bubbleY")
                        return true
                    }

                    else -> return false
                }
            }
        })
    }

    private fun hideBubble() {
        val currentBubbleView = bubbleView
        if (currentBubbleView != null && windowManager != null) {
            try {
                windowManager?.removeView(currentBubbleView)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing bubble view", e)
            }
            bubbleView = null
        }
    }

    private fun transformText(text: String, enhancementType: EnhancementType): String {
        // Simple offline transformations for each style
        return when (enhancementType) {
            EnhancementType.PROFESSIONAL -> {
                text.replace(Regex("\\b(can't|won't|don't|isn't|aren't)\\b", RegexOption.IGNORE_CASE)) { match ->
                    when (match.value.lowercase()) {
                        "can't" -> "cannot"
                        "won't" -> "will not"
                        "don't" -> "do not"
                        "isn't" -> "is not"
                        "aren't" -> "are not"
                        else -> match.value
                    }
                }.replaceFirstChar { it.uppercase() }.let { formal ->
                    if (!formal.endsWith(".") && !formal.endsWith("!") && !formal.endsWith("?")) "$formal." else formal
                }
            }
            EnhancementType.CASUAL -> {
                text.replace(Regex("\\b(cannot|will not|do not|is not|are not)\\b", RegexOption.IGNORE_CASE)) { match ->
                    when (match.value.lowercase()) {
                        "cannot" -> "can't"
                        "will not" -> "won't"
                        "do not" -> "don't"
                        "is not" -> "isn't"
                        "are not" -> "aren't"
                        else -> match.value
                    }
                }.let { casual ->
                    if (casual.endsWith(".")) casual.dropLast(1) + "! 😊" else "$casual! 😊"
                }
            }
            EnhancementType.CONCISE -> {
                text.replace(Regex("\\b(very|really|quite|rather|somewhat|extremely)\\s+", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("\\bin order to\\b", RegexOption.IGNORE_CASE), "to")
                    .replace(Regex("\\bdue to the fact that\\b", RegexOption.IGNORE_CASE), "because")
            }
            EnhancementType.DETAILED -> {
                "$text (with enhanced detail and context for better understanding)"
            }
            else -> {
                text.replaceFirstChar { it.uppercase() }.let { improved ->
                    if (!improved.endsWith(".") && !improved.endsWith("!") && !improved.endsWith("?")) "$improved." else improved
                }
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("enhanced_text", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun replaceSelectedText(newText: String): Boolean {
        val node = selectedNode ?: return false
        return try {
            if (tryReplaceUsingPaste(node, newText)) {
                return true
            }
            val fullText = node.text?.toString() ?: return false
            if (selectionStart >= 0 && selectionEnd > selectionStart &&
                selectionStart < fullText.length && selectionEnd <= fullText.length
            ) {
                val before = fullText.substring(0, selectionStart)
                val after = fullText.substring(selectionEnd)
                val newFullText = before + newText + after
                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newFullText)
                }
                val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                if (success) {
                    val newCursorPosition = selectionStart + newText.length
                    val selectionArgs = Bundle().apply {
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newCursorPosition)
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newCursorPosition)
                    }
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
                }
                return success
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to replace text", e)
            false
        }
    }

    private fun tryReplaceUsingPaste(node: AccessibilityNodeInfo, newText: String): Boolean {
        return try {
            copyToClipboard(newText)
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to replace using paste", e)
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service Destroyed")
        sharedPrefs.edit()
            .putBoolean(KEY_SERVICE_ACTIVE, false)
            .putBoolean(KEY_MONITORING_ENABLED, false)
            .apply()
        cancelPendingBubble()
        serviceScope.cancel()
        hideBubble()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Service Unbound")
        return false // false: allow rebinding
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        Log.d(TAG, "Service Rebound")
        sharedPrefs.edit().putBoolean(KEY_SERVICE_ACTIVE, true).apply()
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
        cancelPendingBubble()
        hideBubble()
    }
}
