package com.example.cleancity.auth

import com.example.cleancity.shared.models.UserRole
import org.slf4j.Logger

/**
 * Создаёт первого администратора из INITIAL_ADMIN_EMAIL/PASSWORD при старте backend,
 * если в системе ещё нет ни одного админа. Идемпотентно — после появления любого
 * админа bootstrap больше не срабатывает.
 *
 * См. docs/superpowers/specs/2026-05-22-bootstrap-admin-design.md
 *
 * @param stage окружение ("DEV" / "PROD"); в PROD пароль валидируется PasswordPolicy (fail-fast).
 */
fun bootstrapInitialAdmin(
    users: UserRepository,
    email: String?,
    password: String?,
    stage: String,
    log: Logger,
) {
    if (email.isNullOrBlank() || password.isNullOrBlank()) {
        log.info("INITIAL_ADMIN не сконфигурирован — bootstrap пропущен")
        return
    }
    if (users.hasAnyAdmin()) {
        log.info("Админ уже существует — bootstrap пропущен")
        return
    }
    if (stage.uppercase() == "PROD") {
        PasswordPolicy.validate(password, UserRole.ADMIN)
    }
    users.create(
        email = email,
        passwordHash = PasswordHasher.hash(password),
        role = UserRole.ADMIN,
        fullName = "Администратор",
        isActive = true,
        emailVerified = true,
        mustChangePassword = false,
    )
    log.info("Bootstrap-админ создан: ${email.lowercase()}")
}
