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

package com.duckduckgo.autofill.ui.credential.management

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import com.duckduckgo.autofill.impl.R

fun FragmentManager.forceExitFragment(tag: String) {
    commit {
        findFragmentByTag(tag)?.let {
            this.remove(it)
            popBackStackImmediate()
        }
    }
}

fun FragmentManager.showFragment(
    fragment: Fragment,
    tag: String,
    shouldAddToBackStack: Boolean
) {
    if (findFragmentByTag(tag) == null) {
        commit {
            setReorderingAllowed(true)
            replace(R.id.fragment_container_view, fragment, tag)
            if (shouldAddToBackStack) addToBackStack(tag)
        }
    }
}
