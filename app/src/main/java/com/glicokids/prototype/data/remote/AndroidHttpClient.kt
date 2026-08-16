package com.glicokids.prototype.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.glicokids.prototype.domain.model.NetworkResult
import com.glicokids.prototype.domain.repository.HttpClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Module 7 — [HttpClient] over the plain JDK [HttpURLConnection]. This project's stack for
 * Module 7 is `HttpURLConnection` + `org.json` + `XmlPullParser`, on purpose: no Retrofit,
 * OkHttp, Gson or Moshi were added.
 *
 * Everything after the connectivity check runs under `withContext(Dispatchers.IO)` because
 * opening a socket, waiting on `getResponseCode()` and reading the body are all blocking calls —
 * left on the caller's dispatcher, any of the three would freeze the main thread for as long as
 * the request takes, exactly the kind of "app not responding" bug a suspend function exists to
 * prevent. This is the one place in Module 7 that has to say so explicitly instead of leaning on
 * a library that already hides it.
 *
 * [connectTimeoutMillis] and [readTimeoutMillis] are both set explicitly — an [HttpURLConnection]
 * with no timeout configured can hang indefinitely on a socket that never answers, which for this
 * app means a nearby-help or food-search screen that never recovers on a bad connection.
 * [HttpURLConnection.disconnect] runs in a `finally` so a connection is never left open on any
 * exit path, success or failure.
 *
 * Never throws: connectivity is checked before the request is attempted, a non-2xx status and
 * any [IOException] during the request both fold into [NetworkResult.Failure.ServiceUnavailable]
 * — there is no way from here to tell "the connection was refused" apart from "the connection
 * dropped mid-read" in any way a caller could act on differently, so both report the same case,
 * with no status code to attach.
 */
@Singleton
class AndroidHttpClient @Inject constructor(
    @ApplicationContext private val context: Context
) : HttpClient {

    override suspend fun get(url: String, userAgent: String): NetworkResult<String> {
        if (!isConnected()) return NetworkResult.Failure.NoConnection

        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", userAgent)
                    connectTimeout = CONNECT_TIMEOUT_MILLIS
                    readTimeout = READ_TIMEOUT_MILLIS
                }

                val statusCode = connection.responseCode
                if (statusCode !in 200..299) {
                    NetworkResult.Failure.ServiceUnavailable(httpStatusCode = statusCode)
                } else {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    NetworkResult.Success(body)
                }
            } catch (e: IOException) {
                NetworkResult.Failure.ServiceUnavailable(httpStatusCode = null)
            } finally {
                connection?.disconnect()
            }
        }
    }

    // Asks for VALIDATED as well as INTERNET, which is the difference between "attached to a
    // network" and "that network actually reaches the internet". A phone sitting on a captive
    // portal or a router with no uplink satisfies the first and fails the second, and reporting
    // that as a service problem instead of a missing connection sends the user looking in the
    // wrong place. Both capabilities predate this app's minimum supported release, so the older
    // `getActiveNetworkInfo` buys nothing here.
    private fun isConnected(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 10_000
        private const val READ_TIMEOUT_MILLIS = 10_000
    }
}
