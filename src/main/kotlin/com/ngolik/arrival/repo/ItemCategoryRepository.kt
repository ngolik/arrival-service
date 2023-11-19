package com.ngolik.arrival.repo

import com.ngolik.arrival.entity.ItemCategory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ItemCategoryRepository: JpaRepository<ItemCategory, Long> {
}