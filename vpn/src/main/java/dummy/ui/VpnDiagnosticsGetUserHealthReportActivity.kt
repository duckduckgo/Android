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

package dummy.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.vpn.databinding.ActivityVpnDiagnosticsGetUserHealthReportBinding

class VpnDiagnosticsGetUserHealthReportActivity : DuckDuckGoActivity() {

    val binding by viewBinding<ActivityVpnDiagnosticsGetUserHealthReportBinding>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        configureNotesInputView()
        configureUiEventHandlers()
    }

    private fun configureUiEventHandlers() {
        binding.doneButton.setOnClickListener {

            val healthStatusValue = when (binding.userHealthSelection.checkedRadioButtonId) {
                binding.goodHealth.id -> "good"
                binding.badHealth.id -> "bad"
                else -> UNDETERMINED_STATUS
            }

            val additionalNotes = binding.userHealthNotes.text.trim().toString()

            val result = Intent().also {
                it.putExtra(RESULT_DATA_KEY_STATUS, healthStatusValue)
                it.putExtra(RESULT_DATA_KEY_NOTES, additionalNotes)
            }
            setResult(RESULT_OK, result)
            finish()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun configureNotesInputView() {
        binding.userHealthNotes.setOnTouchListener { view, event ->
            view.parent.requestDisallowInterceptTouchEvent(true)
            if ((event.action and MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
                view.parent.requestDisallowInterceptTouchEvent(false)
            }
            return@setOnTouchListener false
        }
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, VpnDiagnosticsGetUserHealthReportActivity::class.java)
        }

        const val RESULT_DATA_KEY_STATUS = "status"
        const val RESULT_DATA_KEY_NOTES = "notes"
        const val UNDETERMINED_STATUS = "undetermined"
    }
}


