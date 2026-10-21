package org.ide.lti.feature.plugins

import org.ide.lti.core.domain.plugin.IndexEntry
import org.ide.lti.core.domain.plugin.InstalledRecord
import org.ide.lti.core.domain.plugin.PackageIdentity
import org.ide.lti.core.domain.plugin.TrustDecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarketplaceStatusTest {
    private fun entry(version: String) = IndexEntry(
        publisher = "io.ltirom",
        id = "adb",
        version = version,
        sdkApiRange = "",
        targetSummary = "",
        packageUrl = "https://example.com/p.zip",
        contentDigest = "",
        signatureIdentity = null,
    )

    private fun installed(version: String, archived: Boolean = false, id: String = "adb") = InstalledRecord(
        identity = PackageIdentity("io.ltirom", id, version, "digest"),
        trust = TrustDecision.TRUSTED_UNVERIFIED,
        enabledInWorkspaces = emptyList(),
        revoked = false,
        archived = archived,
        dependencies = emptyList(),
        conflicts = emptyList(),
    )

    @Test
    fun anEntryIsNewInstalledAnUpdateOrOlderThanWhatIsInstalled() {
        assertNull(installStatus(entry("1.3.0"), emptyList()))
        // Another plugin, or an archived version, does not count as installed.
        val others = listOf(installed("1.2.0", id = "hma"), installed("1.2.0", archived = true))
        assertNull(installStatus(entry("1.3.0"), others))

        val same = installStatus(entry("1.3.0"), listOf(installed("1.2.0"), installed("1.3.0")))!!
        assertTrue(same.installedThis && !same.update)

        val update = installStatus(entry("1.10.0"), listOf(installed("1.9.0")))!!
        assertTrue(update.update && !update.installedThis)
        assertEquals("Update from 1.9.0", update.label)

        val older = installStatus(entry("1.2.0"), listOf(installed("1.3.0")))!!
        assertTrue(older.installedThis && !older.update)
        assertEquals("1.3.0 installed", older.label)
    }

    @Test
    fun aSourceIsNamedByItsOwnerAndRepositoryNotAGenericLabel() {
        assertEquals("o/r", sourceLabel("Custom", "https://github.com/o/r"))
        assertEquals("o/r", sourceLabel("", "https://github.com/o/r.git"))
        assertEquals("example.com", sourceLabel("", "https://example.com/plugins/index.json"))
        assertEquals("Mine", sourceLabel("Mine", "https://github.com/o/r"))
    }
}
