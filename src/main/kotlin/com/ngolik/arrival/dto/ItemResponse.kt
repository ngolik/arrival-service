package com.ngolik.arrival.dto

import java.math.BigDecimal

data class ItemResponse(
        val id: Long,
        val name: String,
        val quantity: Int,
        val unitPrice: BigDecimal,
        val totalCost: BigDecimal
)
