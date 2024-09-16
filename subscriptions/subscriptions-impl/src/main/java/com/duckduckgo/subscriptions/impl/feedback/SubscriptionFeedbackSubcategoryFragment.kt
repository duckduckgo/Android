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

package com.duckduckgo.subscriptions.impl.feedback

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.databinding.RowOneLineListItemBinding
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.databinding.ContentFeedbackSubcategoryBinding
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class SubscriptionFeedbackSubcategoryFragment : SubscriptionFeedbackFragment(R.layout.content_feedback_subcategory) {

    @Inject
    lateinit var feedbackSubCategoryProvider: FeedbackSubCategoryProvider

    private val binding: ContentFeedbackSubcategoryBinding by viewBinding()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        val listener = activity as Listener
        val category = requireArguments().getSerializable(EXTRA_CATEGORY) as SubscriptionFeedbackCategory
        val layoutInflater = LayoutInflater.from(context)

        feedbackSubCategoryProvider.getSubCategories(category).forEach {
            val itemBinding = RowOneLineListItemBinding.inflate(layoutInflater, binding.subcategoryContainer, false)
            binding.subcategoryContainer.addView(itemBinding.root)
            itemBinding.root.setPrimaryText(getString(it.key))
            itemBinding.root.setClickListener {
                listener.onUserClickedSubCategory(it.value)
            }
        }
    }

    interface Listener {
        fun onUserClickedSubCategory(subCategory: SubscriptionFeedbackSubCategory)
    }

    companion object {

        private const val EXTRA_CATEGORY = "EXTRA_CATEGORY"
        internal fun instance(
            category: SubscriptionFeedbackCategory,
        ): SubscriptionFeedbackSubcategoryFragment = SubscriptionFeedbackSubcategoryFragment().apply {
            val args = Bundle()
            args.putSerializable(EXTRA_CATEGORY, category)
            arguments = args
        }
    }
}
