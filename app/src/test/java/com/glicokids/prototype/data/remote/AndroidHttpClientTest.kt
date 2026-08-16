package com.glicokids.prototype.data.remote

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import com.glicokids.prototype.domain.model.NetworkResult
import com.google.common.truth.Truth.assertThat
import java.net.ServerSocket
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Module 7 — [AndroidHttpClient] against a real, loopback-only HTTP server ([FakeHttpServer],
 * a hand-rolled one-shot server over [ServerSocket] — the JDK's own `com.sun.net.httpserver` is
 * not on the classpath this module's unit tests compile against, and no test library was added
 * for this instead). This is the one place in the Module 7 suite that exercises the real
 * `HttpURLConnection` code path end to end (headers, status code, body, disconnect) rather than
 * a fake; it never leaves `127.0.0.1`, so it stays as offline as every other test here — nothing
 * here is verifiable against the real Overpass/Open Food Facts/Nominatim services without an
 * actual network call, which this suite does not make.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidHttpClientTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val client = AndroidHttpClient(application)
    private var server: FakeHttpServer? = null

    @After
    fun stopServer() {
        server?.close()
    }

    @Test
    fun `returns the body and sends the given user agent on a 200 response`() = runTest {
        val fakeServer = FakeHttpServer().also { server = it }
        fakeServer.respondOnce(statusLine = "200 OK", body = "hello")

        val result = client.get(fakeServer.url("/ok"), userAgent = "GlicoKids/1.0 (academic prototype)")

        assertThat(result).isEqualTo(NetworkResult.Success("hello"))
        assertThat(fakeServer.lastUserAgent()).isEqualTo("GlicoKids/1.0 (academic prototype)")
    }

    @Test
    fun `reports the status code on a non-2xx response`() = runTest {
        val fakeServer = FakeHttpServer().also { server = it }
        fakeServer.respondOnce(statusLine = "503 Service Unavailable")

        val result = client.get(fakeServer.url("/broken"), userAgent = "GlicoKids/1.0")

        assertThat(result).isEqualTo(NetworkResult.Failure.ServiceUnavailable(503))
    }

    @Test
    fun `reports no connection without ever reaching the server when there is no active network`() = runTest {
        val fakeServer = FakeHttpServer().also { server = it }
        val connectivityManager =
            application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        shadowOf(connectivityManager).setActiveNetworkInfo(null)

        val result = client.get(fakeServer.url("/unreached"), userAgent = "GlicoKids/1.0")

        assertThat(result).isEqualTo(NetworkResult.Failure.NoConnection)
    }
}
