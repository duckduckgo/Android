package com.duckduckgo.mobile.android.duckduckgo.data.base;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by fgei on 6/14/17.
 */

public abstract class BaseSharedPreferencesDataStore<T extends JsonEntity> {
    public abstract String getFileName();

    private SharedPreferences sharedPreferences;
    private Class<T> clss;

    public BaseSharedPreferencesDataStore(Context context, Class<T> clss) {
        sharedPreferences = context.getSharedPreferences(getFileName(), Context.MODE_PRIVATE);
        this.clss = clss;
    }

    public List<T> getAll() {
        List<T> out = new ArrayList<>();
        for (Object item : sharedPreferences.getAll().values()) {
            try {
                T t = clss.newInstance();
                t.fromJson(item.toString());
                out.add(t);
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return out;
    }

    public void insert(JsonEntity jsonEntity) {
        getEditor().putString(jsonEntity.getKey(), jsonEntity.toJson()).apply();
    }

    public void update(JsonEntity jsonEntity) {
        getEditor().remove(jsonEntity.getKey());
        getEditor().putString(jsonEntity.getKey(), jsonEntity.toJson()).apply();
    }

    public void delete(JsonEntity jsonEntity) {
        getEditor().remove(jsonEntity.getKey()).apply();
    }

    public void deleteAll() {
        getEditor().clear().apply();
    }

    private SharedPreferences.Editor getEditor() {
        return sharedPreferences.edit();
    }


}
