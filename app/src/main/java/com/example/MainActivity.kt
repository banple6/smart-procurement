package com.smartprocurement.internal

import android.content.Intent
import android.os.Bundle
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
    enableEdgeToEdge()

    viewModel = ViewModelProvider(this)[SupplyViewModel::class.java]
    dispatchPushIntent(intent)

    setContent {
      MyApplicationTheme {
        SupplyAppContent(viewModel)
      }
    }
  }

  override fun onResume() {
    super.onResume()
    if (::viewModel.isInitialized) {
      viewModel.onAppResumed()
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    dispatchPushIntent(intent)
  }

  private fun dispatchPushIntent(intent: Intent?) {
    if (!::viewModel.isInitialized || intent == null) return
    viewModel.handlePushIntent(
      mapOf(
        "event_id" to intent.getStringExtra("event_id").orEmpty(),
        "event_type" to intent.getStringExtra("event_type").orEmpty(),
        "order_id" to intent.getStringExtra("order_id").orEmpty()
      )
    )
  }
}
