package com.ngolik.arrival.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id

@Entity
data class ItemCategory(
        @Id
        val id: Long,
        val name: String
)
