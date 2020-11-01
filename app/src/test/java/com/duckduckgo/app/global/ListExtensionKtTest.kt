/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.global

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ListExtensionKtTest {

    @ParameterizedTest(name = "When list ({0}) then expected: ({1})")
    @MethodSource("provideTestArguments")
    fun `Should filter blank items from string lists`(
        testList: List<String>,
        expected: List<String>
    ) {
        assertThat(testList.filterBlankItems()).hasSameElementsAs(expected)
    }

    private fun provideTestArguments(): Stream<Arguments> = Stream.of(
        Arguments.of(listOf(""), emptyList<String>()),
        Arguments.of(listOf(" "), emptyList<String>()),
        Arguments.of(listOf("foo.bar"), listOf("foo.bar")),
        Arguments.of(listOf("foo.bar", "ddg.com"), listOf("foo.bar", "ddg.com")),
        Arguments.of(listOf("foo.bar", ""), listOf("foo.bar")),
        Arguments.of(listOf("foo.bar", " "), listOf("foo.bar")),
        Arguments.of(listOf("", "foo.bar"), listOf("foo.bar")),
        Arguments.of(listOf(" ", "foo.bar"), listOf("foo.bar")),
        Arguments.of(listOf("foo.bar", "ddg.com"), listOf("foo.bar", "ddg.com")),
        Arguments.of(listOf("foo.bar", "", "ddg.com"), listOf("foo.bar", "ddg.com")),
        Arguments.of(listOf("foo.bar", " ", "ddg.com"), listOf("foo.bar", "ddg.com")),
        Arguments.of(listOf("foo.bar", "ddg.com", " "), listOf("foo.bar", "ddg.com")),
        Arguments.of(listOf("foo.bar", "ddg.com", ""), listOf("foo.bar", "ddg.com")),
        Arguments.of(listOf("foo.bar", "ddg.com"), listOf("foo.bar", "ddg.com")),
        Arguments.of(listOf("", "foo.bar", "ddg.com"), listOf("foo.bar", "ddg.com")),
        Arguments.of(listOf(" ", "foo.bar", "ddg.com"), listOf("foo.bar", "ddg.com"))
    )
}