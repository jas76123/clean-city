package com.example.cleancity.config

import io.ktor.server.application.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

private val INSECURE_PASSWORDS = setOf(
    "", "cleancity", "password", "postgres", "admin", "changeme", "dev"
)

fun Application.configureDatabase() {
    val dbUrl = environment.config.propertyOrNull("database.url")?.getString()
        ?.takeIf { it.isNotBlank() }
        ?: error("DB_URL is not configured. Set env var or update application.conf.")
    val dbUser = environment.config.propertyOrNull("database.user")?.getString()
        ?.takeIf { it.isNotBlank() }
        ?: error("DB_USER is not configured.")
    val dbPassword = environment.config.propertyOrNull("database.password")?.getString()
        ?: error("DB_PASSWORD is not configured. Set via env var (see .env.example).")

    val stage = environment.config.propertyOrNull("app.stage")?.getString()?.uppercase() ?: "DEV"
    if (stage != "DEV" && dbPassword.lowercase() in INSECURE_PASSWORDS) {
        error("Insecure DB_PASSWORD detected for stage=$stage. Use a strong password.")
    }

    Flyway.configure()
        .dataSource(dbUrl, dbUser, dbPassword)
        .load()
        .migrate()

    Database.connect(
        url = dbUrl,
        driver = "org.postgresql.Driver",
        user = dbUser,
        password = dbPassword
    )
}
