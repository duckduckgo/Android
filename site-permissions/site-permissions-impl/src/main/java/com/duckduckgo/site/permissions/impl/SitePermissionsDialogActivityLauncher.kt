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

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import android.webkit.PermissionRequest
import androidx.activity.result.ActivityResultCaller
import androidx.annotation.StringRes
import androidx.core.net.toUri
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.ui.view.addClickableLink
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.websiteFromGeoLocationsApiOrigin
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.site.permissions.api.SitePermissionsDialogLauncher
import com.duckduckgo.site.permissions.api.SitePermissionsGrantedListener
import com.duckduckgo.site.permissions.api.SitePermissionsManager.SitePermissions
import com.duckduckgo.site.permissions.impl.databinding.ContentSiteDrmPermissionDialogBinding
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionAskSettingType
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionsEntity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar.BaseCallback
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.SnackbarLayout
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@ContributesBinding(FragmentScope::class)
class SitePermissionsDialogActivityLauncher @Inject constructor(
    private val systemPermissionsHelper: SystemPermissionsHelper,
    private val sitePermissionsRepository: SitePermissionsRepository,
    private val dispatcher: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : SitePermissionsDialogLauncher {

    private lateinit var sitePermissionRequest: PermissionRequest
    private lateinit var activity: Activity
    private lateinit var permissionRequested: SitePermissionsRequestedType
    private lateinit var permissionsGrantedListener: SitePermissionsGrantedListener
    private lateinit var permissionsHandledByUser: List<String>
    private lateinit var permissionsHandledAutomatically: List<String>
    private var siteURL: String = ""
    private var tabId: String = ""

    override fun registerPermissionLauncher(caller: ActivityResultCaller) {
        systemPermissionsHelper.registerPermissionLaunchers(
            caller,
            this::onResultSystemPermissionRequest,
            this::onResultMultipleSystemPermissionsRequest,
        )
    }

    override fun askForSitePermission(
        activity: Activity,
        url: String,
        tabId: String,
        permissionsRequested: SitePermissions,
        request: PermissionRequest,
        permissionsGrantedListener: SitePermissionsGrantedListener,
    ) {
        sitePermissionRequest = request
        siteURL = url
        this.tabId = tabId
        this.activity = activity
        this.permissionsGrantedListener = permissionsGrantedListener
        permissionsHandledByUser = permissionsRequested.userHandled
        permissionsHandledAutomatically = permissionsRequested.autoAccept

        when {
            permissionsHandledByUser.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) && permissionsHandledByUser.contains(
                PermissionRequest.RESOURCE_AUDIO_CAPTURE,
            ) -> {
                showSitePermissionsRationaleDialog(R.string.sitePermissionsMicAndCameraDialogTitle, url, this::askForMicAndCameraPermissions)
            }
            permissionsHandledByUser.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) -> {
                showSitePermissionsRationaleDialog(R.string.sitePermissionsMicDialogTitle, url, this::askForMicPermissions)
            }
            permissionsHandledByUser.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) -> {
                showSitePermissionsRationaleDialog(R.string.sitePermissionsCameraDialogTitle, url, this::askForCameraPermissions)
            }
            permissionsHandledByUser.contains(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID) -> {
                showSiteDrmPermissionsDialog(activity, url)
            }
        }
    }

    private fun showSitePermissionsRationaleDialog(
        @StringRes titleRes: Int,
        url: String,
        onPermissionAllowed: () -> Unit,
    ) {
        TextAlertDialogBuilder(activity)
            .setTitle(String.format(activity.getString(titleRes), url.websiteFromGeoLocationsApiOrigin()))
            .setPositiveButton(R.string.sitePermissionsDialogAllowButton)
            .setNegativeButton(R.string.sitePermissionsDialogDenyButton)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        onPermissionAllowed()
                    }

                    override fun onNegativeButtonClicked() {
                        denyPermissions()
                    }
                },
            )
            .show()
    }

    private fun showSiteDrmPermissionsDialog(
        activity: Activity,
        url: String,
    ) {
        val domain = url.extractDomain() ?: url

        // Check if user allowed or denied per session
        val sessionSetting = sitePermissionsRepository.getDrmForSession(domain)
        if (sessionSetting != null) {
            if (sessionSetting) {
                systemPermissionGranted()
            } else {
                denyPermissions()
            }
            return
        }

        // No session-based setting -> proceed to show dialog
        val binding = ContentSiteDrmPermissionDialogBinding.inflate(activity.layoutInflater)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(binding.root)
            .create()

        val title = url.websiteFromGeoLocationsApiOrigin()
        binding.sitePermissionDialogTitle.text = activity.getString(R.string.drmSiteDialogTitle, title)
        binding.sitePermissionDialogSubtitle.addClickableLink(
            DRM_LEARN_MORE_ANNOTATION,
            activity.getText(R.string.drmSiteDialogSubtitle),
        ) {
            denyPermissions()
            dialog.dismiss()
            activity.startActivity(Intent(Intent.ACTION_VIEW, DRM_LEARN_MORE_URL))
        }

        binding.siteAllowAlwaysDrmPermission.setOnClickListener {
            systemPermissionGranted()
            onSiteDrmPermissionSave(domain, SitePermissionAskSettingType.ALLOW_ALWAYS)
            dialog.dismiss()
        }

        binding.siteAllowOnceDrmPermission.setOnClickListener {
            sitePermissionsRepository.saveDrmForSession(domain, true)
            systemPermissionGranted()
            dialog.dismiss()
        }

        binding.siteDenyOnceDrmPermission.setOnClickListener {
            sitePermissionsRepository.saveDrmForSession(domain, false)
            denyPermissions()
            dialog.dismiss()
        }

        binding.siteDenyAlwaysDrmPermission.setOnClickListener {
            denyPermissions()
            onSiteDrmPermissionSave(domain, SitePermissionAskSettingType.DENY_ALWAYS)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun onSiteDrmPermissionSave(
        domain: String,
        drmPermission: SitePermissionAskSettingType,
    ) {
        val sitePermissionsEntity = SitePermissionsEntity(
            domain = domain,
            askDrmSetting = drmPermission.name,
        )

        appCoroutineScope.launch(dispatcher.io()) {
            sitePermissionsRepository.savePermission(sitePermissionsEntity)
        }
    }

    private fun askForMicAndCameraPermissions() {
        permissionRequested = SitePermissionsRequestedType.CAMERA_AND_AUDIO
        when {
            systemPermissionsHelper.hasMicPermissionsGranted() && systemPermissionsHelper.hasCameraPermissionsGranted() -> {
                systemPermissionGranted()
            }
            systemPermissionsHelper.hasMicPermissionsGranted() -> {
                systemPermissionsHelper.requestPermission(Manifest.permission.CAMERA)
            }
            systemPermissionsHelper.hasCameraPermissionsGranted() -> {
                systemPermissionsHelper.requestMultiplePermissions(
                    arrayOf(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.MODIFY_AUDIO_SETTINGS,
                    ),
                )
            }
            else -> {
                systemPermissionsHelper.requestMultiplePermissions(
                    arrayOf(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.MODIFY_AUDIO_SETTINGS,
                        Manifest.permission.CAMERA,
                    ),
                )
            }
        }
    }

    private fun askForMicPermissions() {
        permissionRequested = SitePermissionsRequestedType.AUDIO
        if (systemPermissionsHelper.hasMicPermissionsGranted()) {
            systemPermissionGranted()
        } else {
            systemPermissionsHelper.requestMultiplePermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS))
        }
    }

    private fun askForCameraPermissions() {
        permissionRequested = SitePermissionsRequestedType.CAMERA
        if (systemPermissionsHelper.hasCameraPermissionsGranted()) {
            systemPermissionGranted()
        } else {
            systemPermissionsHelper.requestPermission(Manifest.permission.CAMERA)
        }
    }

    private fun onResultSystemPermissionRequest(granted: Boolean) {
        when (granted) {
            true -> systemPermissionGranted()
            false -> systemPermissionDenied()
        }
    }

    private fun onResultMultipleSystemPermissionsRequest(grantedPermissions: Map<String, Boolean>) {
        if (grantedPermissions.values.contains(false)) {
            systemPermissionDenied()
        } else {
            systemPermissionGranted()
        }
    }

    private fun systemPermissionGranted() {
        val permissions = permissionsHandledAutomatically.toTypedArray() + permissionsHandledByUser
        sitePermissionRequest.grant(permissions)
        permissionsHandledByUser.forEach {
            sitePermissionsRepository.sitePermissionGranted(siteURL, tabId, it)
        }
        checkIfActionNeeded()
    }

    private fun checkIfActionNeeded() {
        when (siteURL.extractDomain().orEmpty()) {
            "whereby.com" -> permissionsGrantedListener.permissionsGrantedOnWhereby()
            else -> {
                // No action needed
            }
        }
    }

    private fun systemPermissionDenied() {
        when (systemPermissionsHelper.isPermissionsRejectedForever(activity)) {
            true -> showSystemPermissionsDeniedDialog()
            false -> showPermissionsDeniedSnackBar()
        }
    }

    private fun showPermissionsDeniedSnackBar() {
        val onPermissionAllowed: () -> Unit
        val message =
            when (permissionRequested) {
                SitePermissionsRequestedType.CAMERA -> {
                    onPermissionAllowed = this::askForCameraPermissions
                    R.string.sitePermissionsCameraDeniedSnackBarMessage
                }
                SitePermissionsRequestedType.AUDIO -> {
                    onPermissionAllowed = this::askForMicPermissions
                    R.string.sitePermissionsMicDeniedSnackBarMessage
                }
                SitePermissionsRequestedType.CAMERA_AND_AUDIO -> {
                    onPermissionAllowed = this::askForMicAndCameraPermissions
                    R.string.sitePermissionsCameraAndMicDeniedSnackBarMessage
                }
            }

        val snackbar = Snackbar.make(activity.window.decorView.rootView, message, Snackbar.LENGTH_LONG)
        val layout = snackbar.view as SnackbarLayout
        if (activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            layout.setPadding(0, 0, 0, 40.toPx())
        }
        snackbar.apply {
            setAction(R.string.sitePermissionsDeniedSnackBarAction) { onPermissionAllowed() }
            addCallback(
                object : Snackbar.Callback() {
                    override fun onDismissed(
                        transientBottomBar: Snackbar?,
                        event: Int,
                    ) {
                        if (event == BaseCallback.DISMISS_EVENT_TIMEOUT) {
                            denyPermissions()
                        }
                    }
                },
            )
            show()
        }
    }

    private fun denyPermissions() {
        if (permissionsHandledAutomatically.isNotEmpty()) {
            sitePermissionRequest.grant(permissionsHandledAutomatically.toTypedArray())
        } else {
            sitePermissionRequest.deny()
        }
    }

    private fun showSystemPermissionsDeniedDialog() {
        denyPermissions()
        val titleRes = when (permissionRequested) {
            SitePermissionsRequestedType.CAMERA -> R.string.systemPermissionDialogCameraDeniedTitle
            SitePermissionsRequestedType.AUDIO -> R.string.systemPermissionDialogAudioDeniedTitle
            SitePermissionsRequestedType.CAMERA_AND_AUDIO -> R.string.systemPermissionDialogCameraAndAudioDeniedTitle
        }
        val contentRes = when (permissionRequested) {
            SitePermissionsRequestedType.CAMERA -> R.string.systemPermissionDialogCameraDeniedContent
            SitePermissionsRequestedType.AUDIO -> R.string.systemPermissionDialogAudioDeniedContent
            SitePermissionsRequestedType.CAMERA_AND_AUDIO -> R.string.systemPermissionDialogCameraAndAudioDeniedContent
        }
        TextAlertDialogBuilder(activity)
            .setTitle(titleRes)
            .setMessage(contentRes)
            .setPositiveButton(R.string.systemPermissionsDeniedDialogPositiveButton)
            .setNegativeButton(R.string.systemPermissionsDeniedDialogNegativeButton)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        val intent = Intent()
                        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        val uri = Uri.fromParts("package", activity.packageName, null)
                        intent.data = uri
                        activity.startActivity(intent)
                    }
                },
            )
            .show()
    }

    companion object {
        private const val DRM_LEARN_MORE_ANNOTATION = "drm_learn_more_link"
        val DRM_LEARN_MORE_URL = "https://duckduckgo.com/duckduckgo-help-pages/privacy/drm-permission/".toUri()
    }
}

fun String.websiteFromGeoLocationsApiOrigin(): String {
    val webPrefix = "www."
    val uri = Uri.parse(this)
    val host = uri.host ?: return this

    return host
        .takeIf { it.startsWith(webPrefix, ignoreCase = true) }
        ?.drop(webPrefix.length)
        ?: host
}

enum class SitePermissionsRequestedType {
    CAMERA,
    AUDIO,
    CAMERA_AND_AUDIO,
}
