package com.duckduckgo.app.kahftube

import android.content.Context
import android.content.SharedPreferences

/**
 * Created by Asif Ahmed on 17/1/24.
 */

open class SharedPreferenceManager(private val context: Context) {

    private var mSettings: SharedPreferences = context.getSharedPreferences(
        KeyString.PREFERENCE_NAME,
        Context.MODE_PRIVATE,
    )
    private val mEditor: SharedPreferences.Editor? = mSettings.edit()

    private val defaultValue: String = ""

    /***
     * Set a value for the key
     */
    fun setValue(
        key: String?,
        value: String?
    ) {
        mEditor!!.putString(key, value)
        mEditor!!.commit()
    }

    /***
     * Set a value for the key
     */
    fun setValue(
        key: String?,
        value: Int
    ) {
        mEditor!!.putInt(key, value)
        mEditor!!.commit()
    }

    /***
     * Set a value for the key
     */
    fun setValue(
        key: String?,
        value: Double
    ) {
        setValue(key, value.toString())
    }

    /***
     * Set a value for the key
     */
    fun setValue(
        key: String?,
        value: Long
    ) {
        mEditor!!.putLong(key, value)
        mEditor!!.commit()
    }

    /****
     * Gets the value from the settings stored natively on the device.
     *
     * @param defaultValue Default value for the key, if one is not found.
     */
    fun getValue(key: String?): String {
        return mSettings.getString(key, "").toString()
    }

    fun getIntValue(
        key: String?,
        defValue: Int = -1
    ): Int {
        return mSettings.getInt(key, defValue)
    }

    fun getLongValue(key: String?): Long {
        return mSettings.getLong(key, -1L)
    }

    /****
     * Gets the value from the preferences stored natively on the device.
     *
     * @param defValue Default value for the key, if one is not found.
     */
    fun getValue(
        key: String?,
        defValue: Boolean
    ): Boolean {
        return mSettings.getBoolean(key, defValue)
    }

    fun setValue(
        key: String?,
        value: Boolean
    ) {
        mEditor!!.putBoolean(key, value)
        mEditor!!.commit()
    }

    /**
     * Clear all the preferences store in this [android.content.SharedPreferences.Editor]
     */
    fun clear(): Boolean {
        return try {
            mEditor!!.clear().commit()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Removes preference entry for the given key.
     *
     * @param key
     */
    fun removeValue(key: String?) {
        if (mEditor != null) {
            mEditor!!.remove(key).commit()
        }
    }

    class KeyString {
        companion object {
            const val PREFERENCE_NAME = "KAHF_TUBE"
            const val NAME = "NAME"
            const val EMAIL = "EMAIL"
            const val IMAGE_SRC = "IMAGE_SRC"
            const val GENDER = "GENDER"
            const val PRACTICING_LEVEL = "PRACTICING_LEVEL"
            const val TOKEN = "TOKEN"
        }
    }
}
