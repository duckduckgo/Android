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

package com.duckduckgo.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.kt
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.duckduckgo.lint.WrongPluginPointCollectorDetector.Companion.WRONG_PLUGIN_POINT_ISSUE
import org.junit.Test

class WrongPluginPointCollectorDetectorTest {
    @Test
    fun `test normal plugin point constructor parameter collecting active plugins`() {
        lint()
            .files(kt("""
                package com.duckduckgo.common.utils.plugins

                interface PluginPoint<T> {
                    fun getPlugins(): Collection<T>
                }
                
                interface ActivePluginPoint<out T : ActivePluginPoint.ActivePlugin> {
                    interface ActivePlugin {
                        suspend fun isActive(): Boolean = true
                    }
                }

                interface MyPlugin
                interface MyPluginActivePlugin : ActivePluginPoint.ActivePlugin
    
                class Duck(private val pp: PluginPoint<MyPluginActivePlugin>) {
                    fun quack() {                    
                    }
                }
            """).indented())
            .issues(WRONG_PLUGIN_POINT_ISSUE)
            .run()
            .expect("""
                src/com/duckduckgo/common/utils/plugins/PluginPoint.kt:16: Error: PluginPoint cannot be collector of ActivePlugin(s) [WrongPluginPointCollectorDetector]
                class Duck(private val pp: PluginPoint<MyPluginActivePlugin>) {
                      ~~~~
                src/com/duckduckgo/common/utils/plugins/PluginPoint.kt:16: Error: PluginPoint cannot be collector of ActivePlugin(s) [WrongPluginPointCollectorDetector]
                class Duck(private val pp: PluginPoint<MyPluginActivePlugin>) {
                                       ~~
                2 errors, 0 warnings
            """.trimMargin())
    }

    @Test
    fun `test active plugin point constructor parameter collecting active plugins`() {
        lint()
            .files(kt("""
                package com.duckduckgo.common.utils.plugins

                interface PluginPoint<T> {
                    fun getPlugins(): Collection<T>
                }
                
                interface ActivePluginPoint<out T : ActivePluginPoint.ActivePlugin> {
                    interface ActivePlugin {
                        suspend fun isActive(): Boolean = true
                    }
                }

                interface MyPlugin
                interface MyPluginActivePlugin : ActivePluginPoint.ActivePlugin
    
                class Duck(private val pp: PluginPoint<MyPlugin>) {
                    fun quack() {                    
                    }
                }
            """).indented())
            .issues(WRONG_PLUGIN_POINT_ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `test normal plugin point field collecting active plugins`() {
        lint()
            .files(kt("""
                package com.duckduckgo.common.utils.plugins

                interface PluginPoint<T> {
                    fun getPlugins(): Collection<T>
                }
                
                interface ActivePluginPoint<out T : ActivePluginPoint.ActivePlugin> {
                    interface ActivePlugin {
                        suspend fun isActive(): Boolean = true
                    }
                }

                interface MyPlugin
                interface MyPluginActivePlugin : ActivePluginPoint.ActivePlugin
    
                class Duck {
                    private val pp: PluginPoint<MyPluginActivePlugin>
                    
                    fun quack() {                    
                    }
                }
            """).indented())
            .issues(WRONG_PLUGIN_POINT_ISSUE)
            .run()
            .expect("""
                src/com/duckduckgo/common/utils/plugins/PluginPoint.kt:17: Error: PluginPoint cannot be collector of ActivePlugin(s) [WrongPluginPointCollectorDetector]
                    private val pp: PluginPoint<MyPluginActivePlugin>
                                ~~
                1 errors, 0 warnings
            """.trimMargin())
    }

    @Test
    fun `test active plugin point field collecting active plugins`() {
        lint()
            .files(kt("""
                package com.duckduckgo.common.utils.plugins

                interface PluginPoint<T> {
                    fun getPlugins(): Collection<T>
                }
                
                interface ActivePluginPoint<out T : ActivePluginPoint.ActivePlugin> {
                    interface ActivePlugin {
                        suspend fun isActive(): Boolean = true
                    }
                }

                interface MyPlugin
                interface MyPluginActivePlugin : ActivePluginPoint.ActivePlugin
    
                class Duck {
                    private val pp: PluginPoint<MyPlugin>
                    
                    fun quack() {                    
                    }
                }
            """).indented())
            .issues(WRONG_PLUGIN_POINT_ISSUE)
            .run()
            .expectClean()
    }
}
