/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.autofill.impl.importing

import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import com.duckduckgo.autofill.impl.R

internal data class PasswordsKeychainAnimationAsset(
    @RawRes val animationRes: Int,
    @DrawableRes val staticRes: Int?,
)

internal fun resolvePasswordsKeychainAnimationAsset(
    pictogramsEnabled: Boolean,
): PasswordsKeychainAnimationAsset = PasswordsKeychainAnimationAsset(
    animationRes = R.raw.anim_password_keys,
    staticRes = if (pictogramsEnabled) R.drawable.passwords_keychain_128 else null,
)
