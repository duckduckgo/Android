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

package com.duckduckgo.common.ui.view.text

import android.R.attr
import android.content.Context
import android.content.res.TypedArray
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.ClassLoaderCreator
import android.text.InputType
import android.text.TextUtils.TruncateAt
import android.text.TextUtils.TruncateAt.END
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.text.method.PasswordTransformationMethod
import android.util.AttributeSet
import android.util.SparseArray
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.inputmethod.EditorInfo
import android.widget.TextView.OnEditorActionListener
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.doOnNextLayout
import androidx.core.view.postDelayed
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doOnTextChanged
import com.duckduckgo.common.ui.view.showKeyboard
import com.duckduckgo.common.ui.view.text.DaxTextInput.Type.INPUT_TYPE_FORM_MODE
import com.duckduckgo.common.ui.view.text.DaxTextInput.Type.INPUT_TYPE_IP_ADDRESS_MODE
import com.duckduckgo.common.ui.view.text.DaxTextInput.Type.INPUT_TYPE_MULTI_LINE
import com.duckduckgo.common.ui.view.text.DaxTextInput.Type.INPUT_TYPE_PASSWORD
import com.duckduckgo.common.ui.view.text.DaxTextInput.Type.INPUT_TYPE_SINGLE_LINE
import com.duckduckgo.common.ui.view.text.DaxTextInput.Type.INPUT_TYPE_URL_MODE
import com.duckduckgo.common.ui.view.text.TextInput.Action
import com.duckduckgo.common.ui.view.text.TextInput.Action.PerformEndAction
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewDaxTextInputBinding
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputLayout.END_ICON_CUSTOM
import com.google.android.material.textfield.TextInputLayout.END_ICON_NONE
import java.util.*

interface TextInput {
    var text: String
    var hint: String
    var isEditable: Boolean
    var error: String?

    fun addTextChangedListener(textWatcher: TextWatcher)
    fun removeTextChangedListener(textWatcher: TextWatcher)
    fun addFocusChangedListener(listener: OnFocusChangeListener)
    fun setEndIcon(
        @DrawableRes endIconRes: Int,
        contentDescription: String? = null,
    )
    fun setSelectAllOnFocus(boolean: Boolean)

    fun removeEndIcon()

    fun onAction(actionHandler: (Action) -> Unit)

    fun setOnEditorActionListener(listener: OnEditorActionListener)
    fun doOnTextChanged(action: (CharSequence?, Int, Int, Int) -> Unit)

    sealed class Action {
        data object PerformEndAction : Action()
    }
}

private const val ENABLED_OPACITY = 1f
private const val DISABLED_OPACITY = 0.4f

