package com.example.cleancity.auth

import at.favre.lib.crypto.bcrypt.BCrypt

/**
 * bcrypt-обёртка с фиксированной стоимостью cost=12.
 * cost=12 — баланс безопасности и латентности (~250ms на современном CPU).
 * При необходимости можно поднять до 14, но это снизит RPS логина.
 */
object PasswordHasher {
    private const val COST = 12

    fun hash(plain: String): String {
        require(plain.isNotEmpty()) { "Password must not be empty" }
        return BCrypt.withDefaults().hashToString(COST, plain.toCharArray())
    }

    fun verify(plain: String, hash: String): Boolean {
        if (plain.isEmpty() || hash.isEmpty()) return false
        return BCrypt.verifyer().verify(plain.toCharArray(), hash).verified
    }
}
