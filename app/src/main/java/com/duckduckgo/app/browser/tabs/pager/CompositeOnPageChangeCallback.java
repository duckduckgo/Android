/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.browser.tabs.pager;

import androidx.annotation.NonNull;
import androidx.annotation.Px;
import androidx.viewpager2.widget.ViewPager2;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;

/** Dispatches {@link ViewPager2.OnPageChangeCallback} events to subscribers. */
final class CompositeOnPageChangeCallback extends ViewPager2.OnPageChangeCallback {
    @NonNull private final List<ViewPager2.OnPageChangeCallback> mCallbacks;

    CompositeOnPageChangeCallback(int initialCapacity) {
        mCallbacks = new ArrayList<>(initialCapacity);
    }

    /** Adds the given callback to the list of subscribers */
    void addOnPageChangeCallback(ViewPager2.OnPageChangeCallback callback) {
        mCallbacks.add(callback);
    }

    /** Removes the given callback from the list of subscribers */
    void removeOnPageChangeCallback(ViewPager2.OnPageChangeCallback callback) {
        mCallbacks.remove(callback);
    }

    /**
     * @see ViewPager2.OnPageChangeCallback#onPageScrolled(int, float, int)
     */
    @Override
    public void onPageScrolled(int position, float positionOffset, @Px int positionOffsetPixels) {
        try {
            for (ViewPager2.OnPageChangeCallback callback : mCallbacks) {
                callback.onPageScrolled(position, positionOffset, positionOffsetPixels);
            }
        } catch (ConcurrentModificationException ex) {
            throwCallbackListModifiedWhileInUse(ex);
        }
    }

    /**
     * @see ViewPager2.OnPageChangeCallback#onPageSelected(int)
     */
    @Override
    public void onPageSelected(int position) {
        try {
            for (ViewPager2.OnPageChangeCallback callback : mCallbacks) {
                callback.onPageSelected(position);
            }
        } catch (ConcurrentModificationException ex) {
            throwCallbackListModifiedWhileInUse(ex);
        }
    }

    /**
     * @see ViewPager2.OnPageChangeCallback#onPageScrollStateChanged(int)
     */
    @Override
    public void onPageScrollStateChanged(@TabPager.ScrollState int state) {
        try {
            for (ViewPager2.OnPageChangeCallback callback : mCallbacks) {
                callback.onPageScrollStateChanged(state);
            }
        } catch (ConcurrentModificationException ex) {
            throwCallbackListModifiedWhileInUse(ex);
        }
    }

    private void throwCallbackListModifiedWhileInUse(ConcurrentModificationException parent) {
        throw new IllegalStateException(
                "Adding and removing callbacks during dispatch to callbacks is not supported",
                parent);
    }
}
