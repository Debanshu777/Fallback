package com.debanshu777

import com.debanshu777.service.SearchService
import com.debanshu777.service.TwilioService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

fun Application.configureRouting(
    twilioService: TwilioService,
    searchService: SearchService
) {
    val logger = LoggerFactory.getLogger("Routing")

    routing {
        // Health check endpoint
        get("/") {
            call.respondText("Fallback Server is running!")
        }

        // Endpoint to receive SMS from Twilio
        post("/twilio/sms") {
            // Parse the form parameters from Twilio's webhook
            val parameters = call.receiveParameters()

            // Extract the necessary information
            val from = parameters["From"] ?: run {
                logger.error("Missing 'From' parameter in Twilio webhook")
                call.respond(HttpStatusCode.BadRequest, "Missing 'From' parameter")
                return@post
            }

            val body = parameters["Body"] ?: run {
                logger.error("Missing 'Body' parameter in Twilio webhook")
                call.respond(HttpStatusCode.BadRequest, "Missing 'Body' parameter")
                return@post
            }

            logger.info("Received SMS from $from: $body")

            // Process the SMS message
            if (body.startsWith("SEARCH:", ignoreCase = true)) {
                // Extract the search query
                val query = body.substringAfter("SEARCH:", "").trim()

                if (query.isNotEmpty()) {
                    // Perform the search
                    val searchResults = searchService.search(query)

                    // Send the results back via SMS
                    twilioService.sendMultipartSms(from, searchResults)

                    logger.info("Search results for '$query' sent to $from")
                } else {
                    twilioService.sendMultipartSms(
                        from,
                        buildEmptyQueryHtml()
                    )
                    logger.warn("Empty search query received from $from")
                }
            } else if (body.startsWith("GET:", ignoreCase = true)) {
                // Extract the URL
                val url = body.substringAfter("GET:", "").trim()

                if (url.isNotEmpty() && url.startsWith("http")) {
                    // Fetch the content
                    val content = searchService.getContent(url)

                    // Send it back via SMS
                    twilioService.sendMultipartSms(from, content)

                    logger.info("Content for '$url' sent to $from")
                } else {
                    twilioService.sendMultipartSms(
                        from,
                        buildInvalidUrlHtml(url)
                    )
                    logger.warn("Invalid URL received from $from: $url")
                }
            } else {
                // Unknown command
                twilioService.sendMultipartSms(
                    from,
                    buildHelpHtml()
                )
                logger.warn("Unknown command received from $from: $body")
            }

            // Respond to Twilio with an empty TwiML response
            // We don't want Twilio to send any automatic replies
            call.respondText(
                """<?xml version="1.0" encoding="UTF-8"?><Response></Response>""",
                contentType = ContentType.Application.Xml
            )
        }
    }
}

private fun buildEmptyQueryHtml(): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Empty Query</title>
            <style>
                body { font-family: Arial, sans-serif; margin: 8px; }
                h1 { font-size: 18px; color: #f57c00; }
                .message { font-size: 14px; }
            </style>
        </head>
        <body>
            <h1>Empty Search Query</h1>
            <div class="message">
                Please provide a search term after "SEARCH:". For example, "SEARCH:emergency services"
            </div>
        </body>
        </html>
    """.trimIndent()
}

private fun buildInvalidUrlHtml(url: String): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Invalid URL</title>
            <style>
                body { font-family: Arial, sans-serif; margin: 8px; }
                h1 { font-size: 18px; color: #f57c00; }
                .message { font-size: 14px; }
            </style>
        </head>
        <body>
            <h1>Invalid URL</h1>
            <div class="message">
                The URL "$url" is invalid. Please provide a valid URL starting with http:// or https://
            </div>
        </body>
        </html>
    """.trimIndent()
}

private fun buildHelpHtml(): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Fallback Help</title>
            <style>
                body { font-family: Arial, sans-serif; margin: 8px; }
                h1 { font-size: 18px; }
                .commands { margin-top: 12px; }
                .command { margin-bottom: 8px; }
                .code { font-family: monospace; font-weight: bold; }
            </style>
        </head>
        <body>
            <h1>Fallback Emergency Browser Help</h1>
            <div class="message">
                Available commands:
            </div>
            <div class="commands">
                <div class="command">
                    <span class="code">SEARCH:query</span> - Search for information
                </div>
                <div class="command">
                    <span class="code">GET:url</span> - Get content from a specific URL
                </div>
            </div>
        </body>
        </html>
    """.trimIndent()
}
