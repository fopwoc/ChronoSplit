package dev.fopwoc.chronosplit.backend

import dev.fopwoc.chronosplit.server.relayModule
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory
import java.io.File

private val logger = LoggerFactory.getLogger("dev.fopwoc.chronosplit.backend.Application")

fun main() {
    val port = System.getenv("HTTP_PORT")?.toIntOrNull() ?: 8080
    logger.info("Starting standalone relay on port {}", port)
    embeddedServer(Netty, host = "0.0.0.0", port = port, module = Application::backendModule)
        .start(wait = true)
}

fun Application.backendModule() {
    val applicationLogger = log
    applicationLogger.info("Initializing standalone relay")
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
    applicationLogger.info("Standalone relay is ready")
}

private fun webAssetsDirectory(): File = File(System.getenv("WEB_ASSETS_DIR") ?: "app/webApp/build/dist/wasmJs/productionExecutable")
