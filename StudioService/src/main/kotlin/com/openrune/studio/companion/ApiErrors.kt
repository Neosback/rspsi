package com.openrune.studio.companion

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.response.respond

enum class ApiErrorCode {
    INVALID_REQUEST,
    UNAUTHORIZED,
    HOST_NOT_ALLOWED,
    ORIGIN_NOT_ALLOWED,
    PROJECT_NOT_OPEN,
    PROJECT_UNSUPPORTED,
    PROJECT_PATH_INVALID,
    PATH_OUTSIDE_PROJECT,
    CAPABILITY_UNAVAILABLE,
    CACHE_UNSUPPORTED,
    INDEX_FAILED,
    INTERNAL_ERROR,
}

data class ApiErrorResponse(
    val code: ApiErrorCode,
    val message: String,
    val details: Map<String, String> = emptyMap(),
)

class ApiException(
    val code: ApiErrorCode,
    val status: HttpStatusCode,
    override val message: String,
    val details: Map<String, String> = emptyMap(),
    cause: Throwable? = null,
) : RuntimeException(message, cause)

fun Application.installApiErrors() {
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(
                cause.status,
                ApiErrorResponse(
                    code = cause.code,
                    message = cause.message,
                    details = cause.details,
                ),
            )
        }

        exception<BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ApiErrorResponse(
                    code = ApiErrorCode.INVALID_REQUEST,
                    message = cause.message ?: "Invalid request.",
                ),
            )
        }

        exception<Throwable> { call, _ ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiErrorResponse(
                    code = ApiErrorCode.INTERNAL_ERROR,
                    message = "Companion could not complete the request.",
                ),
            )
        }
    }
}
