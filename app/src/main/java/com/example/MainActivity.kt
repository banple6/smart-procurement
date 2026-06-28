package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.ui.SupplyAppContent
import com.example.ui.SupplyViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val viewModel = ViewModelProvider(this)[SupplyViewModel::class.java]

    setContent {
      MyApplicationTheme {
        SupplyAppContent(viewModel)
      }
    }
  }
}
