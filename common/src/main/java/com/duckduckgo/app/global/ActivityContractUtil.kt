/*
 * Copyright (c) 2021 DuckDuckGo
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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.result.contract.ActivityResultContract
import java.io.Serializable
import java.lang.IllegalArgumentException
import kotlin.reflect.KClass

/**
 * Interface to provide contract for activity companion object.
 * @param A activity which is bound to the contract
 * @param I input params type of contract
 * @param O output result type of contract
 *
 * Used like following inside Activity that uses contracts:
 * ```
 *  companion object : ActivityContractProvider<SomeActivity, InputType, OutputType> by activityContractProvider()
 * ```
 *
 * If the contract has no output, use "Any" (or Unit) class
 * Contract object can be then accessed via: `SomeActivity.contract`
 */
interface ActivityContractProvider<A: Activity, I: Any, O: Any?> {

    val contract: ActivityResultContract<I, O>

    fun Activity.setContractResult(result: O?, resultCode: Int = Activity.RESULT_OK) {
        setResult(resultCode, result?.let { contractResultOf(it) })
    }

    fun Activity.finishWithContractResult(result: O, resultCode: Int = Activity.RESULT_OK) {
        setContractResult(result, resultCode)
        finish()
    }

    fun contractResultOf(result: O) = Intent().apply {
        putToExtra(SimpleActivityResultContract.EXTRA_OUTPUT, result)
    }

    fun start(context: Context, input: I) = context.startActivity(contract.createIntent(context, input))
}

inline fun <reified A: Activity, reified I: Any, reified O: Any> activityContractProvider()
        : ActivityContractProvider<A, I, O?> = ActivityContractProviderImpl(A::class, O::class)

@Suppress("UseDataClass")
class ActivityContractProviderImpl<A: Activity, I: Any, O: Any>(
        private val activityClass: KClass<A>,
        private val outputClass: KClass<O>
): ActivityContractProvider<A, I, O?> {
    override val contract: ActivityResultContract<I, O?>
        get() = SimpleActivityResultContract(activityClass, outputClass)
}

class SimpleActivityResultContract<A: Activity, I: Any, O: Any>(
        private val activityClass: KClass<A>,
        private val outputClass: KClass<O>
): ActivityResultContract<I, O?>() {

    companion object {
        const val EXTRA_INPUT = "contract_input"
        const val EXTRA_OUTPUT = "contract_output"
    }

    override fun createIntent(context: Context, input: I): Intent = contractIntent(context, activityClass, input)
    override fun parseResult(resultCode: Int, intent: Intent?): O? = intent?.getFromExtraForClass(EXTRA_OUTPUT, outputClass)
}
fun <A: Activity> contractIntent(context: Context, activityClass: KClass<A>, input: Any): Intent =
        Intent(context, activityClass.java).apply { putToExtra(SimpleActivityResultContract.EXTRA_INPUT, input) }

fun Intent.putToExtra(name: String, value: Any?) = when (value) {
    null -> putExtra(name, null as Serializable?)
    is Unit -> this
    is Int -> putExtra(name, value)
    is String -> putExtra(name, value)
    is Long -> putExtra(name, value)
    is Boolean -> putExtra(name, value)
    is Bundle -> putExtra(name, value)
    is Parcelable -> putExtra(name, value)
    is Serializable -> putExtra(name, value)
    else -> throw IllegalArgumentException("can't transform and put type ${value.javaClass} into intent")
}

@Suppress("UNCHECKED_CAST") // type-safety guaranteed through reified types in contract delegates
fun <T: Any> Intent.getFromExtraForClass(name: String, klass: KClass<T>): T? = when {
    klass == Int::class -> getIntExtra(name, 0) as T?
    klass == String::class -> getStringExtra(name) as T?
    klass == Long::class -> getLongExtra(name, 0L) as T?
    klass == Boolean::class -> getBooleanExtra(name, false) as T?
    klass == Bundle::class -> getBundleExtra(name) as T?
    Parcelable::class.java.isAssignableFrom(klass.java) -> {
        setExtrasClassLoader(klass.java.classLoader)
        getParcelableExtra(name) as T?
    }
    Serializable::class.java.isAssignableFrom(klass.java) -> getSerializableExtra(name) as T?
    else -> throw IllegalArgumentException("Type $klass is not supported for intent.")
}