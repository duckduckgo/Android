/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.pir.impl.common.actions

import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.ScanStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions.ScanStepActions
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.scripts.models.BrokerAction

internal fun testBroker(name: String = "test-broker"): Broker = Broker(
    name = name,
    fileName = "$name.json",
    url = "https://$name.com",
    version = "1.0",
    parent = null,
    addedDatetime = 1000L,
    removedAt = 0L,
)

internal fun testScanStep(
    brokerName: String = "test-broker",
    actions: List<BrokerAction> = emptyList(),
): ScanStep = ScanStep(
    broker = testBroker(brokerName),
    step = ScanStepActions(
        stepType = "scan",
        actions = actions,
        scanType = "data",
    ),
)
