package com.ngolik.arrival.service.impl

import com.ngolik.arrival.entity.Arrival
import com.ngolik.arrival.repo.ArrivalRepository
import com.ngolik.arrival.service.ArrivalService
import org.springframework.stereotype.Service

@Service
class ArrivalServiceImpl(private val arrivalRepository: ArrivalRepository): ArrivalService {

    override fun getAllArrivals():List<Arrival> = arrivalRepository.findAll()

    override fun createArrival(arrival: Arrival): Arrival = arrivalRepository.save(arrival)

    override fun getArrivalById(id: Long): Arrival? = arrivalRepository.findById(id).orElse(null)

    override fun markAsWaiting(id: Long, remark: String?): Arrival? =
            arrivalRepository.findById(id).orElse(null)
                    ?.copy(isWaiting = true, remark = remark)
                    ?.let { arrivalRepository.save(it) }
}
