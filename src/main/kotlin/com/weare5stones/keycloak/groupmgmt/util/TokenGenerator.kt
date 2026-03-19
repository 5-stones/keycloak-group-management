package com.weare5stones.keycloak.groupmgmt.util

import java.security.SecureRandom
import java.util.Base64

object TokenGenerator {

    private val secureRandom = SecureRandom()

    fun generateToken(): String {
        val bytes = ByteArray(48)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
