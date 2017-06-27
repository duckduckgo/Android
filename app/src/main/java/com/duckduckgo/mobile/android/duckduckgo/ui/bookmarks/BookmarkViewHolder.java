/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks;

import android.support.v4.view.MotionEventCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.duckduckgo.mobile.android.duckduckgo.R;
import com.duckduckgo.mobile.android.duckduckgo.ui.base.itemtouchhelper.OnStartDragListener;

import butterknife.BindView;
import butterknife.ButterKnife;

public class BookmarkViewHolder extends RecyclerView.ViewHolder {

    interface OnBookmarkListener {
        void onClick(View view, int position);

        void onDelete(View view, int position);
    }

    @BindView(R.id.bookmark_container)
    ViewGroup container;

    @BindView(R.id.bookmark_name_text_view)
    TextView nameTextView;

    @BindView(R.id.bookmark_drag_image_view)
    ImageView dragImageView;

    @BindView(R.id.bookmark_delete_image_button)
    ImageButton deleteImageButton;

    private OnBookmarkListener listener;
    private boolean editable = false;

    private BookmarkViewHolder(final View itemView, final OnBookmarkListener onBookmarkListener,
                               final OnStartDragListener onStartDragListener) {
        super(itemView);
        ButterKnife.bind(this, itemView);
        listener = onBookmarkListener;
        itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onClick(v, getAdapterPosition());
            }
        });
        deleteImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBookmarkListener.onDelete(v, getAdapterPosition());
            }
        });
        dragImageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                dragImageView.performClick();
                switch (MotionEventCompat.getActionMasked(event)) {
                    case MotionEvent.ACTION_DOWN:
                        onStartDragListener.onStartDrag(BookmarkViewHolder.this);
                        return true;
                    case MotionEvent.ACTION_UP:
                        v.performClick();
                        return true;
                }
                return false;
            }
        });
    }

    public void setBookmark(BookmarkEntity bookmarkEntity) {
        nameTextView.setText(bookmarkEntity.getName());
    }

    public void setEditable(boolean editable) {
        if (this.editable == editable) return;
        this.editable = editable;
        final int visibility = BookmarkViewHolder.this.editable ? View.VISIBLE : View.GONE;
        deleteImageButton.setVisibility(visibility);
        dragImageView.setVisibility(visibility);
    }

    public static BookmarkViewHolder inflate(ViewGroup parent, OnBookmarkListener onBookmarkListener,
                                             OnStartDragListener onStartDragListener) {
        return new BookmarkViewHolder(
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.viewholder_bookmark, parent, false),
                onBookmarkListener, onStartDragListener);
    }
}
