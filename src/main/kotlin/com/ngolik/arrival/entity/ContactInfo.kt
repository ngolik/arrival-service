package com.ngolik.arrival.entity

import lombok.Data

@Data
data class ContactInfo(
        val address: String,
        val phone: String,
        val email: String
)
