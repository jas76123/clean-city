package com.example.cleancity

import com.example.cleancity.config.configureDatabase
import com.example.cleancity.markers.MarkerRepository
import com.example.cleancity.markers.MarkerService
import com.example.cleancity.markers.markerRoutes
import com.example.cleancity.storage.LocalStorageService
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    configureDatabase()

    install(ContentNegotiation) { json() }

    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.ContentType)
    }

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, cause.message ?: "Bad request")
        }
        exception<Exception> { call, cause ->
            call.application.environment.log.error("Unhandled exception", cause)
            call.respond(HttpStatusCode.InternalServerError, "Internal server error")
        }
    }

    val storagePath = environment.config.propertyOrNull("storage.path")?.getString()
        ?: System.getenv("STORAGE_PATH")
        ?: "./uploads"
    val port = environment.config.propertyOrNull("ktor.deployment.port")?.getString() ?: "8080"
    val baseUrl = "http://localhost:$port"

    val storage = LocalStorageService(storagePath, baseUrl)
    val repository = MarkerRepository()
    val service = MarkerService(repository, storage)

    routing {
        markerRoutes(service, storage)
    }
}
