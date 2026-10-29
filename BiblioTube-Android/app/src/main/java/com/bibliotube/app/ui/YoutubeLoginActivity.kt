package com.bibliotube.app.ui

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.bibliotube.downloader.BtCookies

/**
 * Login de YouTube dentro de la app: al terminar se exportan las cookies del
 * WebView a cookies.txt (sin PC ni extensiones).
 */
class YoutubeLoginActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        val title = TextView(this).apply {
            text = "Inicia sesion en YouTube"
            textSize = 17f
            setTextColor(Color.BLACK)
            setPadding(28, 28, 28, 4)
        }
        val hint = TextView(this).apply {
            text = "Al terminar pulsa \"Guardar cookies\". Solo se usan para descargar."
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
            setPadding(28, 0, 28, 16)
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(20, 0, 20, 12)
        }
        val cancel = Button(this).apply {
            text = "Cancelar"
            setOnClickListener { finish() }
        }
        val save = Button(this).apply {
            text = "Guardar cookies"
            setOnClickListener { saveCookies() }
        }
        bar.addView(cancel)
        bar.addView(save)

        web = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                // WebView por defecto lleva "wv" en el UA y Google lo bloquea.
                userAgentString =
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    status.text = if (url?.contains("youtube.com") == true) {
                        "Cuando veas tu cuenta arriba, pulsa Guardar cookies."
                    } else {
                        "Inicia sesion con tu cuenta de Google."
                    }
                }
            }
            loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F")
        }

        status = TextView(this).apply {
            text = "Inicia sesion con tu cuenta de Google."
            textSize = 12f
            setTextColor(Color.parseColor("#444444"))
            setPadding(28, 10, 28, 20)
        }

        root.addView(title)
        root.addView(hint)
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(status)
        root.addView(bar)
        setContentView(root)
    }

    private fun saveCookies() {
        val activity = this
        val ctx = applicationContext
        Toast.makeText(this, "Guardando cookies…", Toast.LENGTH_SHORT).show()
        Thread {
            val r = BtCookies.exportWebView(ctx)
            Handler(Looper.getMainLooper()).post {
                r.onSuccess { f ->
                    val n = runCatching { f.readLines().count { it.contains("youtube.com") } }.getOrDefault(0)
                    com.bibliotube.app.data.AppLog.d("dl", "cookies del WebView importadas ($n de youtube)")
                    Toast.makeText(activity, "Cookies guardadas ($n de YouTube)", Toast.LENGTH_LONG).show()
                    setResult(RESULT_OK)
                    finish()
                }.onFailure {
                    Toast.makeText(activity, it.message ?: "No se pudo guardar", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
