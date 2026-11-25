package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class FilePickerRoute(val targetDeviceId: String? = null)

@Serializable
data object TransfersRoute

@Serializable
data object DevicesRoute

@Serializable
data object SettingsRoute

@Serializable
data class QrPairingRoute(val initialShowMyQr: Boolean = false)

@Serializable
data class TransferSummaryRoute(val sessionId: String = "")
