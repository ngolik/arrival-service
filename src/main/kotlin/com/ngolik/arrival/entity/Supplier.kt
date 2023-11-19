package com.ngolik.arrival.entity

import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Id

@Entity
data class Supplier(
        @Id
        val id: Long,
        val name: String,
        @Embedded
        val contactInfo: ContactInfo
)
