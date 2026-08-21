package io.sweatshop.herekitty.domain.di

import io.sweatshop.herekitty.domain.base.AppScope
import io.sweatshop.herekitty.domain.base.ScopedWorkLauncher
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.dsl.module

@Module
@ComponentScan("io.sweatshop.herekitty.domain")
class DomainGeneratedModule

val domainModule = module {
    includes(DomainGeneratedModule().module())

    single { AppScope() }
    single { ScopedWorkLauncher(get<AppScope>()) }
}
