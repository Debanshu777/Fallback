package com.debanshu777

import com.debanshu777.service.SearchService
import com.debanshu777.service.TwilioService
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import org.slf4j.LoggerFactory

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

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
