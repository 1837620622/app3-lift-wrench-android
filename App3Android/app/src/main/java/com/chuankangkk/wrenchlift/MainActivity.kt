package com.chuankangkk.wrenchlift

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.chuankangkk.wrenchlift.ui.WrenchLiftApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WrenchLiftApp()
        }
    }
}
