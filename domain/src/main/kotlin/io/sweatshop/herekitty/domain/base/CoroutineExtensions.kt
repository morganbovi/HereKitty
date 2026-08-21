package io.sweatshop.herekitty.domain.base

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun CoroutineScope.launchCoroutine(
    onError: (Throwable) -> Unit = { onUnhandledError(it) },
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    block: suspend CoroutineScope.() -> Unit,
): Job = launch(dispatcher) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        onError(e)
    }
}

fun <T> CoroutineScope.collectLatestFlow(
    flow: Flow<T>,
    onError: (Throwable) -> Unit = ::onUnhandledError,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    block: suspend CoroutineScope.(T) -> Unit = {},
): Job = launch {
    try {
        withContext(dispatcher) { flow.collectLatest { value -> block(value) } }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        onError(e)
    }
}

internal fun onUnhandledError(error: Throwable) {
    if (error !is CancellationException) {
        Log.e(error) { "An error occurred that was not handled by the caller" }
    }
}
