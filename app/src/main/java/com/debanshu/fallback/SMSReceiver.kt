package com.debanshu.fallback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.util.Log

class SMSReceiver : BroadcastReceiver() {
    interface SMSListener {
        fun onSMSReceived(html: String)
    }

    companion object {
        private const val TAG = "com.debanshu.fallback.SMSReceiver"
        private const val SERVER_PHONE_NUMBER = "+1234567890" // Replace with your actual number
        private const val HTML_PREFIX = "HTML:"
        private const val HTML_PART_PREFIX = "HTML_PART"

        var listener: SMSListener? = null

        private val multipartMessages = mutableMapOf<String, MutableList<Pair<Int, String>>>()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            // Check if this is a legitimate SMS broadcast
            if (isLegitimateSmsBroadcast(context, intent)) {
                processSmsIntent(intent)
            } else {
                Log.w(TAG, "Received SMS intent from unauthorized sender")
            }
        }
    }

    private fun isLegitimateSmsBroadcast(context: Context, intent: Intent): Boolean {
        // The SMS broadcasts typically come from the system with no specific package
        // Or they come from the telephony provider

        // Method 1: Check if intent has extra data that only system SMS broadcasts would have
        if (Telephony.Sms.Intents.getMessagesFromIntent(intent).isNotEmpty()) {
            return true
        }

        // Method 2: Check sender package if available
        val senderPackage = intent.getPackage()
        if (senderPackage != null) {
            // Check if it's from a system telephony provider
            try {
                val packageInfo = context.packageManager.getPackageInfo(senderPackage, 0)
                val appInfo = packageInfo.applicationInfo

                // Check if it's a system app
                if ((appInfo?.flags?.and(android.content.pm.ApplicationInfo.FLAG_SYSTEM)) != 0) {
                    return true
                }
            } catch (e: PackageManager.NameNotFoundException) {
                // Package not found, likely not legitimate
                return false
            }
        }

        // Fallback - if we got this far with no package info but have a valid SMS_RECEIVED action,
        // it's likely from the system
        return true
    }

    private fun processSmsIntent(intent: Intent) {
        for (smsMessage in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
            val sender = smsMessage.displayOriginatingAddress
            val messageBody = smsMessage.messageBody

            Log.d(TAG, "SMS received from $sender: $messageBody")

            // Only process messages from our server number
            if (sender == SERVER_PHONE_NUMBER) {
                if (messageBody.startsWith(HTML_PREFIX)) {
                    val htmlContent = messageBody.substring(HTML_PREFIX.length)
                    listener?.onSMSReceived(htmlContent)
                } else if (messageBody.startsWith(HTML_PART_PREFIX)) {
                    processMultipartMessage(sender, messageBody)
                }
            }
        }
    }

    private fun processMultipartMessage(sender: String, messageBody: String) {
        try {
            val partInfo = messageBody.substringAfter(HTML_PART_PREFIX).substringBefore(":")
            val partNumber = partInfo.substringBefore("/").toInt()
            val totalParts = partInfo.substringAfter("/").toInt()
            val messageId = "$sender:$totalParts"
            val content = messageBody.substringAfter(":")

            if (!multipartMessages.containsKey(messageId)) {
                multipartMessages[messageId] = mutableListOf()
            }
            multipartMessages[messageId]?.add(Pair(partNumber, content))

            val parts = multipartMessages[messageId]
            if (parts != null && parts.size == totalParts) {
                val fullMessage = parts.sortedBy { it.first }
                    .joinToString("") { it.second }

                listener?.onSMSReceived(fullMessage)
                multipartMessages.remove(messageId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing multipart SMS", e)
        }
    }
}