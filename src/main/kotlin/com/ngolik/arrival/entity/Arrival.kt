package com.ngolik.arrival.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import java.time.LocalDateTime

@Entity
data class Arrival(
        @Id
        val id: Long,
        val arrivalDate: LocalDateTime,
        @OneToMany
        val items: List<Item>
)
