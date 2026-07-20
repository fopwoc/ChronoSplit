package dev.fopwoc.chronosplit.backend

import dev.fopwoc.chronosplit.server.relayModule
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.io.File

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, host = "0.0.0.0", port = port, module = Application::backendModule)
        .start(wait = true)
}

fun Application.backendModule() {
    relayModule(mobileAuthToken = System.getenv("MOBILE_AUTH_TOKEN").orEmpty())
    routing {
        get("/") {
            val directory = webAssetsDirectory()
            val index = directory.resolve("index.html")
            if (index.isFile) call.respondFile(index)
            else call.respondText("ChronoSplit relay is running. Web assets are not installed.")
        }
        get("/{path...}") {
            val directory = webAssetsDirectory().canonicalFile
            val path = call.parameters.getAll("path")?.joinToString("/").orEmpty()
            val asset = directory.resolve(path).canonicalFile
            if (asset.isFile && asset.toPath().startsWith(directory.toPath())) call.respondFile(asset)
            else call.respondText("Not found", status = io.ktor.http.HttpStatusCode.NotFound)
        }
    }
}

private fun webAssetsDirectory(): File = File(System.getenv("WEB_ASSETS_DIR") ?: "app/webApp/build/dist/wasmJs/productionExecutable")
