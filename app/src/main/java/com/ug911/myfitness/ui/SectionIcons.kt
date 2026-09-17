package com.ug911.myfitness.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import com.ug911.myfitness.data.model.DaySection

/**
 * An icon per section. Paired with the section's name everywhere it appears, so the
 * accent colour never has to carry the meaning by itself.
 */
val DaySection.icon: ImageVector
    get() = when (this) {
        DaySection.MORNING -> Icons.Filled.FitnessCenter
        DaySection.SCHOOL_RUN -> Icons.Filled.DirectionsCar
        DaySection.BREAKFAST -> Icons.Filled.FreeBreakfast
        DaySection.OFFICE -> Icons.Filled.Work
        DaySection.EVENING -> Icons.Filled.Home
        DaySection.NIGHT -> Icons.Filled.Bedtime
        DaySection.BODY -> Icons.Filled.MonitorHeart
    }
