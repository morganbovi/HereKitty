package io.sweatshop.herekitty.repository.updates

import io.sweatshop.herekitty.domain.base.Log
import io.sweatshop.herekitty.domain.features.updates.model.AppVersion
import io.sweatshop.herekitty.domain.features.updates.model.ReleaseInfo
import io.sweatshop.herekitty.domain.features.updates.model.UpdateState
import io.sweatshop.herekitty.domain.features.updates.repository.UpdateRepository
import io.sweatshop.herekitty.net.Checksums
import io.sweatshop.herekitty.net.HttpFetch
import io.sweatshop.herekitty.updates.GithubReleaseSource
import io.sweatshop.herekitty.updates.PlatformAsset
import io.sweatshop.herekitty.updates.UpdateCheck
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single(binds = [UpdateRepository::class])
class UpdateRepositoryImpl(private val source: GithubReleaseSource) : UpdateRepository {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    override val state = _state.asStateFlow()

    private val stagingDirectory: Path =
        Path.of(System.getProperty("user.home"), ".herekitty", "updates")

    override suspend fun check() {
        // A check while one is already running, or while a download is in flight, would throw away
        // the state the user is watching.
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return

        _state.value = UpdateState.Checking

        val release = source.latest()
        val payload = release?.let { PlatformAsset.updatePayloadForThisMachine(it.assets) }
        _state.value = UpdateCheck.outcomeOf(AppVersion.Current, release, payload)
    }

    override suspend fun download(release: ReleaseInfo) {
        val payload = PlatformAsset.updatePayloadForThisMachine(release.assets)
        if (payload == null) {
            _state.value = UpdateState.Failed("${release.version} has no build for this platform")
            return
        }

        _state.value = UpdateState.Downloading(release, fraction = null)

        try {
            val target = withContext(Dispatchers.IO) {
                Files.createDirectories(stagingDirectory)
                stagingDirectory.resolve(payload.name)
            }

            HttpFetch.toFile(payload.downloadUrl, target) { fraction ->
                _state.value = UpdateState.Downloading(release, fraction)
            }

            verify(release, payload.name, target)

            _state.value = UpdateState.ReadyToInstall(release, target.toString())
            Log.i { "Staged ${release.version} at $target" }
        } catch (e: CancellationException) {
            _state.value = UpdateState.Idle
            throw e
        } catch (e: Exception) {
            Log.e(e) { "Could not stage ${release.version}" }
            _state.value = UpdateState.Failed(e.message ?: "Could not download ${release.version}")
        }
    }

    /**
     * Refuses anything the release does not vouch for.
     *
     * Applying an update replaces the running application, so an unverified download would be
     * arbitrary code with the app's own privileges. A release published without checksums is treated
     * as unverifiable rather than trusted — the failure is visible, which a silent trust would not be.
     *
     * This detects a corrupted or substituted download. It cannot detect a bad release, because the
     * checksums travel with the payload; only a signed app would give that.
     */
    private suspend fun verify(release: ReleaseInfo, name: String, file: Path) {
        val manifestAsset = release.assets.firstOrNull { it.name == CHECKSUM_MANIFEST }
            ?: error("Release ${release.version} publishes no $CHECKSUM_MANIFEST, so it cannot be verified")

        val expected = Checksums.parseManifest(HttpFetch.text(manifestAsset.downloadUrl))[name]
            ?: error("$CHECKSUM_MANIFEST does not list $name")

        val actual = withContext(Dispatchers.IO) { Checksums.sha256(file) }
        if (actual != expected) {
            withContext(Dispatchers.IO) { Files.deleteIfExists(file) }
            error("$name did not match its published checksum")
        }
    }

    override fun applyAndRestart() {
        TODO("The helper swap comes next; staging is what this commit finishes.")
    }

    override fun dismiss() {
        _state.value = UpdateState.Idle
    }

    private companion object {
        const val CHECKSUM_MANIFEST = "SHA256SUMS"
    }
}
