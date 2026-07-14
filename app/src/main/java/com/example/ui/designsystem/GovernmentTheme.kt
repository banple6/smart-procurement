package com.smartprocurement.internal.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

object GovernmentThemeDefaults {
    const val appName = PoliceBrandConfig.appName
    const val appDescription = PoliceBrandConfig.systemName
    const val forceLightTheme = false
}

object GovernmentColors {
    val GovernmentBlue = PoliceColors.PolicePrimary
    val GovernmentBlueDark = PoliceColors.PoliceNavy
    val GovernmentBlueLight = PoliceColors.PoliceLight

    val PageBackground = PoliceColors.PageBackground
    val SurfaceWhite = PoliceColors.SurfaceWhite
    val SurfaceMuted = PoliceColors.SurfaceMuted
    val Divider = PoliceColors.DividerColor
    val Border = PoliceColors.BorderColor

    val TextPrimary = PoliceColors.TextPrimary
    val TextSecondary = PoliceColors.TextSecondary
    val TextTertiary = PoliceColors.TextTertiary
    val TextDisabled = PoliceColors.TextDisabled
    val TextOnPrimary = PoliceColors.TextOnBlue

    val StatusPending = PoliceColors.StatusPending
    val StatusAccepted = PoliceColors.StatusNormal
    val StatusPreparing = PoliceColors.StatusPreparing
    val StatusShipped = PoliceColors.StatusPending
    val StatusCompleted = PoliceColors.StatusCompleted
    val StatusCancelled = PoliceColors.StatusCancelled
    val StatusError = PoliceColors.StatusError
    val StatusWarningBackground = PoliceColors.StatusWarningBackground
    val StatusErrorBackground = PoliceColors.StatusErrorBackground
    val StatusSuccessBackground = PoliceColors.StatusSuccessBackground
}

object GovernmentTypography {
    val PageTitleSize = PoliceTypography.PageTitleSize
    val TopBarTitleSize = PoliceTypography.TopBarTitleSize
    val SectionTitleSize = PoliceTypography.SectionTitleSize
    val ImportantSize = PoliceTypography.SectionTitleSize
    val BodySize = PoliceTypography.BodySize
    val SecondaryBodySize = PoliceTypography.SecondaryBodySize
    val HelperSize = PoliceTypography.HelperSize
    val NoteSize = PoliceTypography.NoteSize
    val Material = PoliceTypography.Material
}

object GovernmentShapes {
    val SmallRadius: Dp = PoliceShapes.StatusRadius
    val MediumRadius: Dp = PoliceShapes.CardRadius
    val LargeRadius: Dp = PoliceShapes.DialogRadius
}

object GovernmentDimens {
    val PagePadding = PoliceDimens.PagePadding
    val SectionSpacing = PoliceDimens.SectionSpacing
    val CardPadding = PoliceDimens.CardPadding
    val FieldSpacing = PoliceDimens.FieldSpacing
    val TopBarHeight = PoliceDimens.TopBarHeight
    val BottomNavigationHeight = PoliceDimens.BottomNavigationHeight
    val PrimaryButtonHeight = PoliceDimens.PrimaryButtonHeight
    val MinTouchTarget = PoliceDimens.MinTouchTarget
    val ListItemMinHeight = PoliceDimens.ListItemMinHeight
    val ProductImageSize = PoliceDimens.ProductImageSize
}

@Composable
fun GovernmentTheme(content: @Composable () -> Unit) {
    PoliceTheme(content)
}
