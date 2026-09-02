package com.ngolik.arrival.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class MarkArrivalSealedRequest(
        @field:NotNull
        val operatorId: Long?,
        @field:Size(max = 500)
        val note: String? = null
)
