package io.ltirom.tooling.core.pipeline

import io.ltirom.tooling.core.ExecutionContext
import io.ltirom.tooling.core.ToolResult
import java.io.File

/**
 * Single Responsibility: Intercepts tool execution to validate that file path arguments
 * stay strictly confined within configured approved root directories (jails).
 *
 * Enforces:
 * 1. Path traversal defense: blocks unconstrained `..` directory escapes (e.g. `../../etc/passwd` or `..\..\Windows`).
 * 2. Jail containment: verifies absolute paths reside within at least one approved root directory.
 */
public class WorkspaceJailInterceptor(
    public val allowedRoots: List<File> = emptyList(),
    public val allowRelativePathsWithoutEscape: Boolean = true
) : ToolExecutionInterceptor {

    private val canonicalRoots: List<File> by lazy {
        allowedRoots.map { it.canonicalFile }
    }

    override suspend fun <R> intercept(chain: ExecutionChain<R>): ToolResult<R> {
        if (allowedRoots.isEmpty()) {
            return chain.proceed()
        }

        val roots = canonicalRoots
        val args = chain.command.getArguments()

        for (arg in args) {
            val violation = checkArgument(arg, roots)
            if (violation != null) {
                return ToolResult.Failure.SecurityViolation(
                    "Workspace jail violation for tool '${chain.command.toolId.logicalName}' in argument '$arg': $violation"
                )
            }
        }

        return chain.proceed()
    }

    public fun checkArgument(arg: String, roots: List<File>): String? {
        val pathPart = extractPathPart(arg)

        // 1. Check for directory traversal sequences
        if (containsTraversal(pathPart)) {
            val escapes = roots.none { root ->
                val candidate = File(root, pathPart).canonicalFile
                candidate.toPath().startsWith(root.toPath())
            }
            if (escapes) {
                return "Directory traversal escape ('..') outside allowed workspace roots"
            }
        }

        // 2. Check absolute path containment
        if (isPathLike(pathPart)) {
            val file = File(pathPart)
            if (file.isAbsolute) {
                val canonical = runCatching { file.canonicalFile }.getOrNull()
                if (canonical != null) {
                    val isContained = roots.any { root ->
                        canonical.toPath().startsWith(root.toPath())
                    }
                    if (!isContained) {
                        return "Absolute path '$canonical' is outside approved workspace roots: ${roots.joinToString { it.absolutePath }}"
                    }
                }
            }
        }

        return null
    }

    private fun extractPathPart(arg: String): String {
        val eqIndex = arg.indexOf('=')
        return if (eqIndex != -1 && eqIndex < arg.lastIndex) {
            arg.substring(eqIndex + 1)
        } else {
            arg
        }
    }

    private fun containsTraversal(str: String): Boolean {
        return str == ".." ||
               str.contains("../") ||
               str.contains("..\\") ||
               str.startsWith("../") ||
               str.startsWith("..\\") ||
               str.endsWith("/..") ||
               str.endsWith("\\..")
    }

    private fun isPathLike(str: String): Boolean {
        return str.startsWith("/") ||
               str.startsWith("\\") ||
               (str.length >= 3 && str[1] == ':' && (str[2] == '\\' || str[2] == '/'))
    }
}
