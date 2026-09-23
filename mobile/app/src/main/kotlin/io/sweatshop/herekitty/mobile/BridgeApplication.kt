package io.sweatshop.herekitty.mobile

import android.app.Application
import io.sweatshop.herekitty.mobile.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class BridgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@BridgeApplication)
            modules(appModule)
        }
    }
}
