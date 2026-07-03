package com.smartprocurement.internal.ui.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object PoliceTypography {
    val PageTitleSize = 22.sp
    val SplashTitleSize = 26.sp
    val TopBarTitleSize = 20.sp
    val SectionTitleSize = 18.sp
    val BodySize = 16.sp
    val SecondaryBodySize = 14.sp
    val HelperSize = 13.sp
    val NoteSize = 12.sp

    val Material = Typography(
        displaySmall = TextStyle(fontFamily = FontFamily.Default, fontSize = PageTitleSize, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = TopBarTitleSize, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = SectionTitleSize, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = BodySize, lineHeight = 24.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = SecondaryBodySize, lineHeight = 22.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
        bodySmall = TextStyle(fontFamily = FontFamily.Default, fontSize = HelperSize, lineHeight = 20.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
        labelLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = BodySize, lineHeight = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
        labelMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = SecondaryBodySize, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
        labelSmall = TextStyle(fontFamily = FontFamily.Default, fontSize = NoteSize, lineHeight = 16.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp)
    )
}
