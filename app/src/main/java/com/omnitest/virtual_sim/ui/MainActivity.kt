package com.omnitest.virtual_sim.ui

import android.content.*
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitest.virtual_sim.service.SimForegroundService
import com.omnitest.virtual_sim.utils.OtpParser

class MainActivity : ComponentActivity() {
    private var incomingMessageByBroadcast = mutableStateOf("Waiting for active message streams...")

    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra("sms_payload")?.let {
                incomingMessageByBroadcast.value = it
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // TC_038 & TC_039: Protect sensitive data stream from system display scraping
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        registerReceiver(smsReceiver, IntentFilter("com.omnitest.SMS_RECEIVED"), RECEIVER_EXPORTED)

        setContent {
            var isLoggedIn by remember { mutableStateOf(false) }
            
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (!isLoggedIn) {
                        LoginView { isLoggedIn = true; startSimEngineService() }
                    } else {
                        DashboardView(incomingMessageByBroadcast.value)
                    }
                }
            }
        }
    }

    private fun startSimEngineService() {
        startService(Intent(this, SimForegroundService::class.java))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(smsReceiver)
    }
}

@Composable
fun LoginView(onAuthSuccess: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Virtual SIM Portal", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Registered Email ID") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { if (name.isNotBlank() && email.contains("@")) onAuthSuccess() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Login & Authenticate")
        }
    }
}

@Composable
fun DashboardView(rawSmsPayload: String) {
    val clipboardManager = LocalClipboardManager.current
    val extractedOtp = remember(rawSmsPayload) { OtpParser.extractOtp(rawSmsPayload) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Active SIM Workspace", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))
        
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(12.dp).background(MaterialTheme.colorScheme.primary, shape = CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
                Text("SIM Network Status: Active Running", fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
        Text("Live Log Monitoring Stream", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedCard(modifier = Modifier.fillMaxWidth().height(150.dp)) {
            Text(text = rawSmsPayload, modifier = Modifier.padding(16.dp), fontSize = 15.sp)
        }

        extractedOtp?.let { otpCode ->
            Spacer(modifier = Modifier.height(24.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Auto-Extracted Authentication Pin", fontSize = 13.sp)
                    Text(text = otpCode, fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, color = MaterialTheme.colorScheme.tertiary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { clipboardManager.setText(AnnotatedString(otpCode)) }) {
                        Text("Copy OTP Token (TC_024)")
                    }
                }
            }
        }
    }
}