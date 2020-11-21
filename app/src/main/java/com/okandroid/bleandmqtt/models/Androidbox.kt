package com.phyathai.NurseTrackingClient.models

import java.io.Serializable

data class Androidbox(
    var device_id: String,
    var datetime: String? = null,
    var message: String? = null
) : Serializable