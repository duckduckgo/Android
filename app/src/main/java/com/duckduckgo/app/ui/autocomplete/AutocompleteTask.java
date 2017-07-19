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

import android.os.AsyncTask;
import android.support.annotation.NonNull;

import com.duckduckgo.app.domain.suggestion.Suggestion;
import com.duckduckgo.app.domain.suggestion.SuggestionRepository;
import com.duckduckgo.app.ui.browser.BrowserPresenter;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class AutocompleteTask extends AsyncTask<String, Void, List<SuggestionEntity>> {
    private WeakReference<BrowserPresenter> browserPresenterRef;
    private SuggestionRepository suggestionRepository;
    private String query;

    public AutocompleteTask(@NonNull BrowserPresenter browserPresenter, @NonNull SuggestionRepository suggestionRepository) {
        browserPresenterRef = new WeakReference<>(browserPresenter);
        this.suggestionRepository = suggestionRepository;
    }

    @Override
    protected List<SuggestionEntity> doInBackground(String... params) {
        query = params[0];
        List<SuggestionEntity> list = new ArrayList<>();
        for (Suggestion suggestion : suggestionRepository.getSuggestions(query)) {
            list.add(new SuggestionEntity(suggestion));
        }
        return list;
    }

    @Override
    protected void onPostExecute(List<SuggestionEntity> suggestionEntities) {
        super.onPostExecute(suggestionEntities);
        if (browserPresenterRef.get() != null) {
            browserPresenterRef.get().onReceiveNewSuggestionsForQuery(suggestionEntities, query);
        }
    }
}
