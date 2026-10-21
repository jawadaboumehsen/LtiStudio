/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.plugin

import org.ide.lti.core.domain.plugin.PluginCatalogPort
import org.ide.lti.core.domain.plugin.PluginPackageCodec
import org.ide.lti.core.model.plugin.PluginPlanTemplate
import org.ide.lti.sdk.patchmod.runtime.PackageReadResult
import org.ide.lti.sdk.patchmod.runtime.PluginPackageReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/**
 * The installed plugins (`<store>/packages/<publisher>/<id>/<version>/package.lti-mod.zip`, written by
 * [InstalledPluginRepository]) as the catalog the Patches/Mods stage resolves plans from. A package is read
 * (and its limits checked) once per version; its `assets/` are extracted next to it on first use.
 */
class InstalledPluginCatalog(private val storeDir: Path) : PluginCatalogPort {
    private val read = ConcurrentHashMap<String, PackageReadResult>()

    override fun templateFor(publisher: String, id: String, version: String): PluginPlanTemplate? =
        (load(publisher, id, version) as? PackageReadResult.Ok)?.pkg?.template

    override fun contentDigestFor(publisher: String, id: String, version: String): String? =
        (load(publisher, id, version) as? PackageReadResult.Ok)?.pkg?.contentDigest

    override fun assetRootFor(publisher: String, id: String, version: String): String? {
        if (load(publisher, id, version) !is PackageReadResult.Ok) return null
        val content = versionDir(publisher, id, version).resolve("content")
        if (!Files.isDirectory(content)) extractAssets(packageFile(publisher, id, version), content)
        return content.toString()
    }

    private fun load(publisher: String, id: String, version: String): PackageReadResult? {
        val file = packageFile(publisher, id, version)
        if (!Files.isRegularFile(file)) return null
        return read.getOrPut("$publisher:$id:$version") { PluginPackageReader.read(file) }
    }

    /** Extracts into a temporary folder and renames it, so a half-extracted folder is never used. */
    private fun extractAssets(zip: Path, content: Path) {
        val partial = content.resolveSibling("content.partial")
        partial.toFile().deleteRecursively()
        ZipFile(zip.toFile()).use { zf ->
            zf.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith(PluginPackageCodec.ASSETS_DIR) }
                .forEach { entry ->
                    // Member names were validated by PluginPackageReader (no "..", no absolute paths).
                    val target = partial.resolve(entry.name).normalize()
                    require(target.startsWith(partial)) { "asset '${entry.name}' escapes the package" }
                    Files.createDirectories(target.parent)
                    zf.getInputStream(entry).use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
                }
        }
        Files.createDirectories(partial)
        Files.move(partial, content, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun versionDir(publisher: String, id: String, version: String): Path =
        storeDir.resolve("packages").resolve(publisher).resolve(id).resolve(version)

    private fun packageFile(publisher: String, id: String, version: String): Path =
        versionDir(publisher, id, version).resolve("package.lti-mod.zip")
}
