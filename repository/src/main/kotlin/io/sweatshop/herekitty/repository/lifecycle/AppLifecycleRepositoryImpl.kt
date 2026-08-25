package io.sweatshop.herekitty.repository.lifecycle

import io.sweatshop.herekitty.domain.base.Log
import io.sweatshop.herekitty.domain.features.lifecycle.repository.AppLifecycleRepository
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

@Single(binds = [AppLifecycleRepository::class])
class AppLifecycleRepositoryImpl : AppLifecycleRepository {

    private val file: Path = Path.of(System.getProperty("user.home"), ".herekitty", "run-state.json")
    private val json = Json { ignoreUnknownKeys = true }

    // Read synchronously, like the layout: this only ever runs once, at the very start of the one
    // composition the app has, before there is anything else to race.
    override fun consumeLastExitWasClean(): Boolean {
        val wasClean = runCatching {
            if (!Files.exists(file)) return@runCatching false
            json.decodeFromString<Marker>(Files.readString(file)).cleanExit
        }.getOrElse {
            Log.w(it) { "Could not read $file; assuming the last exit was not clean" }
            false
        }
        write(Marker(cleanExit = false))
        return wasClean
    }

    override suspend fun markCleanExit() = withContext(Dispatchers.IO) { write(Marker(cleanExit = true)) }

    private fun write(marker: Marker) {
        runCatching {
            Files.createDirectories(file.parent)
            Files.writeString(file, json.encodeToString(marker))
        }.onFailure { Log.w(it) { "Could not save $file" } }
    }

    @Serializable
    private data class Marker(val cleanExit: Boolean)
}
