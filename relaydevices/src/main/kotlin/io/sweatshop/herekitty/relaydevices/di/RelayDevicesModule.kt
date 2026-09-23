package io.sweatshop.herekitty.relaydevices.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.dsl.module

@Module
@ComponentScan("io.sweatshop.herekitty.relaydevices")
class RelayDevicesGeneratedModule

val relayDevicesModule = module {
    includes(RelayDevicesGeneratedModule().module())
}
