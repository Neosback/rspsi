package com.openrune.studio.companion

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompanionServerTest {
    @Test
    fun statusEndpointExposesVersionedCompanionIdentity() = testApplication {
        application {
            companionModule()
        }

        val response = client.get("/api/v1/status")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<String>()
        assertTrue(body.contains("\"name\":\"OpenRune Studio Companion\""))
        assertTrue(body.contains("\"apiVersion\":1"))
        assertTrue(body.contains("\"status\":\"ready\""))
        assertTrue(body.contains("\"capabilities\":[]"))
    }
}
