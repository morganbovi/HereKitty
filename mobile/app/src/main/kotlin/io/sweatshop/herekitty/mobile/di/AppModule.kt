package io.sweatshop.herekitty.mobile.di

import io.sweatshop.herekitty.mobile.injector.di.injectorModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.dsl.module

@Module
@ComponentScan("io.sweatshop.herekitty.mobile")
class AppGeneratedModule

val appModule = module {
    includes(AppGeneratedModule().module())
    includes(injectorModule)
}
