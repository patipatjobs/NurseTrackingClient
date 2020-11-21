package com.phyathai.NurseTrackingClient.models

import java.io.Serializable

data class SendPublish(
    var androidbox:Androidbox,
    var itag:iTAG
) : Serializable