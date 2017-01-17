package com.duckduckgo.mobile.android.providers;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

public class DuckDuckGoProvider extends ContentProvider {

    private static final String TAG = "DuckDuckGoProvider";
    
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {

        // the uri looks like "content://duckduckgo/search_suggest_query/f?limit=50"
        // the last path segment is whatever the user has typed
        String query = uri.getLastPathSegment();

        if ("search_suggest_query".equals(query)) {
            return null;
        }

        // we'll simulate through a MatrixCursor.
        MatrixCursor cursor = new MatrixCursor(new String[]{SearchManager.SUGGEST_COLUMN_TEXT_1,
                                                            SearchManager.SUGGEST_COLUMN_TEXT_2,
                                                            SearchManager.SUGGEST_COLUMN_QUERY,
                                                            "_id"});

		cursor.addRow(new Object[]{"Search DuckDuckGo", query, query, 0});

        return cursor;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
