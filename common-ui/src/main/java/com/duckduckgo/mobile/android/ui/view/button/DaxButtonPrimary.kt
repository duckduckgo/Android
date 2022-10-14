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

package com.duckduckgo.mobile.android.ui.view.button

import android.content.Context
import android.util.AttributeSet
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.ui.view.button.Size.Small
import com.duckduckgo.mobile.android.ui.view.text.DaxTextView.Type
import com.duckduckgo.mobile.android.ui.view.text.DaxTextView.Type.Body1
import com.google.android.material.button.MaterialButton

class DaxButtonPrimary @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet,
    defStyleAttr: Int = R.attr.daxButtonPrimary
) : DaxButton(
    ctx,
    attrs,
    defStyleAttr
)
