package io.sweatshop.herekitty.injector.di

import io.sweatshop.herekitty.adb.di.adbModule
import io.sweatshop.herekitty.domain.di.domainModule
import io.sweatshop.herekitty.repository.di.repositoryModule
import org.koin.dsl.module

val injectorModule = module {
    includes(domainModule)
    includes(adbModule)
    includes(repositoryModule)
}
