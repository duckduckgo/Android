package com.duckduckgo.mobile.android.duckduckgo.data.tab;

import com.duckduckgo.mobile.android.duckduckgo.domain.tab.Tab;

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

public class TabJsonEntityMapper {
    public TabJsonEntity map(Tab tab) {
        return new TabJsonEntity(tab);
    }

    public List<TabJsonEntity> map(List<? extends Tab> list) {
        List<TabJsonEntity> out = new ArrayList<>();
        for (Tab tab : list) {
            out.add(map(tab));
        }
        return out;
    }
}
