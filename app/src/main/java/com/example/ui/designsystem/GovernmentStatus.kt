package com.smartprocurement.internal.ui.designsystem

typealias GovernmentStatusStyle = PoliceStatusStyle

object GovernmentStatus {
    fun fromApiStatus(status: String): GovernmentStatusStyle = PoliceStatus.fromApiStatus(status)
    fun fromUiStatus(status: String): GovernmentStatusStyle = PoliceStatus.fromUiStatus(status)
}
