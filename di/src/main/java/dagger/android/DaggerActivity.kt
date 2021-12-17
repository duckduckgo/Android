/*
 * Copyright (c) 2021 DuckDuckGo
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

package dagger.android

import androidx.appcompat.app.AppCompatActivity
import com.duckduckgo.di.DaggerMap
import javax.inject.Inject

abstract class DaggerActivity : AppCompatActivity(), HasDaggerInjector {
    @Inject lateinit var injectorFactoryMap: DaggerMap<Class<*>, AndroidInjector.Factory<*>>

    override fun daggerFactoryFor(key: Class<*>): AndroidInjector.Factory<*> {
        return injectorFactoryMap[key]
            ?: throw RuntimeException(
                """
                Could not find the dagger component for ${key.simpleName}.
                You probably forgot to create the ${key.simpleName}Component.
                If you DID create the ${key.simpleName}Component, check that it uses @ContributesTo(ActivityScope::class)
                """.trimIndent())
    }
}
