package com.duckduckgo.mobile.android.duckduckgo.domain.tab;

import java.util.List;

/**
 * Created by fgei on 6/14/17.
 */

public interface TabRepository {
    List<? extends Tab> getAll();

    void insert(Tab tab);

    void deleteAll();
}
