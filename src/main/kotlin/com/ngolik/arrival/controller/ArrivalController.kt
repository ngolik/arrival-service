package com.ngolik.arrival.controller

import com.ngolik.arrival.entity.Arrival
import com.ngolik.arrival.service.impl.ArrivalServiceImpl
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/arrivals")
class ArrivalController(private val arrivalService: ArrivalServiceImpl) {

    @GetMapping
    fun getAllArrivals(): List<Arrival> = arrivalService.getAllArrivals()
}