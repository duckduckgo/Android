/*
 * Copyright (c) 2022 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.common.ui.view.dialog

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import com.duckduckgo.common.ui.view.button.ButtonType
import com.duckduckgo.common.ui.view.button.DaxButtonGhost
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.DialogStackedAlertBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * A single stacked button, paired with the [ButtonType] it should be rendered as.
 *
 * Use with [StackedAlertDialogBuilder.setStackedButtons] when a dialog needs a filled
 * primary/secondary action instead of the default ghost styling.
 */
data class StackedButton(
    @StringRes val textId: Int,
    val type: ButtonType = ButtonType.GHOST,
    @DrawableRes val iconId: Int? = null,
)

class StackedAlertDialogBuilder(val context: Context) : DaxAlertDialog {

    abstract class EventListener {
        open fun onDialogShown() {}
        open fun onDialogDismissed() {}
        open fun onDialogCancelled() {}
        open fun onButtonClicked(position: Int) {}
    }

    internal class DefaultEventListener : EventListener()

    /**
     * A button ready to be rendered. A null [type] means "use the builder's default styling",
     * which keeps the ghost/destructive behaviour of [setStackedButtons] and [setDestructiveButtons].
     */
    private data class StackedButtonSpec(
        val text: CharSequence,
        val type: ButtonType?,
        val iconId: Int? = null,
    )

    private var dialog: AlertDialog? = null

    private var listener: EventListener = DefaultEventListener()
    private var titleText: CharSequence = ""
    private var messageText: CharSequence = ""
    private var headerImageDrawableId = 0
    private var stackedButtonList: MutableList<StackedButtonSpec> = mutableListOf()
    private var isDestructiveVersion: Boolean = false
    private var isRebrandUpdate: Boolean = false
    private var componentCallbacks: ComponentCallbacks? = null
    private var isRecreating: Boolean = false

    fun setHeaderImageResource(@DrawableRes drawableId: Int): StackedAlertDialogBuilder {
        headerImageDrawableId = drawableId
        return this
    }

    fun setTitle(@StringRes textId: Int): StackedAlertDialogBuilder {
        titleText = context.getText(textId)
        return this
    }

    fun setTitle(text: CharSequence): StackedAlertDialogBuilder {
        titleText = text
        return this
    }

    fun setMessage(@StringRes textId: Int): StackedAlertDialogBuilder {
        messageText = context.getText(textId)
        return this
    }

    fun setMessage(text: CharSequence): StackedAlertDialogBuilder {
        messageText = text
        return this
    }

    /**
     * Adds the given buttons, all rendered as ghost buttons, or tinted ghost buttons when
     * [setDestructiveButtons] is enabled.
     */
    fun setStackedButtons(@StringRes stackedButtonTextId: List<Int>): StackedAlertDialogBuilder {
        stackedButtonTextId.forEach {
            stackedButtonList.add(StackedButtonSpec(context.getText(it), type = null))
        }
        return this
    }

    /**
     * Adds the given buttons, each rendered as its own [StackedButton.type], so a dialog can mix a
     * filled primary or secondary action with ghost ones.
     *
     * [StackedButton.type] defaults to [ButtonType.GHOST], matching the styling of the
     * [List]-of-string-resources overload. Types set here always win over [setDestructiveButtons].
     */
    @JvmName("setStackedButtonsWithType")
    fun setStackedButtons(stackedButtons: List<StackedButton>): StackedAlertDialogBuilder {
        stackedButtons.forEach {
            stackedButtonList.add(StackedButtonSpec(context.getText(it.textId), it.type, it.iconId))
        }
        return this
    }

    /**
     * Opts into the brand design update styling: a fixed-width card with wider corners, the header
     * image inside a circular container, and full-width buttons with centred labels.
     *
     * Opt-in per dialog rather than driven by the ThemeOverlay.Rebrand overlay, so dialogs that have not been
     * redesigned keep their current appearance.
     *
     * Figma: https://www.figma.com/design/aMaDTBcE9Fsfu40NbjzcrH/Permission--iOS-Android-?node-id=1229-26621
     */
    fun setRebrandUpdate(isRebrandUpdate: Boolean): StackedAlertDialogBuilder {
        this.isRebrandUpdate = isRebrandUpdate
        return this
    }

    fun setDestructiveButtons(isDestructive: Boolean): StackedAlertDialogBuilder {
        isDestructiveVersion = isDestructive
        return this
    }

    fun addEventListener(eventListener: EventListener): StackedAlertDialogBuilder {
        listener = eventListener
        return this
    }

    override fun build(): DaxAlertDialog {
        checkRequiredFieldsSet()
        val binding: DialogStackedAlertBinding = DialogStackedAlertBinding.inflate(LayoutInflater.from(context))

        val dialogTheme = if (isRebrandUpdate) {
            R.style.Widget_DuckDuckGo_Dialog_Rebrand
        } else {
            R.style.Widget_DuckDuckGo_Dialog
        }

        val dialogBuilder = MaterialAlertDialogBuilder(context, dialogTheme)
            .setView(dialogContent(binding))
            .apply {
                setCancelable(false)
                setOnDismissListener {
                    if (!isRecreating) {
                        unregisterConfigurationCallback()
                        listener.onDialogDismissed()
                    }
                }
                setOnCancelListener { if (!isRecreating) listener.onDialogCancelled() }
            }

        dialog = dialogBuilder.create()
        setViews(binding, dialog!!)

        return this
    }

