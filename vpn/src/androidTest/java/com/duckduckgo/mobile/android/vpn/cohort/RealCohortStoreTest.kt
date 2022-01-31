// /*
//  * Copyright (c) 2021 DuckDuckGo
//  *
//  * Licensed under the Apache License, Version 2.0 (the "License");
//  * you may not use this file except in compliance with the License.
//  * You may obtain a copy of the License at
//  *
//  *     http://www.apache.org/licenses/LICENSE-2.0
//  *
//  * Unless required by applicable law or agreed to in writing, software
//  * distributed under the License is distributed on an "AS IS" BASIS,
//  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  * See the License for the specific language governing permissions and
//  * limitations under the License.
//  */
//
// package com.duckduckgo.mobile.android.vpn.cohort
//
// import androidx.test.platform.app.InstrumentationRegistry
// import com.jakewharton.threetenabp.AndroidThreeTen
// import kotlinx.coroutines.ExperimentalCoroutinesApi
// import kotlinx.coroutines.test.TestScope
// import org.junit.Assert.*
// import org.junit.Before
// import org.junit.Test
// import org.threeten.bp.LocalDate
//
// @ExperimentalCoroutinesApi
// class RealCohortStoreTest {
//
//     private lateinit var cohortStore: CohortStore
//
//     @Before
//     fun setup() {
//         AndroidThreeTen.init(InstrumentationRegistry.getInstrumentation().targetContext)
//         cohortStore = RealCohortStore(InstrumentationRegistry.getInstrumentation().targetContext)
//         (cohortStore as RealCohortStore).preferences.edit().clear().apply()
//     }
//
//     @Test
//     fun whenCohortNotSetThenReturnNull() {
//         assertNull(cohortStore.getCohortStoredLocalDate())
//     }
//
//     @Test
//     fun whenSetCohortLocalDateThenStoredCorrectly() {
//         val date = LocalDate.now().plusDays(3)
//         cohortStore.setCohortLocalDate(date)
//
//         assertEquals(date, cohortStore.getCohortStoredLocalDate())
//     }
//
//     @Test
//     fun whenInitialCohortFirstCalledThenStoreInitialCohort() {
//         (cohortStore as RealCohortStore).onVpnStarted(TestScope())
//
//         assertEquals(LocalDate.now(), cohortStore.getCohortStoredLocalDate())
//     }
//
//     @Test
//     fun whenInitialCohortSubsequentCalledThenNoop() {
//         val date = LocalDate.now().plusDays(3)
//         cohortStore.setCohortLocalDate(date)
//
//         (cohortStore as RealCohortStore).onVpnStarted(TestScope())
//
//         assertEquals(date, cohortStore.getCohortStoredLocalDate())
//     }
// }
