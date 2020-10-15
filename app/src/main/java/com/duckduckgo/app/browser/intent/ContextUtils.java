/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.browser.intent;

import android.content.Intent;
import android.net.Uri;

import timber.log.Timber;

public class ContextUtils {
    public static boolean getBooleanExtra(Intent intent, String name, boolean defaultValue) {
        return new SafeIntent(intent).getBooleanExtra(name, defaultValue);
    }

    public static class SafeIntent {
        private final Intent intent;

        public SafeIntent(final Intent intent) {
            this.intent = intent;
        }

        public boolean getBooleanExtra(final String name, final boolean defaultValue) {
            try {
                return intent.getBooleanExtra(name, defaultValue);
            } catch (OutOfMemoryError e) {
                Timber.w("Couldn't get intent extras: OOM. Malformed?");
                return defaultValue;
            } catch (RuntimeException e) {
                Timber.w(e, "Couldn't get intent extras.");
                return defaultValue;
            }
        }

        public String getAction() {
            return intent.getAction();
        }

        public Uri getData() {
            try {
                return intent.getData();
            } catch (OutOfMemoryError e) {
                Timber.w("Couldn't get intent data: OOM. Malformed?");
                return null;
            } catch (RuntimeException e) {
                Timber.w(e, "Couldn't get intent data.");
                return null;
            }
        }
    }
}
