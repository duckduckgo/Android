/*
 * Copyright © 2017-2021 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.crypto

import java.lang.Exception

/**
 * An exception thrown when attempting to parse an invalid key (too short, too long, or byte
 * data inappropriate for the format). The format being parsed can be accessed with the
 * [.getFormat] method.
 */
class KeyFormatException internal constructor(val format: Key.Format, val type: Type) :
    Exception() {

    enum class Type {
        CONTENTS, LENGTH
    }
}
