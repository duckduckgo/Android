package com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.duckduckgo.mobile.android.duckduckgo.Injector;
import com.duckduckgo.mobile.android.duckduckgo.R;
import com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks.itemtouchhelper.DragItemTouchHelperCallback;
import com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks.itemtouchhelper.OnStartDragListener;
import com.duckduckgo.mobile.android.duckduckgo.ui.editbookmark.EditBookmarkDialogFragment;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

public class BookmarksActivity extends AppCompatActivity implements BookmarksView, OnStartDragListener, EditBookmarkDialogFragment.OnEditBookmarkListener {

    public static Intent getStartIntent(Context context) {
        return new Intent(context, BookmarksActivity.class);
    }

    @Nullable
    public static BookmarkModel getResultBookmark(Intent intent) {
        return intent.getParcelableExtra(RESULT_BOOKMARK);
    }

    private static final String RESULT_BOOKMARK = "result_bookmark";

    private static final String EXTRA_IS_EDITING = "extra_is_editing";

    @BindView(R.id.bookmarks_toolbar)
    Toolbar toolbar;

    @BindView(R.id.bookmarks_recycler_view)
    RecyclerView recyclerView;

    @BindView(R.id.bookmarks_empty_text_view)
    TextView emptyTextView;

    private BookmarksAdapter adapter;
    private BookmarksPresenter presenter;
    private ItemTouchHelper itemTouchHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmarks);
        ButterKnife.bind(this);

        initUI();

        presenter = Injector.injectBookmarkPresenter();

        if (savedInstanceState != null) {
            boolean isEditing = savedInstanceState.getBoolean(EXTRA_IS_EDITING);
            presenter.restore(isEditing);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        presenter.attachView(this);
        presenter.load();
    }

    @Override
    protected void onPause() {
        presenter.detachView();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EXTRA_IS_EDITING, adapter.isEditable());
    }

    @Override
    public void onBackPressed() {
        presenter.dismiss();
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) {
            Injector.clearBookmarksPresenter();
        }
        super.onDestroy();
    }

    @Override
    public void loadBookmarks(@NonNull List<BookmarkModel> bookmarks) {
        adapter.setBookmarks(bookmarks);
    }

    @Override
    public void showEmpty(boolean empty) {
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyTextView.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    @Override
    public void showEditButton(boolean visible) {
        setEditMenuItemVisible(visible);
    }

    @Override
    public void showEditMode() {
        setEditMenuItemVisible(false);
        adapter.setEditable(true);
    }

    @Override
    public void dismissEditMode() {
        setEditMenuItemVisible(true);
        adapter.setEditable(false);
    }

    @Override
    public void close() {
        finish();
    }

    @Override
    public void showEditBookmark(@NonNull BookmarkModel bookmarkModel) {
        EditBookmarkDialogFragment dialog = EditBookmarkDialogFragment.newInstance(R.string.bookmark_dialog_title_edit, bookmarkModel);
        dialog.show(getSupportFragmentManager(), EditBookmarkDialogFragment.TAG);
    }

    @Override
    public void resultOpenBookmark(@NonNull BookmarkModel bookmarkModel) {
        Intent intent = new Intent();
        intent.putExtra(RESULT_BOOKMARK, bookmarkModel);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void onStartDrag(RecyclerView.ViewHolder viewHolder) {
        itemTouchHelper.startDrag(viewHolder);
    }

    @Override
    public void onBookmarkEdited(BookmarkModel bookmark) {
        presenter.saveEditedBookmark(bookmark);
    }

    private void initUI() {
        initToolbar();
        initRecyclerView();
    }

    private void initToolbar() {
        toolbar.setTitle(R.string.bookmarks_toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_action_remove);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.dismiss();
            }
        });
        toolbar.inflateMenu(R.menu.bookmarks);
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.action_edit:
                        presenter.edit();
                        return true;
                }
                return false;
            }
        });
    }

    private void initRecyclerView() {
        adapter = new BookmarksAdapter(this, new BookmarksAdapter.OnBookmarkListener() {
            @Override
            public void onBookmarkSelected(View v, int position) {
                presenter.bookmarkSelected(position);
            }

            @Override
            public void onBookmarkDeleted(View v, int position) {
                presenter.bookmarkDeleted(position);
            }

            @Override
            public void onBookmarksSwap(int fromPosition, int toPosition) {
                presenter.bookmarksMoved(fromPosition, toPosition);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        ItemTouchHelper.Callback callback = new DragItemTouchHelperCallback(adapter);
        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(recyclerView);
    }

    private void setEditMenuItemVisible(boolean visible) {
        MenuItem editMenuItem = toolbar.getMenu().findItem(R.id.action_edit);
        editMenuItem.setVisible(visible);
    }
}
