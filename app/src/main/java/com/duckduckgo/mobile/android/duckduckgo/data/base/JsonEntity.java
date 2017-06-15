package com.duckduckgo.mobile.android.duckduckgo.data.base;

/**
 * Created by fgei on 6/12/17.
 */

public abstract class JsonEntity {
    public JsonEntity() {

    }

    abstract public String toJson();

    abstract public void fromJson(String json);

    abstract public String getKey();
}
