package com.framebetween.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp

val AppTypography = Typography(
    bodyLarge = Typography().bodyLarge.copy(lineHeight = 24.sp),
    bodyMedium = Typography().bodyMedium.copy(lineHeight = 20.sp),
    titleMedium = Typography().titleMedium.copy(letterSpacing = 0.1.sp)
)
