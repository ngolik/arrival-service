package com.ngolik.arrival.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import java.math.BigDecimal

@Entity
data class Item(
        @Id
        val id: Long,
        val name: String,
        val quantity: Int,
        val unitPrice: BigDecimal,
        val totalCost: BigDecimal
)
