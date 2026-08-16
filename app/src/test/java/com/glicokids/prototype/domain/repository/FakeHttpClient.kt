package com.glicokids.prototype.domain.repository

import com.glicokids.prototype.domain.model.NetworkResult

/**
 * Module 7 — test double for [HttpClient]. Configurable per-URL response, matched by a
 * containing fragment (the real repositories embed a query string in the URL, so an exact match
 * would be brittle) — lets a repository test drive the exact sequence a real network flake would
 * produce, e.g. the primary Overpass endpoint answering HTML while the mirror answers JSON,
 * without any real network call.
 */
class FakeHttpClient : HttpClient {

    /** Returned when no configured fragment matches the requested URL. */
    var default: NetworkResult<String> = NetworkResult.Failure.NoConnection

    val requestedUrls = mutableListOf<String>()
    val requestedUserAgents = mutableListOf<String>()

    private val responsesByUrlFragment = mutableListOf<Pair<String, NetworkResult<String>>>()

    fun whenUrlContains(fragment: String, result: NetworkResult<String>) {
        responsesByUrlFragment.add(fragment to result)
    }

    override suspend fun get(url: String, userAgent: String): NetworkResult<String> {
        requestedUrls.add(url)
        requestedUserAgents.add(userAgent)
        return responsesByUrlFragment.firstOrNull { (fragment, _) -> url.contains(fragment) }?.second
            ?: default
    }
}
