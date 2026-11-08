import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SingleInstanceGuardTest {
    @Test
    fun `a second holder is refused until the first releases`() {
        val file = Files.createTempDirectory("instance").resolve("nested/instance.lock").toFile()

        val first = assertNotNull(SingleInstanceGuard.acquire(file))
        assertNull(SingleInstanceGuard.acquire(file), "another instance must not start while one runs")

        first.release()
        val again = assertNotNull(SingleInstanceGuard.acquire(file), "free again after the first one exits")
        again.release()
    }

    @Test
    fun `a lock that cannot be opened is explained, with the folder and the reason`() {
        val blocker = Files.createTempFile("instance", "file").toFile()
        val file = blocker.resolve("instance.lock")

        val failure = assertFailsWith<IOException> { SingleInstanceGuard.acquire(file) }
        val text = SingleInstanceGuard.dataFolderProblem(file, failure)

        assertTrue(blocker.path in text, text)
        assertTrue(failure.message.orEmpty() in text, text)
    }
}
