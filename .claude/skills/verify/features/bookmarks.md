# Bookmarks

The user saves the current page as a bookmark, organises bookmarks into folders, edits, moves, deletes
(with undo) and favourites them, and imports bookmarks from a file or from Google.

## Sub-features

- `bm-add` the browser menu's Add Bookmark saves the page and shows a sheet with Add to Favorites and Edit Bookmark.
- `bm-list` Bookmarks lists saved sites and folders.
- `bm-folder-add` Add folder creates a folder (`bookmarks.add-folder-dialog`).
- `bm-move` Edit > location moves a bookmark into a folder through the folder picker (`bookmarks.folders`).
- `bm-delete-undo` Delete removes a bookmark and the snackbar's Undo restores it (`bookmarks.delete-undo-snackbar`).
- `bm-favorite` Add to / Remove From Favorites changes the New Tab Page favourite tiles.
- `bm-import` Import opens the system file picker, or the Google pre-prompt (`bookmarks.google-import-preprompt`) when Google import is available.
- `bm-folder-edit` edit or delete a folder (`bookmarks.edit-folder-dialog`).

## How to get to it (user POV)

- Bookmarks: browser menu > `bookmarksMenuItem` ([BrowserActivity.kt:1362](../../../../app/src/main/java/com/duckduckgo/app/browser/BrowserActivity.kt)); also the launcher's app shortcut (`AppShortcutCreator`), a New Tab Page shortcut (`BookmarksShortcut`), and the Sync paused / invalid-items views.
- Add: loaded page > browser menu > `addBookmarksMenuItem`.
- Edit, move, delete, favourite: Bookmarks > a row's `trailingIconContainer` > `edit` / `addRemoveFavorite` / `delete`. Edit's `savedSiteLocation` opens the folder picker ([SavedSiteDialogFragment.kt:189](../../../../saved-sites/saved-sites-impl/src/main/java/com/duckduckgo/savedsites/impl/dialogs/SavedSiteDialogFragment.kt)).
- Import: Bookmarks > `browserMenu` > `importBookmarks`, or the empty state's `savedSiteEmptyImportButton`.

## Driving it with verify.sh

Preconditions: baseline (reset, Web Content Debugging on); `$V t goto browser.main`, `$V t browse --url https://privacy-test-pages.site/`, `$V wait id:browserWebView 20`.

- **Add.** `$V t record start bm-add.mp4`, `$V t tap id:browserMenu`, `$V wait id:addBookmarksMenuItem`, `$V t tap id:addBookmarksMenuItem`, then within a second `$V t assert visible id:editBookmark` (the sheet has `setAsFavorite`, `setAsFavoriteSwitch`, `editBookmark`). Second view: `$V t tap id:touch_outside`, `$V t tap id:browserMenu`, `$V t tap id:bookmarksMenuItem`, `$V t assert where bookmarks`, `$V t assert visible "text:Privacy Test Pages - Home"`.
- **Folder.** On Bookmarks, `$V t tap id:addFolderMenu`, `$V t type id:internal_edit_text Work`, then `$V t tap id:action_confirm_changes` (it appears only once the name has text), `$V t assert visible text:Work`.
- **Move.** `$V t tap id:trailingIconContainer --index 1` on the bookmark row, `$V t tap id:edit`, `$V t tap id:savedSiteLocation`, `$V t assert where bookmarks.folders`, `$V t tap text:Work`, `$V t tap id:action_confirm_changes`. The root no longer lists the bookmark; `$V t tap text:Work` and `$V t assert visible "text:Privacy Test Pages - Home"`.
- **Delete and undo.** Inside the folder, `$V t tap id:trailingIconContainer --index 1`, `$V t tap id:delete`, then immediately `$V t tap id:snackbar_action` (Undo) with nothing in between. The row is back within a second and still there after reopening the folder.
- **Favourite.** Count tiles first: `$V t goto browser.newtab`, `$V t key back` (drops the keyboard), then count `quickAccessFaviconCard` in `$V t hierarchy --interactive-only --json`. In Bookmarks, open the row overflow and read the `addRemoveFavorite` label before tapping it ("Add to Favorites" or "Remove From Favorites"). Back on the New Tab Page the tile count is one higher or lower. Measured 2 → 3 with `seed bookmarks-populated`, 2026-11-13.
- **Import.** `$V t tap id:browserMenu`, `$V t tap id:importBookmarks`. Without Google import available, the foreground is the system picker (`adb -s <serial> shell dumpsys window | grep mCurrentFocus` shows `com.android.documentsui/...PickActivity`); `key back` returns. With it available, `$V t assert visible id:importButton` and `id:selectFileButton`; `id:closeButton` closes it.
- Seeded state: `$V t seed bookmarks-populated` gives 12 bookmarks in "Fixture Folder A/B" and 3 favourites. Reopen Bookmarks after seeding; a screen that was already open keeps its old list.

## Gotchas

- The Add Bookmark sheet disappears after about 3 s. Assert on it straight after the tap.
- Undo has a few seconds. A hierarchy read plus a screenshot between Delete and Undo used it all up; the Undo tap then failed silently and the delete was committed. Shoot after Undo, not before.
- The favourite toggle is one menu entry whose label flips; a blind tap removes a favourite that the seed already set.
- Google import pre-prompt depends on `importFromGoogleAvailability.isBookmarksImportAvailable()` ([BookmarksViewModel.kt:527](../../../../saved-sites/saved-sites-impl/src/main/java/com/duckduckgo/savedsites/impl/bookmarks/BookmarksViewModel.kt)); it was false on `ddg_verify_api34`. `goto bookmarks.google-import-preprompt` opens it regardless, but that is not a user path.
- `goto bookmarks.google-import-failed` needs `go importGoogleBookmarks '{"params":{"launchSource":""}}'` first; a plain `goto` fails with `missing parameter: params`.
- Driven with trapdoor on 2026-11-13: `bm-add`, `bm-list`, `bm-folder-add`, `bm-move`, `bm-delete-undo`, `bm-favorite`, `bm-import` (file picker path). Not driven: the Google pre-prompt as a user path, `bm-folder-edit`, the app shortcut, New Tab Page shortcut and Sync entry points.
