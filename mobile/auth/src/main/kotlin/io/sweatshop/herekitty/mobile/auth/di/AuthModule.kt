package io.sweatshop.herekitty.mobile.auth.di

import com.google.firebase.auth.FirebaseAuth
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.dsl.module

@Module
@ComponentScan("io.sweatshop.herekitty.mobile.auth")
class AuthGeneratedModule

val authModule = module {
    includes(AuthGeneratedModule().module())

    single { FirebaseAuth.getInstance() }
}
