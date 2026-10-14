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

package com.duckduckgo.common.ui

import android.content.Context
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.AndroidInjector
import dagger.android.HasDaggerInjector
import dagger.android.InjectorFactoryMap
import dagger.android.getFactory
import dagger.android.support.AndroidSupportInjection
import dev.zacsweers.metro.HasMemberInjections
import javax.inject.Inject

/**
 * Base bottom-sheet dialog that wires DuckDuckGo's Dagger injection, mirroring [DuckDuckGoFragment].
 *
 * It implements [HasDaggerInjector] so that child views which self-inject (e.g.
 * `@InjectWith(ViewScope::class)` views resolving their injector by walking up to the hosting
 * fragment) work inside a dialog, just as they do inside a [DuckDuckGoFragment].
 */
@HasMemberInjections
abstract class DuckDuckGoBottomSheetDialogFragment : BottomSheetDialogFragment(), HasDaggerInjector {

    @Inject
    lateinit var injectorFactoryMap: InjectorFactoryMap

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun daggerFactoryFor(key: Class<*>): AndroidInjector.Factory<*, *> {
        return injectorFactoryMap.getFactory(key)
            ?: throw RuntimeException(
                """
                Could not find the dagger component for ${key.simpleName}.
                You probably forgot to annotate your class with @InjectWith(Scope::class).
                """.trimIndent(),
            )
    }
}
