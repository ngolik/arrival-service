package com.ngolik.arrival.service

import com.ngolik.arrival.entity.Arrival

interface ArrivalService {
    fun getAllArrivals(): List<Arrival>
    fun createArrival(arrival: Arrival): Arrival
}