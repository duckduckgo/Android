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

package com.duckduckgo.app.browser.favorites

import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.bookmarks.model.SavedSite
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ViewAutoFavoriteHintItemBinding
import com.duckduckgo.app.global.view.NonUnderlinedClickableSpan
import com.duckduckgo.app.global.view.html
import com.google.android.material.textview.MaterialTextView

class AutoFavoriteHintAdapter(
    private val onActionClicked: (savedSite: SavedSite) -> Unit,
) : RecyclerView.Adapter<AutoFavoriteHintAdapter.AddItemViewHolder>() {

    private var savedSite: SavedSite? = null

    private val undoSpan = object : NonUnderlinedClickableSpan() {
        override fun onClick(widget: View) {
            savedSite?.let(onActionClicked)
        }
    }

    class AddItemViewHolder(val binding: ViewAutoFavoriteHintItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AddItemViewHolder {
        return AddItemViewHolder(ViewAutoFavoriteHintItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: AddItemViewHolder, position: Int) {
        setClickableSpan(holder.binding.favoriteAddedAsFavoriteHint, undoSpan)
        holder.binding.favoriteAddedAsFavoriteHint.setOnClickListener {
            savedSite?.let(onActionClicked)
        }
    }

    private fun setClickableSpan(view: MaterialTextView, span: NonUnderlinedClickableSpan) {
        view.context?.let {
            val htmlString = it.getString(R.string.favoriteAddedOnboardingText).html(it)
            val spannableString = SpannableStringBuilder(htmlString)
            val urlSpans = htmlString.getSpans(0, htmlString.length, URLSpan::class.java)
            urlSpans?.forEachIndexed { index, urlSpan ->
                spannableString.apply {
                    setSpan(
                        span,
                        spannableString.getSpanStart(urlSpan),
                        spannableString.getSpanEnd(urlSpan),
                        spannableString.getSpanFlags(urlSpan)
                    )
                    removeSpan(urlSpan)
                }
            }
            view.apply {
                text = spannableString
                movementMethod = LinkMovementMethod.getInstance()
            }
        }
    }

    fun showHint(savedSite: SavedSite) {
        this.savedSite = savedSite
        notifyDataSetChanged()
    }

    fun clearHint() {
        this.savedSite = null
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = if (savedSite != null) 1 else 0
}
