import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import javax.swing.JOptionPane
import kotlin.system.exitProcess

/**
 * Holds an OS file lock for the life of the process. Tool locks and the settings write lock are in-process, and
 * `/client/stop` cancels every run of the build service, so a second window would let both instances corrupt each
 * other's work. The lock vanishes with the process, so a crash never leaves a stale one behind.
 */
class SingleInstanceGuard private constructor(private val file: RandomAccessFile, private val lock: FileLock) {
    fun release() {
        runCatching { lock.release() }
        runCatching { file.close() }
    }

    companion object {
        /** Null when another instance holds [lockFile]. */
        fun acquire(lockFile: File): SingleInstanceGuard? {
            lockFile.parentFile?.mkdirs()
            val raf = RandomAccessFile(lockFile, "rw")
            val lock = runCatching { raf.channel.tryLock() }.getOrNull()
            if (lock == null) {
                raf.close()
                return null
            }
            return SingleInstanceGuard(raf, lock)
        }

        /**
         * [acquire], or a dialog and exit when [lockFile] cannot be opened: nothing could work without the data folder,
         * and from the packaged app the exception would only reach the launcher's "Failed to launch JVM".
         */
        fun acquireOrExplain(lockFile: File): SingleInstanceGuard? = try {
            acquire(lockFile)
        } catch (e: IOException) {
            JOptionPane.showMessageDialog(
                null,
                dataFolderProblem(lockFile, e),
                "LtiRom Studio cannot start",
                JOptionPane.ERROR_MESSAGE,
            )
            exitProcess(1)
        }

        /** What the person reads when [lockFile] cannot be opened. */
        fun dataFolderProblem(lockFile: File, cause: IOException): String =
            "LtiRom Studio cannot write to its data folder:\n${lockFile.parent}\n\n${cause.message}\n\n" +
                "Check that your account can write there.\n" +
                "A copy started from a protected or sandboxed folder runs with less access: " +
                "move the app elsewhere, or install it."

        fun defaultLockFile(): File {
            val base = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
            return File(base, "LtiRomGui/instance.lock")
        }
    }
}
