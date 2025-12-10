/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.downloads.api.DownloadStateListener
import com.duckduckgo.downloads.api.DownloadsFileActions
import com.duckduckgo.downloads.api.FileDownloader
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.databinding.BottomSheetDuckAiContextualBinding
import com.duckduckgo.duckchat.impl.feature.AIChatDownloadFeature
import com.duckduckgo.duckchat.impl.helper.DuckChatJSHelper
import com.duckduckgo.duckchat.impl.ui.filechooser.FileChooserIntentBuilder
import com.duckduckgo.duckchat.impl.ui.filechooser.capture.camera.CameraHardwareChecker
import com.duckduckgo.duckchat.impl.ui.filechooser.capture.launcher.UploadFromExternalMediaAppLauncher
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import kotlinx.coroutines.CoroutineScope

class DuckChatContextualBottomSheet(
    viewModelFactory: FragmentViewModelFactory,
    webViewClient: DuckChatWebViewClient,
    contentScopeScripts: JsMessaging,
    duckChatJSHelper: DuckChatJSHelper,
    subscriptionsHandler: SubscriptionsHandler,
    appCoroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    browserNav: BrowserNav,
    appBuildConfig: AppBuildConfig,
    fileDownloader: FileDownloader,
    downloadCallback: DownloadStateListener,
    downloadsFileActions: DownloadsFileActions,
    duckChat: DuckChatInternal,
    aiChatDownloadFeature: AIChatDownloadFeature,
    fileChooserIntentBuilder: FileChooserIntentBuilder,
    cameraHardwareChecker: CameraHardwareChecker,
    externalCameraLauncher: UploadFromExternalMediaAppLauncher,
    globalActivityStarter: GlobalActivityStarter,
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetDuckAiContextualBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetDuckAiContextualBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.actionSend.setOnClickListener {
            dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let { bottomSheet ->
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let { bottomSheet ->
            val behavior = BottomSheetBehavior.from(bottomSheet)
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED

            val shapeDrawable = MaterialShapeDrawable.createWithElevationOverlay(context)
            shapeDrawable.shapeAppearanceModel = shapeDrawable.shapeAppearanceModel
                .toBuilder()
                .setTopLeftCorner(
                    CornerFamily.ROUNDED,
                    requireContext().resources.getDimension(com.duckduckgo.mobile.android.R.dimen.dialogBorderRadius),
                )
                .setTopRightCorner(
                    CornerFamily.ROUNDED,
                    requireContext().resources.getDimension(com.duckduckgo.mobile.android.R.dimen.dialogBorderRadius),
                )
                .build()
            bottomSheet.background = shapeDrawable
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "DuckChatBottomSheet"
    }
}
