package com.groovsta.content.dto

import java.util.Locale

fun Long.toDisplayCount(): String {
    val unit = when {
        this >= CRORE -> CountUnit(CRORE, "Cr")
        this >= LAKH -> CountUnit(LAKH, "L")
        this >= THOUSAND -> CountUnit(THOUSAND, "K")
        else -> return toString()
    }
    val scaled = this.toDouble() / unit.value
    return String.format(Locale.US, "%.1f", scaled)
        .removeSuffix(".0") + unit.suffix
}

private data class CountUnit(val value: Long, val suffix: String)

private const val THOUSAND = 1_000L
private const val LAKH = 100_000L
private const val CRORE = 10_000_000L
