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

package com.duckduckgo.feature.toggles.codegen

import com.duckduckgo.anvil.annotations.ContributesPluginPoint
import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.RemoteFeatureStoreNamed
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.Experiment
import com.duckduckgo.feature.toggles.api.Toggle.InternalAlwaysEnabled
import com.squareup.moshi.Moshi
import kotlin.reflect.KClass
import kotlin.reflect.full.functions
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ContributesActivePluginPointCodeGeneratorTest {

    @get:Rule var coroutineRule = CoroutineTestRule()

    private val sharedPreferencesProvider = FakeSharedPreferencesProvider()
    private val moshi = Moshi.Builder().build()

    @Test
    fun `generated plugins have right annotations`() {
        val clazz = Class
            .forName("com.duckduckgo.feature.toggles.codegen.BarActivePlugin_ActivePlugin")
            .kotlin

        assertNotNull(clazz.functions.find { it.name == "isActive" })
        assertTrue(clazz extends MyPlugin::class)

        val priorityAnnotation = clazz.java.getAnnotation(PriorityKey::class.java)!!
        assertNotNull(priorityAnnotation)
        assertEquals(1000, priorityAnnotation.priority)
    }

    @Test
    fun `test generated bar remote features`() {
        val clazz = Class
            .forName("com.duckduckgo.feature.toggles.codegen.BarActivePlugin_ActivePlugin_RemoteFeature")

        assertNotNull(clazz.methods.find { it.name == "self" && it.returnType.kotlin == Toggle::class })
        assertNotNull(clazz.methods.find { it.name == "pluginBarActivePlugin" && it.returnType.kotlin == Toggle::class })

        assertNotNull(
            clazz.kotlin.functions.firstOrNull { it.name == "self" }!!.annotations.firstOrNull { it.annotationClass == Toggle.DefaultValue::class },
        )
        assertNotNull(
            clazz.kotlin.functions.firstOrNull { it.name == "pluginBarActivePlugin" }!!.annotations
                .firstOrNull { it.annotationClass == Toggle.DefaultValue::class },
        )
        assertEquals(
            Toggle.DefaultFeatureValue.TRUE,
            clazz.kotlin.java.methods.find { it.name == "self" }!!.getAnnotation(Toggle.DefaultValue::class.java)!!.defaultValue,
        )
        assertEquals(
            Toggle.DefaultFeatureValue.TRUE,
            clazz.kotlin.java.methods.find { it.name == "pluginBarActivePlugin" }!!.getAnnotation(Toggle.DefaultValue::class.java)!!.defaultValue,
        )

        val featureAnnotation = clazz.kotlin.java.getAnnotation(ContributesRemoteFeature::class.java)!!
        assertEquals(AppScope::class, featureAnnotation.scope)
        assertEquals("pluginPointMyPlugin", featureAnnotation.featureName)
        val expectedClass = Class
            .forName("com.duckduckgo.feature.toggles.codegen.BarActivePlugin_ActivePlugin_RemoteFeature_MultiProcessStore")
        assertEquals(expectedClass.kotlin, featureAnnotation.toggleStore)
    }

    @Test
    fun `test generated foo remote features`() {
        val clazz = Class
            .forName("com.duckduckgo.feature.toggles.codegen.FooActivePlugin_ActivePlugin_RemoteFeature")
        val clazzInternal = Class
            .forName("com.duckduckgo.feature.toggles.codegen.FooActiveInternalPlugin_ActivePlugin_RemoteFeature")

        assertNotNull(clazz.methods.find { it.name == "self" && it.returnType.kotlin == Toggle::class })
        assertNotNull(clazz.methods.find { it.name == "pluginFooActivePlugin" && it.returnType.kotlin == Toggle::class })

        assertNotNull(
            clazz.kotlin.functions.firstOrNull { it.name == "self" }!!.annotations.firstOrNull { it.annotationClass == Toggle.DefaultValue::class },
        )
        assertNotNull(
            clazz.kotlin.functions.firstOrNull { it.name == "pluginFooActivePlugin" }!!.annotations
                .firstOrNull { it.annotationClass == Toggle.DefaultValue::class },
        )
        assertEquals(
            Toggle.DefaultFeatureValue.TRUE,
            clazz.kotlin.java.methods.find { it.name == "self" }!!.getAnnotation(Toggle.DefaultValue::class.java)!!.defaultValue,
        )
        assertEquals(
            Toggle.DefaultFeatureValue.FALSE,
            clazz.kotlin.java.methods.find { it.name == "pluginFooActivePlugin" }!!.getAnnotation(Toggle.DefaultValue::class.java)!!.defaultValue,
        )
        assertEquals(
            Toggle.DefaultFeatureValue.INTERNAL,
            clazzInternal.kotlin.java.methods.find {
                it.name == "pluginFooActiveInternalPlugin"
            }!!.getAnnotation(Toggle.DefaultValue::class.java)!!.defaultValue,
        )

        val featureAnnotation = clazz.kotlin.java.getAnnotation(ContributesRemoteFeature::class.java)!!
        assertEquals(AppScope::class, featureAnnotation.scope)
        assertEquals("pluginPointMyPlugin", featureAnnotation.featureName)
        val expectedClass = Class
            .forName("com.duckduckgo.feature.toggles.codegen.FooActivePlugin_ActivePlugin_RemoteFeature_MultiProcessStore")
        assertEquals(expectedClass.kotlin, featureAnnotation.toggleStore)
    }

    @Test
    fun `test generated experiment remote features`() {
        val clazz = Class
            .forName("com.duckduckgo.feature.toggles.codegen.ExperimentActivePlugin_ActivePlugin_RemoteFeature")

        assertNotNull(clazz.methods.find { it.name == "self" && it.returnType.kotlin == Toggle::class })
        assertNotNull(clazz.methods.find { it.name == "pluginExperimentActivePlugin" && it.returnType.kotlin == Toggle::class })

        assertNotNull(
            clazz.kotlin.functions.firstOrNull { it.name == "self" }!!.annotations.firstOrNull { it.annotationClass == Toggle.DefaultValue::class },
        )
        assertNull(
            clazz.kotlin.functions.firstOrNull { it.name == "self" }!!.annotations.firstOrNull { it.annotationClass == Toggle.Experiment::class },
        )
        assertNull(
            clazz.kotlin.functions.firstOrNull { it.name == "self" }!!
                .annotations.firstOrNull { it.annotationClass == Toggle.InternalAlwaysEnabled::class },
        )
        assertNotNull(
            clazz.kotlin.functions.firstOrNull { it.name == "pluginExperimentActivePlugin" }!!.annotations
                .firstOrNull { it.annotationClass == Toggle.DefaultValue::class },
        )
        assertNotNull(
            clazz.kotlin.functions.firstOrNull { it.name == "pluginExperimentActivePlugin" }!!.annotations
                .firstOrNull { it.annotationClass == Toggle.Experiment::class },
        )
        assertNull(
            clazz.kotlin.functions.firstOrNull { it.name == "pluginExperimentActivePlugin" }!!.annotations
                .firstOrNull { it.annotationClass == Toggle.InternalAlwaysEnabled::class },
        )
        assertEquals(
            Toggle.DefaultFeatureValue.TRUE,
            clazz.kotlin.java.methods.find { it.name == "self" }!!.getAnnotation(Toggle.DefaultValue::class.java)!!.defaultValue,
        )
        assertEquals(
            Toggle.DefaultFeatureValue.TRUE,
            clazz.kotlin.java.methods.find { it.name == "pluginExperimentActivePlugin" }!!
                .getAnnotation(Toggle.DefaultValue::class.java)!!.defaultValue,
        )

        val featureAnnotation = clazz.kotlin.java.getAnnotation(ContributesRemoteFeature::class.java)!!
        assertEquals(AppScope::class, featureAnnotation.scope)
        assertEquals("pluginPointMyPlugin", featureAnnotation.featureName)
        val expectedClass = Class
            .forName("com.duckduckgo.feature.toggles.codegen.ExperimentActivePlugin_ActivePlugin_RemoteFeature_MultiProcessStore")
        assertEquals(expectedClass.kotlin, featureAnnotation.toggleStore)
    }

    @Test
    fun `test generated internal-always-enabled remote features`() {
        val clazz = Class
            .forName("com.duckduckgo.feature.toggles.codegen.InternalAlwaysEnabledActivePlugin_ActivePlugin_RemoteFeature")

        assertNotNull(clazz.methods.find { it.name == "self" && it.returnType.kotlin == Toggle::class })
        assertNotNull(clazz.methods.find { it.name == "pluginInternalAlwaysEnabledActivePlugin" && it.returnType.kotlin == Toggle::class })

        assertNotNull(
            clazz.kotlin.functions.firstOrNull { it.name == "self" }!!.annotations.firstOrNull { it.annotationClass == Toggle.DefaultValue::class },
        )
        assertNull(
            clazz.kotlin.functions.firstOrNull { it.name == "self" }!!.annotations.firstOrNull { it.annotationClass == Toggle.Experiment::class },
        )
        assertNull(
            clazz.kotlin.functions.firstOrNull { it.name == "self" }!!
                .annotations.firstOrNull { it.annotationClass == Toggle.InternalAlwaysEnabled::class },
        )
        assertNotNull(
            clazz.kotlin.functions.firstOrNull { it.name == "pluginInternalAlwaysEnabledActivePlugin" }!!.annotations
                .firstOrNull { it.annotationClass == Toggle.DefaultValue::class },
        )
        assertNull(
            clazz.kotlin.functions.firstOrNull { it.name == "pluginInternalAlwaysEnabledActivePlugin" }!!.annotations
                .firstOrNull { it.annotationClass == Toggle.Experiment::class },
        )
        assertNotNull(
            clazz.kotlin.functions.firstOrNull { it.name == "pluginInternalAlwaysEnabledActivePlugin" }!!.annotations
                .firstOrNull { it.annotationClass == Toggle.InternalAlwaysEnabled::class },
        )
        assertEquals(
            Toggle.DefaultFeatureValue.TRUE,
            clazz.kotlin.java.methods.find { it.name == "self" }!!.getAnnotation(Toggle.DefaultValue::class.java)!!.defaultValue,
        )
        assertEquals(
            Toggle.DefaultFeatureValue.TRUE,
            clazz.kotlin.java.methods.find { it.name == "pluginInternalAlwaysEnabledActivePlugin" }!!
                .getAnnotation(Toggle.DefaultValue::class.java)!!.defaultValue,
        )

        val featureAnnotation = clazz.kotlin.java.getAnnotation(ContributesRemoteFeature::class.java)!!
        assertEquals(AppScope::class, featureAnnotation.scope)
        assertEquals("pluginPointMyPlugin", featureAnnotation.featureName)
        val expectedClass = Class
            .forName("com.duckduckgo.feature.toggles.codegen.InternalAlwaysEnabledActivePlugin_ActivePlugin_RemoteFeature_MultiProcessStore")
        assertEquals(expectedClass.kotlin, featureAnnotation.toggleStore)
    }

    @Test
    fun `test generated plugin point`() {
        val clazz = Class
            .forName("com.duckduckgo.feature.toggles.codegen.Trigger_MyPlugin_ActivePluginPoint")

        val featureAnnotation = clazz.kotlin.java.getAnnotation(ContributesPluginPoint::class.java)!!
        assertEquals(AppScope::class, featureAnnotation.scope)
        assertEquals(MyPlugin::class, featureAnnotation.boundType)
    }

    @Test
    fun `test generated plugin point remote feature`() {
        val clazz = Class
            .forName("com.duckduckgo.feature.toggles.codegen.MyPlugin_ActivePluginPoint_RemoteFeature")

        assertNotNull(clazz.methods.find { it.name == "self" && it.returnType.kotlin == Toggle::class })

        assertNotNull(
            clazz.kotlin.functions.firstOrNull { it.name == "self" }!!.annotations.firstOrNull { it.annotationClass == Toggle.DefaultValue::class },
        )
        assertEquals(
            Toggle.DefaultFeatureValue.TRUE,
            clazz.kotlin.java.methods.find { it.name == "self" }!!.getAnnotation(Toggle.DefaultValue::class.java)!!.defaultValue,
        )

        val featureAnnotation = clazz.kotlin.java.getAnnotation(ContributesRemoteFeature::class.java)!!
        assertEquals(AppScope::class, featureAnnotation.scope)
        assertEquals("pluginPointMyPlugin", featureAnnotation.featureName)
        val expectedClass = Class
            .forName("com.duckduckgo.feature.toggles.codegen.MyPlugin_ActivePluginPoint_RemoteFeature_MultiProcessStore")
        assertEquals(expectedClass.kotlin, featureAnnotation.toggleStore)
    }

    @Test
    fun `test generated triggered plugin point`() {
        val clazz = Class
            .forName("com.duckduckgo.feature.toggles.codegen.Trigger_TriggeredMyPluginTrigger_ActivePluginPoint")

        val featureAnnotation = clazz.kotlin.java.getAnnotation(ContributesPluginPoint::class.java)!!
        assertEquals(AppScope::class, featureAnnotation.scope)
        assertEquals(TriggeredMyPlugin::class, featureAnnotation.boundType)
    }

    @Test
    fun `test generated triggered plugin point remote feature`() {
        val clazz = Class
            .forName("com.duckduckgo.feature.toggles.codegen.TriggeredMyPluginTrigger_ActivePluginPoint_RemoteFeature")

        assertNotNull(clazz.methods.find { it.name == "self" && it.returnType.kotlin == Toggle::class })

        assertNotNull(
            clazz.kotlin.functions.firstOrNull { it.name == "self" }!!.annotations.firstOrNull { it.annotationClass == Toggle.DefaultValue::class },
        )
        assertEquals(
            Toggle.DefaultFeatureValue.TRUE,
            clazz.kotlin.java.methods.find { it.name == "self" }!!.getAnnotation(Toggle.DefaultValue::class.java)!!.defaultValue,
        )

        val featureAnnotation = clazz.kotlin.java.getAnnotation(ContributesRemoteFeature::class.java)!!
        assertEquals(AppScope::class, featureAnnotation.scope)
        assertEquals("pluginPointTriggeredMyPlugin", featureAnnotation.featureName)
    }

    @Test
    fun `test generated triggered foo remote features`() {
        val clazz = Class
            .forName("com.duckduckgo.feature.toggles.codegen.FooActiveTriggeredMyPlugin_ActivePlugin_RemoteFeature")

        assertNotNull(clazz.methods.find { it.name == "self" && it.returnType.kotlin == Toggle::class })
        assertNotNull(clazz.methods.find { it.name == "pluginFooActiveTriggeredMyPlugin" && it.returnType.kotlin == Toggle::class })

        assertNotNull(
            clazz.kotlin.functions.firstOrNull { it.name == "self" }!!.annotations.firstOrNull { it.annotationClass == Toggle.DefaultValue::class },
        )
        assertNotNull(
            clazz.kotlin.functions.firstOrNull { it.name == "pluginFooActiveTriggeredMyPlugin" }!!.annotations
                .firstOrNull { it.annotationClass == Toggle.DefaultValue::class },
        )
        assertEquals(
            Toggle.DefaultFeatureValue.TRUE,
            clazz.kotlin.java.methods.find { it.name == "self" }!!.getAnnotation(Toggle.DefaultValue::class.java)!!.defaultValue,
        )
        assertEquals(
            Toggle.DefaultFeatureValue.FALSE,
            clazz.kotlin.java.methods.find { it.name == "pluginFooActiveTriggeredMyPlugin" }!!
                .getAnnotation(Toggle.DefaultValue::class.java)!!.defaultValue,
        )

        val featureAnnotation = clazz.kotlin.java.getAnnotation(ContributesRemoteFeature::class.java)!!
        assertEquals(AppScope::class, featureAnnotation.scope)
        assertEquals("pluginPointTriggeredMyPlugin", featureAnnotation.featureName)
        val expectedClass = Class
            .forName("com.duckduckgo.feature.toggles.codegen.FooActiveTriggeredMyPlugin_ActivePlugin_RemoteFeature_MultiProcessStore")
        assertEquals(expectedClass.kotlin, featureAnnotation.toggleStore)
    }

    @Test
    fun `test generated plugin multiprocess toggle store`() {
        val clazz = Class
            .forName("com.duckduckgo.feature.toggles.codegen.FooActiveTriggeredMyPlugin_ActivePlugin_RemoteFeature_MultiProcessStore")

        val getMethod = clazz.methods.find { it.name == "get" }!!
        assertEquals(Toggle.State::class, getMethod.returnType.kotlin)
        assertEquals(listOf(String::class.java), getMethod.parameters.map { param -> param.type }.toList())

        val setMethod = clazz.methods.find { it.name == "set" }!!
        assertEquals(Void::class, setMethod.returnType.kotlin)
        assertEquals(listOf(String::class.java, Toggle.State::class.java), setMethod.parameters.map { param -> param.type }.toList())

        assertTrue(clazz.kotlin extends Toggle.Store::class)

        val remoteStoreAnnotation = clazz.kotlin.java.getAnnotation(RemoteFeatureStoreNamed::class.java)!!
        val expectedClass = Class
            .forName("com.duckduckgo.feature.toggles.codegen.FooActiveTriggeredMyPlugin_ActivePlugin_RemoteFeature")
        assertEquals(expectedClass.kotlin, remoteStoreAnnotation.value)
    }

    @Test
    fun `test behavior plugin multiprocess toggle store`() {
        val instance = "com.duckduckgo.feature.toggles.codegen.FooActiveTriggeredMyPlugin_ActivePlugin_RemoteFeature_MultiProcessStore"
            .createClassForName() as Toggle.Store

        instance.set("foo", Toggle.State(enable = false))
        assertEquals(Toggle.State(enable = false), instance.get("foo"))
    }

    @Test
    fun `test generated plugin point multiprocess toggle store`() {
        val clazz = Class
            .forName("com.duckduckgo.feature.toggles.codegen.MyPlugin_ActivePluginPoint_RemoteFeature_MultiProcessStore")

        val getMethod = clazz.methods.find { it.name == "get" }!!
        assertEquals(Toggle.State::class, getMethod.returnType.kotlin)
        assertEquals(listOf(String::class.java), getMethod.parameters.map { param -> param.type }.toList())

        val setMethod = clazz.methods.find { it.name == "set" }!!
        assertEquals(Void::class, setMethod.returnType.kotlin)
        assertEquals(listOf(String::class.java, Toggle.State::class.java), setMethod.parameters.map { param -> param.type }.toList())

        assertTrue(clazz.kotlin extends Toggle.Store::class)

        val remoteStoreAnnotation = clazz.kotlin.java.getAnnotation(RemoteFeatureStoreNamed::class.java)!!
        val expectedClass = Class
            .forName("com.duckduckgo.feature.toggles.codegen.MyPlugin_ActivePluginPoint_RemoteFeature")
        assertEquals(expectedClass.kotlin, remoteStoreAnnotation.value)
    }

    @Test
    fun `test behavior plugin point multiprocess toggle store`() {
        val instance = "com.duckduckgo.feature.toggles.codegen.MyPlugin_ActivePluginPoint_RemoteFeature_MultiProcessStore"
            .createClassForName() as Toggle.Store

        instance.set("foo", Toggle.State(enable = false))
        assertEquals(Toggle.State(enable = false), instance.get("foo"))
    }

    private infix fun KClass<*>.extends(other: KClass<*>): Boolean =
        other.java.isAssignableFrom(this.java)

    private fun String.createClassForName(): Any {
        return Class.forName(this)
            .getConstructor(
                CoroutineScope::class.java,
                DispatcherProvider::class.java,
                SharedPreferencesProvider::class.java,
                Moshi::class.java,
            ).newInstance(
                coroutineRule.testScope,
                coroutineRule.testDispatcherProvider,
                sharedPreferencesProvider,
                moshi,
            )
    }
}
