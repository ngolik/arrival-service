package com.ngolik.arrival.authclient

interface UserValidator {
    fun userExists(operatorId: Long): Boolean
}
