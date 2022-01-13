/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.privacy.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ItemPrivacyPracticeBinding

class PrivacyPracticesAdapter : RecyclerView.Adapter<PrivacyPracticesAdapter.PracticeViewHolder>() {

    companion object {
        const val GOOD = 1
        const val BAD = 2
    }

    private var terms: List<Pair<Int, String>> = ArrayList()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): PracticeViewHolder {
        val binding = ItemPrivacyPracticeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PracticeViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: PracticeViewHolder,
        position: Int
    ) {
        val term = terms[position]
        val descriptionResource = if (term.first == GOOD) R.string.practicesIconContentGood else R.string.practicesIconContentBad
        holder.binding.icon.contentDescription = holder.binding.icon.context.getText(descriptionResource)
        holder.binding.icon.setImageResource(if (term.first == GOOD) R.drawable.icon_success else R.drawable.icon_fail)
        holder.binding.description.text = term.second.capitalize()
    }

    override fun getItemCount(): Int {
        return terms.size
    }

    fun updateData(
        goodTerms: List<String>,
        badTerms: List<String>
    ) {
        terms = goodTerms.map { GOOD to it } + badTerms.map { BAD to it }
        notifyDataSetChanged()
    }

    class PracticeViewHolder(
        val binding: ItemPrivacyPracticeBinding
    ) : RecyclerView.ViewHolder(binding.root)
}
