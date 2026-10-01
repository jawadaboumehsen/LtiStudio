package org.ide.lti.core.data.setup.install

import org.ide.lti.core.data.setup.FakeWslCliExecutor
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtifactStoreTest {

    private lateinit var cli: FakeWslCliExecutor
    private lateinit var store: ArtifactStore

    @BeforeTest
    fun setUp() {
        cli = FakeWslCliExecutor()
        cli.existingPaths.clear()
        store = ArtifactStore(cli, "Ubuntu")
    }

    @Test
    fun `contentId is fingerprint12-contentHash12 where contentHash covers only sorted path sha256 mode lines`() {
        val filesUnsorted = listOf(
            ArtifactFileEntry("bin/second", "hash2", "755"),
            ArtifactFileEntry("bin/first", "hash1", "755"),
        )
        val filesSorted = listOf(
            ArtifactFileEntry("bin/first", "hash1", "755"),
            ArtifactFileEntry("bin/second", "hash2", "755"),
        )

        val hash1 = ArtifactStore.computeContentHash(filesUnsorted)
        val hash2 = ArtifactStore.computeContentHash(filesSorted)
        assertEquals(hash1, hash2, "ContentHash must sort entries so insertion order doesn't affect content identity")

        val fingerprint = "abcdef0123456789abcdef"
        val contentId = ArtifactStore.computeContentId(fingerprint, filesUnsorted)

        val expectedFp12 = fingerprint.take(12)
        val expectedHash12 = hash1.take(12)
        assertEquals("$expectedFp12-$expectedHash12", contentId)
    }

    @Test
    fun `isUnusable correctly executes test command`() {
        // Assume unusable.json exists
        cli.existingPaths.add("/home/lti/LtiRomTools/artifacts/grp/art1/unusable.json")
        assertTrue(store.isUnusable("grp", "art1"))

        // Unusable doesn't exist
        assertFalse(store.isUnusable("grp", "art2"))
    }
}
