import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock

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

        fun defaultLockFile(): File {
            val base = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
            return File(base, "LtiRomGui/instance.lock")
        }
    }
}