    /**
     * The layout has no scroll container of its own, so tall content is clipped rather than
     * scrolled where the card cannot grow - short landscape screens, or large font scales.
     */
    private fun dialogContent(binding: DialogStackedAlertBinding): View {
        if (!isRebrandUpdate) return binding.root

        return ScrollView(context).apply {
            isFillViewport = true
            addView(
                binding.root,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    override fun show() {
        if (dialog == null) {
            build()
        }
        dialog?.show()
        if (isRebrandUpdate) {
            registerConfigurationCallback()
        }
        listener.onDialogShown()
    }

    /**
     * Rebuilds the dialog against the new configuration. The dialog is not cancellable, so dropping
     * it on rotation would strand the caller's flow with no way back to it.
     */
    private fun registerConfigurationCallback() {
        if (componentCallbacks != null) return

        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) = recreate()

            override fun onLowMemory() = Unit
        }
        context.registerComponentCallbacks(callbacks)
        componentCallbacks = callbacks
    }

    private fun unregisterConfigurationCallback() {
        componentCallbacks?.let { context.unregisterComponentCallbacks(it) }
        componentCallbacks = null
    }

    private fun recreate() {
        if (dialog?.isShowing != true) return

        isRecreating = true
        dialog?.dismiss()
        build()
        dialog?.show()
        isRecreating = false
    }

    override fun dismiss() {
        dialog?.dismiss()
    }

    override fun isShowing(): Boolean = dialog?.isShowing == true
    private fun setViews(
        binding: DialogStackedAlertBinding,
        dialog: AlertDialog,
    ) {
        if (headerImageDrawableId > 0) {
            binding.stackedAlertDialogImage.setImageResource(headerImageDrawableId)
            if (isRebrandUpdate) {
                val inset = context.resources.getDimensionPixelSize(R.dimen.rebrandDialogIconInset)
                binding.stackedAlertDialogImage.setBackgroundResource(R.drawable.background_dialog_icon_circular)
                binding.stackedAlertDialogImage.setPadding(inset, inset, inset, inset)
                binding.stackedAlertDialogTitle.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = context.resources.getDimensionPixelSize(R.dimen.keyline_4)
                }
            }
        } else {
            binding.stackedAlertDialogImage.gone()
        }

        binding.stackedAlertDialogTitle.text = titleText

        if (messageText.isEmpty()) {
            binding.stackedlertDialogMessage.gone()
        } else {
            binding.stackedlertDialogMessage.text = messageText
        }

        if (isRebrandUpdate) {
            val sidePadding = context.resources.getDimensionPixelSize(R.dimen.rebrandDialogButtonSidePadding)
            binding.stackedAlertDialogButtonLayout.setPadding(sidePadding, 0, sidePadding, 0)
        }

        addButtons(binding.stackedAlertDialogButtonLayout, stackedButtonList, dialog)
    }

    private fun addButtons(
        root: LinearLayout,
        stackedButtonList: MutableList<StackedButtonSpec>,
        dialog: AlertDialog,
    ) {
        val buttonSpacing = context.resources.getDimensionPixelSize(R.dimen.keyline_2)
        stackedButtonList.forEachIndexed { index, stackedButton ->
            val button = when {
                stackedButton.type != null -> {
                    stackedButton.type.getView(context)
                }

                isDestructiveVersion && index == stackedButtonList.lastIndex -> {
                    val ghostButton = DaxButtonGhost(context, null)
                    ghostButton.setTextColor(
                        ContextCompat.getColorStateList(
                            context,
                            R.color.destructive_text_color_selector,
                        ),
                    )
                    ghostButton
                }

                isDestructiveVersion -> {
                    val ghostButton = DaxButtonGhost(context, null)
                    ghostButton.setTextColor(
                        ContextCompat.getColorStateList(
                            context,
                            R.color.secondary_text_color_selector,
                        ),
                    )
                    ghostButton
                }

                else -> {
                    DaxButtonGhost(context, null)
                }
            }

            button.text = stackedButton.text
            stackedButton.iconId?.let {
                button.setIconResource(it)
                button.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            }
            button.layoutParams = LinearLayout.LayoutParams(
                if (isRebrandUpdate) LinearLayout.LayoutParams.MATCH_PARENT else LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                if (index > 0 && isRebrandUpdate) {
                    topMargin = (buttonSpacing - button.insetTop - button.insetBottom).coerceAtLeast(0)
                }
            }

            button.setOnClickListener {
                listener.onButtonClicked(index)
                dialog.dismiss()
            }

            root.addView(button)
        }
    }

    private fun checkRequiredFieldsSet() {
        if (stackedButtonList.isEmpty()) {
            throw Exception("VerticallyStackedAlertDialog: You must always provide a list of buttons")
        }
        if (titleText.isEmpty()) {
            throw Exception("TextAlertDialog: You must always provide a Title")
        }
    }
}
