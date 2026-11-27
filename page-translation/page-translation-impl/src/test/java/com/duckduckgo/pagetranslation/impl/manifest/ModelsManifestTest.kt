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

package com.duckduckgo.pagetranslation.impl.manifest

import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifest.ModelFile
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsManifestTest {
    @Test
    fun nameAnInstalledFileAfterItsKeyWithTheFormatExtensionOfItsPath() {
        assertEquals("model.bin", fileAt("es-en/3.0/base-memory/model.esen.intgemm.alphas.bin.zst").getInstalledFileName("model"))
        assertEquals("vocab.spm", fileAt("es-en/3.0/base-memory/vocab.esen.spm.zst").getInstalledFileName("vocab"))
        assertEquals("model.npz", fileAt("es-en/model.npz.zst").getInstalledFileName("model"))
    }

    @Test
    fun nameAnInstalledFileAfterItsKeyWhenItsPathHasNoFormatExtension() {
        assertEquals("model", fileAt("es-en/model.zst").getInstalledFileName("model"))
        assertEquals("model", fileAt("es.en/model").getInstalledFileName("model"))
    }

    private fun fileAt(path: String) = ModelFile(path = path, bytes = 1, sha256 = "a", decompressedSize = 1, decompressedHash = "b")
}
