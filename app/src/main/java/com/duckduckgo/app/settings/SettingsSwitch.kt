/*
 * Copyright (c) 2018 DuckDuckGo
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

@file:Suppress("MemberVisibilityCanBePrivate")

package com.duckduckgo.app.settings

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import com.duckduckgo.app.browser.R
import org.jetbrains.anko.childrenRecursiveSequence


class SettingsSwitch : ConstraintLayout {

    private var root: View
    private var titleView: TextView
    private var switchView: SwitchCompat

    private var switchChangeListener: CompoundButton.OnCheckedChangeListener? = null

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, R.style.SettingsSwitch)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {

        root = LayoutInflater.from(context).inflate(R.layout.settings_switch, this, true)
        titleView = root.findViewById(R.id.title)
        switchView = root.findViewById(R.id.switchView)

        val attributes = context.obtainStyledAttributes(attrs, R.styleable.SettingsSwitch)
        setTitle(attributes.getString(R.styleable.SettingsSwitch_settingsSwitchTitle) ?: "")
        switchView.quietlySetIsChecked(attributes.getBoolean(R.styleable.SettingsSwitch_settingsSwitchChecked, false))
        attributes.recycle()
    }

    fun setTitle(title: String) {
        titleView.text = title
    }

    override fun setEnabled(enabled: Boolean) {
        root.childrenRecursiveSequence().forEach { it.isEnabled = enabled }
        super.setEnabled(enabled)
    }

    fun setOnCheckedChangeListener(onCheckedChangeListener: CompoundButton.OnCheckedChangeListener) {
        switchChangeListener = onCheckedChangeListener
        switchView.setOnCheckedChangeListener(switchChangeListener)
    }

    fun SwitchCompat.quietlySetIsChecked(newCheckedState: Boolean) {
        this.setOnCheckedChangeListener(null)
        this.isChecked = newCheckedState
        this.setOnCheckedChangeListener(switchChangeListener)
    }

    fun quietlySetIsChecked(newCheckedState: Boolean) {
        switchView.quietlySetIsChecked(newCheckedState)
    }

    /**
     * Need to do a bit of manual state saving, otherwise multiple Switches on the same View would overwrite each others' saved states
     */
    override fun onSaveInstanceState(): Parcelable? {
        val bundle = Bundle()

        bundle.putParcelable(RESTORE_KEY_SUPER, super.onSaveInstanceState())
        bundle.putBoolean(RESTORE_KEY_CHECKED, switchView.isChecked)

        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            super.onRestoreInstanceState(state.getParcelable(RESTORE_KEY_SUPER))
            switchView.isChecked = state.getBoolean(RESTORE_KEY_CHECKED)
        }
    }

    companion object {
        private const val RESTORE_KEY_SUPER = "super"
        private const val RESTORE_KEY_CHECKED = "checked"
    }

}