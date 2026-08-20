package com.ngolik.arrival.dto

import com.ngolik.arrival.entity.Arrival
import com.ngolik.arrival.entity.Item

fun Item.toResponse() = ItemResponse(
        id = id,
        name = name,
        quantity = quantity,
        unitPrice = unitPrice,
        totalCost = totalCost
)

fun Arrival.toResponse() = ArrivalResponse(
        id = id,
        arrivalDate = arrivalDate,
        items = items.map { it.toResponse() }
)
