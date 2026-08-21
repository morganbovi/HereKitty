package io.sweatshop.herekitty.domain.base

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

interface WorkLauncher {
    val scope: CoroutineScope

    fun launchCoroutine(
        onError: (Throwable) -> Unit = ::onError,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
        block: suspend CoroutineScope.() -> Unit,
    ): Job = scope.launchCoroutine(onError, dispatcher, block)

    fun <T> collectLatestFlow(
        flow: Flow<T>,
        onError: (Throwable) -> Unit = ::onError,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
        block: suspend CoroutineScope.(T) -> Unit = {},
    ): Job = scope.collectLatestFlow(flow, onError, dispatcher, block)

    fun onError(e: Throwable) = onUnhandledError(e)
}

class ScopedWorkLauncher(override val scope: CoroutineScope) : WorkLauncher
