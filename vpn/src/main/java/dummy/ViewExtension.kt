/*
 * Copyright (c) 2020 DuckDuckGo
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

package dummy

import android.widget.CompoundButton
import android.widget.ToggleButton
import androidx.appcompat.widget.SwitchCompat
import com.google.firebase.perf.FirebasePerformance

fun SwitchCompat.quietlySetIsChecked(newCheckedState: Boolean, changeListener: CompoundButton.OnCheckedChangeListener?) {
    setOnCheckedChangeListener(null)
    isChecked = newCheckedState
    setOnCheckedChangeListener(changeListener)
}

fun ToggleButton.quietlySetIsChecked(newCheckedState: Boolean, changeListener: CompoundButton.OnCheckedChangeListener?) {
    setOnCheckedChangeListener(null)
    isChecked = newCheckedState
    setOnCheckedChangeListener(changeListener)
}