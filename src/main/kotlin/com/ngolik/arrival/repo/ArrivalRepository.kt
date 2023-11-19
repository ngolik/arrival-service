package com.ngolik.arrival.repo

import com.ngolik.arrival.entity.Arrival
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ArrivalRepository : JpaRepository<Arrival, Long> {
}