package io.sweatshop.herekitty.updates

import io.sweatshop.herekitty.domain.features.updates.model.AppVersion
import io.sweatshop.herekitty.domain.features.updates.model.ReleaseAsset
import io.sweatshop.herekitty.domain.features.updates.model.ReleaseInfo
import io.sweatshop.herekitty.domain.features.updates.model.UpdateState

/**
 * What a completed check means.
 *
 * Takes the already-resolved [installer] rather than working it out, so the decision does not depend
 * on the machine it runs on and can be tested for every combination on any of them.
 */
object UpdateCheck {

    fun outcomeOf(current: AppVersion, release: ReleaseInfo?, installer: ReleaseAsset?): UpdateState = when {
        release == null -> UpdateState.Failed("Could not read the latest release")

        // Not `<`: a release equal to what is running is not an update, and one *older* is a
        // downgrade, which an update check must never offer.
        release.version <= current -> UpdateState.UpToDate(current)

        // A newer release with nothing this machine can install is not an offer. Saying so beats
        // silently reporting up to date, which would look like the check was broken.
        installer == null -> UpdateState.Failed("${release.version} has no build for this platform")

        else -> UpdateState.Available(release)
    }
}
