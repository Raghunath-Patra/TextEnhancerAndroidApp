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
import kotlinx.coroutines.*

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

        val selectedTextView = currentBubbleView.findViewById<TextView>(R.id.tvSelectedText)
        val transformedTextView = currentBubbleView.findViewById<TextView>(R.id.tvTransformedText)
        val copyButton = currentBubbleView.findViewById<Button>(R.id.btnCopy)
        val replaceButton = currentBubbleView.findViewById<Button>(R.id.btnReplace)
        val closeButton = currentBubbleView.findViewById<Button>(R.id.btnClose)

        selectedTextView.text = "Selected: $selectedText"

        val transformedText = transformText(selectedText)
        transformedTextView.text = "Transformed: $transformedText"

        copyButton.setOnClickListener {
            copyToClipboard(transformedText)
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            hideBubble()
        }

        replaceButton.setOnClickListener {
            if (replaceSelectedText(transformedText)) {
                Toast.makeText(this, "Text replaced", Toast.LENGTH_SHORT).show()
                hideBubble()
            } else {
                copyToClipboard(transformedText)
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
        return text.replaceFirstChar { it.uppercase() } + " (Professional)"
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("transformed_text", text)
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