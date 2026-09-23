package io.sweatshop.herekitty.injector.di

import io.sweatshop.herekitty.adb.di.adbModule
import io.sweatshop.herekitty.auth.di.authModule
import io.sweatshop.herekitty.domain.di.domainModule
import io.sweatshop.herekitty.relaybridge.di.relayBridgeModule
import io.sweatshop.herekitty.relaydevices.di.relayDevicesModule
import io.sweatshop.herekitty.repository.di.repositoryModule
import io.sweatshop.herekitty.updates.di.updatesModule
import org.koin.dsl.module

val injectorModule = module {
    includes(domainModule)
    includes(adbModule)
    includes(authModule)
    includes(relayDevicesModule)
    includes(relayBridgeModule)
    includes(updatesModule)
    includes(repositoryModule)
}
