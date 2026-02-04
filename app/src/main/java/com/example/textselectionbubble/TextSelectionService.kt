// TextSelectionService.kt - Updated with minimized icon and improved UX flow
package com.example.textselectionbubble

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
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

    // Position tracking
    private var bubbleX = 100
    private var bubbleY = 200

    // API components
    private lateinit var sessionManager: UserSessionManager
    private lateinit var textEnhancementRepository: TextEnhancementRepository
    private var isEnhancing = false
    private var enhancedText = ""
    private var selectedEnhancementType = EnhancementType.GENERAL

    // UI components
    private var enhancementButtons: List<Button> = emptyList()

    // Bubble state
    private var isExpanded = false
    private lateinit var minimizedView: CardView
    private lateinit var expandedView: CardView

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service Connected")

        // Initialize shared preferences
        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Mark service as active (connected to system)
        sharedPrefs.edit().putBoolean(KEY_SERVICE_ACTIVE, true).apply()

        // Initialize other components
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        sessionManager = UserSessionManager(applicationContext)
        textEnhancementRepository = TextEnhancementRepository(NetworkModule.apiService)

        // Configure service for text selection monitoring only
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        serviceInfo = info

        Log.d(TAG, "Service configured and ready - waiting for text selection events")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Only process if monitoring is enabled
        if (!isMonitoringEnabled()) {
            return
        }

        // Only process text selection events
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            handleTextSelectionChanged(event)
        }
    }

    private fun isMonitoringEnabled(): Boolean {
        return sharedPrefs.getBoolean(KEY_MONITORING_ENABLED, false)
    }

    private fun handleTextSelectionChanged(event: AccessibilityEvent) {
        Log.d(TAG, "Text selection changed - From: ${event.fromIndex}, To: ${event.toIndex}")

        // Cancel any pending bubble show
        cancelPendingBubble()

        // Get the source node
        val sourceNode = event.source
        if (sourceNode == null) {
            Log.d(TAG, "Source node is null")
            hideBubble()
            return
        }

        // Store the node reference
        selectedNode = sourceNode

        // Get the selected text
        val newSelectedText = extractSelectedText(sourceNode, event.fromIndex, event.toIndex)
        Log.d(TAG, "Extracted text: '$newSelectedText'")

        // Validate selection
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
            Log.d(TAG, "Invalid selection, hiding bubble")
            hideBubble()
        }
    }

    private fun extractSelectedText(node: AccessibilityNodeInfo, fromIndex: Int, toIndex: Int): String? {
        return try {
            // Method 1: Direct text extraction
            val nodeText = node.text
            if (nodeText != null && fromIndex >= 0 && toIndex > fromIndex &&
                fromIndex < nodeText.length && toIndex <= nodeText.length) {

                val selectedText = nodeText.subSequence(fromIndex, toIndex).toString()
                Log.d(TAG, "Direct extraction: '$selectedText'")
                return selectedText
            }

            // Method 2: Clipboard fallback (minimal use)
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

        // Initialize views
        minimizedView = currentBubbleView.findViewById(R.id.minimizedView)
        expandedView = currentBubbleView.findViewById(R.id.expandedView)

        // Initialize UI elements in expanded view
        val selectedTextView = expandedView.findViewById<TextView>(R.id.tvSelectedText)
        val transformedTextView = expandedView.findViewById<TextView>(R.id.tvTransformedText)
        val enhanceButton = expandedView.findViewById<Button>(R.id.btnEnhance)
        val copyButton = expandedView.findViewById<Button>(R.id.btnCopy)
        val replaceButton = expandedView.findViewById<Button>(R.id.btnReplace)
        val closeButton = expandedView.findViewById<android.widget.ImageButton>(R.id.btnClose)
        val minimizeButton = expandedView.findViewById<android.widget.ImageButton>(R.id.btnMinimize)
        val enhancedResultSection = expandedView.findViewById<LinearLayout>(R.id.enhancedResultSection)
        val progressContainer = expandedView.findViewById<LinearLayout>(R.id.progressContainer)

        // Enhancement type buttons
        val generalButton = expandedView.findViewById<Button>(R.id.btnGeneral)
        val professionalButton = expandedView.findViewById<Button>(R.id.btnProfessional)
        val casualButton = expandedView.findViewById<Button>(R.id.btnCasual)
        val conciseButton = expandedView.findViewById<Button>(R.id.btnConcise)
        val detailedButton = expandedView.findViewById<Button>(R.id.btnDetailed)

        enhancementButtons = listOf(generalButton, professionalButton, casualButton, conciseButton, detailedButton)

        // Initialize UI
        selectedTextView.text = selectedText
        enhancedResultSection.visibility = View.GONE

        // Reset state
        enhancedText = ""
        selectedEnhancementType = EnhancementType.GENERAL
        isExpanded = false

        // Show minimized view first
        minimizedView.visibility = View.VISIBLE
        expandedView.visibility = View.GONE

        // Set up button handlers
        setupEnhancementTypeButtons()
        setupActionButtons(transformedTextView, copyButton, replaceButton, enhanceButton,
            enhancedResultSection, progressContainer)

        // Minimize button - collapse to icon
        minimizeButton.setOnClickListener {
            collapseToIcon()
        }

        // Close button - completely hide bubble
        closeButton.setOnClickListener {
            hideBubble()
        }

        // Minimized icon click - expand to full bubble
        minimizedView.setOnClickListener {
            expandBubble()
        }

        // Configure window parameters
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
            Log.d(TAG, "Bubble shown successfully (minimized)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show bubble", e)
        }
    }

    private fun expandBubble() {
        isExpanded = true
        minimizedView.visibility = View.GONE
        expandedView.visibility = View.VISIBLE
        Log.d(TAG, "Bubble expanded")
    }

    private fun collapseToIcon() {
        isExpanded = false
        expandedView.visibility = View.GONE
        minimizedView.visibility = View.VISIBLE
        Log.d(TAG, "Bubble collapsed to icon")
    }

    private fun setupActionButtons(
        transformedTextView: TextView,
        copyButton: Button,
        replaceButton: Button,
        enhanceButton: Button,
        enhancedResultSection: LinearLayout,
        progressContainer: LinearLayout
    ) {
        enhanceButton.setOnClickListener {
            if (isEnhancing) return@setOnClickListener
            enhanceText(transformedTextView, copyButton, replaceButton, enhanceButton,
                enhancedResultSection, progressContainer)
        }

        copyButton.setOnClickListener {
            copyToClipboard(enhancedText)
            showButtonFeedback(copyButton, "✓ Copied", R.color.success_color)
            Toast.makeText(this, "Enhanced text copied!", Toast.LENGTH_SHORT).show()
        }

        replaceButton.setOnClickListener {
            val originalText = replaceButton.text
            replaceButton.text = "⏳"
            replaceButton.isEnabled = false

            if (replaceSelectedText(enhancedText)) {
                showButtonFeedback(replaceButton, "✓ Replaced", R.color.success_color)
                Toast.makeText(this, "Text replaced successfully!", Toast.LENGTH_SHORT).show()

                serviceScope.launch {
                    delay(1500)
                    hideBubble()
                }
            } else {
                // Fallback to copy
                copyToClipboard(enhancedText)
                showButtonFeedback(replaceButton, "📋 Copied", R.color.warning_color)
                Toast.makeText(this, "Copied to clipboard - paste manually", Toast.LENGTH_LONG).show()

                serviceScope.launch {
                    delay(2000)
                    replaceButton.text = originalText
                    replaceButton.isEnabled = true
                }
            }
        }
    }

    private fun showButtonFeedback(button: Button, text: String, colorRes: Int) {
        val originalText = button.text
        val originalColor = button.currentTextColor

        button.text = text
        button.setTextColor(ContextCompat.getColor(this, colorRes))

        serviceScope.launch {
            delay(1500)
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

        enhancementButtons.forEachIndexed { index, button ->
            val enhancementType = enhancementTypes[index]
            button.setOnClickListener {
                selectedEnhancementType = enhancementType
                updateEnhancementButtonStates()
            }
        }

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

    private fun enhanceText(
        transformedTextView: TextView,
        copyButton: Button,
        replaceButton: Button,
        enhanceButton: Button,
        enhancedResultSection: LinearLayout,
        progressContainer: LinearLayout
    ) {
        serviceScope.launch {
            try {
                val accessToken = sessionManager.getAccessToken().first()

                if (accessToken == null) {
                    Toast.makeText(this@TextSelectionService,
                        "Please log in to enhance text", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                isEnhancing = true
                enhanceButton.isEnabled = false
                enhanceButton.text = "⏳ Processing..."
                progressContainer.visibility = View.VISIBLE

                when (val result = textEnhancementRepository.enhanceText(
                    accessToken,
                    selectedText,
                    selectedEnhancementType
                )) {
                    is ApiResult.Success -> {
                        enhancedText = result.data.enhancedText
                        transformedTextView.text = enhancedText

                        // Show the enhanced result section
                        enhancedResultSection.visibility = View.VISIBLE
                        progressContainer.visibility = View.GONE

                        sessionManager.updateUserUsage(
                            tokensUsedToday = result.data.tokensUsedToday,
                            tokensRemaining = result.data.tokensRemainingToday,
                            lastUsageDate = null
                        )

                        Toast.makeText(this@TextSelectionService,
                            "✅ Enhanced! (${result.data.tokensUsedThisRequest} tokens)",
                            Toast.LENGTH_SHORT).show()
                    }

                    is ApiResult.Error -> {
                        progressContainer.visibility = View.GONE

                        if (result.message.contains("token limit") || result.message.contains("tokens")) {
                            // Use offline mode
                            enhancedText = transformText(selectedText, selectedEnhancementType)
                            transformedTextView.text = enhancedText
                            enhancedResultSection.visibility = View.VISIBLE

                            Toast.makeText(this@TextSelectionService,
                                "⚠️ Daily limit reached - using offline mode",
                                Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this@TextSelectionService,
                                "⚠️ ${result.message}", Toast.LENGTH_LONG).show()
                        }
                    }

                    is ApiResult.Loading -> {
                        // Already handled
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error enhancing text", e)
                progressContainer.visibility = View.GONE

                // Fallback to offline mode
                enhancedText = transformText(selectedText, selectedEnhancementType)
                transformedTextView.text = enhancedText
                enhancedResultSection.visibility = View.VISIBLE

                Toast.makeText(this@TextSelectionService,
                    "⚠️ Using offline mode", Toast.LENGTH_SHORT).show()
            } finally {
                isEnhancing = false
                enhanceButton.isEnabled = true
                enhanceButton.text = "✨ Enhance Text"
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
        // Offline fallback transformation
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
                    if (!formal.endsWith(".") && !formal.endsWith("!") && !formal.endsWith("?")) {
                        "$formal."
                    } else formal
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
                    if (!improved.endsWith(".") && !improved.endsWith("!") && !improved.endsWith("?")) {
                        "$improved."
                    } else improved
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
            // Method 1: Try paste method first
            if (tryReplaceUsingPaste(node, newText)) {
                return true
            }

            // Method 2: Try SET_TEXT action
            val fullText = node.text?.toString() ?: return false
            if (selectionStart >= 0 && selectionEnd > selectionStart &&
                selectionStart < fullText.length && selectionEnd <= fullText.length) {

                val before = fullText.substring(0, selectionStart)
                val after = fullText.substring(selectionEnd)
                val newFullText = before + newText + after

                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newFullText)
                }

                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to replace text", e)
            false
        }
    }

    private fun tryReplaceUsingPaste(node: AccessibilityNodeInfo, newText: String): Boolean {
        return try {
            copyToClipboard(newText)
            // Small delay to ensure clipboard is updated
            Thread.sleep(100)
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to replace using paste", e)
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service Destroyed")

        // Clean up and mark service as stopped
        sharedPrefs.edit()
            .putBoolean(KEY_SERVICE_ACTIVE, false)
            .putBoolean(KEY_MONITORING_ENABLED, false)
            .apply()

        cancelPendingBubble()
        serviceScope.cancel()
        hideBubble()
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
        cancelPendingBubble()
        hideBubble()
    }
}