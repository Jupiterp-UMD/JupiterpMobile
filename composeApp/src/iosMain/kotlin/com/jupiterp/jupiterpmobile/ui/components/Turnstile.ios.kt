@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.jupiterp.jupiterpmobile.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.UIKit.UIColor
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

private const val HANDLER_NAME = "jupiterp"

/** Receives tokens posted by the page through `webkit.messageHandlers.jupiterp`. */
private class TurnstileMessageHandler(
    private val onMessage: (String) -> Unit
) : NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage
    ) {
        (didReceiveScriptMessage.body as? String)?.let(onMessage)
    }
}

@Composable
actual fun TurnstileWidget(
    siteKey: String,
    darkTheme: Boolean,
    generation: Int,
    onToken: (String?) -> Unit,
    modifier: Modifier
) {
    val currentOnToken by rememberUpdatedState(onToken)
    val handler = remember { TurnstileMessageHandler { token -> currentOnToken(token.ifEmpty { null }) } }
    key(generation, darkTheme) {
        UIKitView(
            factory = {
                val configuration = WKWebViewConfiguration()
                configuration.userContentController.addScriptMessageHandler(handler, HANDLER_NAME)
                WKWebView(frame = CGRectZero.readValue(), configuration = configuration).apply {
                    setOpaque(false)
                    backgroundColor = UIColor.clearColor
                    scrollView.scrollEnabled = false
                    loadHTMLString(
                        turnstileHtml(siteKey, darkTheme),
                        baseURL = NSURL.URLWithString(ReviewConfig.TURNSTILE_BASE_URL)
                    )
                }
            },
            modifier = modifier.fillMaxWidth().height(72.dp),
            onRelease = { webView ->
                // The content controller retains its handler; release it with the view
                webView.configuration.userContentController.removeScriptMessageHandlerForName(HANDLER_NAME)
            }
        )
    }
}
