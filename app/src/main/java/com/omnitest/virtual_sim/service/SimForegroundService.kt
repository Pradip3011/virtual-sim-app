package com.omnitest.virtual_sim.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.omnitest.virtual_sim.model.StreamRegistration
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SimForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "CHANNEL_SIM_ENGINE"
        const val NOTIFICATION_ID = 1001

        const val EXTRA_CLIENT_ID = "KEY_CLIENT_ID"

        const val ACTION_STREAM_MESSAGE = "com.omnitest.STREAM_MESSAGE_RECEIVED"
        const val EXTRA_STREAM_PAYLOAD = "stream_payload"

        // Emulator host alias for local dev:
        // 10.0.2.2 points from Android emulator to host machine.
        private const val WS_HOST = "10.0.2.2"
        private const val WS_PORT = 8080
        private const val WS_PATH = "/sim-stream"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var streamJob: Job? = null

    private val client = HttpClient(CIO) {
        install(WebSockets)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val clientId = intent?.getStringExtra(EXTRA_CLIENT_ID)?.takeIf { it.isNotBlank() }
            ?: "android-client"

        startForeground(NOTIFICATION_ID, buildNotification(clientId))
        startNetworkStream(clientId)

        return START_STICKY
    }

    private fun buildNotification(clientId: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Realtime stream active")
            .setContentText("Connected as $clientId")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startNetworkStream(clientId: String) {
        streamJob?.cancel()

        streamJob = serviceScope.launch {
            var retryDelayMs = 5_000L

            while (isActive) {
                try {
                    client.webSocket(
                        host = WS_HOST,
                        port = WS_PORT,
                        path = WS_PATH
                    ) {
                        val registration = StreamRegistration(
                            action = "REGISTER",
                            clientId = clientId
                        )

                        send(Frame.Text(Json.encodeToString(registration)))

                        // Reset retry delay after a successful connection
                        retryDelayMs = 5_000L

                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val message = frame.readText()
                                broadcastIncomingMessage(message)
                            }
                        }
                    }
                } catch (_: Exception) {
                    delay(retryDelayMs)
                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(60_000L)
                }
            }
        }
    }

    private fun broadcastIncomingMessage(payload: String) {
        val broadcastIntent = Intent(ACTION_STREAM_MESSAGE).apply {
            putExtra(EXTRA_STREAM_PAYLOAD, payload)
        }
        sendBroadcast(broadcastIntent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Virtual SIM Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground service for realtime stream connectivity"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        streamJob?.cancel()
        serviceScope.cancel()
        client.close()
        super.onDestroy()
    }
}
