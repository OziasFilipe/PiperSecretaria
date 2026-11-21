package com.piper.secretaria

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import com.piper.secretaria.ui.DeepLink
import com.piper.secretaria.ui.MainViewModel
import com.piper.secretaria.ui.PiperRoot
import com.piper.secretaria.ui.theme.PiperTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val deepLink = mutableStateOf<DeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLink.value = intent.toDeepLink()
        setContent {
            PiperTheme {
                PiperRoot(vm, deepLink.value, onDeepLinkHandled = { deepLink.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLink.value = intent.toDeepLink()
    }

    private fun Intent.toDeepLink(): DeepLink? {
        val route = getStringExtra(EXTRA_ROUTE) ?: return null
        return DeepLink(route, getLongExtra(EXTRA_ITEM_ID, 0), System.nanoTime())
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        const val EXTRA_ITEM_ID = "item_id"
    }
}
