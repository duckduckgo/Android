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

package com.duckduckgo.autofill.ui.credential.management.viewing

import android.text.Editable
import android.text.TextWatcher
import com.duckduckgo.autofill.impl.databinding.FragmentAutofillManagementEditModeBinding
import javax.inject.Inject

fun FragmentAutofillManagementEditModeBinding.watchSaveState(
    saveStateWatcher: SaveStateWatcher,
    saveStateUpdater: (Boolean) -> Unit = {}
) {
    saveStateWatcher.start(saveStateUpdater) {
        domainTitleEditText.text.isEmpty() && domainEditText.text.isEmpty() &&
            notesEditText.text.isEmpty() && passwordEditText.text.isEmpty() && usernameEditText.text.isEmpty()
    }
    domainEditText.addTextChangedListener(saveStateWatcher)
    notesEditText.addTextChangedListener(saveStateWatcher)
    usernameEditText.addTextChangedListener(saveStateWatcher)
    passwordEditText.addTextChangedListener(saveStateWatcher)
}

fun FragmentAutofillManagementEditModeBinding.removeSaveStateWatcher(saveStateWatcher: SaveStateWatcher) {
    domainTitleEditText.removeTextChangedListener(saveStateWatcher)
    domainEditText.removeTextChangedListener(saveStateWatcher)
    notesEditText.removeTextChangedListener(saveStateWatcher)
    usernameEditText.removeTextChangedListener(saveStateWatcher)
    passwordEditText.removeTextChangedListener(saveStateWatcher)
}

class SaveStateWatcher @Inject constructor() : TextWatcher {
    private var _saveStateUpdater: (Boolean) -> Unit = {}
    private var _allTextInputIsEmpty: () -> Boolean = { false }
    fun start(
        saveStateUpdater: (Boolean) -> Unit,
        allTextInputIsEmpty: () -> Boolean
    ) {
        _saveStateUpdater = saveStateUpdater
        _allTextInputIsEmpty = allTextInputIsEmpty
    }

    override fun beforeTextChanged(
        s: CharSequence?,
        start: Int,
        count: Int,
        after: Int
    ) {
    }

    override fun onTextChanged(
        s: CharSequence?,
        start: Int,
        before: Int,
        count: Int
    ) {
    }

    override fun afterTextChanged(s: Editable?) {
        _saveStateUpdater(!_allTextInputIsEmpty())
    }
}
