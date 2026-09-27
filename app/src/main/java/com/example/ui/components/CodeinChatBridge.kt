package com.example.ui.components

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/** Bridges the same-origin chat stream from the hidden WebView into Compose. */
class CodeinChatBridge {
    private val mainHandler = Handler(Looper.getMainLooper())

    var onStarted: () -> Unit = {}
    var onToken: (String) -> Unit = {}
    var onFinished: () -> Unit = {}
    var onError: (String) -> Unit = {}

    @JavascriptInterface
    fun started() = mainHandler.post { onStarted() }

    @JavascriptInterface
    fun token(value: String) = mainHandler.post { onToken(value) }

    @JavascriptInterface
    fun finished() = mainHandler.post(onFinished)

    @JavascriptInterface
    fun error(value: String) = mainHandler.post { onError(value) }
}
