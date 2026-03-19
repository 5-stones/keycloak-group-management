package com.weare5stones.keycloak.groupmgmt.util

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun Long.toIsoString(): String =
    Instant.ofEpochMilli(this).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
