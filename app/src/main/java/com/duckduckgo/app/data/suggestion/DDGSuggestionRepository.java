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

package com.duckduckgo.app.data.suggestion;

import android.support.annotation.NonNull;

import com.duckduckgo.app.domain.suggestion.Suggestion;
import com.duckduckgo.app.domain.suggestion.SuggestionRepository;
import com.duckduckgo.app.util.AppUrls;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import timber.log.Timber;

public class DDGSuggestionRepository implements SuggestionRepository {

    @Override
    public List<Suggestion> getSuggestions(@NonNull String query) {
        List<Suggestion> list = new ArrayList<>();
        try {
            String autocompleteUrl = AppUrls.getAutocompleteUrl(query);
            URL url = new URL(autocompleteUrl);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            reader.close();
            JSONArray jsonArray = new JSONArray(stringBuilder.toString());
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                SuggestionJsonEntity suggestion = new SuggestionJsonEntity();
                suggestion.fromJson(jsonObject.toString());
                //list.add(new SuggestionJsonEntity(jsonObject.getString("phrase")));
                list.add(suggestion);
            }
        } catch (MalformedURLException e) {
            Timber.e(e, "getSuggestions, query: %s", query);
        } catch (IOException e) {
            Timber.e(e, "getSuggestions, query: %s", query);
        } catch (JSONException e) {
            Timber.e(e, "getSuggestions, query: %s", query);
        }
        return list;
    }
}
