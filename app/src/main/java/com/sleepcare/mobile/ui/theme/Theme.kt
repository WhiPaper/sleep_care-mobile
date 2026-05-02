package com.sleepcare.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// 앱 전체에 적용되는 Material 3 다크 컬러 스킴입니다.
private val SleepCareColorScheme = darkColorScheme(
    primary = SleepCarePrimary,
    onPrimary = SleepCareOnPrimary,
    primaryContainer = SleepCarePrimaryContainer,
    onPrimaryContainer = SleepCareOnPrimaryContainer,
    inversePrimary = SleepCareInversePrimary,
    secondary = SleepCareSecondary,
    onSecondary = SleepCareOnSecondary,
    secondaryContainer = SleepCareSecondaryContainer,
    onSecondaryContainer = SleepCareOnSecondaryContainer,
    tertiary = SleepCareTertiary,
    onTertiary = SleepCareOnTertiary,
    tertiaryContainer = SleepCareTertiaryContainer,
    onTertiaryContainer = SleepCareOnTertiaryContainer,
    background = SleepCareBackground,
    onBackground = SleepCareOnBackground,
    surface = SleepCareSurface,
    onSurface = SleepCareOnSurface,
    surfaceVariant = SleepCareSurfaceVariant,
    onSurfaceVariant = SleepCareOnSurfaceVariant,
    surfaceTint = SleepCareSurfaceTint,
    surfaceBright = SleepCareSurfaceBright,
    surfaceContainerLowest = SleepCareSurfaceContainerLowest,
    surfaceContainerLow = SleepCareSurfaceContainerLow,
    surfaceContainer = SleepCareSurfaceContainer,
    surfaceContainerHigh = SleepCareSurfaceContainerHigh,
    surfaceContainerHighest = SleepCareSurfaceContainerHighest,
    outline = SleepCareOutline,
    outlineVariant = SleepCareOutlineVariant,
    error = SleepCareError,
    onError = SleepCareOnError,
    errorContainer = SleepCareErrorContainer,
    onErrorContainer = SleepCareOnErrorContainer,
    scrim = Color.Black
)

// 카드와 버튼의 둥근 정도를 한곳에서 관리합니다.
val SleepCareShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
)

@Composable
// Sleep Care 모바일 앱의 최상위 테마 래퍼입니다.
fun SleepCareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme: ColorScheme = if (darkTheme) {
        SleepCareColorScheme
    } else {
        SleepCareColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SleepCareTypography,
        shapes = SleepCareShapes,
        content = content
    )
}
