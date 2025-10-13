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

package com.duckduckgo.sync.impl.ui.qrcode

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ViewSquareDecoratedBarcodeBinding
import com.duckduckgo.sync.impl.ui.qrcode.SquareDecoratedBarcodeViewModel.Command
import com.duckduckgo.sync.impl.ui.qrcode.SquareDecoratedBarcodeViewModel.Command.CheckCameraAvailable
import com.duckduckgo.sync.impl.ui.qrcode.SquareDecoratedBarcodeViewModel.Command.CheckPermissions
import com.duckduckgo.sync.impl.ui.qrcode.SquareDecoratedBarcodeViewModel.Command.OpenSettings
import com.duckduckgo.sync.impl.ui.qrcode.SquareDecoratedBarcodeViewModel.Command.RequestPermissions
import com.duckduckgo.sync.impl.ui.qrcode.SquareDecoratedBarcodeViewModel.ViewState
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * Encapsulates DecoratedBarcodeView forcing a 1:1 aspect ratio, and adds custom frame to the scanner
 */
@InjectWith(ViewScope::class)
class SyncBarcodeView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) :
    FrameLayout(context, attrs, defStyleAttr) {

    @Inject
    lateinit var viewModelFactory: SquareDecoratedBarcodeViewModel.Factory

    @Inject
    lateinit var dispatchers: DispatcherProvider

    private val cameraBlockedDrawable by lazy {
        ContextCompat.getDrawable(context, R.drawable.camera_blocked)
    }

    private val cameraPermissionDeniedDrawable by lazy {
        ContextCompat.getDrawable(context, R.drawable.camera_permission)
    }

    private val binding: ViewSquareDecoratedBarcodeBinding by viewBinding()

    private val viewModel: SquareDecoratedBarcodeViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[SquareDecoratedBarcodeViewModel::class.java]
    }

    private val conflatedStateJob = ConflatedJob()
    private val conflatedCommandJob = ConflatedJob()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel)

        val scope = findViewTreeLifecycleOwner()?.lifecycleScope!!

        conflatedStateJob += viewModel.viewState
            .onEach { render(it) }
            .launchIn(scope)

        conflatedCommandJob += viewModel.commands()
            .onEach { processCommands(it) }
            .launchIn(scope)

        binding.goToSettingsButton.setOnClickListener {
            viewModel.goToSettings()
        }
    }

    override fun onDetachedFromWindow() {
        conflatedStateJob.cancel()
        conflatedCommandJob.cancel()
        super.onDetachedFromWindow()
    }

    fun resume() {
        binding.barcodeView.resume()
    }

    fun pause() {
        binding.barcodeView.pause()
    }

    fun onCtaClicked(onClick: () -> Unit) {
        binding.barcodeCta.setOnClickListener {
            onClick.invoke()
        }
        binding.cameraPermissionsBarcodeCta.setOnClickListener {
            onClick.invoke()
        }
    }

    fun decodeSingle(onQrCodeRead: (String) -> Unit) {
        binding.barcodeView.decodeSingle {
            onQrCodeRead(it.text)
        }
    }

    private fun processCommands(command: Command) {
        when (command) {
            is CheckCameraAvailable -> checkCameraAvailable()
            is CheckPermissions -> checkPermissions()
            is RequestPermissions -> showCameraPermissionsPrompt()
            is OpenSettings -> openSettings()
        }
    }

    private fun render(viewState: ViewState) {
        when (viewState) {
            is ViewState.CameraUnavailable -> showCameraUnavailable()
            is ViewState.PermissionsNotGranted -> showPermissionDenied()
            is ViewState.PermissionsGranted -> showPermissionsGranted()
            is ViewState.Unknown -> showUnknownStatus()
        }
    }

    private fun checkCameraAvailable() {
        val cameraAvailable = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        viewModel.handleCameraAvailability(cameraAvailable)
    }

    private fun checkPermissions() {
        val enabled = ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        viewModel.handlePermissions(enabled == PackageManager.PERMISSION_GRANTED)
    }

    @SuppressLint("InlinedApi")
    private fun showCameraPermissionsPrompt() {
        pause()
        ActivityCompat.requestPermissions(getActivity(), arrayOf(Manifest.permission.CAMERA), 1)
    }

    private fun showPermissionDenied() {
        pause()
        binding.permissionsGroup.visibility = View.VISIBLE
        binding.cameraStatusIcon.setImageDrawable(cameraPermissionDeniedDrawable)
        binding.cameraStatusTitle.text = context.getString(R.string.sync_camera_permission_required)
    }

    private fun showUnknownStatus() {
        pause()
        binding.cameraStatusContainer.visibility = View.GONE
    }

    private fun showPermissionsGranted() {
        resume()
        binding.cameraStatusContainer.visibility = View.GONE
    }

    private fun showCameraUnavailable() {
        pause()
        binding.permissionsGroup.visibility = View.GONE
        binding.cameraStatusIcon.setImageDrawable(cameraBlockedDrawable)
        binding.cameraStatusTitle.text = context.getString(R.string.sync_camera_unavailable)
        binding.cameraUnavailableGroup.visibility = View.VISIBLE
    }

    private fun openSettings() {
        pause()
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", context.packageName, null)
        intent.data = uri
        context.startActivity(intent)
    }

    private fun getActivity(): Activity {
        // Gross way of unwrapping the Activity. Taken from 'MediaRouteButton.java'.
        var context = context
        while (context is ContextWrapper) {
            if (context is Activity) {
                return context
            }
            context = context.baseContext
        }
        throw IllegalStateException("The ${this.javaClass.simpleName}'s Context is not an Activity.")
    }
}
