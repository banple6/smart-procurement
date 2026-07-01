package com.smartprocurement.internal.ui.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography as MaterialTypography

object GovernmentThemeDefaults {
    const val appName = "景荣鲜配"
    const val appDescription = "内部生鲜采购与配送管理系统"
    const val forceLightTheme = true
}

object GovernmentColors {
    val GovernmentBlue = Color(0xFF1F5D8F)
    val GovernmentBlueDark = Color(0xFF17476D)
    val GovernmentBlueLight = Color(0xFFEAF2F8)

    val PageBackground = Color(0xFFF4F6F8)
    val SurfaceWhite = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFF8F9FA)
    val Divider = Color(0xFFE3E8ED)
    val Border = Color(0xFFD5DDE5)

    val TextPrimary = Color(0xFF1F2329)
    val TextSecondary = Color(0xFF5F6B76)
    val TextTertiary = Color(0xFF87919C)
    val TextDisabled = Color(0xFFAAB2BA)
    val TextOnPrimary = Color(0xFFFFFFFF)

    val StatusPending = Color(0xFF2368A2)
    val StatusAccepted = Color(0xFF237A57)
    val StatusPreparing = Color(0xFFA96500)
    val StatusShipped = Color(0xFF385F9D)
    val StatusCompleted = Color(0xFF28783E)
    val StatusCancelled = Color(0xFF747C85)
    val StatusError = Color(0xFFC62828)
    val StatusWarningBackground = Color(0xFFFFF6E5)
    val StatusErrorBackground = Color(0xFFFFF0F0)
    val StatusSuccessBackground = Color(0xFFEDF7F0)
}

object GovernmentTypography {
    val PageTitleSize = 22.sp
    val TopBarTitleSize = 20.sp
    val SectionTitleSize = 18.sp
    val ImportantSize = 18.sp
    val BodySize = 16.sp
    val SecondaryBodySize = 14.sp
    val HelperSize = 13.sp
    val NoteSize = 12.sp

    val Material = MaterialTypography(
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

object GovernmentShapes {
    val SmallRadius: Dp = 4.dp
    val MediumRadius: Dp = 8.dp
    val LargeRadius: Dp = 10.dp
}

object GovernmentDimens {
    val PagePadding = 16.dp
    val SectionSpacing = 16.dp
    val CardPadding = 16.dp
    val FieldSpacing = 12.dp
    val TopBarHeight = 56.dp
    val BottomNavigationHeight = 68.dp
    val PrimaryButtonHeight = 52.dp
    val MinTouchTarget = 48.dp
    val ListItemMinHeight = 56.dp
    val ProductImageSize = 92.dp
}

private val GovernmentLightColors = lightColorScheme(
    primary = GovernmentColors.GovernmentBlue,
    onPrimary = GovernmentColors.TextOnPrimary,
    primaryContainer = GovernmentColors.GovernmentBlueLight,
    onPrimaryContainer = GovernmentColors.GovernmentBlueDark,
    secondary = GovernmentColors.GovernmentBlueDark,
    onSecondary = GovernmentColors.TextOnPrimary,
    secondaryContainer = GovernmentColors.GovernmentBlueLight,
    onSecondaryContainer = GovernmentColors.GovernmentBlueDark,
    background = GovernmentColors.PageBackground,
    onBackground = GovernmentColors.TextPrimary,
    surface = GovernmentColors.SurfaceWhite,
    onSurface = GovernmentColors.TextPrimary,
    surfaceVariant = GovernmentColors.SurfaceMuted,
    onSurfaceVariant = GovernmentColors.TextSecondary,
    outline = GovernmentColors.Border,
    outlineVariant = GovernmentColors.Divider,
    error = GovernmentColors.StatusError,
    errorContainer = GovernmentColors.StatusErrorBackground,
    onErrorContainer = GovernmentColors.StatusError
)

@Composable
fun GovernmentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GovernmentLightColors,
        typography = GovernmentTypography.Material,
        content = content
    )
}
