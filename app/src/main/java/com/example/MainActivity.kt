package com.smartprocurement.internal

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.smartprocurement.internal.ui.SupplyAppContent
import com.smartprocurement.internal.ui.SupplyViewModel
import com.smartprocurement.internal.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  private lateinit var viewModel: SupplyViewModel

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
      navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
    )

    viewModel = ViewModelProvider(this)[SupplyViewModel::class.java]

    setContent {
      MyApplicationTheme {
        SupplyAppContent(viewModel)
      }
    }
  }

  override fun onResume() {
    super.onResume()
    if (::viewModel.isInitialized) {
      viewModel.refreshActiveData()
    }
  }
}
