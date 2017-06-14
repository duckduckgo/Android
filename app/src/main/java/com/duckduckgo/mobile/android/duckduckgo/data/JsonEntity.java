package com.duckduckgo.mobile.android.duckduckgo.data;

/**
 * Created by fgei on 6/12/17.
 */

public interface JsonEntity {
    String toJson();

    void fromJson(String json);
}
