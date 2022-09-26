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

package com.duckduckgo.mobile.android.ui.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.ClassLoaderCreator
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.util.AttributeSet
import android.util.SparseArray
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.inputmethod.EditorInfo
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.doOnNextLayout
import androidx.core.view.updateLayoutParams
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewOutlinedTextInputBinding
import com.duckduckgo.mobile.android.ui.view.OutlinedTextInput.Action
import com.duckduckgo.mobile.android.ui.view.OutlinedTextInput.Action.PerformEndAction
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputLayout.END_ICON_CUSTOM
import com.google.android.material.textfield.TextInputLayout.END_ICON_NONE

interface OutlinedTextInput {
    var text: String
    var isEditable: Boolean

    fun addTextChangedListener(textWatcher: TextWatcher)
    fun removeTextChangedListener(textWatcher: TextWatcher)

    fun onAction(actionHandler: (Action) -> Unit)

    sealed class Action {
        object PerformEndAction : Action()
    }
}

class OutLinedTextInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), OutlinedTextInput {
    private val binding: ViewOutlinedTextInputBinding by viewBinding()
    private val transformationMethod by lazy {
        PasswordTransformationMethod.getInstance()
    }
    private var isPassword: Boolean = false
    private var isPasswordShown: Boolean = false

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.OutLinedTextInputView,
            0,
            com.google.android.material.R.style.Widget_MaterialComponents_TextInputEditText_OutlinedBox
        ).apply {
            text = getString(R.styleable.OutLinedTextInputView_android_text) ?: ""
            getDrawable(R.styleable.OutLinedTextInputView_endIcon)?.let {
                setupEndIcon(it, getString(R.styleable.OutLinedTextInputView_endIconContentDescription) ?: "")
            }

            // This needs to be done after we know that the view has the end icon set
            isEditable = getBoolean(R.styleable.OutLinedTextInputView_editable, true)
            binding.internalInputLayout.setHintWithoutAnimation(getString(R.styleable.OutLinedTextInputView_android_hint))
            isPassword = getBoolean(R.styleable.OutLinedTextInputView_isPassword, false)
            if (isPassword) {
                setupPasswordMode()
            } else {
                setupTextMode()
            }

            binding.internalEditText.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    showKeyboard()
                }
            }
            recycle()
        }
    }

    override var text: String
        get() = binding.internalEditText.text.toString()
        set(value) {
            binding.internalEditText.setText(value)
        }

    override var isEditable: Boolean
        get() = binding.internalEditText.isEnabled
        set(value) {
            binding.internalEditText.isEnabled = value
            handleIsEditableChangeForEndIcon(value)
        }

    private fun handleIsEditableChangeForEndIcon(isEditable: Boolean) {
        if (binding.internalInputLayout.endIconMode != END_ICON_NONE) {
            binding.internalInputLayout.isEndIconVisible = !isEditable
            if (isEditable && isPassword) {
                binding.internalPasswordIcon.updateLayoutParams<LayoutParams> {
                    this.marginEnd = context.resources.getDimensionPixelSize(R.dimen.outlinedTextPasswordEndMarginWithoutEndIcon)
                }
            } else {
                binding.internalPasswordIcon.updateLayoutParams<LayoutParams> {
                    this.marginEnd = context.resources.getDimensionPixelSize(R.dimen.outlinedTextPasswordEndMarginWithEndIcon)
                }
            }
        }
    }

    override fun addTextChangedListener(textWatcher: TextWatcher) {
        binding.internalEditText.addTextChangedListener(textWatcher)
    }

    override fun removeTextChangedListener(textWatcher: TextWatcher) {
        binding.internalEditText.removeTextChangedListener(textWatcher)
    }

    override fun onAction(actionHandler: (Action) -> Unit) {
        binding.internalInputLayout.setEndIconOnClickListener {
            actionHandler(PerformEndAction)
        }
    }

    private fun setupEndIcon(
        drawable: Drawable,
        contentDescription: String
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
        binding.internalEditText.inputType = EditorInfo.TYPE_TEXT_VARIATION_PASSWORD or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
        // This has to be set before the transformationMethod. Else, it will force the password to be visible.
        binding.internalEditText.isSingleLine = false
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
            isPasswordShown = !isPasswordShown
            if (isPasswordShown) {
                binding.internalPasswordIcon.setImageResource(R.drawable.ic_password_hide)
                binding.internalEditText.transformationMethod = null
            } else {
                binding.internalPasswordIcon.setImageResource(R.drawable.ic_password_show)
                binding.internalEditText.transformationMethod = transformationMethod
            }
        }
    }

    private fun setupTextMode() {
        binding.internalPasswordIcon.visibility = View.GONE
        binding.internalEditText.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
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

    internal class SavedState : BaseSavedState {
        var childrenStates: SparseArray<Parcelable>? = null

        constructor(superState: Parcelable?) : super(superState)
        private constructor(
            parcel: Parcel,
            classLoader: ClassLoader?
        ) : super(parcel) {
            childrenStates = parcel.readSparseArray(classLoader)
        }

        override fun writeToParcel(
            out: Parcel,
            flags: Int
        ) {
            super.writeToParcel(out, flags)
            out.writeSparseArray(childrenStates)
        }

        companion object {
            @JvmField
            val CREATOR: ClassLoaderCreator<SavedState> = object : ClassLoaderCreator<SavedState> {
                override fun createFromParcel(
                    source: Parcel,
                    loader: ClassLoader?
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
