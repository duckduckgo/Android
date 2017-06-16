package com.duckduckgo.mobile.android.duckduckgo.data.base;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by fgei on 6/14/17.
 */

public class SharedPreferencesDataStore<T extends JsonEntity> {

    private SharedPreferences sharedPreferences;
    private EntityCreator<T> entityCreator;

    public interface EntityCreator<T> {
        T create();
    }

    public SharedPreferencesDataStore(Context context, String fileName, EntityCreator<T> entityCreator) {
        sharedPreferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE);
        this.entityCreator = entityCreator;
    }

    public List<T> getAll() {
        List<T> out = new ArrayList<>();
        for (Object item : sharedPreferences.getAll().values()) {
            T t = entityCreator.create();
            t.fromJson(item.toString());
            out.add(t);

        }
        return out;
    }

    public void insert(T t) {
        getEditor().putString(t.getKey(), t.toJson()).apply();
    }

    public void update(T t) {
        getEditor().remove(t.getKey());
        getEditor().putString(t.getKey(), t.toJson()).apply();
    }

    public void delete(T t) {
        getEditor().remove(t.getKey()).apply();
    }

    public void deleteAll() {
        getEditor().clear().apply();
    }

    private SharedPreferences.Editor getEditor() {
        return sharedPreferences.edit();
    }


}
