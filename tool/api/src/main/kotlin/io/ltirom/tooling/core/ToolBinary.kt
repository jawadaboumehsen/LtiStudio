package io.ltirom.tooling.core

import java.io.File

public enum class BinaryType {
    ELF_BINARY,
    PYTHON_SCRIPT,
    BASH_SCRIPT,
    JAR_WRAPPER,
    UNKNOWN
}

public data class ToolBinary(
    val toolId: ToolId,
    val file: File
) {
    val type: BinaryType by lazy {
        if (!file.isFile) BinaryType.UNKNOWN
        else {
            val name = file.name
            when {
                name.endsWith(".jar") -> BinaryType.JAR_WRAPPER
                name.endsWith(".py") -> BinaryType.PYTHON_SCRIPT
                name.endsWith(".sh") -> BinaryType.BASH_SCRIPT
                else -> {
                    // Check shebang
                    try {
                        file.inputStream().use { input ->
                            val header = ByteArray(4)
                            val read = input.read(header)
                            if (read >= 2 && header[0] == '#'.code.toByte() && header[1] == '!'.code.toByte()) {
                                val line = String(header, 0, read) + input.bufferedReader().readLine().orEmpty()
                                when {
                                    line.contains("python") -> BinaryType.PYTHON_SCRIPT
                                    line.contains("bash") || line.contains("sh") -> BinaryType.BASH_SCRIPT
                                    else -> BinaryType.UNKNOWN
                                }
                            } else if (read >= 4 && header[0] == 0x7F.toByte() && header[1] == 'E'.code.toByte() && header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte()) {
                                BinaryType.ELF_BINARY
                            } else {
                                BinaryType.UNKNOWN
                            }
                        }
                    } catch (e: Exception) {
                        BinaryType.UNKNOWN
                    }
                }
            }
        }
    }

    public fun isValid(): Boolean {
        return file.isFile && file.canExecute()
    }
}
