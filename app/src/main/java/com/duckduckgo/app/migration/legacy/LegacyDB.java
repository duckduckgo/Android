/*
 * Copyright (c) 2018 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.migration.legacy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.preference.PreferenceManager;

public class LegacyDB {

    private SQLiteDatabase db;

    private static final String APP_INSERT = "insert or replace into " +
            LegacyDBContracts.APP_TABLE.TABLE_NAME + " (" +
            LegacyDBContracts.APP_TABLE.COLUMN_TITLE + "," + LegacyDBContracts.APP_TABLE.COLUMN_PACKAGE +
            ") values (?,?)";

    // if type = recent search, data = query.  if type = web page / feed item, data = title, url is target
    // extraType is for feed source
//	private static final String HISTORY_INSERT = "insert or replace into " + LegacyDBContracts.HISTORY_TABLE.TABLE_NAME + " (type, data, url, extraType) values (?,?,?,?)";


    public LegacyDB(Context context) {
        OpenHelper openHelper = new OpenHelper(context);
        this.db = openHelper.getWritableDatabase();
    }

    public void deleteAll() {
        this.db.delete(LegacyDBContracts.FEED_TABLE.TABLE_NAME, null, null);
        this.db.delete(LegacyDBContracts.SAVED_SEARCH_TABLE.TABLE_NAME, null, null);
    }

    private LegacyFeedObject getFeedObject(Cursor c) {
        final String id = c.getString(c.getColumnIndex(LegacyDBContracts.FEED_TABLE._ID));
        final String title = c.getString(c.getColumnIndex(LegacyDBContracts.FEED_TABLE.COLUMN_TITLE));
        final String description = c.getString(c.getColumnIndex(LegacyDBContracts.FEED_TABLE.COLUMN_DESCRIPTION));
        final String feed = c.getString(c.getColumnIndex(LegacyDBContracts.FEED_TABLE.COLUMN_FEED));
        final String url = c.getString(c.getColumnIndex(LegacyDBContracts.FEED_TABLE.COLUMN_URL));
        final String imageurl = c.getString(c.getColumnIndex(LegacyDBContracts.FEED_TABLE.COLUMN_IMAGE_URL));
        final String favicon = c.getString(c.getColumnIndex(LegacyDBContracts.FEED_TABLE.COLUMN_FAVICON));
        final String timestamp = c.getString(c.getColumnIndex(LegacyDBContracts.FEED_TABLE.COLUMN_TIMESTAMP));
        final String category = c.getString(c.getColumnIndex(LegacyDBContracts.FEED_TABLE.COLUMN_CATEGORY));
        final String type = c.getString(c.getColumnIndex(LegacyDBContracts.FEED_TABLE.COLUMN_TYPE));
        final String articleurl = c.getString(c.getColumnIndex(LegacyDBContracts.FEED_TABLE.COLUMN_ARTICLE_URL));
        final String hidden = c.getString(c.getColumnIndex(LegacyDBContracts.FEED_TABLE.COLUMN_HIDDEN));
        return new LegacyFeedObject(id, title, description, feed, url, imageurl, favicon, timestamp, category, type, articleurl, "", hidden);
    }

    public ArrayList<LegacyFeedObject> selectAll(){
        ArrayList<LegacyFeedObject> feeds = null;
        Cursor c = null;
        try {
            c = this.db.query(LegacyDBContracts.FEED_TABLE.TABLE_NAME, null, null, null , null, null, null);
            if(c.moveToFirst()) {
                feeds = new ArrayList<LegacyFeedObject>(30);
                do {
                    feeds.add(getFeedObject(c));
                } while(c.moveToNext());
            }
        } finally {
            if(c!=null) {
                c.close();
            }
        }
        return feeds;
    }

    public Cursor getCursorSavedSearch() {
        return this.db.query(LegacyDBContracts.SAVED_SEARCH_TABLE.TABLE_NAME, null, null, null, null, null, LegacyDBContracts.SAVED_SEARCH_TABLE._ID + " DESC");
    }


    private static class OpenHelper extends SQLiteOpenHelper {

        private final Context appContext;

        OpenHelper(Context context) {
            super(context, LegacyDBContracts.DATABASE_NAME, null, LegacyDBContracts.DATABASE_VERSION);
            this.appContext = context.getApplicationContext();
        }

        private void dropTables(SQLiteDatabase db) {
            db.execSQL("DROP TABLE IF EXISTS " + LegacyDBContracts.FEED_TABLE.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + LegacyDBContracts.APP_TABLE.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + LegacyDBContracts.HISTORY_TABLE.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + LegacyDBContracts.SAVED_SEARCH_TABLE.TABLE_NAME);
        }

        private void createFeedTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + LegacyDBContracts.FEED_TABLE.TABLE_NAME + "("
                    + LegacyDBContracts.FEED_TABLE._ID + " VARCHAR(300) UNIQUE, "
                    + LegacyDBContracts.FEED_TABLE.COLUMN_TITLE + " VARCHAR(300), "
                    + LegacyDBContracts.FEED_TABLE.COLUMN_DESCRIPTION + " VARCHAR(300), "
                    + LegacyDBContracts.FEED_TABLE.COLUMN_FEED + " VARCHAR(300), "
                    + LegacyDBContracts.FEED_TABLE.COLUMN_URL + " VARCHAR(300), "
                    + LegacyDBContracts.FEED_TABLE.COLUMN_IMAGE_URL + " VARCHAR(300), "
                    + LegacyDBContracts.FEED_TABLE.COLUMN_FAVICON + " VARCHAR(300), "
                    + LegacyDBContracts.FEED_TABLE.COLUMN_TIMESTAMP + " VARCHAR(300), "
                    + LegacyDBContracts.FEED_TABLE.COLUMN_CATEGORY + " VARCHAR(300), "
                    + LegacyDBContracts.FEED_TABLE.COLUMN_TYPE + " VARCHAR(300), "
                    + LegacyDBContracts.FEED_TABLE.COLUMN_ARTICLE_URL + " VARCHAR(300), "
                    //+"hidden CHAR(1)"
                    + LegacyDBContracts.FEED_TABLE.COLUMN_HIDDEN + " CHAR(1), "
                    + LegacyDBContracts.FEED_TABLE.COLUMN_FAVORITE + " VARCHAR(300)"
                    + ")"
            );

            db.execSQL("CREATE INDEX idx_id ON " + LegacyDBContracts.FEED_TABLE.TABLE_NAME + " (" + LegacyDBContracts.FEED_TABLE._ID + ") ");
            db.execSQL("CREATE INDEX idx_idtype ON " + LegacyDBContracts.FEED_TABLE.TABLE_NAME + " (" + LegacyDBContracts.FEED_TABLE._ID + ", " + LegacyDBContracts.FEED_TABLE.COLUMN_TYPE + ") ");
        }

        private void createAppTable(SQLiteDatabase db) {

            // Generates warning/error when inline to execSQL method
            String sql = "CREATE VIRTUAL TABLE " + LegacyDBContracts.APP_TABLE.TABLE_NAME + " USING FTS3 ("
                    + LegacyDBContracts.APP_TABLE.COLUMN_TITLE + " VARCHAR(300), "
                    + LegacyDBContracts.APP_TABLE.COLUMN_PACKAGE + " VARCHAR(300) "
                    + ")";

            db.execSQL(sql);
        }

        private void createHistoryTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + LegacyDBContracts.HISTORY_TABLE.TABLE_NAME + "("
                    + LegacyDBContracts.HISTORY_TABLE._ID + " INTEGER PRIMARY KEY, "
                    + LegacyDBContracts.HISTORY_TABLE.COLUMN_TYPE + " VARCHAR(300), "
                    + LegacyDBContracts.HISTORY_TABLE.COLUMN_DATA + " VARCHAR(300), "
                    + LegacyDBContracts.HISTORY_TABLE.COLUMN_URL + " VARCHAR(300), "
                    + LegacyDBContracts.HISTORY_TABLE.COLUMN_EXTRA_TYPE + " VARCHAR(300), "
                    + LegacyDBContracts.HISTORY_TABLE.COLUMN_FEED_ID + " VARCHAR(300)"
                    + ")"
            );
        }

        private void createSavedSearchTable(SQLiteDatabase db) {
            // Generates warning/error when inline to execSQL method
            String sql = "CREATE TABLE " + LegacyDBContracts.SAVED_SEARCH_TABLE.TABLE_NAME + "(" +
                    LegacyDBContracts.SAVED_SEARCH_TABLE._ID + " INTEGER PRIMARY KEY, " +
                    LegacyDBContracts.SAVED_SEARCH_TABLE.COLUMN_TITLE + " VARCHAR(300), " +
                    LegacyDBContracts.SAVED_SEARCH_TABLE.COLUMN_QUERY + " VARCHAR(300) UNIQUE)";
            db.execSQL(sql);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            createFeedTable(db);
            createAppTable(db);
            createHistoryTable(db);
            createSavedSearchTable(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if(oldVersion == 4 && newVersion >= 12) {
                ContentValues contentValues = new ContentValues();

                // shape old FEED_TABLE like the new, and rename it as FEED_TABLE_old
                db.execSQL("DROP INDEX IF EXISTS idx_id");
                db.execSQL("DROP INDEX IF EXISTS idx_idtype");
                db.execSQL("ALTER TABLE " + LegacyDBContracts.FEED_TABLE.TABLE_NAME + " RENAME TO " + LegacyDBContracts.FEED_TABLE.TABLE_NAME + "_old");

                dropTables(db);
                onCreate(db);

                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext);

                // ***** recent queries *******
                List<String> recentQueries = LegacyUtils.loadList(sharedPreferences, "recentsearch");
                Collections.reverse(recentQueries);
                for(String query : recentQueries) {
                    // insertRecentSearch
                    contentValues.clear();
                    contentValues.put(LegacyDBContracts.HISTORY_TABLE.COLUMN_TYPE, "R");
                    contentValues.put(LegacyDBContracts.HISTORY_TABLE.COLUMN_DATA, query);
                    contentValues.put(LegacyDBContracts.HISTORY_TABLE.COLUMN_URL, "");
                    contentValues.put(LegacyDBContracts.HISTORY_TABLE.COLUMN_EXTRA_TYPE, "");
                    contentValues.put(LegacyDBContracts.HISTORY_TABLE.COLUMN_FEED_ID, "");
                    db.insert(LegacyDBContracts.HISTORY_TABLE.TABLE_NAME, null, contentValues);
                }
                // ****************************

                // ****** saved search ********
                Cursor c = db.query(LegacyDBContracts.FEED_TABLE.TABLE_NAME + "_old", new String[]{"url"}, LegacyDBContracts.FEED_TABLE.COLUMN_FEED + "=''", null, null, null, null);
                while(c.moveToNext()) {
                    final String url = c.getString(0);
                    final String query = LegacyUtils.getQueryIfSerp(url);
                    if(query == null)
                        continue;
                    contentValues.clear();
                    contentValues.put(LegacyDBContracts.SAVED_SEARCH_TABLE.COLUMN_QUERY, query);
                    db.insert(LegacyDBContracts.SAVED_SEARCH_TABLE.TABLE_NAME, null, contentValues);
                }
                // *****************************

                // ***** saved feed items *****
                db.execSQL("DELETE FROM " + LegacyDBContracts.FEED_TABLE.TABLE_NAME + "_old WHERE "+ LegacyDBContracts.FEED_TABLE.COLUMN_FEED+"='' ");
                db.execSQL("INSERT INTO " + LegacyDBContracts.FEED_TABLE.TABLE_NAME + " SELECT *,'','F' FROM " + LegacyDBContracts.FEED_TABLE.TABLE_NAME + "_old");
                db.execSQL("DROP TABLE IF EXISTS " + LegacyDBContracts.FEED_TABLE.TABLE_NAME + "_old");
                // ****************************

            }
            else if(oldVersion == 12 && newVersion >= 14) {
                // shape old FEED_TABLE like the new, and rename it as FEED_TABLE_old
                db.execSQL("DROP INDEX IF EXISTS idx_id");
                db.execSQL("DROP INDEX IF EXISTS idx_idtype");
                db.execSQL("ALTER TABLE " + LegacyDBContracts.FEED_TABLE.TABLE_NAME + " RENAME TO " + LegacyDBContracts.FEED_TABLE.TABLE_NAME + "_old");

                db.execSQL("DROP TABLE IF EXISTS " + LegacyDBContracts.FEED_TABLE.TABLE_NAME);
                createFeedTable(db);

                // ***** saved feed items *****
                db.execSQL("DELETE FROM " + LegacyDBContracts.FEED_TABLE.TABLE_NAME + "_old WHERE " + LegacyDBContracts.FEED_TABLE.COLUMN_FEED + "='' ");
                db.execSQL("INSERT INTO " + LegacyDBContracts.FEED_TABLE.TABLE_NAME + " SELECT " +
                        LegacyDBContracts.FEED_TABLE._ID + ", " +
                        LegacyDBContracts.FEED_TABLE.COLUMN_TITLE + ", " +
                        LegacyDBContracts.FEED_TABLE.COLUMN_DESCRIPTION + ", " +
                        LegacyDBContracts.FEED_TABLE.COLUMN_FEED + ", " +
                        LegacyDBContracts.FEED_TABLE.COLUMN_URL + ", " +
                        LegacyDBContracts.FEED_TABLE.COLUMN_IMAGE_URL + "," +
                        LegacyDBContracts.FEED_TABLE.COLUMN_FAVICON + ", " +
                        LegacyDBContracts.FEED_TABLE.COLUMN_TIMESTAMP + ", " +
                        LegacyDBContracts.FEED_TABLE.COLUMN_CATEGORY + ", " +
                        LegacyDBContracts.FEED_TABLE.COLUMN_TYPE + ", " +
                        "'' AS " + LegacyDBContracts.FEED_TABLE.COLUMN_ARTICLE_URL + ", " +
                        LegacyDBContracts.FEED_TABLE.COLUMN_HIDDEN + " FROM " + LegacyDBContracts.FEED_TABLE.TABLE_NAME + "_old");
                db.execSQL("DROP TABLE IF EXISTS " + LegacyDBContracts.FEED_TABLE.TABLE_NAME + "_old");
                // ****************************
            }
            else if(oldVersion == 14 && newVersion >=15) {
                // shape old FEED_TABLE like the new, and rename it as FEED_TABLE_old
                db.execSQL("DROP INDEX IF EXISTS idx_id");
                db.execSQL("DROP INDEX IF EXISTS idx_idtype");
                db.execSQL("ALTER TABLE " + LegacyDBContracts.FEED_TABLE.TABLE_NAME + " RENAME TO " + LegacyDBContracts.FEED_TABLE.TABLE_NAME + "_old");

                db.execSQL("DROP TABLE IF EXISTS " + LegacyDBContracts.FEED_TABLE.TABLE_NAME);
                createFeedTable(db);

                // ***** saved feed items *****
                db.execSQL("DELETE FROM " + LegacyDBContracts.FEED_TABLE.TABLE_NAME + "_old WHERE " + LegacyDBContracts.FEED_TABLE.COLUMN_FEED + "='' ");
                db.execSQL("INSERT INTO " + LegacyDBContracts.FEED_TABLE.TABLE_NAME + " SELECT " +
                        LegacyDBContracts.FEED_TABLE._ID + ", " +
                        LegacyDBContracts.FEED_TABLE.COLUMN_TITLE + ", " +
                        LegacyDBContracts.FEED_TABLE.COLUMN_DESCRIPTION + ", " +
                        LegacyDBContracts.FEED_TABLE.COLUMN_FEED + ", " +
                        LegacyDBContracts.FEED_TABLE.COLUMN_URL + ", " +
                        LegacyDBContracts.FEED_TABLE.COLUMN_IMAGE_URL + "," +
                        LegacyDBContracts.FEED_TABLE.COLUMN_FAVICON + ", " +
                        LegacyDBContracts.FEED_TABLE.COLUMN_TIMESTAMP + ", " + "" +
                        LegacyDBContracts.FEED_TABLE.COLUMN_CATEGORY + ", " +
                        LegacyDBContracts.FEED_TABLE.COLUMN_TYPE + ", " +
                        LegacyDBContracts.FEED_TABLE.COLUMN_ARTICLE_URL + ", " +
                        LegacyDBContracts.FEED_TABLE.COLUMN_HIDDEN + ", " +
                        "'F' FROM " + LegacyDBContracts.FEED_TABLE.TABLE_NAME + "_old");
                db.execSQL("DROP TABLE IF EXISTS " + LegacyDBContracts.FEED_TABLE.TABLE_NAME + "_old");
                //***** set new favlue for favorite *****
                String newFavoriteValue = String.valueOf(System.currentTimeMillis());
                db.execSQL("UPDATE " + LegacyDBContracts.FEED_TABLE.TABLE_NAME + " SET " + LegacyDBContracts.FEED_TABLE.COLUMN_FAVORITE + "=" + newFavoriteValue + " WHERE " + LegacyDBContracts.FEED_TABLE.COLUMN_HIDDEN + "='F'");
                // ****************************
            }
            else if(oldVersion == 15 && newVersion >= 16) {
                db.execSQL("ALTER TABLE " + LegacyDBContracts.SAVED_SEARCH_TABLE.TABLE_NAME+ " RENAME TO " + LegacyDBContracts.SAVED_SEARCH_TABLE.TABLE_NAME + "_old");
                db.execSQL("DROP TABLE IF EXISTS " + LegacyDBContracts.SAVED_SEARCH_TABLE.TABLE_NAME);
                createSavedSearchTable(db);

                // Generates warning/error when inline to execSQL method
                String sql = "INSERT INTO " + LegacyDBContracts.SAVED_SEARCH_TABLE.TABLE_NAME + " SELECT " +
                        LegacyDBContracts.SAVED_SEARCH_TABLE._ID + ", " +
                        LegacyDBContracts.SAVED_SEARCH_TABLE.COLUMN_QUERY + ", " +
                        LegacyDBContracts.SAVED_SEARCH_TABLE.COLUMN_QUERY + " FROM " + LegacyDBContracts.SAVED_SEARCH_TABLE.TABLE_NAME + "_old";
                db.execSQL(sql);

                db.execSQL("DROP TABLE IF EXISTS " + LegacyDBContracts.SAVED_SEARCH_TABLE.TABLE_NAME+ "_old");
            }
            else {
                dropTables(db);
                onCreate(db);
            }
        }
    }

    public void close(){
        db.close();
    }

    public SQLiteDatabase getSQLiteDB() {
        return db;
    }

}
