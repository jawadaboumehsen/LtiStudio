import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
}
