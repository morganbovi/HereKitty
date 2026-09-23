package io.sweatshop.herekitty.auth.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.dsl.module

@Module
@ComponentScan("io.sweatshop.herekitty.auth")
class AuthGeneratedModule

val authModule = module {
    includes(AuthGeneratedModule().module())
}
