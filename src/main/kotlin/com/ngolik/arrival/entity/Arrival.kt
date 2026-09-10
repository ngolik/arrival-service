package com.ngolik.arrival.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
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
        val items: List<Item>,
        val isWaiting: Boolean = false,
        @field:Size(max = 500)
        val remark: String? = null,
        val isDamaged: Boolean = false,
        @field:Size(max = 500)
        val damageRemark: String? = null,
        val isSealed: Boolean = false,
        @field:Size(max = 500)
        val sealNote: String? = null,
        val isSurplus: Boolean = false,
        @field:Size(max = 500)
        val surplusRemark: String? = null
)
