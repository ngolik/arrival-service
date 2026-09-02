package com.ngolik.arrival.dto

import java.time.LocalDateTime

data class ArrivalResponse(
        val id: Long,
        val arrivalDate: LocalDateTime,
        val items: List<ItemResponse>,
        val isWaiting: Boolean,
        val remark: String?,
        val isDamaged: Boolean,
        val damageRemark: String?
)
