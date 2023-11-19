package com.ngolik.arrival

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication
@EnableDiscoveryClient
@EnableJpaRepositories(basePackages = ["com.ngolik.arrival.repo"])
class ArrivalServiceApplication

fun main(args: Array<String>) {
    runApplication<ArrivalServiceApplication>(*args)
}
