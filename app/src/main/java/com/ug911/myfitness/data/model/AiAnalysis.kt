package com.ug911.myfitness.data.model

import java.time.LocalDate

/**
 * One run of "Review my week": the exact snapshot that was sent, and the three
 * outputs that came back. Storing [inputSnapshot] is what makes a review reproducible.
 */
data class AiAnalysis(
    val id: Long = 0,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val inputSnapshot: String,
    val review: String,
    val insights: List<String>,
    val planRationale: String?,
    val provider: String,
    val model: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
)
