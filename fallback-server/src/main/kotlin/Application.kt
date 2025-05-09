package com.debanshu777

import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.serialization.jackson.jackson
import service.TwilioService
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import org.slf4j.LoggerFactory
import service.SearchService

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val logger = LoggerFactory.getLogger("Application")

    // Install content negotiation
    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }

    // Initialize services
    logger.info("Initializing services...")

    logger.info(environment.config.toMap().toString())
    val twilioService = try {
        TwilioService()
    } catch (e: Exception) {
        logger.error("Error initializing Twilio service: ${e.message}", e)
        throw e
    }

    val searchService = SearchService()

    // Configure routing
    configureRouting(twilioService, searchService)

    logger.info("Fallback Server started successfully!")
    logger.info("Listening for SMS webhook requests at /twilio/sms")
}
