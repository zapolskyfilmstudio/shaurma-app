package com.shaurma.mvp.ui

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

val MoscowZone: ZoneId = ZoneId.of("Europe/Moscow")

fun Long.toMoscowDateTime(): LocalDateTime =
    Instant.ofEpochMilli(this).atZone(MoscowZone).toLocalDateTime()

fun LocalDateTime.toEpochMillisMoscow(): Long =
    atZone(MoscowZone).toInstant().toEpochMilli()

fun minimumRequestedTime(serverNowMillis: Long, maxCookingMinutes: Int): Long {
    val raw = serverNowMillis.toMoscowDateTime().plusMinutes(maxCookingMinutes.toLong())
    val roundedMinute = ((raw.minute + 9) / 10) * 10
    val rounded = raw
        .withSecond(0)
        .withNano(0)
        .withMinute(0)
        .plusHours(if (roundedMinute == 60) 1 else 0)
        .plusMinutes((roundedMinute % 60).toLong())
    return rounded.toEpochMillisMoscow()
}

fun maximumRequestedTime(serverNowMillis: Long): Long {
    val today = serverNowMillis.toMoscowDateTime().toLocalDate()
    return LocalDateTime.of(today.plusDays(3), LocalTime.of(23, 50)).toEpochMillisMoscow()
}

fun endOfSelectedDay(date: LocalDate): Long =
    LocalDateTime.of(date, LocalTime.of(23, 50)).toEpochMillisMoscow()
