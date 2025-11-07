/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.subscriptions.internal.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.subscriptions.internal.SubsSettingPlugin
import com.duckduckgo.subscriptions.internal.databinding.SubsOverrideLocalPurchasedAtViewBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

@InjectWith(ViewScope::class)
class OverrideSubscriptionLocalPurchasedAtView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var sharedPreferencesProvider: SharedPreferencesProvider

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    private val binding: SubsOverrideLocalPurchasedAtViewBinding by viewBinding()

    private val localPurchaseAtStore by lazy {
        LocalPurchaseAtStore(sharedPreferencesProvider)
    }

    private val dateETFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("US/Eastern")
    }

    private val viewCoroutineScope: LifecycleCoroutineScope?
        get() = findViewTreeLifecycleOwner()?.lifecycleScope

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.also { base ->
            viewCoroutineScope?.launch(dispatcherProvider.main()) {
                val timestamp = localPurchaseAtStore.localPurchasedAt ?: return@launch
                base.subscriptionEnroll.text = dateETFormat.format(Date(timestamp))
            }

            base.subscriptionEnrollSave.setOnClickListener {
                val date = dateETFormat.parse(binding.subscriptionEnroll.text)
                if (date != null) {
                    localPurchaseAtStore.localPurchasedAt = date.time
                    Toast.makeText(this.context, "Subscription date updated", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this.context, "Invalid date format. Use yyyy-MM-dd", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@ContributesMultibinding(ActivityScope::class)
class OverrideSubscriptionLocalPurchasedAtViewPlugin @Inject constructor() : SubsSettingPlugin {
    override fun getView(context: Context): View {
        return OverrideSubscriptionLocalPurchasedAtView(context)
    }
}

/**
 * Real SubscriptionsDataStore cannot be used directly without going through AuthRepository.
 * This class is intended only for manual testing purposes. It allows overriding the local "purchased at" time.
 */
private class LocalPurchaseAtStore(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) {
    private val encryptedPreferences: SharedPreferences? by lazy { encryptedPreferences() }
    private fun encryptedPreferences(): SharedPreferences? {
        return sharedPreferencesProvider.getEncryptedSharedPreferences(FILENAME, multiprocess = true)
    }

    var localPurchasedAt: Long?
        get() = encryptedPreferences?.getLong(KEY_LOCAL_PURCHASED_AT, 0L).takeIf { it != 0L }
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                if (value == null) {
                    remove(KEY_LOCAL_PURCHASED_AT)
                } else {
                    putLong(KEY_LOCAL_PURCHASED_AT, value)
                }
            }
        }

    companion object {
        const val FILENAME = "com.duckduckgo.subscriptions.store"
        const val KEY_LOCAL_PURCHASED_AT = "KEY_LOCAL_PURCHASED_AT"
    }
}
