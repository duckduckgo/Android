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

package dagger.android.support

import android.app.Application
import androidx.fragment.app.Fragment
import dagger.android.AndroidInjector

class AndroidSupportInjection {
    companion object {
        inline fun <reified T : Fragment> inject(instance: T) {
            AndroidInjector.inject(instance.context?.applicationContext as Application, instance)
        }
    }
}
