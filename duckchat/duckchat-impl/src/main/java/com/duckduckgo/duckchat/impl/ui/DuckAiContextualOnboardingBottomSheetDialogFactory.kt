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

package com.duckduckgo.duckchat.impl.ui

import android.content.Context
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface DuckAiContextualOnboardingBottomSheetDialogFactory {
    fun create(context: Context): DuckAiContextualOnboardingBottomSheetDialog
}

@ContributesBinding(AppScope::class)
class RealDuckAiContextualOnboardingBottomSheetDialogFactory @Inject constructor(
    private val viewModel: DuckAiContextualOnboardingViewModel,
    private val dispatcherProvider: DispatcherProvider,
    private val globalActivityStarter: GlobalActivityStarter,
    private val pixel: Pixel,
) : DuckAiContextualOnboardingBottomSheetDialogFactory {

    override fun create(context: Context): DuckAiContextualOnboardingBottomSheetDialog =
        DuckAiContextualOnboardingBottomSheetDialog(
            context = context,
            viewModel = viewModel,
            globalActivityStarter = globalActivityStarter,
            dispatcherProvider = dispatcherProvider,
            pixel = pixel,
        )
}
