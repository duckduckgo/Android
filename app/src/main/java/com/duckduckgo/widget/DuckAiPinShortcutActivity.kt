package com.duckduckgo.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R

/**
 * This activity is capable of handling requests to create a shortcut.
 * Exists purely to be able to add the Duck.ai shortcut on the home screen from the widget picker.
 */
class DuckAiPinShortcutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            val shortcutInfo = createShortcutInfo(this)
            ShortcutManagerCompat.reportShortcutUsed(this, SHORTCUT_ID)
            setResult(RESULT_OK, ShortcutManagerCompat.createShortcutResultIntent(this, shortcutInfo))
        } else {
            setResult(RESULT_OK)
        }

        finish()
    }

    private fun createShortcutInfo(context: Context): ShortcutInfoCompat {
        val shortLabel = getString(R.string.duckAiOnlyPinShortcutLabel)

        val shortcutIntent = BrowserActivity.intent(context, openDuckChat = true, duckChatSessionActive = true).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        return ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setShortLabel(shortLabel)
            .setIcon(IconCompat.createWithResource(context, R.drawable.duckai_64))
            .setIntent(shortcutIntent)
            .build()
    }

    companion object {
        private const val SHORTCUT_ID = "duck_ai_from_widget_picker"
    }
}
