package com.debanshu.fallback

import android.Manifest
import com.debanshu.fallback.ui.theme.FallbackTheme
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity(), SMSReceiver.SMSListener {

    private val SMS_PERMISSION_REQUEST_CODE = 1001
    private val SERVER_PHONE_NUMBER = "+18052510397"
    private val smsReceiver = SMSReceiver()


    private val _htmlContent = MutableStateFlow(
        "<html><body><h1>Emergency Browser</h1><p>Search for content when there's no internet.</p></body></html>"
    )
    private val htmlContent = _htmlContent.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestSmsPermissions()

        SMSReceiver.listener = this
        registerReceiver(smsReceiver, IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION))

        setContent {
            FallbackTheme {
                EmergencyBrowserApp(htmlContent) { query ->
                    sendSearchSms(query)
                }
            }
        }
    }

    @Composable
    fun EmergencyBrowserApp(
        htmlContent: StateFlow<String>,
        onSearch: (String) -> Unit
    ) {
        var searchQuery by remember { mutableStateOf("") }
        val currentHtmlContent by htmlContent.collectAsState()
        var isLoading by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        if (searchQuery.isNotBlank()) {
                            onSearch(searchQuery)
                            isLoading = true
                        }
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                            }
                        }
                        settings.javaScriptEnabled = true
                    }
                },
                update = { webView ->
                    webView.loadDataWithBaseURL(
                        null,
                        currentHtmlContent,
                        "text/html",
                        "UTF-8",
                        null
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(smsReceiver)
        SMSReceiver.listener = null
    }

    override fun onSMSReceived(html: String) {
        _htmlContent.tryEmit(html)
    }

    private fun requestSmsPermissions() {
        val permissions = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest,
                SMS_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun sendSearchSms(query: String) {
        try {
            val smsManager = SmsManager.getDefault()
            val message = "SEARCH:$query"
            smsManager.sendTextMessage(SERVER_PHONE_NUMBER, null, message, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}