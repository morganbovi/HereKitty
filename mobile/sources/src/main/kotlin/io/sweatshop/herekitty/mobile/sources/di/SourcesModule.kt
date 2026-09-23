package io.sweatshop.herekitty.mobile.sources.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.dsl.module

@Module
@ComponentScan("io.sweatshop.herekitty.mobile.sources")
class SourcesGeneratedModule

val sourcesModule = module {
    includes(SourcesGeneratedModule().module())
}
