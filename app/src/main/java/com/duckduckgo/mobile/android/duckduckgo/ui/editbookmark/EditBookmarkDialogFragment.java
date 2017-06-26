package com.duckduckgo.mobile.android.duckduckgo.ui.editbookmark;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.View;
import android.widget.EditText;

import com.duckduckgo.mobile.android.duckduckgo.R;
import com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks.BookmarkEntity;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 *    Copyright 2017 DuckDuckGo
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

public class EditBookmarkDialogFragment extends AppCompatDialogFragment {

    public static final String TAG = EditBookmarkDialogFragment.class.getSimpleName();

    public static EditBookmarkDialogFragment newInstance(int titleResId, @NonNull BookmarkEntity bookmark) {
        EditBookmarkDialogFragment dialog = new EditBookmarkDialogFragment();
        Bundle args = new Bundle();
        args.putInt(EXTRA_TITLE, titleResId);
        args.putParcelable(EXTRA_BOOKMARK, bookmark);
        dialog.setArguments(args);
        return dialog;
    }

    private static final String EXTRA_TITLE = "extra_title";
    private static final String EXTRA_BOOKMARK = "extra_bookmark";

    public interface OnEditBookmarkListener {
        void onBookmarkEdited(BookmarkEntity bookmark);
    }

    @BindView(R.id.dialog_edit_bookmark_name_edit_text)
    EditText nameEditText;

    @BindView(R.id.dialog_edit_bookmark_url_edit_text)
    EditText urlEditText;

    private OnEditBookmarkListener onEditBookmarkListener;

    private int titleResId;
    private BookmarkEntity bookmarkEntity;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            onEditBookmarkListener = (OnEditBookmarkListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement " + OnEditBookmarkListener.class.getSimpleName());
        }
    }

    @Override
    public void onDetach() {
        onEditBookmarkListener = null;
        super.onDetach();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        titleResId = getArguments().getInt(EXTRA_TITLE);
        bookmarkEntity = getArguments().getParcelable(EXTRA_BOOKMARK);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View rootView = View.inflate(getContext(), R.layout.dialog_edit_bookmark, null);
        ButterKnife.bind(this, rootView);
        initUI();
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext())
                .setTitle(titleResId)
                .setView(rootView)
                .setPositiveButton(R.string.bookmark_dialog_save_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        onSaveButtonClick();
                    }
                })
                .setNegativeButton(R.string.bookmark_dialog_cancel_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dismiss();
                    }
                });
        return builder.create();
    }

    private void initUI() {
        nameEditText.setText(bookmarkEntity.getName());
        urlEditText.setText(bookmarkEntity.getUrl());
    }

    private void onSaveButtonClick() {
        onEditBookmarkListener.onBookmarkEdited(getEditedBookmark());
    }

    private BookmarkEntity getEditedBookmark() {
        bookmarkEntity.setName(nameEditText.getText().toString());
        bookmarkEntity.setUrl(urlEditText.getText().toString());
        return bookmarkEntity;
    }
}
