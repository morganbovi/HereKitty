package io.sweatshop.herekitty.repository.logs

import io.sweatshop.herekitty.domain.base.Log
import io.sweatshop.herekitty.domain.features.logs.model.LastSessionInfo
import io.sweatshop.herekitty.domain.features.logs.model.SessionSource
import io.sweatshop.herekitty.domain.features.logs.repository.LastSessionRepository
import io.sweatshop.herekitty.domain.features.logs.repository.LogSession
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

/**
 * One recording per device serial, overwritten on every clean quit, so a slot whose device is gone
 * next launch can still open what it last held instead of sitting empty. [RecordingFiles] already
 * knows this format; this only decides where each serial's copy lives.
 */
@Single(binds = [LastSessionRepository::class])
class LastSessionRepositoryImpl : LastSessionRepository {

    private val directory: Path = Path.of(System.getProperty("user.home"), ".herekitty", "last-session")
    private val extensionSuffix = ".${RecordingFiles.EXTENSION}"

    override fun findAll(): List<LastSessionInfo> {
        if (!Files.isDirectory(directory)) return emptyList()
        val serials = Files.list(directory).use { paths ->
            paths.map { it.fileName.toString() }
                .filter { it.endsWith(extensionSuffix) }
                .map { it.removeSuffix(extensionSuffix) }
                .toList()
        }
        return serials.mapNotNull(::find).sortedByDescending { it.exportedAtMillis }
    }

    override fun find(serial: String): LastSessionInfo? {
        val path = pathFor(serial)
        if (!Files.exists(path)) return null
        return runCatching {
            val header = RecordingFiles.readHeader(path)
            LastSessionInfo(
                serial = serial,
                path = path,
                recordedFrom = header.recordedFrom,
                exportedAtMillis = header.exportedAtMillis,
                lineCount = header.lineCount,
            )
        }.getOrElse {
            Log.w(it) { "Could not read the last session for $serial from $path" }
            null
        }
    }

    override suspend fun saveAll(sessions: List<LogSession>) = coroutineScope {
        sessions
            .filter { it.isLive && it.source.value is SessionSource.Device }
            .forEach { session ->
                launch {
                    val serial = (session.source.value as SessionSource.Device).device.serial
                    session.exportTo(pathFor(serial))
                        .onFailure { Log.w(it) { "Could not save the last session for $serial" } }
                }
            }
    }

    private fun pathFor(serial: String): Path = directory.resolve("$serial.${RecordingFiles.EXTENSION}")
}
