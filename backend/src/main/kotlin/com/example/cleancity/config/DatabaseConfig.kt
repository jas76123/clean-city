package com.example.cleancity.config

import io.ktor.server.application.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

fun Application.configureDatabase() {
    val dbUrl = environment.config.propertyOrNull("database.url")?.getString()
        ?: System.getenv("DB_URL")
        ?: "jdbc:postgresql://localhost:5432/cleancity"
    val dbUser = environment.config.propertyOrNull("database.user")?.getString()
        ?: System.getenv("DB_USER")
        ?: "cleancity"
    val dbPassword = environment.config.propertyOrNull("database.password")?.getString()
        ?: System.getenv("DB_PASSWORD")
        ?: "cleancity"

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
