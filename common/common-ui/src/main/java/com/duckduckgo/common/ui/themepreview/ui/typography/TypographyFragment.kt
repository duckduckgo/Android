/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.common.ui.themepreview.ui.typography

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.view.text.DaxTextView.Typography
import com.duckduckgo.mobile.android.R

/** Fragment to display a list of subsystems that show the values of this app's theme. */
@SuppressLint("NoFragment") // we don't use DI here
class TypographyFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_components_typography, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceBundle: Bundle?,
    ) {
        val daxTextView = view.findViewById<DaxTextView>(R.id.typographyTitle)
        daxTextView.setTypography(Typography.Body1)
    }
}
