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

package com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.duckduckgo.mobile.android.vpn.R

enum class TrackingSignal(
    val signaltag: String,
    @StringRes val signalDisplayName: Int,
    @DrawableRes val signalIcon: Int
) {
    AAID("AAID", R.string.atp_TrackingSignalAAID, R.drawable.ic_signal_advertising_id),
    DEVICE_ID("device_id", R.string.atp_TrackingSignalUniqueIdentifier, R.drawable.ic_signal_advertising_id),
    FB_PERSISTENT_ID("fb_persistent_id", R.string.atp_TrackingSignalUniqueIdentifier, R.drawable.ic_signal_advertising_id),
    UUID("uuid", R.string.atp_TrackingSignalUniqueIdentifier, R.drawable.ic_signal_advertising_id),
    DEVICE_FINGERPRINTING_ID("device_fingerprint_id", R.string.atp_TrackingSignalUniqueIdentifier, R.drawable.ic_signal_advertising_id),
    SET_COOKIE("set_cookie", R.string.atp_TrackingSignalCookies, R.drawable.ic_signal_cookie),
    GET_COOKIE("get_cookie", R.string.atp_TrackingSignalCookies, R.drawable.ic_signal_cookie),
    OS_VERSION("os_version", R.string.atp_TrackingSignalOsVersion, R.drawable.ic_signal_os),
    DEVICE_SDK_DATA("device_sdk_data", R.string.atp_TrackingSignalOsVersion, R.drawable.ic_signal_os),
    OS_BUILD_VERSION("platform", R.string.atp_TrackingSignalOsBuildNumber, R.drawable.ic_signal_os),
    PLATFORM("os_version", R.string.atp_TrackingSignalOsVersion, R.drawable.ic_signal_os),
    DEVICE_MAKE("device_make", R.string.atp_TrackingSignalDeviceBrand, R.drawable.ic_signal_device),
    DEVICE_MODEL("device_model", R.string.atp_TrackingSignalDeviceModel, R.drawable.ic_signal_device),
    DEVICE_HARDWARE_NAME("device_hardware_name", R.string.atp_TrackingSignalDeviceModel, R.drawable.ic_signal_device),
    DEVICE_CPU_TYPE("device_cpu_type", R.string.atp_TrackingSignalCPUData, R.drawable.ic_signal_device),
    DEVICE_CPU_STATUS("device_resolution", R.string.atp_TrackingSignalCPUData, R.drawable.ic_signal_device),
    DEVICE_RESOLUTION("device_hardware_name", R.string.atp_TrackingSignalScreenResolution, R.drawable.ic_signal_device),
    DEVICE_TOTAL_STORAGE("device_total_storage", R.string.atp_TrackingSignalInternalStorage, R.drawable.ic_signal_storage),
    EXTERNAL_TOTAL_STORAGE("external_total_storage", R.string.atp_TrackingSignalExternalStorage, R.drawable.ic_signal_storage),
    DEVIC_TOTAL_MEMORY("device_total_memory", R.string.atp_TrackingSignalDeviceMemory, R.drawable.ic_signal_storage),
    DEVICE_NAME("device_name", R.string.atp_TrackingSignalDeviceName, R.drawable.ic_signal_settings),
    WIFI_SSID("wifi_ssid", R.string.atp_TrackingSignalWifiNetworkName, R.drawable.ic_signal_wifi),
    APP_NAME("app_name", R.string.atp_TrackingSignalAppName, R.drawable.ic_signal_app),
    APP_VERSION("app_version", R.string.atp_TrackingSignalAppVersion, R.drawable.ic_signal_app),
    LOCAL_IP("local_ip", R.string.atp_TrackingSignalLocalIPAddress, R.drawable.ic_signal_wifi),
    NETWORK_ISP("network_isp", R.string.atp_TrackingSignalISP, R.drawable.ic_signal_identifiers),
    DEVICE_BOOT_TIME("device_boot_time", R.string.atp_TrackingSignalDeviceBootTime, R.drawable.ic_signal_time),
    DEVICE_CONNECTIVITY("device_connectivity", R.string.atp_TrackingSignalNetworkConnectionType, R.drawable.ic_signal_settings),
    DEVICE_VOLUME("device_volume", R.string.atp_TrackingSignalSystemVolume, R.drawable.ic_signal_volume),
    DEVICE_BATTERY_LEVEL("device_battery_level", R.string.atp_TrackingSignalBatteryLevel, R.drawable.ic_signal_battery),
    DEVICE_CHARGING_STATUS("local_ip", R.string.atp_TrackingSignalChargingStatus, R.drawable.ic_signal_battery),
    SCREEN_BRIGHTNESS("device_brightness", R.string.atp_TrackingSignalBrightness, R.drawable.ic_signal_brightness),
    DEVICE_HEADPHONES_STATUS("device_headphones_status", R.string.atp_TrackingSignalHeadphoneStatus, R.drawable.ic_signal_volume),
    ACCELEROMETER_DATA("accelerometer_data", R.string.atp_TrackingSignalAccelerometerData, R.drawable.ic_signal_sensor),
    ROTATION_DATA("roration_data", R.string.atp_TrackingSignalRotationData, R.drawable.ic_signal_sensor),
    DEVICE_ORIENTATION("device_orientation", R.string.atp_TrackingSignalDeviceOrientation, R.drawable.ic_signal_device),
    DEVICE_MAGNOMETER("device_magnometer", R.string.atp_TrackingSignalMagnetometerData, R.drawable.ic_signal_sensor),
    DEVICE_FREE_STORAGE("device_free_storage", R.string.atp_TrackingSignalAvailableInternalStorage, R.drawable.ic_signal_storage),
    EXTERNAL_FREE_STORAGE("external_free_storage", R.string.atp_TrackingSignalAvailableExternalStorage, R.drawable.ic_signal_storage),
    DEVICE_FREE_MEMORY("device_free_memory", R.string.atp_TrackingSignalAvailableDeviceMemory, R.drawable.ic_signal_storage),
    DEVICE_SCREEN_MARGINS("device_screen_margins", R.string.atp_TrackingSignalScreenMargins, R.drawable.ic_signal_device),
    DEVICE_SCREEN_DENSITY("device_screen_density", R.string.atp_TrackingSignalScreenDensity, R.drawable.ic_signal_device),
    DEVICE_FONT_SIZE("device_font_size", R.string.atp_TrackingSignalFontSize, R.drawable.ic_signal_settings),
    NETWORK_CARRIER("network_carrier", R.string.atp_TrackingSignalNetworkCarrier, R.drawable.ic_signal_identifiers),
    INSTALL_DATE("install_date", R.string.atp_TrackingSignalAppInstallDate, R.drawable.ic_signal_time),
    FIRST_LAUNCH_DATE("first_launch_date", R.string.atp_TrackingSignalFirstAppLaunchDate, R.drawable.ic_signal_time),
    GPS_COORDINATES("gps_coordinates", R.string.atp_TrackingSignalGPSCoordinates, R.drawable.ic_signal_gps),
    POSTAL_CODE("postal_code", R.string.atp_TrackingSignalPostalCode, R.drawable.ic_signal_pin),
    NEIGHBOURHOOD("neighbourhood", R.string.atp_TrackingSignalNeighbourhood, R.drawable.ic_signal_pin),
    CITY("city", R.string.atp_TrackingSignalCity, R.drawable.ic_signal_pin),
    STATE("state", R.string.atp_TrackingSignalState, R.drawable.ic_signal_pin),
    COUNTRY("country", R.string.atp_TrackingSignalCountry, R.drawable.ic_signal_pin),
    TIMEZONE("timezone", R.string.atp_TrackingSignalTimezone, R.drawable.ic_signal_pin),
    EMAIL_ADDRESS("email_address", R.string.atp_TrackingSignalEmailAddress, R.drawable.ic_signal_person),
    BIRTHDAY("birthday", R.string.atp_TrackingSignalBirthday, R.drawable.ic_signal_person),
    GENDER("gender", R.string.atp_TrackingSignalGender, R.drawable.ic_signal_person),
    FIRST_NAME("first_name", R.string.atp_TrackingSignalFirstName, R.drawable.ic_signal_person),
    LAST_NAME("last_name", R.string.atp_TrackingSignalLastName, R.drawable.ic_signal_person);

    companion object {
        fun fromTag(signalTag: String): TrackingSignal {
            return valueOf(signalTag.uppercase())
        }
    }
}
