package com.ngolik.arrival.controller

import com.ngolik.arrival.entity.Arrival
import com.ngolik.arrival.service.ArrivalService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/arrivals")
class ArrivalController(private val arrivalService: ArrivalService) {

    @GetMapping
    fun getAllArrivals(): List<Arrival> = arrivalService.getAllArrivals()

    @PostMapping
    fun createArrival(@Valid @RequestBody arrival: Arrival): ResponseEntity<Arrival> =
            ResponseEntity.status(HttpStatus.CREATED).body(arrivalService.createArrival(arrival))
}