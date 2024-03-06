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

package com.duckduckgo.verifiedinstallation

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.verifiedinstallation.apppackage.VerificationCheckAppPackage
import com.duckduckgo.verifiedinstallation.buildtype.VerificationCheckBuildType
import com.duckduckgo.verifiedinstallation.certificate.VerificationCheckBuildCertificate
import com.duckduckgo.verifiedinstallation.installsource.VerificationCheckPlayStoreInstall
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class IsVerifiedPlayStoreInstallImpl @Inject constructor(
    private val packageChecker: VerificationCheckAppPackage,
    private val buildTypeChecker: VerificationCheckBuildType,
    private val certificateChecker: VerificationCheckBuildCertificate,
    private val playStoreInstallChecker: VerificationCheckPlayStoreInstall,
) : IsVerifiedPlayStoreInstall {

    // cached in memory since none of the checks can change while the app is running
    private val isVerifiedInstall: Boolean by lazy {
        buildTypeChecker.isPlayReleaseBuild() &&
            packageChecker.isProductionPackage() &&
            playStoreInstallChecker.installedFromPlayStore() &&
            certificateChecker.builtWithVerifiedCertificate()
    }

    override fun invoke(): Boolean {
        return isVerifiedInstall
    }
}
