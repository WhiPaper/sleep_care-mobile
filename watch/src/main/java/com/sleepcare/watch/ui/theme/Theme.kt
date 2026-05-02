package com.sleepcare.watch.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Typography
import androidx.wear.compose.material.Shapes

// Wear OS 화면에 적용하는 어두운 Sleep Care 색상입니다.
private val NightColors = Colors(
    primary = Color(0xFFBDC2FF),
    primaryVariant = Color(0xFF1A237E),
    secondary = Color(0xFF44D8F1),
    secondaryVariant = Color(0xFF00353D),
    error = Color(0xFFFFB4AB),
    onPrimary = Color(0xFF1B247F),
    onSecondary = Color(0xFF00363E),
    onError = Color(0xFF690005),
    surface = Color(0xFF111415),
    onSurface = Color(0xFFE2E2E4),
    background = Color(0xFF111415),
    onBackground = Color(0xFFE2E2E4),
)

private val NightTypography = Typography()
private val NightShapes = Shapes()

@Composable
// 워치 앱의 최상위 MaterialTheme 래퍼입니다.
fun SleepCareWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = NightColors,
        typography = NightTypography,
        shapes = NightShapes,
        content = content,
    )
}
