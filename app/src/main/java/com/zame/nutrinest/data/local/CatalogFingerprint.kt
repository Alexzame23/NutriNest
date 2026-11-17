package com.zame.nutrinest.data.local

import java.security.MessageDigest

/** sqlite-typed-v1: UTF-8 byte-length-prefixed tokens; doubles retain their exact IEEE bits. */
internal object CatalogFingerprint {
    fun token(value: String): String = "${value.toByteArray(Charsets.UTF_8).size}:$value"
    fun real(value: Double): String = java.lang.Long.toHexString(value.toRawBits()).padStart(16, '0')
    fun sha256(value: String): String = hex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))
    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
