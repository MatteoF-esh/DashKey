package com.example.testmessagesimple.data

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

class AuthInterceptor : Interceptor {

    private val failedRequests = ConcurrentLinkedQueue<Long>()
    private val MAX_FAILED_REQUESTS = 3
    private val TIME_WINDOW_MS = 5000 // 5 seconds

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val currentTime = System.currentTimeMillis()

        // Remove old failed requests from the queue
        while (failedRequests.isNotEmpty() && currentTime - failedRequests.peek()!! > TIME_WINDOW_MS) {
            failedRequests.poll()
        }

        if (failedRequests.size >= MAX_FAILED_REQUESTS) {
            throw IOException("Too many failed requests. Please try again later.")
        }

        val request = chain.request()
        val response = chain.proceed(request)

        if (!response.isSuccessful) {
            failedRequests.add(currentTime)
        } else {
            // If we get a successful response, we can clear the failed requests queue
            // depending on the application logic. For a login, a single successful
            // request means the user is authenticated.
            failedRequests.clear()
        }

        return response
    }
}
