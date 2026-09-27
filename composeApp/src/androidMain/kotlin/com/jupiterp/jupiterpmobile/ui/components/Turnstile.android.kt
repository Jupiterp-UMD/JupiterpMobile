package com.jupiterp.jupiterpmobile.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Receives tokens from the page. Called on a WebView thread, so it hops to the view's thread. */
private class TurnstileBridge(
    private val view: WebView,
    private val onToken: (String?) -> Unit
) {
    @JavascriptInterface
    fun onToken(token: String) {
        view.post { onToken(token.ifEmpty { null }) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun TurnstileWidget(
    siteKey: String,
    darkTheme: Boolean,
    generation: Int,
    onToken: (String?) -> Unit,
    modifier: Modifier
) {
    val currentOnToken by rememberUpdatedState(onToken)
    key(generation, darkTheme) {
        AndroidView(
            modifier = modifier.fillMaxWidth().height(72.dp),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    setBackgroundColor(Color.TRANSPARENT)
                    addJavascriptInterface(TurnstileBridge(this) { currentOnToken(it) }, "JupiterpBridge")
                    loadDataWithBaseURL(
                        ReviewConfig.TURNSTILE_BASE_URL,
                        turnstileHtml(siteKey, darkTheme),
                        "text/html",
                        "utf-8",
                        null
                    )
                }
            },
            onRelease = { it.destroy() }
        )
    }
}
