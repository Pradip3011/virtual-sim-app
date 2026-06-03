package com.omnitest.virtual_sim.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*

class SimForegroundService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = HttpClient(CIO) { install(WebSockets) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "SIM_CHANNEL")
            .setContentTitle("Virtual SIM Engine Active")
            .setContentText("Listening for real-time OTP transmissions...")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
        connectToWebSocket()
        return START_STICKY
    }

    private fun connectToWebSocket() {
        serviceScope.launch {
            try {
                // Pointing directly to your active backend ngrok tunnel address
                client.webSocket(host = "playtime-facebook-discard.ngrok-free.dev", path = "/ws/sms") {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val textPayload = frame.readText()
                            broadcastIncomingSms(textPayload)
                        }
                    }
                }
            } catch (e: Exception) {
                // TC_008 & TC_012: Graceful exponential fallback on connection drops
                delay(5000)
                connectToWebSocket()
            }
        }
    }

    private fun broadcastIncomingSms(payload: String) {
        val intent = Intent("com.omnitest.SMS_RECEIVED").apply {
            putExtra("sms_payload", payload)
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "SIM_CHANNEL", "Virtual SIM Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        client.close()
    }
}
