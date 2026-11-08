/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.toolkit

/** A symbolic link as the phone sees it: [path] on the device, pointing at [target] (as written in the link). */
public data class DeviceLink(val path: String, val target: String)

/**
 * Symbolic links of a ROM checked the way the phone resolves them. The trees are one folder
 * per partition; system is the root `/`, every other partition is mounted on `/<name>` and hides system's folder
 * of that name. A link into an empty top-level folder of system (`/apex`, `/data`, `/mnt`, ...) points at
 * something only made at run time and is not checked.
 *
 * Two steps, because only WSL can look at the files: [LISTING_SCRIPT] lists the links, [resolve] says which
 * paths must exist, [EXISTS_SCRIPT] says which of those do not, and [broken] puts it together.
 */
public object LinkCheck {
    /**
     * `sh -c LISTING_SCRIPT sh <tree> <partition>...`: `P\t<partition>` per partition folder present, its links as
     * `L\t<partition>\t<path>\t<target>`, then system's empty top-level folders as `E\t<folder>`.
     */
    public const val LISTING_SCRIPT: String =
        "r=\"\$1\"; shift; for p in \"\$@\"; do [ -d \"\$r/\$p\" ] || continue; printf 'P\\t%s\\n' \"\$p\"; " +
            "find \"\$r/\$p\" -type l -printf \"L\\t\$p\\t%P\\t%l\\n\"; done; [ -d \"\$r/system\" ] && " +
            "find \"\$r/system\" -mindepth 1 -maxdepth 1 -type d -empty -printf 'E\\t%P\\n'; true"

    /** `sh -c EXISTS_SCRIPT sh <tree> <path>...`: the paths that do not exist, one per line. */
    public const val EXISTS_SCRIPT: String =
        "r=\"\$1\"; shift; for p in \"\$@\"; do [ -e \"\$r/\$p\" ] || printf '%s\\n' \"\$p\"; done"

    private const val MAX_HOPS = 40

    /** A parsed [LISTING_SCRIPT] output. */
    public data class Listing(
        val links: Map<String, String>,
        val runtimeRoots: Set<String>,
        val partitions: Set<String>,
    )

    /** Where a link ends: a file of the trees ([treePath], `<partition>/<path>`), at run time, or in a loop. */
    public sealed interface Ending {
        public data class InTree(val treePath: String) : Ending

        public data object Runtime : Ending

        public data object Loop : Ending
    }

    public fun parse(output: String): Listing {
        val links = mutableMapOf<String, String>()
        val roots = mutableSetOf<String>()
        val rows = output.lines().map { it.split('\t') }
        val partitions = rows.filter { it.size == 2 && it[0] == "P" }.map { it[1] }.toSet()
        rows.forEach { row ->
            when {
                row.size == 4 && row[0] == "L" -> {
                    val partition = row[1]
                    val path = row[2]
                    val target = row[3]
                    // System's own folder of a mounted partition is hidden by that partition on the phone.
                    val top = path.substringBefore('/')
                    val hidden = partition == SYSTEM && top != SYSTEM && top in partitions
                    if (!hidden) links[devicePath(partition, path)] = target
                }

                row.size == 2 && row[0] == "E" -> roots += row[1]
            }
        }
        return Listing(links, roots, partitions)
    }

    /** Where each link ends; the [Ending.InTree] paths are the ones [EXISTS_SCRIPT] must look at. */
    public fun resolve(listing: Listing): Map<String, Ending> = listing.links.keys.associateWith { follow(it, listing) }

    /** Links that end on nothing, with what they point at, given [missing] from [EXISTS_SCRIPT]. */
    public fun broken(listing: Listing, endings: Map<String, Ending>, missing: Set<String>): List<DeviceLink> =
        endings.filter { (_, end) -> end == Ending.Loop || (end is Ending.InTree && end.treePath in missing) }
            .keys.sorted().map { DeviceLink(it, listing.links.getValue(it)) }

    /** The links broken in the build but not in the stock firmware: the ones the build broke. */
    public fun newlyBroken(stock: List<DeviceLink>, build: List<DeviceLink>): List<DeviceLink> {
        val before = stock.map { it.path }.toSet()
        return build.filter { it.path !in before }
    }

    /** Walks [link] one part at a time, as the kernel does: a link is followed before a later `..` applies to it. */
    private fun follow(link: String, listing: Listing): Ending {
        val done = ArrayDeque<String>()
        val rest = ArrayDeque(link.split('/'))
        var hops = 0
        while (rest.isNotEmpty()) {
            val part = rest.removeFirst()
            when (part) {
                "", "." -> continue

                ".." -> done.removeLastOrNull()

                else -> {
                    val target = listing.links["/" + (done + part).joinToString("/")]
                    if (target == null) {
                        done.addLast(part)
                    } else {
                        if (++hops > MAX_HOPS) return Ending.Loop
                        if (target.startsWith("/")) done.clear()
                        rest.addAll(0, target.split('/'))
                    }
                }
            }
        }
        return ending("/" + done.joinToString("/"), listing)
    }

    private fun ending(path: String, listing: Listing): Ending {
        val top = path.trim('/').substringBefore('/')
        val partition = top.takeIf { it in listing.partitions && it != SYSTEM }
        return when {
            partition != null -> Ending.InTree("$partition/${path.trim('/').substringAfter('/', "")}".trimEnd('/'))
            top in listing.runtimeRoots -> Ending.Runtime
            else -> Ending.InTree("$SYSTEM/${path.trim('/')}".trimEnd('/'))
        }
    }

    private fun devicePath(partition: String, path: String): String =
        if (partition == SYSTEM) "/$path" else "/$partition/$path"

    private const val SYSTEM = "system"
}
