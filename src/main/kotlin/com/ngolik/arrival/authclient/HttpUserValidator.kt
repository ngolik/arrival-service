package com.ngolik.arrival.authclient

import com.ngolik.arrival.exception.AuthServiceUnavailableException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate

@Component
class HttpUserValidator(
        private val restTemplate: RestTemplate,
        @Value("\${auth-service.base-url}") private val baseUrl: String
) : UserValidator {

    override fun userExists(operatorId: Long): Boolean {
        val url = "${baseUrl.trimEnd('/')}/auth/api/users/$operatorId"
        return try {
            restTemplate.getForEntity(url, Any::class.java)
            true
        } catch (e: HttpClientErrorException.NotFound) {
            false
        } catch (e: RestClientException) {
            throw AuthServiceUnavailableException(
                    "failed to validate operator $operatorId with auth-service at $url", e)
        }
    }
}
