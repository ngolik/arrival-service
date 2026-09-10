package com.ngolik.arrival.controller

import com.ngolik.arrival.dto.ArrivalResponse
import com.ngolik.arrival.dto.MarkArrivalDamagedRequest
import com.ngolik.arrival.dto.MarkArrivalSealedRequest
import com.ngolik.arrival.dto.MarkArrivalSurplusRequest
import com.ngolik.arrival.dto.MarkArrivalWaitingRequest
import com.ngolik.arrival.dto.toResponse
import com.ngolik.arrival.entity.Arrival
import com.ngolik.arrival.exception.AuthServiceUnavailableException
import com.ngolik.arrival.exception.UnknownOperatorException
import com.ngolik.arrival.service.ArrivalService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
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

    @PutMapping("/{id}/waiting")
    fun markArrivalAsWaiting(
            @PathVariable id: Long,
            @Valid @RequestBody(required = false) request: MarkArrivalWaitingRequest?
    ): ResponseEntity<ArrivalResponse> =
            arrivalService.markAsWaiting(id, (request ?: MarkArrivalWaitingRequest()).remark)
                    ?.let { ResponseEntity.ok(it.toResponse()) }
                    ?: ResponseEntity.notFound().build()

    @PutMapping("/{id}/damaged")
    fun markArrivalAsDamaged(
            @PathVariable id: Long,
            @Valid @RequestBody(required = false) request: MarkArrivalDamagedRequest?
    ): ResponseEntity<ArrivalResponse> =
            arrivalService.markAsDamaged(id, (request ?: MarkArrivalDamagedRequest()).remark)
                    ?.let { ResponseEntity.ok(it.toResponse()) }
                    ?: ResponseEntity.notFound().build()

    @PutMapping("/{id}/sealed")
    fun markArrivalAsSealed(
            @PathVariable id: Long,
            @Valid @RequestBody request: MarkArrivalSealedRequest
    ): ResponseEntity<ArrivalResponse> =
            try {
                arrivalService.markAsSealed(id, request.operatorId!!, request.note)
                        ?.let { ResponseEntity.ok(it.toResponse()) }
                        ?: ResponseEntity.notFound().build()
            } catch (e: UnknownOperatorException) {
                ResponseEntity.badRequest().build()
            } catch (e: AuthServiceUnavailableException) {
                ResponseEntity.status(HttpStatus.BAD_GATEWAY).build()
            }

    @PutMapping("/{id}/surplus")
    fun markArrivalAsSurplus(
            @PathVariable id: Long,
            @Valid @RequestBody(required = false) request: MarkArrivalSurplusRequest?
    ): ResponseEntity<ArrivalResponse> =
            arrivalService.markAsSurplus(id, (request ?: MarkArrivalSurplusRequest()).remark)
                    ?.let { ResponseEntity.ok(it.toResponse()) }
                    ?: ResponseEntity.notFound().build()
}