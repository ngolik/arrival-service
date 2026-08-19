package com.ngolik.arrival.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

@Entity
data class Arrival(
        @field:NotNull
        @Id
        val id: Long,
        @field:NotNull
        val arrivalDate: LocalDateTime,
        @field:NotNull
        @OneToMany
        val items: List<Item>
)
