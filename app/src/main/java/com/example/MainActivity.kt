package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.firebase.FirebaseProvider
import com.example.navigation.SmartSchoolApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseProvider.ensureInitialized(this)
        enableEdgeToEdge()
        setContent {
            SmartSchoolApp()
        }
    }
}
