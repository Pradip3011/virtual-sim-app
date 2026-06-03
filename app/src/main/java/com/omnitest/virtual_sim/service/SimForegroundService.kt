package com.omnitest.virtual_sim.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSocket
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SimRegistration(val action: String, val number: String)

class SimForegroundService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webSocketJob: Job? = null
    private val client = HttpClient { install(WebSockets) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Catch the phone number coming from MainActivity (fallback to default if empty)
        val registeredNumber = intent?.getStringExtra("KEY_PHONE_NUMBER") ?: "+91727841422"

        // 2. Start the system status bar notification required for background services
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "CHANNEL_SIM_ENGINE")
            .setContentTitle("Virtual SIM Line Active")
            .setContentText("Monitoring streams for $registeredNumber")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        startForeground(1, notification)

        // 3. Connect to network stream with the target phone number
        startNetworkStream(registeredNumber)

        return START_STICKY
    }

    private fun startNetworkStream(phoneNumber: String) {
        webSocketJob?.cancel() // Clear any old connections safely
        
        webSocketJob = serviceScope.launch {
            while (isActive) {
                try {
                    // Replace "10.0.2.2" with your actual cloud/backend IP address
                    client.webSocket(host = "10.0.2.2", port = 8080, path = "/sim-stream") {
                        
                        // Send registration payload with your number to the server
                        val regPayload = SimRegistration(action = "REGISTER", number = phoneNumber)
                        send(Frame.Text(Json.encodeToString(regPayload)))

                        // Listen infinitely for incoming calls/OTPs sent by your server
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val messageBody = frame.readText()
                                broadcastMessageToUi(messageBody)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Network dropped or server offline; wait 5 seconds and retry loop
                    delay(5000)
                }
            }
        }
    }

    private fun broadcastMessageToUi(payload: String) {
        val broadcastIntent = Intent("com.omnitest.SMS_RECEIVED").apply {
            putExtra("sms_payload", payload)
        }
        sendBroadcast(broadcastIntent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "CHANNEL_SIM_ENGINE",
                "Virtual SIM Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel() // Stop all background threads cleanly
    }
}
