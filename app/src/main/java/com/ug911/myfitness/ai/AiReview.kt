package com.ug911.myfitness.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The three outputs a weekly review must produce, as returned by the model. */
@Serializable
data class AiReviewPayload(
    val review: String = "",
    val insights: List<String> = emptyList(),
    val plan: AiPlanPayload? = null,
)

@Serializable
data class AiPlanPayload(
    val rationale: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val targets: List<AiTargetPayload> = emptyList(),
)

@Serializable
data class AiTargetPayload(
    /** Tracker name as it appears in the snapshot; matched leniently on the way in. */
    val tracker: String,
    @SerialName("target_frequency") val targetFrequency: Int? = null,
    @SerialName("target_value") val targetValue: Double? = null,
    val note: String? = null,
)
