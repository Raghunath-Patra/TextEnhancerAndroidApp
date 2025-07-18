package com.example.textselectionbubble

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
        private const val SELECTION_DELAY_MS = 500L
    }

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var selectedText = ""
    private var selectedNode: AccessibilityNodeInfo? = null
    private var selectionStart = -1
    private var selectionEnd = -1
    private var delayJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Save bubble position
    private var bubbleX = 100
    private var bubbleY = 200

    // API integration
    private lateinit var sessionManager: UserSessionManager
    private lateinit var textEnhancementRepository: TextEnhancementRepository
    private var isEnhancing = false
    private var enhancedText = ""
    private var selectedEnhancementType = EnhancementType.GENERAL

    // Enhancement type buttons
    private var enhancementButtons: List<Button> = emptyList()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service Connected")

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }

        serviceInfo = info
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Initialize API components
        sessionManager = UserSessionManager(applicationContext)
        textEnhancementRepository = TextEnhancementRepository(NetworkModule.apiService)

        // Save service state to SharedPreferences
        val sharedPrefs = getSharedPreferences("TextSelectionBubble", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("service_running", true).apply()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            Log.d(TAG, "Text selection changed")

            // Cancel any pending bubble show
            cancelPendingBubble()

            val newSelectedText = getSelectedText(event)

            // Store selection indices for replacement
            selectionStart = event.fromIndex
            selectionEnd = event.toIndex

            Log.d(TAG, "New selected text: '$newSelectedText'")
            Log.d(TAG, "From index: ${event.fromIndex}, To index: ${event.toIndex}")

            val hasValidSelection = event.fromIndex >= 0 &&
                    event.toIndex > event.fromIndex &&
                    newSelectedText != null &&
                    newSelectedText.trim().isNotEmpty() &&
                    newSelectedText.trim().length > 1

            if (hasValidSelection) {
                selectedText = newSelectedText!!.trim()
                selectedNode = event.source
                Log.d(TAG, "Valid selection detected: $selectedText")

                // Show bubble after delay
                scheduleShowBubble()
            } else {
                Log.d(TAG, "No valid selection, hiding bubble")
                hideBubble()
            }
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

    private fun getSelectedText(event: AccessibilityEvent): String? {
        val source = event.source ?: return null

        val start = event.fromIndex
        val end = event.toIndex

        Log.d(TAG, "Selection indices - Start: $start, End: $end")

        if (start < 0 || end <= start) {
            return null
        }

        val text = source.text
        if (text.isNullOrEmpty()) {
            return null
        }

        if (start >= text.length || end > text.length) {
            return null
        }

        val selectedText = text.subSequence(start, end).toString()
        Log.d(TAG, "Extracted selected text: '$selectedText'")

        return selectedText
    }

    private fun showBubble() {
        hideBubble()

        val inflater = LayoutInflater.from(this)
        bubbleView = inflater.inflate(R.layout.bubble_layout, null)

        val currentBubbleView = bubbleView ?: return

        // Initialize UI elements
        val selectedTextView = currentBubbleView.findViewById<TextView>(R.id.tvSelectedText)
        val transformedTextView = currentBubbleView.findViewById<TextView>(R.id.tvTransformedText)
        val enhanceButton = currentBubbleView.findViewById<Button>(R.id.btnEnhance)
        val copyButton = currentBubbleView.findViewById<Button>(R.id.btnCopy)
        val replaceButton = currentBubbleView.findViewById<Button>(R.id.btnReplace)
        val closeButton = currentBubbleView.findViewById<Button>(R.id.btnClose)

        // Enhancement type buttons
        val generalButton = currentBubbleView.findViewById<Button>(R.id.btnGeneral)
        val professionalButton = currentBubbleView.findViewById<Button>(R.id.btnProfessional)
        val casualButton = currentBubbleView.findViewById<Button>(R.id.btnCasual)
        val conciseButton = currentBubbleView.findViewById<Button>(R.id.btnConcise)
        val detailedButton = currentBubbleView.findViewById<Button>(R.id.btnDetailed)

        enhancementButtons = listOf(generalButton, professionalButton, casualButton, conciseButton, detailedButton)

        // Initialize UI
        selectedTextView.text = "Selected: ${selectedText.take(50)}${if (selectedText.length > 50) "..." else ""}"
        transformedTextView.text = "Select enhancement style and tap ✨ to enhance text"

        // Reset enhanced text
        enhancedText = ""
        selectedEnhancementType = EnhancementType.GENERAL

        // Initially disable copy/replace buttons until text is enhanced
        disableActionButtons(copyButton, replaceButton)
        enhanceButton.isEnabled = true

        // Set up enhancement type selection
        setupEnhancementTypeButtons()

        // Enhance button click handler
        enhanceButton.setOnClickListener {
            if (isEnhancing) return@setOnClickListener
            enhanceText(transformedTextView, copyButton, replaceButton, enhanceButton)
        }

        copyButton.setOnClickListener {
            val textToCopy = if (enhancedText.isNotEmpty()) enhancedText else selectedText
            copyToClipboard(textToCopy)

            // Provide visual feedback
            val originalText = copyButton.text
            copyButton.text = "✓"
            copyButton.setTextColor(ContextCompat.getColor(this, R.color.success_color))

            // Reset after delay
            serviceScope.launch {
                delay(1000)
                copyButton.text = "📋"
                copyButton.setTextColor(ContextCompat.getColor(this@TextSelectionService, android.R.color.white))
            }

            Toast.makeText(this, "Copied enhanced text!", Toast.LENGTH_SHORT).show()
        }

        replaceButton.setOnClickListener {
            val textToReplace = if (enhancedText.isNotEmpty()) enhancedText else selectedText

            // Provide visual feedback
            val originalText = replaceButton.text
            replaceButton.text = "⏳"

            if (replaceSelectedText(textToReplace)) {
                replaceButton.text = "✓"
                replaceButton.setTextColor(ContextCompat.getColor(this, R.color.success_color))
                Toast.makeText(this, "Text replaced successfully!", Toast.LENGTH_SHORT).show()

                // Hide bubble after successful replacement
                serviceScope.launch {
                    delay(1000)
                    hideBubble()
                }
            } else {
                copyToClipboard(textToReplace)
                replaceButton.text = "📋"
                replaceButton.setTextColor(ContextCompat.getColor(this, R.color.warning_color))
                Toast.makeText(this, "Copied to clipboard - manual paste needed", Toast.LENGTH_LONG).show()

                // Reset after delay
                serviceScope.launch {
                    delay(2000)
                    replaceButton.text = "🔄"
                    replaceButton.setTextColor(ContextCompat.getColor(this@TextSelectionService, android.R.color.white))
                }
            }
        }

        closeButton.setOnClickListener { hideBubble() }

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

    private fun setupEnhancementTypeButtons() {
        val enhancementTypes = listOf(
            EnhancementType.GENERAL,
            EnhancementType.PROFESSIONAL,
            EnhancementType.CASUAL,
            EnhancementType.CONCISE,
            EnhancementType.DETAILED
        )

        enhancementButtons.forEachIndexed { index, button ->
            val enhancementType = enhancementTypes[index]

            button.setOnClickListener {
                selectedEnhancementType = enhancementType
                updateEnhancementButtonStates()
            }
        }

        // Set initial state
        updateEnhancementButtonStates()
    }

    private fun updateEnhancementButtonStates() {
        val enhancementTypes = listOf(
            EnhancementType.GENERAL,
            EnhancementType.PROFESSIONAL,
            EnhancementType.CASUAL,
            EnhancementType.CONCISE,
            EnhancementType.DETAILED
        )

        enhancementButtons.forEachIndexed { index, button ->
            val isSelected = enhancementTypes[index] == selectedEnhancementType

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
                enhanceButton.text = "⏳ Enhancing..."
                transformedTextView.text = "Enhancing with ${selectedEnhancementType.value} style..."

                when (val result = textEnhancementRepository.enhanceText(
                    accessToken,
                    selectedText,
                    selectedEnhancementType
                )) {
                    is ApiResult.Success -> {
                        enhancedText = result.data.enhancedText
                        transformedTextView.text = enhancedText

                        // Enable copy/replace buttons
                        enableActionButtons(copyButton, replaceButton)

                        // Update user token usage in session
                        sessionManager.updateUserUsage(
                            tokensUsedToday = result.data.tokensUsedToday,
                            tokensRemaining = result.data.tokensRemainingToday,
                            lastUsageDate = null
                        )

                        Toast.makeText(this@TextSelectionService,
                            "Enhanced with ${selectedEnhancementType.value} style! (${result.data.tokensUsedThisRequest} tokens)",
                            Toast.LENGTH_SHORT).show()
                    }

                    is ApiResult.Error -> {
                        transformedTextView.text = "⚠️ ${result.message}"

                        // If it's a token limit error, allow manual enhancement
                        if (result.message.contains("token limit") || result.message.contains("tokens")) {
                            enhancedText = transformText(selectedText, selectedEnhancementType)
                            transformedTextView.text = "Enhanced (offline): $enhancedText"
                            enableActionButtons(copyButton, replaceButton)
                        }
                    }

                    is ApiResult.Loading -> {
                        // Already handled above
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error enhancing text", e)
                transformedTextView.text = "⚠️ Enhancement failed - trying offline mode"

                // Fallback to simple transformation
                enhancedText = transformText(selectedText, selectedEnhancementType)
                transformedTextView.text = "Enhanced (offline): $enhancedText"
                enableActionButtons(copyButton, replaceButton)

                Toast.makeText(this@TextSelectionService,
                    "Using offline enhancement",
                    Toast.LENGTH_SHORT).show()
            } finally {
                isEnhancing = false
                enhanceButton.isEnabled = true
                enhanceButton.text = "✨ Enhance"
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
                        if (!isDragging) {
                            return false
                        }

                        bubbleX = params.x
                        bubbleY = params.y
                        Log.d(TAG, "Bubble position saved: x=$bubbleX, y=$bubbleY")

                        isDragging = false
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
        // Fallback transformation when API is not available
        return when (enhancementType) {
            EnhancementType.PROFESSIONAL -> {
                // Make text more formal
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
                    if (!formal.endsWith(".") && !formal.endsWith("!") && !formal.endsWith("?")) {
                        "$formal."
                    } else formal
                }
            }

            EnhancementType.CASUAL -> {
                // Make text more casual and friendly
                val casualText = text.replace(Regex("\\b(cannot|will not|do not|is not|are not)\\b", RegexOption.IGNORE_CASE)) { match ->
                    when (match.value.lowercase()) {
                        "cannot" -> "can't"
                        "will not" -> "won't"
                        "do not" -> "don't"
                        "is not" -> "isn't"
                        "are not" -> "aren't"
                        else -> match.value
                    }
                }

                // Add casual expressions
                val endings = listOf(" 😊", " 👍", "!")
                if (casualText.endsWith(".")) {
                    casualText.dropLast(1) + endings.random()
                } else {
                    casualText + endings.random()
                }
            }

            EnhancementType.CONCISE -> {
                // Make text shorter and more direct
                text.split(" ").let { words ->
                    if (words.size > 10) {
                        words.take(words.size / 2).joinToString(" ") + "..."
                    } else {
                        text.replace(Regex("\\b(very|really|quite|rather|somewhat|extremely)\\s+", RegexOption.IGNORE_CASE), "")
                            .replace(Regex("\\bin order to\\b", RegexOption.IGNORE_CASE), "to")
                            .replace(Regex("\\bdue to the fact that\\b", RegexOption.IGNORE_CASE), "because")
                    }
                }
            }

            EnhancementType.DETAILED -> {
                // Add more detail and explanation
                val sentences = text.split(Regex("[.!?]+")).filter { it.trim().isNotEmpty() }
                sentences.joinToString(". ") { sentence ->
                    val trimmed = sentence.trim()
                    if (trimmed.isNotEmpty()) {
                        "$trimmed (with additional context and explanation)"
                    } else trimmed
                } + "."
            }

            else -> {
                // General enhancement - improve grammar and clarity
                text.replaceFirstChar { it.uppercase() }.let { improved ->
                    if (!improved.endsWith(".") && !improved.endsWith("!") && !improved.endsWith("?")) {
                        "$improved."
                    } else improved
                } + " [Enhanced]"
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
            // Method 1: Try to replace just the selected text using ACTION_PASTE
            if (tryReplaceUsingPaste(node, newText)) {
                return true
            }

            // Method 2: Fallback to manual text replacement
            val fullText = node.text?.toString() ?: return false

            if (selectionStart >= 0 && selectionEnd > selectionStart &&
                selectionStart < fullText.length && selectionEnd <= fullText.length) {

                val before = fullText.substring(0, selectionStart)
                val after = fullText.substring(selectionEnd)
                val newFullText = before + newText + after

                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newFullText)
                }

                val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

                if (success) {
                    // Try to set cursor position after the replaced text
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
            // First copy the new text to clipboard
            copyToClipboard(newText)

            // Try to paste - this should replace the selected text
            val pasteSuccess = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)

            if (pasteSuccess) {
                Log.d(TAG, "Successfully replaced text using paste")
                return true
            }

            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to replace using paste", e)
            false
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
        hideBubble()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Update service state in SharedPreferences
        val sharedPrefs = getSharedPreferences("TextSelectionBubble", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("service_running", false).apply()

        cancelPendingBubble()
        serviceScope.cancel()
        hideBubble()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Service Unbound")

        // Update service state in SharedPreferences
        val sharedPrefs = getSharedPreferences("TextSelectionBubble", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("service_running", false).apply()

        return super.onUnbind(intent)
    }
}