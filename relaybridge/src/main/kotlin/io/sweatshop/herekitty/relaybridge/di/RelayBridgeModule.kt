package io.sweatshop.herekitty.relaybridge.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.dsl.module

@Module
@ComponentScan("io.sweatshop.herekitty.relaybridge")
class RelayBridgeGeneratedModule

val relayBridgeModule = module {
    includes(RelayBridgeGeneratedModule().module())
}
