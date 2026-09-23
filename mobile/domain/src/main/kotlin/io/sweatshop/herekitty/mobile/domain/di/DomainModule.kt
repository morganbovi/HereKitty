package io.sweatshop.herekitty.mobile.domain.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.dsl.module

@Module
@ComponentScan("io.sweatshop.herekitty.mobile.domain")
class DomainGeneratedModule

val domainModule = module {
    includes(DomainGeneratedModule().module())
}
