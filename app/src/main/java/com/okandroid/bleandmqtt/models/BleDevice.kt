package com.phyathai.NurseTrackingClient.models

import java.io.Serializable

data class BleDevice(
    var name: String? = null,
    var address: String? = null,
    var rssi: String? = null

) : Serializable