package com.tailsync.app.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.text.Html

/**
 * Helper class for clipboard operations with HTML support.
 */
class ClipboardHelper(private val context: Context) {

    private val clipboardManager: ClipboardManager by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    data class ClipboardContent(
        val plainText: String,
        val htmlText: String?,
        val hasHtml: Boolean
    )

    /**
     * Read current clipboard content, extracting both plain text and HTML if available.
     */
    fun readClipboard(): ClipboardContent? {
        val clip = clipboardManager.primaryClip ?: return null
        if (clip.itemCount == 0) return null

        val item = clip.getItemAt(0)
        val description = clip.description

        val hasHtml = description?.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) == true
        val htmlText = if (hasHtml) item.htmlText else null
        val plainText = item.coerceToText(context)?.toString() ?: return null

        return ClipboardContent(
            plainText = plainText,
            htmlText = htmlText,
            hasHtml = hasHtml
        )
    }

    /**
     * Write content to clipboard with optional HTML formatting.
     */
    fun writeClipboard(plainText: String, htmlText: String? = null) {
        val clip = if (htmlText != null) {
            // Create clip with both HTML and plain text
            ClipData.newHtmlText("TailSync", plainText, htmlText)
        } else {
            // Plain text only
            ClipData.newPlainText("TailSync", plainText)
        }
        clipboardManager.setPrimaryClip(clip)
    }

    /**
     * Convert plain text to basic HTML (preserves line breaks).
     */
    fun plainTextToHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br>")
    }

    /**
     * Convert HTML to plain text.
     */
    fun htmlToPlainText(html: String): String {
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
    }

    /**
     * Register a listener for clipboard changes.
     */
    fun addClipboardListener(listener: ClipboardManager.OnPrimaryClipChangedListener) {
        clipboardManager.addPrimaryClipChangedListener(listener)
    }

    /**
     * Remove clipboard change listener.
     */
    fun removeClipboardListener(listener: ClipboardManager.OnPrimaryClipChangedListener) {
        clipboardManager.removePrimaryClipChangedListener(listener)
    }
}
