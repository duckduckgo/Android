package com.duckduckgo.mobile.android.duckduckgo.data.bookmark;

import com.duckduckgo.mobile.android.duckduckgo.data.base.JsonEntity;
import com.duckduckgo.mobile.android.duckduckgo.domain.bookmark.Bookmark;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by fgei on 6/12/17.
 */

public class BookmarkJsonEntity extends JsonEntity implements Bookmark {
    private String id;
    private String name;
    private String url;
    private int index;

    public BookmarkJsonEntity() {
    }

    public BookmarkJsonEntity(Bookmark bookmark) {
        id = bookmark.getId();
        name = bookmark.getName();
        url = bookmark.getUrl();
        index = bookmark.getIndex();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public int getIndex() {
        return index;
    }

    private static final String KEY_ID = "id";
    private static final String KEY_INDEX = "index";
    private static final String KEY_NAME = "name";
    private static final String KEY_URL = "url";


    @Override
    public String toJson() {
        String json = "";
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put(KEY_ID, id);
            jsonObject.put(KEY_INDEX, index);
            jsonObject.put(KEY_NAME, name);
            jsonObject.put(KEY_URL, url);
            json = jsonObject.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    @Override
    public void fromJson(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            id = jsonObject.getString(KEY_ID);
            index = jsonObject.getInt(KEY_INDEX);
            name = jsonObject.getString(KEY_NAME);
            url = jsonObject.getString(KEY_URL);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getKey() {
        return id;
    }
}
