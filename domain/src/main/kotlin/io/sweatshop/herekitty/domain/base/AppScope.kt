package io.sweatshop.herekitty.domain.base

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppScope : CoroutineScope {
    override val coroutineContext =
        SupervisorJob() +
            Dispatchers.Default +
            CoroutineExceptionHandler { _, throwable -> Log.e(throwable) { "Something failed on the AppScope" } }
}
