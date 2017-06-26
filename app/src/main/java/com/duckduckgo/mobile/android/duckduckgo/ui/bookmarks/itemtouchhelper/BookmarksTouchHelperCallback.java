package com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks.itemtouchhelper;

import com.duckduckgo.mobile.android.duckduckgo.ui.base.itemtouchhelper.ItemTouchHelperCallback;

/**
 * Created by fgei on 6/23/17.
 */

public class BookmarksTouchHelperCallback extends ItemTouchHelperCallback<BookmarksTouchHelperAdapter> {
    public BookmarksTouchHelperCallback(BookmarksTouchHelperAdapter adapter) {
        super(adapter);
    }

    @Override
    public boolean isItemMoveEnabled() {
        return true;
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return getAdapter().isEditable();
    }
}
