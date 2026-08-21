package io.sweatshop.herekitty.repository.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.dsl.module

@Module
@ComponentScan("io.sweatshop.herekitty.repository")
class RepositoryGeneratedModule

val repositoryModule = module {
    includes(RepositoryGeneratedModule().module())
}
