package com.ngolik.arrival.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ngolik.arrival.authclient.UserValidator
import com.ngolik.arrival.dto.MarkArrivalDamagedRequest
import com.ngolik.arrival.dto.MarkArrivalSealedRequest
import com.ngolik.arrival.dto.MarkArrivalWaitingRequest
import com.ngolik.arrival.entity.Arrival
import com.ngolik.arrival.entity.Item
import com.ngolik.arrival.exception.AuthServiceUnavailableException
import com.ngolik.arrival.exception.UnknownOperatorException
import com.ngolik.arrival.service.ArrivalService
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
class ArrivalControllerTest(@Autowired private val mockMvc: MockMvc, @Autowired private val objectMapper: ObjectMapper) {

    @MockBean
    private lateinit var arrivalService: ArrivalService

    @MockBean
    private lateinit var userValidator: UserValidator

    private fun sampleArrival() = Arrival(
            id = 1L,
            arrivalDate = LocalDateTime.of(2026, 8, 19, 10, 0),
            items = listOf(Item(id = 1L, name = "Widget", quantity = 10, unitPrice = BigDecimal.ONE, totalCost = BigDecimal.TEN))
    )

    @Test
    fun `GET arrivals returns 200 with arrival response DTOs`() {
        val arrival = sampleArrival()
        `when`(arrivalService.getAllArrivals()).thenReturn(listOf(arrival))

        mockMvc.perform(get("/api/arrivals"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].items[0].name").value("Widget"))
    }

    @Test
    fun `GET arrivals by id returns 200 with the arrival response DTO when found`() {
        val arrival = sampleArrival()
        `when`(arrivalService.getArrivalById(1L)).thenReturn(arrival)

        mockMvc.perform(get("/api/arrivals/1"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Widget"))
    }

    @Test
    fun `GET arrivals by id returns 404 when not found`() {
        `when`(arrivalService.getArrivalById(99L)).thenReturn(null)

        mockMvc.perform(get("/api/arrivals/99"))
                .andExpect(status().isNotFound)
    }

    @Test
    fun `POST arrivals with a valid body returns 201 with an arrival response DTO`() {
        val arrival = sampleArrival()
        `when`(arrivalService.createArrival(arrival)).thenReturn(arrival)

        mockMvc.perform(
                post("/api/arrivals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(arrival))
        ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Widget"))
    }

    @Test
    fun `POST arrivals with a missing required field returns 400`() {
        val invalidJson = """{"id":1,"items":[]}"""

        mockMvc.perform(
                post("/api/arrivals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson)
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `PUT arrivals waiting with a remark returns 200 with the updated arrival response DTO`() {
        val arrival = sampleArrival()
        val waitingArrival = arrival.copy(isWaiting = true, remark = "Delayed at customs")
        `when`(arrivalService.markAsWaiting(1L, "Delayed at customs")).thenReturn(waitingArrival)

        mockMvc.perform(
                put("/api/arrivals/1/waiting")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MarkArrivalWaitingRequest(remark = "Delayed at customs")))
        ).andExpect(status().isOk)
                .andExpect(jsonPath("$.isWaiting").value(true))
                .andExpect(jsonPath("$.remark").value("Delayed at customs"))
    }

    @Test
    fun `PUT arrivals waiting with no body returns 200 with a null remark`() {
        val arrival = sampleArrival()
        val waitingArrival = arrival.copy(isWaiting = true, remark = null)
        `when`(arrivalService.markAsWaiting(eq(1L), isNull())).thenReturn(waitingArrival)

        mockMvc.perform(put("/api/arrivals/1/waiting"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.isWaiting").value(true))
    }

    @Test
    fun `PUT arrivals waiting for a missing arrival returns 404`() {
        `when`(arrivalService.markAsWaiting(eq(99L), isNull())).thenReturn(null)

        mockMvc.perform(put("/api/arrivals/99/waiting"))
                .andExpect(status().isNotFound)
    }

    @Test
    fun `PUT arrivals waiting with a remark over 500 characters returns 400 and does not update`() {
        val tooLongRemark = "a".repeat(501)

        mockMvc.perform(
                put("/api/arrivals/1/waiting")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MarkArrivalWaitingRequest(remark = tooLongRemark)))
        ).andExpect(status().isBadRequest)

        verify(arrivalService, never()).markAsWaiting(eq(1L), anyString())
    }

    @Test
    fun `GET arrivals by id surfaces waiting state and remark once set`() {
        val waitingArrival = sampleArrival().copy(isWaiting = true, remark = "Delayed at customs")
        `when`(arrivalService.getArrivalById(1L)).thenReturn(waitingArrival)

        mockMvc.perform(get("/api/arrivals/1"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.isWaiting").value(true))
                .andExpect(jsonPath("$.remark").value("Delayed at customs"))
    }

    @Test
    fun `PUT arrivals damaged with a remark returns 200 with the updated arrival response DTO`() {
        val arrival = sampleArrival()
        val damagedArrival = arrival.copy(isDamaged = true, damageRemark = "Crushed pallet")
        `when`(arrivalService.markAsDamaged(1L, "Crushed pallet")).thenReturn(damagedArrival)

        mockMvc.perform(
                put("/api/arrivals/1/damaged")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MarkArrivalDamagedRequest(remark = "Crushed pallet")))
        ).andExpect(status().isOk)
                .andExpect(jsonPath("$.isDamaged").value(true))
                .andExpect(jsonPath("$.damageRemark").value("Crushed pallet"))
    }

    @Test
    fun `PUT arrivals damaged with no body returns 200 with a null remark`() {
        val arrival = sampleArrival()
        val damagedArrival = arrival.copy(isDamaged = true, damageRemark = null)
        `when`(arrivalService.markAsDamaged(eq(1L), isNull())).thenReturn(damagedArrival)

        mockMvc.perform(put("/api/arrivals/1/damaged"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.isDamaged").value(true))
    }

    @Test
    fun `PUT arrivals damaged for a missing arrival returns 404`() {
        `when`(arrivalService.markAsDamaged(eq(99L), isNull())).thenReturn(null)

        mockMvc.perform(put("/api/arrivals/99/damaged"))
                .andExpect(status().isNotFound)
    }

    @Test
    fun `PUT arrivals damaged with a remark over 500 characters returns 400 and does not update`() {
        val tooLongRemark = "a".repeat(501)

        mockMvc.perform(
                put("/api/arrivals/1/damaged")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MarkArrivalDamagedRequest(remark = tooLongRemark)))
        ).andExpect(status().isBadRequest)

        verify(arrivalService, never()).markAsDamaged(eq(1L), anyString())
    }

    @Test
    fun `GET arrivals by id surfaces damaged state and remark once set`() {
        val damagedArrival = sampleArrival().copy(isDamaged = true, damageRemark = "Crushed pallet")
        `when`(arrivalService.getArrivalById(1L)).thenReturn(damagedArrival)

        mockMvc.perform(get("/api/arrivals/1"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.isDamaged").value(true))
                .andExpect(jsonPath("$.damageRemark").value("Crushed pallet"))
    }

    @Test
    fun `GET arrivals by id surfaces waiting and damaged as independent facts with separate remarks`() {
        val bothFlaggedArrival = sampleArrival()
                .copy(isWaiting = true, remark = "Delayed at customs", isDamaged = true, damageRemark = "Crushed pallet")
        `when`(arrivalService.getArrivalById(1L)).thenReturn(bothFlaggedArrival)

        mockMvc.perform(get("/api/arrivals/1"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.isWaiting").value(true))
                .andExpect(jsonPath("$.remark").value("Delayed at customs"))
                .andExpect(jsonPath("$.isDamaged").value(true))
                .andExpect(jsonPath("$.damageRemark").value("Crushed pallet"))
    }

    @Test
    fun `PUT arrivals sealed with a note returns 200 with the updated arrival response DTO`() {
        val arrival = sampleArrival()
        val sealedArrival = arrival.copy(isSealed = true, sealNote = "Inspected, passed")
        `when`(arrivalService.markAsSealed(1L, 42L, "Inspected, passed")).thenReturn(sealedArrival)

        mockMvc.perform(
                put("/api/arrivals/1/sealed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MarkArrivalSealedRequest(operatorId = 42L, note = "Inspected, passed")))
        ).andExpect(status().isOk)
                .andExpect(jsonPath("$.isSealed").value(true))
                .andExpect(jsonPath("$.sealNote").value("Inspected, passed"))
    }

    @Test
    fun `PUT arrivals sealed with no note returns 200 with a null seal note`() {
        val arrival = sampleArrival()
        val sealedArrival = arrival.copy(isSealed = true, sealNote = null)
        `when`(arrivalService.markAsSealed(eq(1L), eq(42L), isNull())).thenReturn(sealedArrival)

        mockMvc.perform(
                put("/api/arrivals/1/sealed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MarkArrivalSealedRequest(operatorId = 42L)))
        ).andExpect(status().isOk)
                .andExpect(jsonPath("$.isSealed").value(true))
    }

    @Test
    fun `PUT arrivals sealed for a missing arrival returns 404`() {
        `when`(arrivalService.markAsSealed(eq(99L), eq(42L), isNull())).thenReturn(null)

        mockMvc.perform(
                put("/api/arrivals/99/sealed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MarkArrivalSealedRequest(operatorId = 42L)))
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `PUT arrivals sealed with a note over 500 characters returns 400 and does not update`() {
        val tooLongNote = "a".repeat(501)

        mockMvc.perform(
                put("/api/arrivals/1/sealed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MarkArrivalSealedRequest(operatorId = 42L, note = tooLongNote)))
        ).andExpect(status().isBadRequest)

        verify(arrivalService, never()).markAsSealed(eq(1L), eq(42L), anyString())
    }

    @Test
    fun `PUT arrivals sealed with a body missing operatorId returns 400`() {
        val missingOperatorJson = """{"note":"Inspected"}"""

        mockMvc.perform(
                put("/api/arrivals/1/sealed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingOperatorJson)
        ).andExpect(status().isBadRequest)

        verify(arrivalService, never()).markAsSealed(eq(1L), anyLong(), anyString())
    }

    @Test
    fun `PUT arrivals sealed with an unknown operator returns 400 and does not update`() {
        `when`(arrivalService.markAsSealed(eq(1L), eq(99L), isNull()))
                .thenThrow(UnknownOperatorException("operatorId 99 does not correspond to an existing user"))

        mockMvc.perform(
                put("/api/arrivals/1/sealed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MarkArrivalSealedRequest(operatorId = 99L)))
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `PUT arrivals sealed when auth-service is unreachable returns 502`() {
        `when`(arrivalService.markAsSealed(eq(1L), eq(42L), isNull()))
                .thenThrow(AuthServiceUnavailableException("failed to validate operator 42 with auth-service"))

        mockMvc.perform(
                put("/api/arrivals/1/sealed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MarkArrivalSealedRequest(operatorId = 42L)))
        ).andExpect(status().isBadGateway)
    }

    @Test
    fun `GET arrivals by id surfaces sealed state and note once set`() {
        val sealedArrival = sampleArrival().copy(isSealed = true, sealNote = "Inspected, passed")
        `when`(arrivalService.getArrivalById(1L)).thenReturn(sealedArrival)

        mockMvc.perform(get("/api/arrivals/1"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.isSealed").value(true))
                .andExpect(jsonPath("$.sealNote").value("Inspected, passed"))
    }

    @Test
    fun `GET arrivals by id surfaces waiting, damaged, and sealed as independent facts with separate notes`() {
        val allFlaggedArrival = sampleArrival()
                .copy(
                        isWaiting = true, remark = "Delayed at customs",
                        isDamaged = true, damageRemark = "Crushed pallet",
                        isSealed = true, sealNote = "Inspected, passed"
                )
        `when`(arrivalService.getArrivalById(1L)).thenReturn(allFlaggedArrival)

        mockMvc.perform(get("/api/arrivals/1"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.isWaiting").value(true))
                .andExpect(jsonPath("$.remark").value("Delayed at customs"))
                .andExpect(jsonPath("$.isDamaged").value(true))
                .andExpect(jsonPath("$.damageRemark").value("Crushed pallet"))
                .andExpect(jsonPath("$.isSealed").value(true))
                .andExpect(jsonPath("$.sealNote").value("Inspected, passed"))
    }
}
