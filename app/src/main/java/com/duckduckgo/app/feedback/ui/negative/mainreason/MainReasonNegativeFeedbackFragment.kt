/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.feedback.ui.negative.mainreason

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentFeedbackNegativeDisambiguationMainReasonBinding
import com.duckduckgo.app.feedback.ui.common.FeedbackFragment
import com.duckduckgo.app.feedback.ui.common.FeedbackItemDecoration
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.MainReason
import com.duckduckgo.app.feedback.ui.negative.FeedbackTypeDisplay
import com.duckduckgo.app.feedback.ui.negative.FeedbackTypeDisplay.FeedbackTypeMainReasonDisplay
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

class MainReasonNegativeFeedbackFragment : FeedbackFragment(R.layout.content_feedback_negative_disambiguation_main_reason) {
    private lateinit var recyclerAdapter: MainReasonAdapter

    interface MainReasonNegativeFeedbackListener {

        fun userSelectedNegativeFeedbackMainReason(type: MainReason)
    }

    private val binding: ContentFeedbackNegativeDisambiguationMainReasonBinding by viewBinding()

    private val listener: MainReasonNegativeFeedbackListener?
        get() = activity as MainReasonNegativeFeedbackListener

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        recyclerAdapter = MainReasonAdapter(object : (FeedbackTypeMainReasonDisplay) -> Unit {
            override fun invoke(reason: FeedbackTypeMainReasonDisplay) {
                listener?.userSelectedNegativeFeedbackMainReason(reason.mainReason)
            }
        })

        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        activity?.let {
            binding.recyclerView.layoutManager = LinearLayoutManager(it)
            binding.recyclerView.adapter = recyclerAdapter
            binding.recyclerView.addItemDecoration(FeedbackItemDecoration(ContextCompat.getDrawable(it, R.drawable.feedback_list_divider)!!))

            val listValues = getMainReasonsDisplayText()
            recyclerAdapter.submitList(listValues)
        }
    }

    private fun getMainReasonsDisplayText(): List<FeedbackTypeMainReasonDisplay> {
        return MainReason.values().mapNotNull {
            FeedbackTypeDisplay.mainReasons[it]
        }
    }

    companion object {

        fun instance(): MainReasonNegativeFeedbackFragment {
            return MainReasonNegativeFeedbackFragment()
        }
    }
}
