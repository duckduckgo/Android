package com.duckduckgo.mobile.android.duckduckgo.domian;

/**
 * Created by fgei on 5/29/17.
 */

public interface Tab {
    int getId();

    String getTitle();

    String getUrl();

    int getPosition();

    byte[] getState();
}
