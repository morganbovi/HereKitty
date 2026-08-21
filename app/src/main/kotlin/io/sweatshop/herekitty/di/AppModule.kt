package io.sweatshop.herekitty.di

import io.sweatshop.herekitty.injector.di.injectorModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.dsl.module

@Module
@ComponentScan("io.sweatshop.herekitty")
class AppGeneratedModule

val appModule = module {
    includes(AppGeneratedModule().module())
    includes(injectorModule)
}
