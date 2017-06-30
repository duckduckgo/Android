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

package com.duckduckgo.app.ui.autocomplete;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.duckduckgo.app.R;
import com.duckduckgo.app.util.UrlUtils;

import butterknife.BindView;
import butterknife.ButterKnife;

public class SuggestionViewHolder extends RecyclerView.ViewHolder {

    public interface OnSuggestionListener {
        void onSuggestionSelected(View v, int position);

        void onAddToQuerySelected(View v, int position);
    }

    @BindView(R.id.suggestion_icon_image_view)
    ImageView iconImageView;

    @BindView(R.id.suggestion_add_image_button)
    ImageButton addToQueryImageButton;

    @BindView(R.id.suggestion_title_text_view)
    TextView suggestionTextView;

    public SuggestionViewHolder(View itemView, final OnSuggestionListener onSuggestionListener) {
        super(itemView);
        ButterKnife.bind(this, itemView);
        itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSuggestionListener.onSuggestionSelected(v, getAdapterPosition());
            }
        });
        addToQueryImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSuggestionListener.onAddToQuerySelected(v, getAdapterPosition());
            }
        });

    }

    public void setSuggestion(SuggestionEntity suggestion) {
        String suggestionText = suggestion.getSuggestion();

        suggestionTextView.setText(suggestionText);

        int iconResId = UrlUtils.isUrl(suggestionText) ? R.drawable.ic_globe : R.drawable.ic_small_loupe;
        iconImageView.setImageResource(iconResId);
    }

    public static SuggestionViewHolder inflate(ViewGroup parent, OnSuggestionListener onSuggestionListener) {
        return new SuggestionViewHolder(
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.viewholder_suggestion, parent, false),
                onSuggestionListener);
    }
}
