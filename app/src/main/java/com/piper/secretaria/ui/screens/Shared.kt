package com.piper.secretaria.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.piper.secretaria.data.EventType
import com.piper.secretaria.ui.theme.PiperColors

fun EventType.icon(): ImageVector = when (this) {
    EventType.MEETING -> Icons.Default.Videocam
    EventType.CALL -> Icons.Default.Call
    EventType.TASK -> Icons.Default.Work
    EventType.PERSONAL -> Icons.Default.Person
}

fun EventType.color(): Color = when (this) {
    EventType.MEETING -> PiperColors.Purple
    EventType.CALL -> PiperColors.Green
    EventType.TASK -> PiperColors.Orange
    EventType.PERSONAL -> PiperColors.Pink
}
