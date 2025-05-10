package com.debanshu777.service

import com.twilio.Twilio
import com.twilio.rest.api.v2010.account.Message
import com.twilio.type.PhoneNumber
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

class TwilioService(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    val twilioAccountSid = System.getenv("TWILIO_ACCOUNT_SID") ?: throw IllegalStateException("TWILIO_ACCOUNT_SID not set")
    val twilioAuthToken = System.getenv("TWILIO_AUTH_TOKEN") ?: throw IllegalStateException("TWILIO_AUTH_TOKEN not set")
    val twilioPhoneNumber = System.getenv("TWILIO_PHONE_NUMBER") ?: throw IllegalStateException("TWILIO_PHONE_NUMBER not set")
    private val messageQueue = mutableMapOf<String, MutableList<PendingSmsMessage>>()
    @OptIn(ExperimentalAtomicApi::class)
    private val processingQueue = mutableMapOf<String, AtomicBoolean>()

    init {
        Twilio.init(twilioAccountSid, twilioAuthToken)
        logger.info("Twilio initialized with account: $twilioAccountSid and phone number: $twilioPhoneNumber")
    }

    /**
     * Sends an SMS message asynchronously
     * @param to The recipient's phone number
     * @param body The message content
     * @return Status of the message send operation
     */
    private suspend fun sendSms(to: String, body: String): MessageStatus = withContext(dispatcher) {
        return@withContext try {
            logger.info("Sending SMS to $to")
            val message = Message.creator(
                PhoneNumber(to),
                PhoneNumber(twilioPhoneNumber),
                body
            ).create()

            val status = mapTwilioStatus(message.status)
            logger.info("SMS sent to $to with status: $status")
            status
        } catch (e: Exception) {
            logger.error("Failed to send SMS to $to: ${e.message}", e)
            MessageStatus.FAILED
        }
    }

    /**
     * Sends a large message as multiple SMS parts with sequential delivery
     * @param to The recipient's phone number
     * @param fullMessage The complete message to send
     * @param chunkSize Maximum size for each SMS part
     * @return True if all parts were successfully sent, false otherwise
     */
    @OptIn(ExperimentalAtomicApi::class)
    suspend fun sendMultipartSms(
        to: String,
        fullMessage: String,
        chunkSize: Int = 160
    ): Boolean = withContext(dispatcher) {
        logger.info("Preparing to send multipart SMS to $to")

        // Break message into parts
        val parts = fullMessage.chunked(chunkSize)
        val totalParts = parts.size

        if (totalParts == 1) {
            return@withContext sendSms(to, fullMessage) == MessageStatus.DELIVERED
        }

        val queueId = "$to-${System.currentTimeMillis()}"
        val pendingMessages = parts.mapIndexed { index, part ->
            val partNumber = index + 1
            val message = "HTML_PART$partNumber/$totalParts:$part"
            PendingSmsMessage(to, message, partNumber, totalParts)
        }.toMutableList()

        // Add to queue and start processing
        synchronized(messageQueue) {
            messageQueue[queueId] = pendingMessages
            processingQueue[queueId] = AtomicBoolean(false)
        }

        // Process the queue asynchronously
        return@withContext processQueue(queueId)
    }

    /**
     * Processes messages in the queue sequentially
     * @param queueId Identifier for the queue to process
     * @return True if all messages were sent successfully, false otherwise
     */
    @OptIn(ExperimentalAtomicApi::class)
    private suspend fun processQueue(queueId: String): Boolean {
        val isProcessing = processingQueue[queueId] ?: return false

        // If already processing, wait for completion
        if (!isProcessing.compareAndSet(false, true)) {
            return waitForCompletion(queueId)
        }

        try {
            val messages = messageQueue[queueId] ?: return false
            var success = true

            while (messages.isNotEmpty() && success) {
                val message = messages.first()

                // Send message and wait for result
                val status = sendSms(message.to, message.body)
                success = status == MessageStatus.DELIVERED || status == MessageStatus.SENT

                if (success) {
                    logger.info("SMS part ${message.partNumber}/${message.totalParts} delivered to ${message.to}")
                    synchronized(messageQueue) {
                        messages.removeFirst()
                    }
                } else {
                    logger.error("Failed to send SMS part ${message.partNumber}/${message.totalParts} to ${message.to}: $status")
                    // Retry logic could be added here
                    delay(3000) // Wait before retrying
                }
            }

            // Clean up if all messages sent or failed permanently
            synchronized(messageQueue) {
                messageQueue.remove(queueId)
                processingQueue.remove(queueId)
            }

            return success
        } finally {
            isProcessing.store(false)
        }
    }

    /**
     * Waits for queue processing to complete
     * @param queueId Identifier for the queue
     * @return True if the queue was processed successfully
     */
    @OptIn(ExperimentalAtomicApi::class)
    private suspend fun waitForCompletion(queueId: String): Boolean {
        var attempts = 0
        while (processingQueue[queueId] != null && attempts < 30) {
            delay(1000)
            attempts++
        }
        return messageQueue[queueId]?.isEmpty() ?: true
    }

    /**
     * Maps Twilio message status to our internal status
     */
    private fun mapTwilioStatus(status: Message.Status): MessageStatus {
        return when (status) {
            Message.Status.QUEUED -> MessageStatus.QUEUED
            Message.Status.SENDING -> MessageStatus.SENDING
            Message.Status.SENT -> MessageStatus.SENT
            Message.Status.DELIVERED -> MessageStatus.DELIVERED
            Message.Status.FAILED -> MessageStatus.FAILED
            Message.Status.UNDELIVERED -> MessageStatus.UNDELIVERED
            else -> MessageStatus.UNKNOWN
        }
    }

    /**
     * Represents a message pending delivery
     */
    private data class PendingSmsMessage(
        val to: String,
        val body: String,
        val partNumber: Int,
        val totalParts: Int
    )

    /**
     * Represents possible message statuses
     */
    enum class MessageStatus {
        QUEUED,
        SENDING,
        SENT,
        FAILED,
        DELIVERED,
        UNDELIVERED,
        UNKNOWN
    }
}