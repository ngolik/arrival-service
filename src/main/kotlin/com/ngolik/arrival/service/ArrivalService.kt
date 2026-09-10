package com.ngolik.arrival.service

import com.ngolik.arrival.entity.Arrival

interface ArrivalService {
    fun getAllArrivals(): List<Arrival>
    fun createArrival(arrival: Arrival): Arrival
    fun getArrivalById(id: Long): Arrival?
    fun markAsWaiting(id: Long, remark: String?): Arrival?
    fun markAsDamaged(id: Long, remark: String?): Arrival?
    fun markAsSealed(id: Long, operatorId: Long, note: String?): Arrival?
    fun markAsSurplus(id: Long, remark: String?): Arrival?
}