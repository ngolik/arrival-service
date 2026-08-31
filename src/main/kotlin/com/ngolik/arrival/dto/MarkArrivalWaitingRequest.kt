package com.ngolik.arrival.dto

import jakarta.validation.constraints.Size

data class MarkArrivalWaitingRequest(
        @field:Size(max = 500)
        val remark: String? = null
)
