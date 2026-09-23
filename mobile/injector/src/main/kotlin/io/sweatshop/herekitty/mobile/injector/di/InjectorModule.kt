package io.sweatshop.herekitty.mobile.injector.di

import io.sweatshop.herekitty.mobile.auth.di.authModule
import io.sweatshop.herekitty.mobile.domain.di.domainModule
import io.sweatshop.herekitty.mobile.sources.di.sourcesModule
import org.koin.dsl.module

val injectorModule = module {
    includes(domainModule)
    includes(authModule)
    includes(sourcesModule)
}
