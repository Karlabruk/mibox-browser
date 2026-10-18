package com.mibox.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.text.InputType
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var cursor: CursorView
    private lateinit var adBlocker: AdBlocker

    private val handler = Handler(Looper.getMainLooper())
    private var centerLongPressed = false
    private val openMenuRunnable = Runnable {
        centerLongPressed = true
        showMenu()
    }

    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adBlocker = AdBlocker(applicationContext)

        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        webView = WebView(this)
        cursor = CursorView(this)
        root.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(cursor, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        setupWebView()
        root.post { cursor.moveTo(root.width / 2f, root.height / 2f) }

        webView.loadUrl(HOME)
        Toast.makeText(this, "Segure o botão OK para abrir o menu", Toast.LENGTH_LONG).show()

        if (adBlocker.needsUpdate()) updateBlocklist(silent = true)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.javaScriptCanOpenWindowsAutomatically = false
        s.setSupportMultipleWindows(true)
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        // Mostra a versão "desktop" dos sites, melhor para TV
        s.userAgentString = s.userAgentString
            .replace(Regex("\\(Linux; Android[^)]*\\)"), "(X11; Linux x86_64)")
            .replace(" Mobile", "")
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                return adBlocker.intercept(request?.url?.host)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return true
                return when (url.scheme) {
                    "https" -> adBlocker.isBlocked(url.host)
                    "http" -> {
                        view?.loadUrl(url.buildUpon().scheme("https").build().toString())
                        true
                    }
                    else -> true // bloqueia intent:, market:, etc.
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(CLEAN_JS, null)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            // Bloqueia TODAS as janelas pop-up / pop-under
            override fun onCreateWindow(
                view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?
            ): Boolean = false

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) return
                customView = view
                customCallback = callback
                root.addView(view, 1, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                webView.visibility = View.GONE
            }

            override fun onHideCustomView() {
                hideCustomView()
            }
        }
    }

    private fun hideCustomView() {
        val v = customView ?: return
        root.removeView(v)
        customView = null
        webView.visibility = View.VISIBLE
        customCallback?.onCustomViewHidden()
        customCallback = null
    }

    // ---------- Controle remoto ----------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val step = (8 + minOf(event.repeatCount, 25)) * resources.displayMetrics.density
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> moveCursor(0f, -step)
                        KeyEvent.KEYCODE_DPAD_DOWN -> moveCursor(0f, step)
                        KeyEvent.KEYCODE_DPAD_LEFT -> moveCursor(-step, 0f)
                        else -> moveCursor(step, 0f)
                    }
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (event.repeatCount == 0) {
                        centerLongPressed = false
                        handler.postDelayed(openMenuRunnable, 700)
                    }
                } else if (event.action == KeyEvent.ACTION_UP) {
                    handler.removeCallbacks(openMenuRunnable)
                    if (!centerLongPressed) clickAtCursor()
                }
                return true
            }

            KeyEvent.KEYCODE_MENU -> {
                if (event.action == KeyEvent.ACTION_UP) showMenu()
                return true
            }

            KeyEvent.KEYCODE_BACK -> {
                if (event.action == KeyEvent.ACTION_UP) {
                    when {
                        customView != null -> hideCustomView()
                        webView.canGoBack() -> webView.goBack()
                        else -> finish()
                    }
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun moveCursor(dx: Float, dy: Float) {
        val w = root.width.toFloat()
        val h = root.height.toFloat()
        val edge = 48f * resources.displayMetrics.density
        val x = cursor.cx
        val y = cursor.cy
        // Encostou na borda da tela: rola a página
        when {
            dy < 0 && y <= edge -> webView.scrollBy(0, (dy * 3).toInt())
            dy > 0 && y >= h - edge -> webView.scrollBy(0, (dy * 3).toInt())
            dx < 0 && x <= edge -> webView.scrollBy((dx * 3).toInt(), 0)
            dx > 0 && x >= w - edge -> webView.scrollBy((dx * 3).toInt(), 0)
        }
        cursor.moveTo((x + dx).coerceIn(0f, w - 1f), (y + dy).coerceIn(0f, h - 1f))
    }

    private fun clickAtCursor() {
        val target: View = customView ?: webView
        val x = cursor.cx
        val y = cursor.cy
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        target.dispatchTouchEvent(down)
        down.recycle()
        handler.postDelayed({
            val up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
            up.source = InputDevice.SOURCE_TOUCHSCREEN
            target.dispatchTouchEvent(up)
            up.recycle()
        }, 60)
    }

    // ---------- Menu ----------

    private fun showMenu() {
        val items = arrayOf(
            "Ir para endereço / pesquisar",
            "Voltar",
            "Avançar",
            "Recarregar",
            "Página inicial",
            "Atualizar lista de bloqueio",
            "Sair"
        )
        AlertDialog.Builder(this)
            .setTitle("Menu  •  ${adBlocker.blockedCount} bloqueados")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> askUrl()
                    1 -> if (webView.canGoBack()) webView.goBack()
                    2 -> if (webView.canGoForward()) webView.goForward()
                    3 -> webView.reload()
                    4 -> webView.loadUrl(HOME)
                    5 -> updateBlocklist(silent = false)
                    6 -> finish()
                }
            }
            .show()
    }

    private fun askUrl() {
        val input = EditText(this)
        input.setSingleLine()
        input.hint = "Endereço ou pesquisa"
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        input.imeOptions = EditorInfo.IME_ACTION_GO

        val dialog = AlertDialog.Builder(this)
            .setTitle("Ir para")
            .setView(input)
            .setPositiveButton("Ir") { _, _ -> go(input.text.toString()) }
            .setNegativeButton("Cancelar", null)
            .create()
        input.setOnEditorActionListener { _, _, _ ->
            dialog.dismiss()
            go(input.text.toString())
            true
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        input.requestFocus()
    }

    private fun go(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val url = when {
            t.startsWith("https://") -> t
            t.startsWith("http://") -> "https://" + t.removePrefix("http://")
            t.contains(" ") || !t.contains(".") -> "https://duckduckgo.com/?q=" + Uri.encode(t)
            else -> "https://$t"
        }
        webView.loadUrl(url)
    }

    private fun updateBlocklist(silent: Boolean) {
        if (!silent) Toast.makeText(this, "Atualizando lista de bloqueio...", Toast.LENGTH_SHORT).show()
        thread {
            val ok = adBlocker.update()
            runOnUiThread {
                if (ok) {
                    Toast.makeText(this, "Lista atualizada: ${adBlocker.size()} domínios bloqueados", Toast.LENGTH_LONG).show()
                } else if (!silent) {
                    Toast.makeText(this, "Não foi possível atualizar a lista", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ---------- Ciclo de vida ----------

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        root.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val HOME = "https://duckduckgo.com/"

        // Esconde anúncios restantes e faz links "nova aba" abrirem na mesma aba
        private val CLEAN_JS = """
            (function(){
              try {
                var st = document.createElement('style');
                st.textContent = 'ins.adsbygoogle,.adsbygoogle,[id^="google_ads_iframe"],iframe[src*="doubleclick.net"],iframe[src*="googlesyndication.com"]{display:none !important}';
                document.head.appendChild(st);
                document.querySelectorAll('a[target="_blank"]').forEach(function(a){ a.target = '_self'; });
              } catch(e) {}
            })();
        """.trimIndent()
    }
}
