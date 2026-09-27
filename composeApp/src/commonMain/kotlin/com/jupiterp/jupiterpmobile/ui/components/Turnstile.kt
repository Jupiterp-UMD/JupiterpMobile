package com.jupiterp.jupiterpmobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Settings for writing reviews from the app.
 *
 * Review submissions are captcha-checked with Cloudflare Turnstile, the same
 * widget the site uses. The site key is public by design (the site ships it
 * to every browser as PUBLIC_TURNSTILE_SITE_KEY); paste the production value
 * here. Blank means no captcha is shown and no token is sent, which is what
 * the API expects when it's deployed without a Turnstile secret.
 *
 * The widget runs in a WebView whose base URL is [TURNSTILE_BASE_URL], so the
 * site key's hostname allowlist must include www.jupiterp.com.
 */
object ReviewConfig {
    const val TURNSTILE_SITE_KEY = ""
    const val TURNSTILE_BASE_URL = "https://www.jupiterp.com/"
    const val REVIEW_POLICY_URL = "https://www.jupiterp.com/review-policy"

    val captchaRequired: Boolean get() = TURNSTILE_SITE_KEY.isNotBlank()
}

/**
 * Renders the Turnstile challenge and reports its token: a non-empty token
 * when solved, null when it expires or errors (tokens last a few minutes, and
 * a considered review often takes longer). Changing [generation] discards the
 * widget and starts a fresh challenge, since each token is single-use.
 */
@Composable
expect fun TurnstileWidget(
    siteKey: String,
    darkTheme: Boolean,
    generation: Int,
    onToken: (String?) -> Unit,
    modifier: Modifier = Modifier
)

/** Page hosting the widget; posts tokens to whichever native bridge exists. */
internal fun turnstileHtml(siteKey: String, darkTheme: Boolean): String {
    val theme = if (darkTheme) "dark" else "light"
    // The key is a Cloudflare site key (alphanumerics, "_" and "-"); strip
    // anything else rather than interpolate it into script
    val safeKey = siteKey.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
    return """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <style>html,body{margin:0;padding:0;background:transparent;}#w{display:flex;justify-content:center;}</style>
          <script src="https://challenges.cloudflare.com/turnstile/v0/api.js?onload=jupiterpReady&render=explicit" async defer></script>
        </head>
        <body>
          <div id="w"></div>
          <script>
            function send(token) {
              if (window.JupiterpBridge) { window.JupiterpBridge.onToken(token); }
              else if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.jupiterp) {
                window.webkit.messageHandlers.jupiterp.postMessage(token);
              }
            }
            function jupiterpReady() {
              turnstile.render('#w', {
                sitekey: '$safeKey',
                theme: '$theme',
                size: 'flexible',
                callback: function (token) { send(token); },
                'expired-callback': function () { send(''); },
                'error-callback': function () { send(''); }
              });
            }
          </script>
        </body>
        </html>
    """.trimIndent()
}
