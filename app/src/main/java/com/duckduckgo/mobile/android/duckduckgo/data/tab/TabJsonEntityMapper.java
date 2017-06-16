package com.duckduckgo.mobile.android.duckduckgo.data.tab;

import com.duckduckgo.mobile.android.duckduckgo.domain.tab.Tab;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by fgei on 6/14/17.
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
