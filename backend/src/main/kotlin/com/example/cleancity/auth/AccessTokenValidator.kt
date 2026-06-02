package com.example.cleancity.auth

import com.auth0.jwt.interfaces.Payload
import io.ktor.server.auth.jwt.JWTPrincipal

/**
 * Единая логика валидации access-токена для JWT-аутентификации.
 *
 * Проверяет не только подпись/срок (это делает verifier до вызова) и тип токена,
 * но и то, что пользователь всё ещё активен (`is_active`). Это обеспечивает
 * НЕМЕДЛЕННЫЙ эффект блокировки/деактивации: у забаненного пользователя уже
 * выданный access-токен (резидент — до 60 мин) перестаёт давать доступ на
 * следующем же запросе, а не только после истечения токена/отказа refresh.
 *
 * Используется и в проде (Application.module), и в тестовой обвязке — чтобы
 * поведение не расходилось.
 *
 * Возвращает [JWTPrincipal] для валидного токена активного пользователя, иначе null.
 */
fun validateAccessPrincipal(payload: Payload, users: UserRepository): JWTPrincipal? {
    val subject = payload.subject?.toLongOrNull() ?: return null
    if (payload.getClaim("type").asString() != "access") return null
    val user = users.findById(subject) ?: return null
    if (!user.isActive) return null
    return JWTPrincipal(payload)
}
