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

package com.duckduckgo.autofill.impl.service

import android.app.assist.AssistStructure.ViewNode
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.impl.service.AutofillFieldType.PASSWORD
import com.duckduckgo.autofill.impl.service.AutofillFieldType.UNKNOWN
import com.duckduckgo.autofill.impl.service.AutofillFieldType.USERNAME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class RealAutofillParserTest {

    private val appBuildConfig: AppBuildConfig = mock()
    private val viewNodeClassifier: ViewNodeClassifier = mock<ViewNodeClassifier>().also {
        whenever(it.classify(any())).thenReturn(UNKNOWN)
    }

    private val testee = RealAutofillParser(appBuildConfig, viewNodeClassifier)

    @Test
    fun whenZeroWindowNodesThenReturnEmptyList() {
        val assistStructure = assistStructure()
            .windowNodes()
        val nodes = testee.parseStructure(assistStructure)
        assertTrue(nodes.isEmpty())
    }

    @Test
    fun whenWindowNodeHasNoRootNodeThenReturnEmptyList() {
        val assistStructure = assistStructure()
            .windowNodes(windowNode())
        val nodes = testee.parseStructure(assistStructure)
        assertTrue(nodes.isEmpty())
    }

    @Test
    fun whenMultipleRootNodesThenReturnMultipleParsedRoots() {
        val assistStructure = assistStructure()
            .windowNodes(
                windowNode().rootViewNode(viewNode()),
                windowNode().rootViewNode(viewNode()),
            )
        val nodes = testee.parseStructure(assistStructure)
        assertEquals(2, nodes.size)
    }

    @Test
    fun whenViewNodeWithoutAutofillIdThenSkipItAtAnyLevel() {
        val assistStructure = assistStructure()
            .windowNodes(
                windowNode().rootViewNode(viewNode()),
                windowNode().rootViewNode(
                    viewNode().childrenNodes(viewNode(), viewNode()),
                ),
            )
        val nodes = testee.parseStructure(assistStructure)
        assertEquals(2, nodes.size)
        nodes.forEach {
            assertEquals(0, it.parsedAutofillFields.size)
        }
    }

    @Test
    fun whenRootNodeHasChildNodesThenParseValidOnes() {
        val assistStructure = assistStructure().windowNodes(
            windowNode().rootViewNode(
                viewNode()
                    .autofillId(autofillId())
                    .childrenNodes(
                        viewNode().autofillId(autofillId()),
                        viewNode().autofillId(autofillId()),
                        viewNode(), // this node should be skipped
                    ),
            ),
        )

        val nodes = testee.parseStructure(assistStructure)

        assertEquals(1, nodes.size)
        assertEquals(3, nodes.first().parsedAutofillFields.size)
    }

    // if child nodes have package or domain, returned autofill node should have them
    @Test
    fun whenChildNodesHavePackageOrDomainThenReturnedAutofillRootNodeHasThem() {
        val assistStructure = assistStructure().windowNodes(
            windowNode().rootViewNode(
                viewNode()
                    .autofillId(autofillId())
                    .childrenNodes(
                        viewNode().autofillId(autofillId()).packageId("com.android.package"),
                        viewNode().autofillId(autofillId()).webDomain("example.com"),
                        viewNode(), // this node should be skipped
                    ),
            ),
        )

        val nodes = testee.parseStructure(assistStructure)

        assertEquals(1, nodes.size)
        assertEquals(3, nodes.first().parsedAutofillFields.size)
        assertEquals("com.android.package", nodes.first().packageId)
        assertEquals("http://example.com", nodes.first().website)
    }

    @Test
    fun whenParsingStructureThenAnyValidFieldIsParsedAndClassified() {
        val assistStructure = assistStructure().windowNodes(
            windowNode().rootViewNode(
                viewNode().autofillId(autofillId())
                    .autofillType(USERNAME)
                    .packageId("com.android.package")
                    .childrenNodes(
                        viewNode().autofillId(autofillId())
                            .packageId("com.android.package").autofillType(USERNAME),
                        viewNode().autofillId(autofillId())
                            .webDomain("example.com"),
                        viewNode().autofillId(autofillId())
                            .packageId("com.android.package").autofillType(PASSWORD),
                        viewNode().autofillId(autofillId())
                            .webDomain("example.com"),
                    ),
            ),
            windowNode().rootViewNode(
                viewNode()
                    .autofillId(autofillId())
                    .childrenNodes(
                        viewNode().packageId("com.android.package2").autofillType(USERNAME), // invalid autofill id
                        viewNode().autofillId(autofillId()).autofillType(USERNAME),
                        viewNode().autofillId(autofillId()).webDomain("example2.com"),
                        viewNode().autofillId(autofillId()).packageId("com.android.package2").autofillType(USERNAME),
                        viewNode().autofillId(autofillId()).autofillType(PASSWORD),
                    ),
            ),
        )

        val nodes = testee.parseStructure(assistStructure)

        assertEquals(2, nodes.size)
        val firstNode = nodes[0]
        assertEquals(5, firstNode.parsedAutofillFields.size)
        assertEquals("com.android.package", firstNode.packageId)
        assertEquals("http://example.com", firstNode.website)
        assertEquals(2, firstNode.parsedAutofillFields.filter { it.type == USERNAME }.size)
        assertEquals(1, firstNode.parsedAutofillFields.filter { it.type == PASSWORD }.size)
        assertEquals(2, firstNode.parsedAutofillFields.filter { it.type == UNKNOWN }.size)

        val secondNode = nodes[1]
        assertEquals(5, secondNode.parsedAutofillFields.size)
        assertEquals("com.android.package2", secondNode.packageId)
        assertEquals("http://example2.com", secondNode.website)
        assertEquals(2, secondNode.parsedAutofillFields.filter { it.type == USERNAME }.size)
        assertEquals(1, secondNode.parsedAutofillFields.filter { it.type == PASSWORD }.size)
        assertEquals(2, secondNode.parsedAutofillFields.filter { it.type == UNKNOWN }.size)
    }

    @Test
    fun whenMultiplePackageIdsThenReturnFirstValidOne() {
        val assistStructure = assistStructure().windowNodes(
            windowNode().rootViewNode(
                viewNode().autofillId(autofillId())
                    .packageId("")
                    .childrenNodes(
                        viewNode().autofillId(autofillId())
                            .packageId("android"),
                        viewNode()
                            .packageId("com.android.package"),
                        viewNode().autofillId(autofillId())
                            .packageId("com.android.package"),
                        viewNode().autofillId(autofillId())
                            .packageId("com.android.package2"),
                    ),
            ),
        )

        val nodes = testee.parseStructure(assistStructure)

        assertEquals("com.android.package", nodes.first().packageId)
    }

    // if a node doesn't have a text value, skip it

    private fun ViewNode.autofillType(autofillFieldType: AutofillFieldType): ViewNode {
        whenever(viewNodeClassifier.classify(this)).thenReturn(autofillFieldType)
        return this
    }
}
