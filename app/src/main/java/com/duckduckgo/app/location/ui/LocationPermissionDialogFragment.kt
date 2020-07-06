/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.location.ui

import android.app.Dialog
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.location.data.LocationPermissionType
import org.jetbrains.anko.find

class LocationPermissionDialogFragment : DialogFragment() {

    interface Listener {
        fun onSiteLocationPermissionSelected(permission: LocationPermissionType)
    }

    private lateinit var listener: Listener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val rootView = layoutInflater.inflate(R.layout.content_location_permission_dialog, null)

        rootView.find<TextView>(R.id.locationPermissionDialogAllowAlways).setOnClickListener {
            listener.onSiteLocationPermissionSelected(LocationPermissionType.ALLOW_ALWAYS)
        }

        rootView.find<TextView>(R.id.locationPermissionDialogAllowAlways).setOnClickListener {
            listener.onSiteLocationPermissionSelected(LocationPermissionType.ALLOW_ONCE)
        }

        rootView.find<TextView>(R.id.locationPermissionDialogAllowAlways).setOnClickListener {
            listener.onSiteLocationPermissionSelected(LocationPermissionType.DENY_ALWAYS)
        }

        rootView.find<TextView>(R.id.locationPermissionDialogAllowAlways).setOnClickListener {
            listener.onSiteLocationPermissionSelected(LocationPermissionType.DENY_ONCE)
        }

        return AlertDialog.Builder(requireActivity(), R.style.AlertDialogTheme)
            .setView(rootView)
            .create()
    }
}
