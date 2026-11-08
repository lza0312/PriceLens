package com.pricelens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.pricelens.core.ShareInbox
import com.pricelens.ui.AppRoot
import com.pricelens.ui.theme.PriceLensTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 用户可拒绝，不影响 App 内使用，仅影响后台通知 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppRootHostActivity.setup(this)
        ShareInbox.acceptIntent(intent)
        requestNotificationPermission()
        setContent {
            PriceLensTheme {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ShareInbox.acceptIntent(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

private object AppRootHostActivity {
    fun setup(activity: ComponentActivity) {
        // 预留：若后续需要开启 edge-to-edge，可在这里统一处理。
        @Suppress("UNUSED_EXPRESSION")
        activity
    }
}
