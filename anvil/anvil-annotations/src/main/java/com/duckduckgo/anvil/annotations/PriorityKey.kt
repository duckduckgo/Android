/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.anvil.annotations

import dagger.MapKey

/**
 * Use [PriorityKey] in combination with @ContributesMultibinding when contributing a plugin and want
 * to assign a priority to the instance.
 * Lower priority values mean the associated plugin comes first in the list of plugins.
 * When two plugins have the same priority the priority is resolved sorting by class instance fully qualified name
 *
 * Note: Plugins that are not annotated with [PriorityKey] will always come last in the list of plugins and order
 * by class instance fully qualified name
 */
@MapKey
annotation class PriorityKey(val priority: Int)
