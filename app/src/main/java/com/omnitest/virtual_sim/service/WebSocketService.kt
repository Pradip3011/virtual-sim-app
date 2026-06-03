package com.omnitest.virtual_sim.service

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SimRegistration(val action: String, val number: String)

class WebSocketService {
    private val client = HttpClient { install(WebSockets) }
    
    private val _incomingMessages = MutableStateFlow<String>("No messages yet")
    val incomingMessages: StateFlow<String> = _incomingMessages

    suspend fun connectAndRegister(myNumber: String = "+91727841422") {
        // Replace "10.0.2.2" with your actual local or hosted server IP address
        client.webSocket(host = "10.0.2.2", port = 8080, path = "/sim-stream") {
            
            // Send registration payload immediately upon connection
            val registration = SimRegistration(action = "REGISTER", number = myNumber)
            send(Frame.Text(Json.encodeToString(registration)))
            
            // Loop infinitely to listen for incoming OTP text data frames
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    _incomingMessages.value = frame.readText()
                }
            }
        }
    }
}
