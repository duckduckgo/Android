package org.halalz.kahftube.extentions

import android.content.Context
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * Created by Asif Ahmed on 17/1/24.
 */

fun Context.readAssetFile(fileName: String): String {
    val stringBuilder = StringBuilder()
    try {
        val inputStream = this.assets.open(fileName)
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))

        var line: String?
        while (bufferedReader.readLine().also { line = it } != null) {
            stringBuilder.append(line).append('\n')
        }
    } catch (e: IOException) {
        println("Exception is -> ${e.localizedMessage}")
        e.printStackTrace()
    }
    return stringBuilder.toString()
}
