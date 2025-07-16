// TextSelectionService.kt
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
import androidx.lifecycle.lifecycleScope
import com.example.textselectionbubble.data.UserSessionManager
import com.example.textselectionbubble.data.models.EnhancementType
import com.example.textselectionbubble.data.network.ApiResult
import com.example.textselectionbubble.data.network.NetworkModule
import com.example.textselectionbubble.data.repository.TextEnhancementRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

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

                // Show bubble after delay (but don't enhance automatically)
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

        val selectedTextView = currentBubbleView.findViewById<TextView>(R.id.tvSelectedText)
        val transformedTextView = currentBubbleView.findViewById<TextView>(R.id.tvTransformedText)
        val enhanceButton = currentBubbleView.findViewById<Button>(R.id.btnEnhance)
        val copyButton = currentBubbleView.findViewById<Button>(R.id.btnCopy)
        val replaceButton = currentBubbleView.findViewById<Button>(R.id.btnReplace)
        val closeButton = currentBubbleView.findViewById<Button>(R.id.btnClose)

        // Initialize UI
        selectedTextView.text = "Selected: $selectedText"
        transformedTextView.text = "Click 'Enhance' to improve this text with AI"

        // Reset enhanced text
        enhancedText = ""

        // Initially disable copy/replace buttons until text is enhanced
        copyButton.isEnabled = false
        replaceButton.isEnabled = false
        enhanceButton.isEnabled = true

        // Enhance button click handler
        enhanceButton.setOnClickListener {
            if (isEnhancing) return@setOnClickListener

            serviceScope.launch {
                try {
                    val accessToken = sessionManager.getAccessToken().first()

                    if (accessToken == null) {
                        transformedTextView.text = "Please log in to enhance text"
                        return@launch
                    }

                    isEnhancing = true
                    enhanceButton.isEnabled = false
                    transformedTextView.text = "Enhancing..."

                    when (val result = textEnhancementRepository.enhanceText(
                        accessToken,
                        selectedText,
                        EnhancementType.GENERAL
                    )) {
                        is ApiResult.Success -> {
                            enhancedText = result.data.enhancedText
                            transformedTextView.text = "Enhanced: $enhancedText"

                            // Enable copy/replace buttons
                            copyButton.isEnabled = true
                            replaceButton.isEnabled = true

                            // Update user token usage in session
                            sessionManager.updateUserUsage(
                                tokensUsedToday = result.data.tokensUsedToday,
                                tokensRemaining = result.data.tokensRemainingToday,
                                lastUsageDate = null
                            )

                            Toast.makeText(this@TextSelectionService, "Text enhanced successfully!", Toast.LENGTH_SHORT).show()
                        }

                        is ApiResult.Error -> {
                            transformedTextView.text = "Error: ${result.message}"

                            // If it's a token limit error, allow manual enhancement
                            if (result.message.contains("token limit") || result.message.contains("tokens")) {
                                enhancedText = transformText(selectedText) // Fallback to simple transform
                                transformedTextView.text = "Enhanced (fallback): $enhancedText"
                                copyButton.isEnabled = true
                                replaceButton.isEnabled = true
                            }
                        }

                        is ApiResult.Loading -> {
                            // Already handled above
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error enhancing text", e)
                    transformedTextView.text = "Enhancement failed"

                    // Fallback to simple transformation
                    enhancedText = transformText(selectedText)
                    transformedTextView.text = "Enhanced (fallback): $enhancedText"
                    copyButton.isEnabled = true
                    replaceButton.isEnabled = true
                } finally {
                    isEnhancing = false
                    enhanceButton.isEnabled = true
                }
            }
        }

        copyButton.setOnClickListener {
            val textToCopy = if (enhancedText.isNotEmpty()) enhancedText else selectedText
            copyToClipboard(textToCopy)
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            hideBubble()
        }

        replaceButton.setOnClickListener {
            val textToReplace = if (enhancedText.isNotEmpty()) enhancedText else selectedText
            if (replaceSelectedText(textToReplace)) {
                Toast.makeText(this, "Text replaced", Toast.LENGTH_SHORT).show()
                hideBubble()
            } else {
                copyToClipboard(textToReplace)
                Toast.makeText(this, "Copied to clipboard - paste to replace", Toast.LENGTH_SHORT).show()
                hideBubble()
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

    private fun makeBubbleDraggable(view: View, params: WindowManager.LayoutParams) {
        view.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false
            private val dragThreshold = 10

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

                        if (!isDragging && (kotlin.math.abs(deltaX) > dragThreshold || kotlin.math.abs(deltaY) > dragThreshold)) {
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

    private fun transformText(text: String): String {
        // Fallback transformation when API is not available
        return text.replaceFirstChar { it.uppercase() } + " (Enhanced)"
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