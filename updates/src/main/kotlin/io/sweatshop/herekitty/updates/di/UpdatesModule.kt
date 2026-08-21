package io.sweatshop.herekitty.updates.di

import io.sweatshop.herekitty.updates.GithubReleaseSource
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.dsl.module

@Module
@ComponentScan("io.sweatshop.herekitty.updates")
class UpdatesGeneratedModule

val updatesModule = module {
    includes(UpdatesGeneratedModule().module())

    single { GithubReleaseSource.fromEnvironment() }
}
