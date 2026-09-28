package com.openrune.studio.companion

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.intercept
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.response.respond
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class CompanionSecurity(
    val token: String,
    val generatedToken: Boolean = false,
) {
    init {
        require(token.length >= MIN_TOKEN_LENGTH) {
            "Companion token must contain at least $MIN_TOKEN_LENGTH characters"
        }
    }

    fun acceptsToken(candidate: String?): Boolean {
        if (candidate == null) {
            return false
        }
        return MessageDigest.isEqual(
            token.toByteArray(Charsets.UTF_8),
            candidate.toByteArray(Charsets.UTF_8),
        )
    }

    fun acceptsHost(hostHeader: String?): Boolean =
        hostHeader != null && LOOPBACK_HOST.matches(hostHeader.trim())

    fun acceptsOrigin(originHeader: String?): Boolean {
        if (originHeader.isNullOrBlank()) {
            return true
        }
        val uri = runCatching { URI.create(originHeader) }.getOrNull() ?: return false
        return uri.scheme in setOf("http", "https") && uri.host?.lowercase() in LOOPBACK_ORIGIN_HOSTS
    }

    companion object {
        const val TOKEN_HEADER = "X-OpenRune-Studio-Token"
        private const val MIN_TOKEN_LENGTH = 24
        private val LOOPBACK_HOST =
            Regex(
                """^(?:localhost|127\.0\.0\.1|\[::1])(?::\d{1,5})?$""",
                RegexOption.IGNORE_CASE,
            )
        private val LOOPBACK_ORIGIN_HOSTS = setOf("localhost", "127.0.0.1", "::1")

        fun create(environmentToken: String? = System.getenv("OPENRUNE_STUDIO_TOKEN")): CompanionSecurity {
            val configured = environmentToken?.trim().orEmpty()
            if (configured.isNotEmpty()) {
                return CompanionSecurity(configured, generatedToken = false)
            }

            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            return CompanionSecurity(token, generatedToken = true)
        }
    }
}

fun Application.installCompanionSecurity(security: CompanionSecurity) {
    intercept(ApplicationCallPipeline.Plugins) {
        if (!call.request.path().startsWith("/api/v1/")) {
            return@intercept
        }

        val host = call.request.headers[HttpHeaders.Host]
        if (!security.acceptsHost(host)) {
            call.respond(
                HttpStatusCode.Forbidden,
                ApiErrorResponse(
                    code = ApiErrorCode.HOST_NOT_ALLOWED,
                    message = "Companion accepts loopback Host headers only.",
                ),
            )
            finish()
            return@intercept
        }

        val origin = call.request.header(HttpHeaders.Origin)
        if (!security.acceptsOrigin(origin)) {
            call.respond(
                HttpStatusCode.Forbidden,
                ApiErrorResponse(
                    code = ApiErrorCode.ORIGIN_NOT_ALLOWED,
                    message = "Companion accepts loopback browser origins only.",
                ),
            )
            finish()
            return@intercept
        }

        if (!security.acceptsToken(call.request.header(CompanionSecurity.TOKEN_HEADER))) {
            call.respond(
                HttpStatusCode.Unauthorized,
                ApiErrorResponse(
                    code = ApiErrorCode.UNAUTHORIZED,
                    message = "A valid Companion session token is required.",
                ),
            )
            finish()
        }
    }
}
