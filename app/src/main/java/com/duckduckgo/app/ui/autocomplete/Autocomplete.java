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

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;

import com.duckduckgo.app.R;

import java.util.List;

public class Autocomplete extends RecyclerView implements AutocompleteView {
    public interface OnAutocompleteListener {
        void onSuggestionClick(View view, int position);

        void onSuggestionAddToQuery(View view, int position);
    }

    private boolean showAutocompleteResults = false;

    private OnAutocompleteListener onAutocompleteListener;

    private SuggestionAdapter adapter;

    public Autocomplete(Context context) {
        super(context);
    }

    public Autocomplete(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public Autocomplete(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setOnAutocompleteListener(OnAutocompleteListener onAutocompleteListener) {
        this.onAutocompleteListener = onAutocompleteListener;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setBackgroundColor(ContextCompat.getColor(getContext(), R.color.autocomplete_shadow_background));
        adapter = new SuggestionAdapter(new SuggestionAdapter.OnSuggestionListener() {
            @Override
            public void onClick(View v, int position) {
                if (onAutocompleteListener != null) {
                    onAutocompleteListener.onSuggestionClick(v, position);
                }
            }

            @Override
            public void onAddToQuery(View v, int position) {
                if (onAutocompleteListener != null) {
                    onAutocompleteListener.onSuggestionAddToQuery(v, position);
                }
            }
        });
        setLayoutManager(new LinearLayoutManager(getContext()));
        setAdapter(adapter);
    }

    @Override
    public void addSuggestions(@NonNull List<SuggestionEntity> suggestions) {
        adapter.setSuggestions(suggestions);
        if(showAutocompleteResults) {
            if(getVisibility() != View.VISIBLE) {
                setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void show() {
        showAutocompleteResults = true;
    }

    @Override
    public void hide() {
        showAutocompleteResults = false;
        if(getVisibility() == View.GONE) return;
        setVisibility(View.GONE);
        adapter.clear();
    }
}
