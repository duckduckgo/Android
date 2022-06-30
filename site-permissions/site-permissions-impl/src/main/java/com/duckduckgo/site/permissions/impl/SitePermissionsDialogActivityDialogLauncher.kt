/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.site.permissions.impl

import android.content.Context
import android.widget.Toast
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.site.permissions.api.SitePermissionsDialogLauncher
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(ActivityScope::class)

class SitePermissionsDialogActivityDialogLauncher @Inject constructor(

): SitePermissionsDialogLauncher {

    override fun showSitePermissionDialog(
        context: Context,
        permissionsRequested: Array<String>
    ) {
        permissionsRequested.forEach {
            //TODO implement dialog
            Toast.makeText(context, "$it Requested", Toast.LENGTH_SHORT).show()
        }
    }
}
