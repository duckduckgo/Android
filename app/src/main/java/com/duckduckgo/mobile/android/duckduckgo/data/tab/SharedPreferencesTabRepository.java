package com.duckduckgo.mobile.android.duckduckgo.data.tab;

import android.support.annotation.NonNull;

import com.duckduckgo.mobile.android.duckduckgo.domain.tab.Tab;
import com.duckduckgo.mobile.android.duckduckgo.domain.tab.TabRepository;

import java.util.ArrayList;
import java.util.List;

/**
 *    Copyright 2017 DuckDuckGo
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

public class SharedPreferencesTabRepository implements TabRepository {

    private TabSharedPreferences tabSharedPreferences;
    private TabJsonEntityMapper tabJsonEntityMapper;

    public SharedPreferencesTabRepository(@NonNull TabSharedPreferences tabSharedPreferences, @NonNull TabJsonEntityMapper tabJsonEntityMapper) {
        this.tabSharedPreferences = tabSharedPreferences;
        this.tabJsonEntityMapper = tabJsonEntityMapper;
    }

    @Override
    public List<Tab> getAll() {
        List<Tab> list = new ArrayList<>();
        for (Tab tab : tabSharedPreferences.getAll()) {
            list.add(tab);
        }
        return list;
    }

    @Override
    public void insert(Tab tab) {
        tabSharedPreferences.insert(tabJsonEntityMapper.map(tab));
    }

    @Override
    public void deleteAll() {
        tabSharedPreferences.deleteAll();
    }
}
