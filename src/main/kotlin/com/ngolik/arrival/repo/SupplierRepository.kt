package com.ngolik.arrival.repo

import com.ngolik.arrival.entity.Supplier
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SupplierRepository: JpaRepository<Supplier, Long> {
}