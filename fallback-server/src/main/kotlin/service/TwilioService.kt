package com.debanshu777.service

import com.twilio.Twilio
import com.twilio.rest.api.v2010.account.Message
import com.twilio.type.PhoneNumber
import org.slf4j.LoggerFactory

class TwilioService() {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    val twilioAccountSid = System.getenv("TWILIO_ACCOUNT_SID") ?: throw IllegalStateException("TWILIO_ACCOUNT_SID not set")
    val twilioAuthToken = System.getenv("TWILIO_AUTH_TOKEN") ?: throw IllegalStateException("TWILIO_AUTH_TOKEN not set")
    val twilioPhoneNumber = System.getenv("TWILIO_PHONE_NUMBER") ?: throw IllegalStateException("TWILIO_PHONE_NUMBER not set")


    init {
        Twilio.init(twilioAccountSid, twilioAuthToken)
        logger.info("Twilio initialized with account: $twilioAccountSid and phone number: $twilioPhoneNumber")
    }

    /**
     * Sends an SMS with the given content
     */
    fun sendSms(to: String, content: String) {
        try {
            // For very long content, we need to split it into multiple messages
            if (content.length <= 1600) {
                // Standard SMS
                Message.creator(
                    PhoneNumber(to),
                    PhoneNumber(twilioPhoneNumber),
                    "HTML:$content"
                ).create()

                logger.info("SMS sent to $to")
            } else {
                // Content too long, split into multiple parts
                sendMultipartSms(to, content)
            }
        } catch (e: Exception) {
            logger.error("Error sending SMS: ${e.message}", e)
            throw e
        }
    }

    /**
     * Splits long content into multiple SMS messages
     */
    private fun sendMultipartSms(to: String, content: String) {
        // Maximum content length per SMS part (accounting for headers)
        val maxPartSize = 1500

        // Calculate total parts needed
        val parts = content.chunked(maxPartSize)
        val totalParts = parts.size

        logger.info("Sending multipart SMS to $to with $totalParts parts")

        // Send each part with appropriate headers
        parts.forEachIndexed { index, part ->
            val partNumber = index + 1
            val message = "HTML_PART$partNumber/$totalParts:$part"

            Message.creator(
                PhoneNumber(to),
                PhoneNumber(twilioPhoneNumber),
                message
            ).create()

            logger.info("SMS part $partNumber/$totalParts sent to $to")
        }
    }
}