class DaxTextInput @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr), TextInput {
    private val binding: ViewDaxTextInputBinding by viewBinding()
    private val transformationMethod by lazy {
        PasswordTransformationMethod.getInstance()
    }
    private var truncated: Boolean = false
    private var isPassword: Boolean = false
    private var isPasswordShown: Boolean = false
    private var isClickable: Boolean = false
    private var internalFocusChangedListener: OnFocusChangeListener? = null

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.DaxTextInput,
            0,
            com.google.android.material.R.style.Widget_MaterialComponents_TextInputEditText_OutlinedBox,
        ).apply {
            text = getString(R.styleable.DaxTextInput_android_text).orEmpty()
            getDrawable(R.styleable.DaxTextInput_endIcon)?.let {
                setupEndIconDrawable(it, getString(R.styleable.DaxTextInput_endIconContentDescription).orEmpty())
            }

            // This needs to be done after we know that the view has the end icon set
            isClickable = getBoolean(R.styleable.DaxTextInput_clickable, false)
            isEditable = getBoolean(R.styleable.DaxTextInput_editable, true)
            binding.internalInputLayout.setHintWithoutAnimation(getString(R.styleable.DaxTextInput_android_hint))

            val inputType = getInputType()
            setupInputMode(inputType)

            val minLines = getInt(R.styleable.DaxTextInput_android_minLines, 1)
            setMinLines(inputType, minLines)

            truncated = getBoolean(R.styleable.DaxTextInput_singleLineTextTruncated, false)
            setSingleLineTextTruncation(truncated)

            context.obtainStyledAttributes(attrs, intArrayOf(attr.imeOptions)).apply {
                binding.internalEditText.imeOptions = getInt(0, EditorInfo.IME_NULL)
                recycle()
            }

            if (getBoolean(R.styleable.DaxTextInput_capitalizeKeyboard, false)) {
                binding.internalEditText.inputType = binding.internalEditText.inputType or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            }

            val enabled = getBoolean(R.styleable.DaxTextInput_android_enabled, true)
            isEnabled = enabled

            setFocusListener()

            recycle()
        }
    }

    override fun doOnTextChanged(action: (CharSequence?, Int, Int, Int) -> Unit) {
        binding.internalEditText.doOnTextChanged(action)
    }
    override fun setOnEditorActionListener(listener: OnEditorActionListener) {
        binding.internalEditText.setOnEditorActionListener(listener)
    }

    private fun setupInputMode(inputType: Type) {
        isPassword = inputType == INPUT_TYPE_PASSWORD
        if (isPassword) {
            setupPasswordMode()
        } else {
            setupTextMode(inputType)
        }
    }

    private fun setMinLines(inputType: Type, minLines: Int) {
        if (inputType == INPUT_TYPE_FORM_MODE) {
            binding.internalEditText.minLines = 3
        } else {
            binding.internalEditText.minLines = minLines
        }
    }

    private fun setFocusListener() {
        binding.internalEditText.onFocusChangeListener = OnFocusChangeListener { view, hasFocus ->
            internalFocusChangedListener?.onFocusChange(view, hasFocus)
            if (hasFocus) {
                if (isPassword) {
                    showPassword()
                }
                binding.internalEditText.showKeyboard()
            } else {
                if (isPassword) {
                    hidePassword()
                }
            }
        }
    }

    override var text: String
        get() = binding.internalEditText.text.toString()
        set(value) {
            binding.internalEditText.setText(value)
            binding.internalEditText.setSelection(value.length)
        }

    override var hint: String
        get() = binding.internalEditText.hint.toString()
        set(value) {
            binding.internalInputLayout.setHintWithoutAnimation(value)
        }

    override var isEditable: Boolean
        get() = binding.internalEditText.isEnabled
        set(value) {
            binding.internalEditText.isEnabled = value
            handleIsEditableChangeForEndIcon(value)
        }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        binding.internalInputLayout.isEnabled = enabled
        binding.internalPasswordIcon.isEnabled = enabled
        binding.root.alpha = if (enabled) ENABLED_OPACITY else DISABLED_OPACITY
    }

    override var error: String?
        get() = binding.internalInputLayout.error.toString()
        set(value) {
            binding.internalInputLayout.error = value
        }

    fun showKeyboardDelayed() {
        binding.root.postDelayed(KEYBOARD_DELAY) {
            binding.internalEditText.showKeyboard()
        }
    }

    private fun handleIsEditableChangeForEndIcon(isEditable: Boolean) {
        if (binding.internalInputLayout.endIconMode != END_ICON_NONE) {
            binding.internalInputLayout.isEndIconVisible = !isEditable
            if (isEditable && isPassword) {
                binding.internalPasswordIcon.updateLayoutParams<LayoutParams> {
                    this.marginEnd = context.resources.getDimensionPixelSize(R.dimen.outlinedTextPasswordEndMarginWithoutEndIcon)
                }
            } else if (isClickable) {
                binding.internalInputLayout.isEndIconVisible = true
                binding.internalPasswordIcon.updateLayoutParams<LayoutParams> {
                    this.marginEnd = context.resources.getDimensionPixelSize(R.dimen.outlinedTextPasswordEndMarginWithoutEndIcon)
                }
            } else {
                binding.internalPasswordIcon.updateLayoutParams<LayoutParams> {
                    this.marginEnd = context.resources.getDimensionPixelSize(R.dimen.outlinedTextPasswordEndMarginWithEndIcon)
                }
            }
        } else {
            if (isClickable) {
                binding.internalEditText.isEnabled = false
            }
        }
    }

    override fun addTextChangedListener(textWatcher: TextWatcher) {
        binding.internalEditText.addTextChangedListener(textWatcher)
    }

    override fun removeTextChangedListener(textWatcher: TextWatcher) {
        binding.internalEditText.removeTextChangedListener(textWatcher)
    }

    override fun addFocusChangedListener(listener: OnFocusChangeListener) {
        internalFocusChangedListener = listener
    }

    override fun setEndIcon(
        endIconRes: Int,
        contentDescription: String?,
    ) {
        ContextCompat.getDrawable(context, endIconRes)?.let {
            setupEndIconDrawable(it, contentDescription.orEmpty())
        }
    }

    override fun setSelectAllOnFocus(boolean: Boolean) {
        binding.internalEditText.setSelectAllOnFocus(boolean)
    }

    override fun removeEndIcon() {
        binding.internalInputLayout.apply {
            endIconMode = END_ICON_NONE
            endIconDrawable = null
            isEndIconVisible = false
            endIconContentDescription = null
        }
    }

    override fun onAction(actionHandler: (Action) -> Unit) {
        binding.internalInputLayout.setEndIconOnClickListener {
            actionHandler(PerformEndAction)
        }

        if (isClickable) {
            binding.internalEditText.onFocusChangeListener = OnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    actionHandler(PerformEndAction)
                    view.clearFocus()
                }
            }
        }
    }

    private fun setupEndIconDrawable(
        drawable: Drawable,
        contentDescription: String,
    ) {
        binding.internalInputLayout.apply {
            endIconMode = END_ICON_CUSTOM
            endIconDrawable = drawable
            isEndIconVisible = true
            endIconContentDescription = contentDescription
        }
    }

    private fun setupPasswordMode() {
        binding.internalPasswordIcon.visibility = VISIBLE
        binding.internalEditText.inputType = EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
        binding.internalEditText.transformationMethod = transformationMethod
        // We are using the suffix text to make space for the password icon. We can't modify the suffix textview enough to be able to use it
        // as the password show/hide icon. SuffixTextView seems to be anchored to the top of the TextInputLayout.
        binding.internalInputLayout.suffixText = " "
        binding.internalInputLayout.suffixTextView.width = context.resources.getDimensionPixelSize(R.dimen.outlinedTextPasswordIconSize)
        // We don't need the margin since we don't need to push the icon beside the end icon
        if (!binding.internalInputLayout.isEndIconVisible) {
            binding.internalPasswordIcon.updateLayoutParams<LayoutParams> {
                this.marginEnd = context.resources.getDimensionPixelSize(R.dimen.outlinedTextPasswordEndMarginWithoutEndIcon)
            }
        }
        binding.internalPasswordIcon.setOnClickListener {
            if (isPasswordShown) {
                hidePassword()
            } else {
                showPassword()
            }
        }
    }

    private fun showPassword() {
        isPasswordShown = true
        binding.internalPasswordIcon.setImageResource(R.drawable.ic_eye_closed_24)

        val inputType = if (binding.internalEditText.hasFocus()) {
            EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
        }
        binding.internalEditText.inputType = inputType
        binding.internalEditText.transformationMethod = null
        binding.internalEditText.setSelection(binding.internalEditText.length())
    }

    private fun hidePassword() {
        isPasswordShown = false
        binding.internalPasswordIcon.setImageResource(R.drawable.ic_eye_24)
        binding.internalEditText.inputType = EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
        binding.internalEditText.transformationMethod = transformationMethod
        binding.internalEditText.setSelection(binding.internalEditText.length())
    }

    private fun setupTextMode(inputType: Type) {
        binding.internalPasswordIcon.visibility = View.GONE

        if (inputType == INPUT_TYPE_SINGLE_LINE) {
            binding.internalEditText.ellipsize = TruncateAt.END
        }

        if (inputType == INPUT_TYPE_IP_ADDRESS_MODE) {
            binding.internalEditText.inputType = EditorInfo.TYPE_CLASS_NUMBER or EditorInfo.TYPE_NUMBER_FLAG_DECIMAL
            binding.internalEditText.keyListener = DigitsKeyListener.getInstance("0123456789.")
        } else if (inputType == INPUT_TYPE_MULTI_LINE || inputType == INPUT_TYPE_FORM_MODE) {
            binding.internalEditText.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
        } else if (inputType == INPUT_TYPE_URL_MODE) {
            binding.internalEditText.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_URI
        } else {
            binding.internalEditText.inputType = EditorInfo.TYPE_CLASS_TEXT
        }
    }

    private fun setSingleLineTextTruncation(truncated: Boolean) {
        if (truncated) {
            binding.internalEditText.apply {
                inputType = EditorInfo.TYPE_NULL
                ellipsize = END
            }
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        // All savedstate logic added here is necessary since this view have children with assigned ids. Without this code, having multiple
        // OutlinedTextInput instances causes the state to be overwritten for children with the same id.
        // More info: https://web.archive.org/web/20180625034135/http://trickyandroid.com/saving-android-view-state-correctly/
        val superState = super.onSaveInstanceState()
        val ss = SavedState(superState)
        ss.childrenStates = SparseArray<Parcelable>()
        for (i in 0 until childCount) {
            getChildAt(i).saveHierarchyState(ss.childrenStates)
        }
        return ss
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        val ss = state as SavedState
        super.onRestoreInstanceState(ss.superState)
        for (i in 0 until childCount) {
            getChildAt(i).restoreHierarchyState(ss.childrenStates)
        }
    }

    override fun dispatchSaveInstanceState(container: SparseArray<Parcelable?>?) {
        dispatchFreezeSelfOnly(container)
    }

    override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable?>?) {
        dispatchThawSelfOnly(container)
    }

    private fun TextInputLayout.setHintWithoutAnimation(hint: String?) {
        val hintAnimationEnabledSnapshot = isHintAnimationEnabled
        isHintAnimationEnabled = false
        this.hint = hint
        doOnNextLayout { isHintAnimationEnabled = hintAnimationEnabledSnapshot }
    }

    private fun TypedArray.getInputType(): Type {
        val inputTypeInt = getInt(R.styleable.DaxTextInput_type, INPUT_TYPE_MULTI_LINE.value)
        return Type.values().firstOrNull { it.value == inputTypeInt } ?: INPUT_TYPE_MULTI_LINE
    }

    enum class Type(val value: Int) {
        INPUT_TYPE_MULTI_LINE(0),
        INPUT_TYPE_SINGLE_LINE(1),
        INPUT_TYPE_PASSWORD(2),
        INPUT_TYPE_FORM_MODE(3),
        INPUT_TYPE_IP_ADDRESS_MODE(4),
        INPUT_TYPE_URL_MODE(5),
    }

    companion object {
        private const val KEYBOARD_DELAY = 500L
    }

    internal class SavedState : BaseSavedState {
        var childrenStates: SparseArray<Parcelable>? = null

        constructor(superState: Parcelable?) : super(superState)
        private constructor(
            parcel: Parcel,
            classLoader: ClassLoader?,
        ) : super(parcel) {
            childrenStates = parcel.readSparseArray(classLoader)
        }

        override fun writeToParcel(
            out: Parcel,
            flags: Int,
        ) {
            super.writeToParcel(out, flags)
            out.writeSparseArray(childrenStates)
        }

        companion object {

            @JvmField
            val CREATOR: ClassLoaderCreator<SavedState> = object : ClassLoaderCreator<SavedState> {
                override fun createFromParcel(
                    source: Parcel,
                    loader: ClassLoader?,
                ): SavedState {
                    return SavedState(source, loader)
                }

                override fun createFromParcel(source: Parcel): SavedState {
                    return createFromParcel(source, null)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }
}
