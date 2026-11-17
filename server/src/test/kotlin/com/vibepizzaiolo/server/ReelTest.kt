package com.vibepizzaiolo.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReelTest {
    private fun server(test: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) = testApplication {
        val db = Db.connect("jdbc:h2:mem:r${System.nanoTime()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", null, null).apply { migrate() }
        application { module(db, PhotoStore(Files.createTempDirectory("photos").toFile()), null) }
        test()
    }

    @Test
    fun `the reel page and its files are served with camera allowed`() = server {
        val page = client.get("/reel")
        assertEquals(HttpStatusCode.OK, page.status)
        assertTrue(page.bodyAsText().contains("/reel/app.js"))
        assertTrue(page.headers["Permissions-Policy"]!!.contains("camera=(self)"))
        assertTrue(page.headers["Content-Security-Policy"]!!.contains("media-src 'self' blob: mediastream:"))

        assertTrue(client.get("/reel/app.js").bodyAsText().contains("getUserMedia"))
        assertEquals(HttpStatusCode.OK, client.get("/reel/app.css").status)
        val voice = client.get("/reel/pronta.mp3")
        assertEquals("audio/mpeg", voice.headers["Content-Type"])
        assertTrue(voice.readRawBytes().size > 1000)

        assertEquals(HttpStatusCode.NotFound, client.get("/reel/../Main.kt").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/reel/nope.js").status)
    }
}
