package com.openrune.studio.service

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.request.header
import io.ktor.server.request.path
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class StudioServiceSecurity(
    val token: String,
    val generatedToken: Boolean = false,
) {
    init {
        require(token.length >= MIN_TOKEN_LENGTH) {
            "Studio service token must contain at least $MIN_TOKEN_LENGTH characters"
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

        fun create(environmentToken: String? = System.getenv("OPENRUNE_STUDIO_TOKEN")): StudioServiceSecurity {
            val configured = environmentToken?.trim().orEmpty()
            if (configured.isNotEmpty()) {
                return StudioServiceSecurity(configured, generatedToken = false)
            }

            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            return StudioServiceSecurity(token, generatedToken = true)
        }
    }
}

private class StudioServiceSecurityConfig {
    lateinit var security: StudioServiceSecurity
}

private val StudioServiceSecurityPlugin =
    createApplicationPlugin(
        name = "OpenRuneStudioServiceSecurity",
        createConfiguration = ::StudioServiceSecurityConfig,
    ) {
        val security = pluginConfig.security

        onCall { call ->
            if (!call.request.path().startsWith("/api/v1/")) {
                return@onCall
            }

            val host = call.request.headers[HttpHeaders.Host]
            if (!security.acceptsHost(host)) {
                throw ApiException(
                    code = ApiErrorCode.HOST_NOT_ALLOWED,
                    status = HttpStatusCode.Forbidden,
                    message = "Studio service accepts loopback Host headers only.",
                )
            }

            val origin = call.request.header(HttpHeaders.Origin)
            if (!security.acceptsOrigin(origin)) {
                throw ApiException(
                    code = ApiErrorCode.ORIGIN_NOT_ALLOWED,
                    status = HttpStatusCode.Forbidden,
                    message = "Studio service accepts loopback browser origins only.",
                )
            }

            if (!security.acceptsToken(call.request.header(StudioServiceSecurity.TOKEN_HEADER))) {
                throw ApiException(
                    code = ApiErrorCode.UNAUTHORIZED,
                    status = HttpStatusCode.Unauthorized,
                    message = "A valid Studio service session token is required.",
                )
            }
        }
    }

fun Application.installStudioServiceSecurity(security: StudioServiceSecurity) {
    install(StudioServiceSecurityPlugin) {
        this.security = security
    }
}
