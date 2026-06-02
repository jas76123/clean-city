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

    // В DEV дополнительно подключаем db/seed-dev (V99__seed_dev.sql и пр.),
    // в проде классpath не содержит эти миграции, поэтому даже добавление
    // locations безопасно — но для явности привязываем условно.
    val locations = if (stage == "DEV") {
        arrayOf("classpath:db/migration", "classpath:db/seed-dev")
    } else {
        arrayOf("classpath:db/migration")
    }

    // В DEV допускаем out-of-order: сид-миграция db/seed-dev (V99__seed_dev.sql)
    // намеренно имеет высокий номер и всегда применяется последней, поэтому любая
    // новая реальная миграция (V9, V10, …) идёт «до» неё. Без outOfOrder Flyway
    // отвергает её как нарушение порядка. В проде сид-миграций нет, порядок строгий.
    Flyway.configure()
        .dataSource(dbUrl, dbUser, dbPassword)
        .locations(*locations)
        .outOfOrder(stage == "DEV")
        .load()
        .migrate()

    Database.connect(
        url = dbUrl,
        driver = "org.postgresql.Driver",
        user = dbUser,
        password = dbPassword
    )
}
