package com.ngolik.arrival.dto

import jakarta.validation.constraints.Size

data class MarkArrivalDamagedRequest(
        @field:Size(max = 500)
        val remark: String? = null
)
