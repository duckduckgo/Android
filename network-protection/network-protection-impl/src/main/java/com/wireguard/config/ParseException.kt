/*
 * Copyright © 2017-2021 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.config

import java.lang.Exception

/**
 *
 */
class ParseException @JvmOverloads constructor(
    val parsingClass: Class<*>,
    val text: CharSequence,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
