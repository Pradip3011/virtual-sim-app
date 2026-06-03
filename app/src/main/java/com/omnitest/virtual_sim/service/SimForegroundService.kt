package com.omnitest.virtual_sim.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SimRegistration(val action: String, val number: String)

class SimForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webSocketJob: Job? = null

    private val client = HttpClient(CIO) {
        install(WebSockets)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val registeredNumber = intent?.getStringExtra("KEY_PHONE_NUMBER") ?: "+91727841422"

        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, "CHANNEL_SIM_ENGINE")
            .setContentTitle("Virtual SIM Line Active")
            .setContentText("Monitoring streams for $registeredNumber")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)
        startNetworkStream(registeredNumber)

        return START_STICKY
    }

    private fun startNetworkStream(phoneNumber: String) {
        webSocketJob?.cancel()

        webSocketJob = serviceScope.launch {
            while (isActive) {
                try {
                    client.webSocket(
                        host = "10.0.2.2",
                        port = 8080,
                        path = "/sim-stream"
                    ) {
                        val regPayload = SimRegistration(
                            action = "REGISTER",
                            number = phoneNumber
                        )

                        send(Frame.Text(Json.encodeToString(regPayload)))

                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val messageBody = frame.readText()
                                broadcastMessageToUi(messageBody)
                            }
                        }
                    }
                } catch (e: Exception) {
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
        webSocketJob?.cancel()
        serviceScope.cancel()
        client.close()
    }
}
