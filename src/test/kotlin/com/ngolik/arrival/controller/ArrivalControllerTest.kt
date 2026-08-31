package com.ngolik.arrival.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ngolik.arrival.dto.MarkArrivalWaitingRequest
import com.ngolik.arrival.entity.Arrival
import com.ngolik.arrival.entity.Item
import com.ngolik.arrival.service.ArrivalService
import org.junit.jupiter.api.Test
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
}
