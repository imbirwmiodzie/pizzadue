package com.vibepizzaiolo.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

/**
 * The pizza reel in the browser: one page that films with the phone's camera and edits the reel
 * on the phone itself. Nothing is uploaded, so the server only hands out the files.
 */
private object ReelPage {
    private fun load(name: String) = ReelPage::class.java.getResourceAsStream("/reel/$name")!!.use { it.readBytes() }
    val html = load("index.html")
    val files = mapOf(
        "app.js" to (ContentType.Text.JavaScript.withParameter("charset", "utf-8") to load("app.js")),
        "app.css" to (ContentType.Text.CSS.withParameter("charset", "utf-8") to load("app.css")),
        "pronta.mp3" to (ContentType("audio", "mpeg") to load("pronta.mp3")),
    )
}

private suspend fun ApplicationCall.reelFile(type: ContentType, bytes: ByteArray) {
    // only this server's own files; the camera's picture and the finished reel are blob: and mediastream: URLs
    response.header(
        "Content-Security-Policy",
        "default-src 'self'; media-src 'self' blob: mediastream:; img-src 'self' blob: data:; frame-ancestors 'none'",
    )
    response.header("Permissions-Policy", "camera=(self), microphone=(self)")
    response.header("X-Content-Type-Options", "nosniff")
    response.header("Referrer-Policy", "no-referrer")
    response.header(HttpHeaders.CacheControl, "no-cache")
    respondBytes(bytes, type)
}

fun Application.reelRoutes() {
    routing {
        get("/reel") { call.reelFile(ContentType.Text.Html.withParameter("charset", "utf-8"), ReelPage.html) }
        get("/reel/{file}") {
            val (type, bytes) = ReelPage.files[call.param("file")] ?: refuse(HttpStatusCode.NotFound, "No such file.")
            call.reelFile(type, bytes)
        }
    }
}
