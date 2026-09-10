package com.ngolik.arrival.service.impl

import com.ngolik.arrival.authclient.UserValidator
import com.ngolik.arrival.entity.Arrival
import com.ngolik.arrival.exception.UnknownOperatorException
import com.ngolik.arrival.repo.ArrivalRepository
import com.ngolik.arrival.service.ArrivalService
import org.springframework.stereotype.Service

@Service
class ArrivalServiceImpl(
        private val arrivalRepository: ArrivalRepository,
        private val userValidator: UserValidator
): ArrivalService {

    override fun getAllArrivals():List<Arrival> = arrivalRepository.findAll()

    override fun createArrival(arrival: Arrival): Arrival = arrivalRepository.save(arrival)

    override fun getArrivalById(id: Long): Arrival? = arrivalRepository.findById(id).orElse(null)

    override fun markAsWaiting(id: Long, remark: String?): Arrival? =
            arrivalRepository.findById(id).orElse(null)
                    ?.copy(isWaiting = true, remark = remark)
                    ?.let { arrivalRepository.save(it) }

    override fun markAsDamaged(id: Long, remark: String?): Arrival? =
            arrivalRepository.findById(id).orElse(null)
                    ?.copy(isDamaged = true, damageRemark = remark)
                    ?.let { arrivalRepository.save(it) }

    override fun markAsSealed(id: Long, operatorId: Long, note: String?): Arrival? {
        if (!userValidator.userExists(operatorId)) {
            throw UnknownOperatorException("operatorId $operatorId does not correspond to an existing user")
        }
        return arrivalRepository.findById(id).orElse(null)
                ?.copy(isSealed = true, sealNote = note)
                ?.let { arrivalRepository.save(it) }
    }

    override fun markAsSurplus(id: Long, remark: String?): Arrival? =
            arrivalRepository.findById(id).orElse(null)
                    ?.copy(isSurplus = true, surplusRemark = remark)
                    ?.let { arrivalRepository.save(it) }
}
