/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.checkpoint

import org.ide.lti.core.domain.pipeline.CacheKeys
import org.ide.lti.core.domain.pipeline.ContentManifest
import org.ide.lti.core.domain.pipeline.ManifestEntry
import org.ide.lti.core.domain.pipeline.ManifestEntryKind
import org.ide.lti.core.domain.pipeline.StageCheckpoint
import org.ide.lti.core.domain.pipeline.VerificationReceipt
import org.ide.lti.core.domain.ports.CheckpointPort
import org.ide.lti.core.domain.ports.MAX_TRANSFER_CHUNK_BYTES
import org.ide.lti.core.domain.ports.StagingHandle
import org.ide.lti.core.domain.ports.StagingResult
import org.ide.lti.core.model.workspace.Workspace
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.UUID

/**
 * File-system backed repository for stage checkpoints and staging trees over [java.nio].
 */
public class CheckpointRepository : CheckpointPort {

    override suspend fun stage(ws: Workspace, checkpoint: StageCheckpoint, requiredBytes: Long): StagingResult {
        val wsRoot = Path.of(ws.path).toAbsolutePath().normalize()
        if (!Files.exists(wsRoot)) {
            Files.createDirectories(wsRoot)
        }

        val availableBytes = Files.getFileStore(wsRoot).usableSpace
        val sourceRoot = wsRoot.resolve(checkpoint.rootRelPath).normalize()

        val failure = when {
            availableBytes < requiredBytes -> StagingResult.InsufficientDisk(
                requiredBytes = requiredBytes,
                availableBytes = availableBytes,
            )
            !Files.exists(sourceRoot) -> StagingResult.SourceMissing(checkpoint.rootRelPath)
            else -> null
        }
        if (failure != null) return failure

        val randomId = UUID.randomUUID().toString().replace("-", "").take(8)
        val stagingDirName = "${checkpoint.stageId}-$randomId"
        val stagingRelPath = ".staging/$stagingDirName"
        val stagingDir = wsRoot.resolve(stagingRelPath).normalize()
        Files.createDirectories(stagingDir)

        copyTree(sourceRoot, stagingDir)

        val handle = StagingHandle(
            id = stagingDirName,
            checkpoint = checkpoint,
            stagingRelPath = stagingRelPath,
        )
        return StagingResult.Staged(handle)
    }

    private fun copyTree(sourceRoot: Path, stagingDir: Path) {
        Files.walk(sourceRoot).use { stream ->
            stream.forEach { sourcePath ->
                if (sourcePath != sourceRoot) {
                    copyPath(sourceRoot, sourcePath, stagingDir)
                }
            }
        }
    }

    private fun copyPath(sourceRoot: Path, sourcePath: Path, stagingDir: Path) {
        val rel = sourceRoot.relativize(sourcePath)
        val destPath = stagingDir.resolve(rel)
        when {
            Files.isSymbolicLink(sourcePath) -> copySymlink(sourcePath, destPath)
            Files.isDirectory(sourcePath, LinkOption.NOFOLLOW_LINKS) -> Files.createDirectories(destPath)
            Files.isRegularFile(sourcePath, LinkOption.NOFOLLOW_LINKS) -> {
                Files.createDirectories(destPath.parent)
                copyStreamed(sourcePath, destPath)
            }
        }
    }

