package com.duckduckgo.duckchat.impl.ui

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.appcompat.widget.AppCompatEditText

class SubmitOnEnterEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle,
) : AppCompatEditText(context, attrs, defStyleAttr) {

    var submitOnEnterEnabled: Boolean = false
    var onEnter: (() -> Unit)? = null

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.imeOptions = outAttrs.imeOptions or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_ENTER_ACTION or
            EditorInfo.IME_ACTION_GO

        val inputConnection = super.onCreateInputConnection(outAttrs)

        return object : InputConnectionWrapper(inputConnection, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (submitOnEnterEnabled && text != null && text.contains("\n")) {
                    onEnter?.invoke()
                    return true
                }
                return super.commitText(text, newCursorPosition)
            }
            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (submitOnEnterEnabled && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                    onEnter?.invoke()
                    return true
                }
                return super.sendKeyEvent(event)
            }
        }
    }
}
