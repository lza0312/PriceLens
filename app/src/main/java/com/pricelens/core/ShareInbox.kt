package com.pricelens.core

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 接收其他 App 分享来的文本，交给采集页预填。 */
object ShareInbox {

    private val mutableText = MutableStateFlow<String?>(null)
    val text: StateFlow<String?> = mutableText

    fun acceptIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!shared.isNullOrBlank()) mutableText.value = shared
        }
    }

    fun consume() {
        mutableText.value = null
    }
}
