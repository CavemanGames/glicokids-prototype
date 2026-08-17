package com.glicokids.prototype.data.remote

import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference

/**
 * Module 7 — a minimal, one-shot HTTP server over a plain [ServerSocket], built for
 * [AndroidHttpClientTest]. Only [java.net] is used, the same package [AndroidHttpClient] itself
 * relies on — the JDK's own `com.sun.net.httpserver.HttpServer` looked like a shortcut here, but
 * is not on the classpath this module's unit tests compile against, and no test library was
 * added just to stand in for it.
 *
 * [respondOnce] accepts exactly one connection on a background thread, records the request's
 * `User-Agent` header, writes the configured status/body, then closes — everything this suite
 * needs from a server, nothing more.
 */
class FakeHttpServer {

    private val serverSocket = ServerSocket(0)
    private val userAgent = AtomicReference<String?>(null)
    private var worker: Thread? = null

    fun url(path: String) = "http://127.0.0.1:${serverSocket.localPort}$path"

    fun respondOnce(statusLine: String, body: String = "") {
        worker = Thread {
            serverSocket.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                var line = reader.readLine()
                while (!line.isNullOrEmpty()) {
                    val separatorIndex = line.indexOf(':')
                    if (separatorIndex > 0 && line.take(separatorIndex).equals("User-Agent", ignoreCase = true)) {
                        userAgent.set(line.substring(separatorIndex + 1).trim())
                    }
                    line = reader.readLine()
                }

                val bodyBytes = body.toByteArray(Charsets.UTF_8)
                val responseHead = "HTTP/1.1 $statusLine\r\n" +
                    "Content-Length: ${bodyBytes.size}\r\n" +
                    "Connection: close\r\n\r\n"

                socket.getOutputStream().apply {
                    write(responseHead.toByteArray(Charsets.UTF_8))
                    write(bodyBytes)
                    flush()
                }
            }
        }.apply { start() }
    }

    /** Blocks until the one accepted request finished being handled, then returns its
     * `User-Agent` header — the join is what makes the value visible across threads, not just
     * the socket write itself. */
    fun lastUserAgent(): String? {
        worker?.join(JOIN_TIMEOUT_MILLIS)
        return userAgent.get()
    }

    fun close() {
        worker?.join(JOIN_TIMEOUT_MILLIS)
        serverSocket.close()
    }

    companion object {
        private const val JOIN_TIMEOUT_MILLIS = 5_000L
    }
}
