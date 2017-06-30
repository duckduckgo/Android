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

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

public class SuggestionAdapter extends RecyclerView.Adapter<SuggestionViewHolder> {

    public interface OnSuggestionListener {
        void onClick(View v, int position);

        void onAddToQuery(View v, int position);
    }

    private OnSuggestionListener onSuggestionListener;
    private List<SuggestionEntity> suggestions;
    private String filter = "";

    public SuggestionAdapter(@NonNull OnSuggestionListener onSuggestionListener) {
        this.onSuggestionListener = onSuggestionListener;
    }

    public void setSuggestions(@NonNull List<SuggestionEntity> suggestions, @NonNull String filter) {
        this.suggestions = suggestions;
        this.filter = filter;
        notifyDataSetChanged();
    }

    public void clear() {
        suggestions.clear();
        notifyDataSetChanged();
    }

    @Override
    public SuggestionViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return SuggestionViewHolder.inflate(parent, new SuggestionViewHolder.OnSuggestionListener() {
            @Override
            public void onSuggestionSelected(View v, int position) {
                onSuggestionListener.onClick(v, position);
            }

            @Override
            public void onAddToQuerySelected(View v, int position) {
                onSuggestionListener.onAddToQuery(v, position);
            }
        });
    }

    @Override
    public void onBindViewHolder(SuggestionViewHolder holder, int position) {
        SuggestionEntity suggestion = suggestions.get(position);
        holder.setSuggestion(suggestion, filter);
    }

    @Override
    public int getItemCount() {
        return suggestions.size();
    }
}
