package com.ngolik.arrival.controller

import com.ngolik.arrival.dto.ArrivalResponse
import com.ngolik.arrival.dto.toResponse
import com.ngolik.arrival.entity.Arrival
import com.ngolik.arrival.service.ArrivalService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/arrivals")
class ArrivalController(private val arrivalService: ArrivalService) {

    @GetMapping
    fun getAllArrivals(): List<ArrivalResponse> = arrivalService.getAllArrivals().map { it.toResponse() }

    @GetMapping("/{id}")
    fun getArrivalById(@PathVariable id: Long): ResponseEntity<ArrivalResponse> =
            arrivalService.getArrivalById(id)?.let { ResponseEntity.ok(it.toResponse()) }
                    ?: ResponseEntity.notFound().build()

    @PostMapping
    fun createArrival(@Valid @RequestBody arrival: Arrival): ResponseEntity<ArrivalResponse> =
            ResponseEntity.status(HttpStatus.CREATED).body(arrivalService.createArrival(arrival).toResponse())
}