    private fun copySymlink(sourcePath: Path, destPath: Path) {
        Files.createDirectories(destPath.parent)
        try {
            Files.copy(
                sourcePath,
                destPath,
                LinkOption.NOFOLLOW_LINKS,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: Exception) {
            val linkTarget = Files.readSymbolicLink(sourcePath)
            Files.createSymbolicLink(destPath, linkTarget)
        }
    }

    /**
     * Re-hashes every manifest entry's actual bytes under the checkpoint root and compares size and sha256.
     *
     * A descriptor (such as matching file size) that matches while the actual file bytes differ MUST fail
     * verification. This byte-level check is the fundamental guarantee ensuring corrupt or tampered artifacts
     * cannot silently pass as valid checkpoints.
     */
    override suspend fun verify(ws: Workspace, checkpoint: StageCheckpoint): VerificationReceipt {
        val wsRoot = Path.of(ws.path).toAbsolutePath().normalize()
        val checkpointRoot = wsRoot.resolve(checkpoint.rootRelPath).normalize()

        val mismatches = mutableListOf<String>()
        if (!Files.exists(checkpointRoot)) {
            mismatches.addAll(checkpoint.manifest.entries.map { it.relPath })
            return VerificationReceipt(
                verifiedAtEpochMs = System.currentTimeMillis(),
                method = "sha256-content",
                verified = false,
                mismatches = mismatches,
            )
        }

        for (entry in checkpoint.manifest.entries) {
            val entryPath = checkpointRoot.resolve(entry.relPath).normalize()
            if (!verifyEntry(entryPath, entry)) {
                mismatches.add(entry.relPath)
            }
        }

        return VerificationReceipt(
            verifiedAtEpochMs = System.currentTimeMillis(),
            method = "sha256-content",
            verified = mismatches.isEmpty(),
            mismatches = mismatches,
        )
    }

    private fun verifyEntry(entryPath: Path, entry: ManifestEntry): Boolean {
        if (!Files.exists(entryPath, LinkOption.NOFOLLOW_LINKS)) {
            return false
        }
        return when (entry.kind) {
            ManifestEntryKind.SYMLINK -> verifySymlink(entryPath, entry)
            ManifestEntryKind.FILE,
            ManifestEntryKind.SIDECAR,
            -> verifyRegularFile(entryPath, entry)
        }
    }

    private fun verifySymlink(entryPath: Path, entry: ManifestEntry): Boolean {
        if (!Files.isSymbolicLink(entryPath)) return false
        val linkTarget = Files.readSymbolicLink(entryPath).toString().replace('\\', '/')
        val targetBytes = linkTarget.toByteArray(Charsets.UTF_8)
        val actualSha = CacheKeys.sha256(linkTarget)
        return targetBytes.size.toLong() == entry.sizeBytes && actualSha == entry.sha256
    }

    private fun verifyRegularFile(entryPath: Path, entry: ManifestEntry): Boolean {
        if (Files.isSymbolicLink(entryPath) || Files.isDirectory(entryPath, LinkOption.NOFOLLOW_LINKS)) {
            return false
        }
        return Files.size(entryPath) == entry.sizeBytes && sha256Of(entryPath) == entry.sha256
    }

    /**
     * Atomically promotes the staged directory to the final checkpoint destination.
     *
     * An existing directory for that digest is reused, not overwritten, because identical content
     * digest implies identical artifact content by definition.
     */
    override suspend fun promote(ws: Workspace, handle: StagingHandle, actual: ContentManifest): StageCheckpoint {
        val wsRoot = Path.of(ws.path).toAbsolutePath().normalize()
        val stagingDir = wsRoot.resolve(handle.stagingRelPath).normalize()
        if (!Files.exists(stagingDir)) {
            error("Staging directory does not exist: ${handle.stagingRelPath}")
        }

        val stagedManifest = manifestOf(stagingDir)
        if (stagedManifest != actual) {
            val diff = findFirstDifference(stagedManifest, actual)
            error("Staged manifest does not match actual manifest: $diff")
        }

        val outputDigest = stagedManifest.canonicalDigest()
        val finalRelPath = "checkpoints/${handle.checkpoint.stageId}/$outputDigest"
        val finalDir = wsRoot.resolve(finalRelPath).normalize()

        if (Files.exists(finalDir)) {
            // Identical content by definition; reuse existing directory and discard staging
            deleteRecursively(stagingDir)
        } else {
            Files.createDirectories(finalDir.parent)
            try {
                Files.move(stagingDir, finalDir, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                copyTreeRecursively(stagingDir, finalDir)
                deleteRecursively(stagingDir)
            }
        }

        val sidecarPaths = stagedManifest.entries
            .filter { it.kind == ManifestEntryKind.SIDECAR }
            .map { it.relPath }

        return StageCheckpoint(
            stageId = handle.checkpoint.stageId,
            stageVersion = handle.checkpoint.stageVersion,
            inputDigest = handle.checkpoint.inputDigest,
            rootRelPath = finalRelPath,
            manifest = stagedManifest,
            sidecarRelPaths = sidecarPaths,
            outputDigest = outputDigest,
            verification = null,
        )
    }

    override suspend fun discard(ws: Workspace, handle: StagingHandle) {
        val wsRoot = Path.of(ws.path).toAbsolutePath().normalize()
        val stagingDir = wsRoot.resolve(handle.stagingRelPath).normalize()
        deleteRecursively(stagingDir)
    }
}

/**
 * Computes a [ContentManifest] from the given [root] path, sorting entries by relative path.
 *
 * Sidecar files located under any `configs/` directory are typed as [ManifestEntryKind.SIDECAR],
 * symbolic links are typed as [ManifestEntryKind.SYMLINK] without dereferencing, and all other
 * regular files are typed as [ManifestEntryKind.FILE].
 */
public fun manifestOf(root: Path): ContentManifest {
    if (!Files.exists(root)) {
        return ContentManifest(emptyList())
    }

    val entries = mutableListOf<ManifestEntry>()
    Files.walk(root).use { stream ->
        stream.forEach { p ->
            if (p != root && !Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                val rel = root.relativize(p).toString().replace('\\', '/')
                val isSymlink = Files.isSymbolicLink(p)
                val kind = when {
                    isSymlink -> ManifestEntryKind.SYMLINK
                    rel.startsWith("configs/") || rel.contains("/configs/") -> ManifestEntryKind.SIDECAR
                    else -> ManifestEntryKind.FILE
                }

                val (size, sha) = if (isSymlink) {
                    val linkTarget = Files.readSymbolicLink(p).toString().replace('\\', '/')
                    val targetBytes = linkTarget.toByteArray(Charsets.UTF_8)
                    Pair(targetBytes.size.toLong(), CacheKeys.sha256(linkTarget))
                } else {
                    Pair(Files.size(p), sha256Of(p))
                }

                entries.add(
                    ManifestEntry(
                        relPath = rel,
                        sizeBytes = size,
                        sha256 = sha,
                        kind = kind,
                    ),
                )
            }
        }
    }

    return ContentManifest(entries.sortedBy { it.relPath })
}

private fun sha256Of(file: Path): String {
    val md = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(MAX_TRANSFER_CHUNK_BYTES.toInt())
    Files.newInputStream(file).use { input ->
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            md.update(buffer, 0, read)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

private fun copyStreamed(source: Path, destination: Path) {
    val buffer = ByteArray(MAX_TRANSFER_CHUNK_BYTES.toInt())
    Files.newInputStream(source).use { input ->
        Files.newOutputStream(destination).use { output ->
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
            }
        }
    }
}

private fun copyTreeRecursively(source: Path, destination: Path) {
    Files.walk(source).use { stream ->
        stream.forEach { p ->
            val rel = source.relativize(p)
            val dest = destination.resolve(rel)
            if (Files.isSymbolicLink(p)) {
                Files.createDirectories(dest.parent)
                try {
                    Files.copy(p, dest, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: Exception) {
                    val linkTarget = Files.readSymbolicLink(p)
                    Files.createSymbolicLink(dest, linkTarget)
                }
            } else if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(dest)
            } else if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(dest.parent)
                copyStreamed(p, dest)
            }
        }
    }
}

private fun deleteRecursively(path: Path) {
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
    Files.walkFileTree(
        path,
        object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.deleteIfExists(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                Files.deleteIfExists(dir)
                return FileVisitResult.CONTINUE
            }
        },
    )
}

private fun findFirstDifference(actual: ContentManifest, expected: ContentManifest): String {
    val actualMap = actual.entries.associateBy { it.relPath }
    val expectedMap = expected.entries.associateBy { it.relPath }

    val missingOrMismatch = expectedMap.entries.firstNotNullOfOrNull { (path, exp) ->
        val act = actualMap[path]
        when {
            act == null -> "Missing entry '$path'"
            act.kind != exp.kind -> "Kind mismatch at '$path': expected ${exp.kind}, got ${act.kind}"
            act.sizeBytes != exp.sizeBytes ->
                "Size mismatch at '$path': expected ${exp.sizeBytes}, got ${act.sizeBytes}"
            act.sha256 != exp.sha256 -> "Sha256 mismatch at '$path': expected ${exp.sha256}, got ${act.sha256}"
            else -> null
        }
    }
    if (missingOrMismatch != null) return missingOrMismatch

    val unexpected = actualMap.keys.firstOrNull { it !in expectedMap }
    return if (unexpected != null) "Unexpected entry '$unexpected'" else "Canonical digest mismatch"
}
