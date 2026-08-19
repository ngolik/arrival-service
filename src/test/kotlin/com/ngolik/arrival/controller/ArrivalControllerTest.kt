package com.ngolik.arrival.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ngolik.arrival.entity.Arrival
import com.ngolik.arrival.entity.Item
import com.ngolik.arrival.service.ArrivalService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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
    fun `POST arrivals with a valid body returns 201`() {
        val arrival = sampleArrival()
        `when`(arrivalService.createArrival(arrival)).thenReturn(arrival)

        mockMvc.perform(
                post("/api/arrivals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(arrival))
        ).andExpect(status().isCreated)
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
}
