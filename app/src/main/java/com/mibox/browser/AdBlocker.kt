package com.mibox.browser

import android.content.Context
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Bloqueador de anúncios, rastreadores e domínios maliciosos baseado em listas de domínios.
 * Começa com uma lista pequena embutida e, em segundo plano, carrega/atualiza listas completas.
 */
class AdBlocker(context: Context) {

    private val listFile = File(context.filesDir, "blocklist.txt")

    @Volatile
    private var blocked: Set<String> = HashSet(BUILTIN)
    private val counter = AtomicInteger(0)

    val blockedCount: Int
        get() = counter.get()

    init {
        thread { load() }
    }

    fun size(): Int = blocked.size

    fun needsUpdate(): Boolean {
        val sevenDays = 7L * 24 * 60 * 60 * 1000
        return !listFile.exists() || System.currentTimeMillis() - listFile.lastModified() > sevenDays
    }

    /** Verifica o domínio e todos os "domínios-pai" (ex.: a.b.ads.com -> b.ads.com -> ads.com). */
    fun isBlocked(host: String?): Boolean {
        if (host == null) return false
        val set = blocked
        var h = host.lowercase()
        while (true) {
            if (set.contains(h)) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
        }
    }

    /** Devolve uma resposta vazia se o domínio estiver bloqueado; caso contrário, null (carrega normal). */
    fun intercept(host: String?): WebResourceResponse? {
        if (!isBlocked(host)) return null
        counter.incrementAndGet()
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }

    /** Baixa as listas da internet. Chamar fora da thread principal. */
    fun update(): Boolean {
        val tmp = File(listFile.parentFile, "blocklist.tmp")
        try {
            tmp.delete()
            var okCount = 0
            tmp.outputStream().use { out ->
                for (address in LIST_URLS) {
                    try {
                        val conn = URL(address).openConnection() as HttpURLConnection
                        conn.connectTimeout = 15000
                        conn.readTimeout = 60000
                        conn.inputStream.use { it.copyTo(out) }
                        out.write('\n'.code)
                        okCount++
                    } catch (e: Exception) {
                        // tenta a próxima lista
                    }
                }
            }
            if (okCount == 0 || tmp.length() < 1000) {
                tmp.delete()
                return false
            }
            if (listFile.exists()) listFile.delete()
            tmp.renameTo(listFile)
            load()
            return true
        } catch (e: Exception) {
            tmp.delete()
            return false
        }
    }

    private fun load() {
        val set = HashSet<String>(BUILTIN)
        if (listFile.exists()) {
            try {
                listFile.forEachLine { parseLine(it, set) }
            } catch (e: Exception) {
                // arquivo corrompido: usa só a lista embutida
            }
        }
        blocked = set
    }

    private fun parseLine(raw: String, out: MutableSet<String>) {
        val line = raw.substringBefore('#').trim().lowercase()
        if (line.isEmpty()) return
        val parts = line.split(WHITESPACE)
        val host = if (parts.size >= 2 && (parts[0] == "127.0.0.1" || parts[0] == "0.0.0.0")) parts[1] else parts[0]
        if (host.contains('.') && host != "localhost") out.add(host)
    }

    companion object {
        private val WHITESPACE = Regex("\\s+")

        private val LIST_URLS = listOf(
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext"
        )

        private val BUILTIN = listOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adnxs.com", "taboola.com", "outbrain.com", "popads.net", "popcash.net",
            "propellerads.com", "exoclick.com", "juicyads.com", "adsterra.com",
            "onclickads.net", "clickadu.com", "trafficjunky.com", "adform.net",
            "criteo.com", "pubmatic.com", "rubiconproject.com", "openx.net"
        )
    }
}
