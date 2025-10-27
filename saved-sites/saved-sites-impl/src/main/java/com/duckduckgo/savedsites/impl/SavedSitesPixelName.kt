/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.savedsites.impl

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding

enum class SavedSitesPixelName(override val pixelName: String) : Pixel.PixelName {
    /** Bookmarks Screen **/
    MENU_ACTION_BOOKMARKS_PRESSED_DAILY("m_navigation_menu_bookmarks_daily"),
    BOOKMARK_IMPORT_SUCCESS("m_bi_s"),
    BOOKMARK_IMPORT_ERROR("m_bi_e"),
    BOOKMARK_EXPORT_SUCCESS("m_be_a"),
    BOOKMARK_EXPORT_ERROR("m_be_e"),
    FAVORITE_BOOKMARKS_ITEM_PRESSED("m_fav_b"),
    BOOKMARK_LAUNCHED("m_bookmark_launched"),
    BOOKMARK_LAUNCHED_DAILY("m_bookmark_launched_daily"),

    /** Edit Bookmark Dialog **/
    EDIT_FAVOURITE_DIALOG_SHOWN("m_edit_favourite_dialog_shown"),
    EDIT_FAVOURITE_DIALOG_SHOWN_DAILY("m_edit_favourite_dialog_shown_daily"),
    EDIT_BOOKMARK_ADD_FAVORITE_TOGGLED("m_edit_bookmark_add_favorite"),
    EDIT_BOOKMARK_ADD_FAVORITE_TOGGLED_DAILY("m_edit_bookmark_add_favorite_daily"),
    EDIT_BOOKMARK_REMOVE_FAVORITE_TOGGLED("m_edit_bookmark_remove_favorite"),
    EDIT_BOOKMARK_DELETE_BOOKMARK_CLICKED("m_edit_bookmark_delete"),
    EDIT_BOOKMARK_DELETE_BOOKMARK_CONFIRMED("m_edit_bookmark_delete_confirm"),
    EDIT_BOOKMARK_DELETE_BOOKMARK_CANCELLED("m_edit_bookmark_delete_cancel"),

    /** Browser Menu **/
    BOOKMARK_MENU_ADD_FAVORITE_CLICKED("m_bookmark_menu_add_favorite"),
    BOOKMARK_MENU_EDIT_BOOKMARK_CLICKED("m_bookmark_menu_edit"),
    BOOKMARK_MENU_REMOVE_FAVORITE_CLICKED("m_bookmark_menu_remove_favorite"),
    BOOKMARK_MENU_DELETE_BOOKMARK_CLICKED("m_bookmark_menu_delete"),
    BOOKMARK_MENU_IMPORT_CLICKED("m_bookmark_menu_import_clicked"),
    BOOKMARK_MENU_EXPORT_CLICKED("m_bookmark_menu_export_clicked"),
    BOOKMARK_MENU_ADD_FOLDER_CLICKED("m_bookmark_menu_add_folder_clicked"),
    BOOKMARK_MENU_SORT_NAME_CLICKED("m_bookmark_menu_sort_name_clicked"),
    BOOKMARK_MENU_SORT_MANUAL_CLICKED("m_bookmark_menu_sort_manual_clicked"),

    /** New Tab Pixels **/
    FAVOURITES_LIST_EXPANDED("m_new_tab_page_favorites_expanded"),
    FAVOURITES_LIST_COLLAPSED("m_new_tab_page_favorites_collapsed"),
    FAVOURITES_TOOLTIP_PRESSED("m_new_tab_page_favorites_info_tooltip"),
    FAVOURITES_SECTION_TOGGLED_OFF("m_new_tab_page_customize_section_off_favorites"),
    FAVOURITES_SECTION_TOGGLED_ON("m_new_tab_page_customize_section_on_favorites"),
    FAVOURITE_CLICKED("m_favorite_clicked"),
    FAVOURITE_CLICKED_DAILY("m_favorite_clicked_daily"),
    MENU_ACTION_ADD_FAVORITE_PRESSED_DAILY("m_nav_af_p_daily"),
    FAVOURITE_REMOVED("m_favorite_removed"),
    FAVOURITE_DELETED("m_favorite_deleted"),

    /** Bookmark import from Google **/
    BOOKMARK_IMPORT_FROM_GOOGLE_PREIMPORT_DIALOG_SHOWN("bookmark_import_from_google_dialog_shown"),
    BOOKMARK_IMPORT_FROM_GOOGLE_PREIMPORT_DIALOG_DISMISSED("bookmark_import_from_google_dialog_dismissed"),
    BOOKMARK_IMPORT_FROM_GOOGLE_PREIMPORT_DIALOG_START_FLOW("bookmark_import_from_google_dialog_startflow"),
    BOOKMARK_IMPORT_FROM_GOOGLE_PREIMPORT_DIALOG_FROM_FILE("bookmark_import_from_google_dialog_fromfile"),
}

object SavedSitesPixelParameters {
    const val SORT_MODE = "sort_mode"
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelParamRemovalPlugin::class,
)
object SavedSitePixelsRequiringDataCleaning : PixelParamRemovalPlugin {
    override fun names(): List<Pair<String, Set<PixelParameter>>> {
        return listOf(
            SavedSitesPixelName.BOOKMARK_IMPORT_FROM_GOOGLE_PREIMPORT_DIALOG_SHOWN.pixelName to PixelParameter.removeAtb(),
            SavedSitesPixelName.BOOKMARK_IMPORT_FROM_GOOGLE_PREIMPORT_DIALOG_DISMISSED.pixelName to PixelParameter.removeAtb(),
            SavedSitesPixelName.BOOKMARK_IMPORT_FROM_GOOGLE_PREIMPORT_DIALOG_START_FLOW.pixelName to PixelParameter.removeAtb(),
            SavedSitesPixelName.BOOKMARK_IMPORT_FROM_GOOGLE_PREIMPORT_DIALOG_FROM_FILE.pixelName to PixelParameter.removeAtb(),
        )
    }
}
