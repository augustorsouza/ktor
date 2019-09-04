/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.takeFrom
import io.ktor.client.response.HttpReceivePipeline
import io.ktor.client.response.HttpResponse
import io.ktor.util.AttributeKey

typealias RetryCondition = suspend (response: HttpResponse) -> Boolean

data class NeedRetryConfig(internal var condition: RetryCondition = { false }, internal var retry: Boolean = false)

class NeedRetry(private val retry: RetryCondition = { false }) : HttpClientFeature<NeedRetryConfig, NeedRetry> {

    override val key: AttributeKey<NeedRetry> = AttributeKey("NeedRetry")

    override fun prepare(block: NeedRetryConfig.() -> Unit): NeedRetry = NeedRetryConfig().apply(block).condition.let(::NeedRetry)

    override fun install(feature: NeedRetry, scope: HttpClient) = scope.receivePipeline.intercept(HttpReceivePipeline.After) {
        try {
            feature.retry(context.response).let { retry ->
                if (retry) context.client.execute(HttpRequestBuilder().takeFrom(context.request))
                proceedWith(it)
            }
        } catch (cause: Throwable) {
            throw cause
        }
    }
}

private fun NeedRetryConfig.bind(block: RetryCondition) { condition = {
    retry || block(it)
}}

fun HttpClientConfig<*>.needRetry(retryCondition: RetryCondition) {
    val retry: NeedRetryConfig.() -> Unit = { bind(retryCondition) }
    install(NeedRetry(), retry)
}
