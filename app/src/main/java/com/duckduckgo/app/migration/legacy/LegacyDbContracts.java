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

import android.provider.BaseColumns;

public final class LegacyDbContracts {
    public static final String DATABASE_NAME = "ddg.db";
    public static final int DATABASE_VERSION = 16;

    public static final class FEED_TABLE implements BaseColumns {
        public static final String TABLE_NAME = "feed";

        // THE TITLE OF THE FEED
        public static final String COLUMN_TITLE = "title";

        // DESCRIPTION OF THE FEED
        public static final String COLUMN_DESCRIPTION = "description";

        // FEED
        public static final String COLUMN_FEED = "feed";

        // URL OF THE FEED
        public static final String COLUMN_URL = "url";

        // IMAGE URL OF THE FEED
        public static final String COLUMN_IMAGE_URL = "imageurl";

        // FAVICON OF THE FEED
        public static final String COLUMN_FAVICON = "favicon";

        // TIMESTAMP OF THE FEED
        public static final String COLUMN_TIMESTAMP = "timestamp";

        // CATEGORY OF THE FEED
        public static final String COLUMN_CATEGORY = "category";

        // TYPE OF THE FEED
        public static final String COLUMN_TYPE = "type";

        // ARTICLE URL OF THE FEED
        public static final String COLUMN_ARTICLE_URL = "articleurl";

        // WHETHER FEED IS HIDDEN
        public static final String COLUMN_HIDDEN = "hidden";

        // WHETHER FEED IS FAVORITE
        public static final String COLUMN_FAVORITE = "favorite";

    }

    public static final class APP_TABLE {
        public static final String TABLE_NAME = "apps";

        // THE TITLE OF THE APP
        public static final String COLUMN_TITLE = "title";

        // PACKAGE NAME OF THE APP
        public static final String COLUMN_PACKAGE = "package";
    }

    public static final class HISTORY_TABLE implements BaseColumns {
        public static final String TABLE_NAME = "history";

        // TYPE OF THE FEED
        public static final String COLUMN_TYPE = "type";

        // URL OF THE FEED
        public static final String COLUMN_URL = "url";

        // DATA OF THE FEED
        public static final String COLUMN_DATA = "data";

        // EXTRA TYPE OF THE FEED
        public static final String COLUMN_EXTRA_TYPE = "extraType";

        // ID OF THE FEED
        public static final String COLUMN_FEED_ID = "feedId";
    }

    public static final class SAVED_SEARCH_TABLE implements BaseColumns {
        public static final String TABLE_NAME = "saved_search";

        // THE TITLE OF THE SEARCH
        public static final String COLUMN_TITLE = "title";

        // SEARCH QUERY
        public static final String COLUMN_QUERY = "query";
    }
}
