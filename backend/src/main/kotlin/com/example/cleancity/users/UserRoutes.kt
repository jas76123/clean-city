package com.example.cleancity.users

import com.example.cleancity.auth.AuthService
import com.example.cleancity.auth.requireUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.userRoutes(authService: AuthService) {
    route("/users") {
        authenticate("auth-jwt") {
            get("/me") {
                val userId = call.requireUserId()
                val user = authService.getUserById(userId)
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(HttpStatusCode.OK, user)
            }
        }
    }
}
