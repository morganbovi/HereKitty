package io.sweatshop.herekitty.adb.di

import io.sweatshop.herekitty.adb.AdbEndpoint
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.dsl.module

@Module
@ComponentScan("io.sweatshop.herekitty.adb")
class AdbGeneratedModule

val adbModule = module {
    includes(AdbGeneratedModule().module())

    single { AdbEndpoint.fromEnvironment() }
}
