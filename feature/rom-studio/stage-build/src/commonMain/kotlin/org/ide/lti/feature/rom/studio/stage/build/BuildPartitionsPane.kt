/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.build

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.IdeTable
import org.ide.lti.core.designsystem.component.display.IdeTableHeader
import org.ide.lti.core.designsystem.component.display.IdeTableHeaderCell
import org.ide.lti.core.designsystem.component.display.IdeTableRow
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.pipeline.toolkit.BuildFilesystems
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.BuildSettings
import org.ide.lti.feature.rom.studio.api.formatBytes

/**
 * Every per-partition build choice in one table: filesystem, EROFS compression, deduplication and cluster, and the
 * image size of an ext4 or F2FS image. A choice that does not apply to a row says why instead.
 */
@Composable
internal fun PartitionsPane(
    settings: BuildSettings,
    target: TargetDevice,
    facts: BuildFacts,
    onChange: (BuildSettings) -> Unit,
) {
    val stock = facts.stockImages.associate { it.path.substringAfterLast('/').removeSuffix(".img") to it.sizeBytes }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.Medium)
            .testTag("PartitionsPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Heading("Partitions")
        Body(
            "How each dynamic partition is built. \"Stage setting\" follows the choices above; everything else is " +
                "that partition's own and is recorded in the build's cache key.",
        )
        IdeTable {
            IdeTableHeader {
                COLUMNS.forEach { (title, weight) -> IdeTableHeaderCell(title, Modifier.weight(weight)) }
            }
            target.dynamicPartitions.forEach { p ->
                IdeTableRow(
                    modifier = Modifier.testTag("PartitionRow_$p"),
                ) {
                    PartitionRow(
                        settings,
                        p,
                        stock[p],
                        facts.superblocks[p]?.stock?.fs?.name?.lowercase(),
                        facts.erofsCompressions,
                        onChange,
                    )
                }
            }
        }
        AboutPartitions()
    }
}

@Composable
private fun RowScope.PartitionRow(
    settings: BuildSettings,
    p: String,
    stockBytes: Long?,
    stockFs: String?,
    kernelReads: List<String>?,
    onChange: (BuildSettings) -> Unit,
) {
    val fs = BuildFilesystems.of(settings, p)
    val erofs = fs == BuildFilesystems.EROFS
    val kept = p in settings.keepStock
    val compressed = !kept && erofs && BuildFilesystems.erofsCompression(settings, p) != BuildFilesystems.NO_COMPRESSION
    Cell(0) { NameCell(settings, p, stockBytes, stockFs, onChange) }
    Cell(1) {
        SelectField(
            selected = settings.partitionFilesystem[p],
            options = listOf(SelectOption<String?>(null, "Stage setting (${settings.filesystem})")) +
                BuildFilesystems.SUPPORTED.map { SelectOption(it, it, FILESYSTEM_DETAIL[it]) },
            onSelect = { onChange(settings.copy(partitionFilesystem = settings.partitionFilesystem.with(p, it))) },
            tag = "Filesystem_$p",
            disabledText = AS_SHIPPED.takeIf { kept },
        )
    }
    Cell(2) {
        val own = settings.partitionCompression[p]
        val compressor = own?.substringBefore(',')
        fun set(value: String?) =
            onChange(settings.copy(partitionCompression = settings.partitionCompression.with(p, value)))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
            SelectField(
                selected = compressor,
                options = listOf(SelectOption<String?>(null, "Stage setting (${settings.compression})")) +
                    (PARTITION_COMPRESSION + KERNEL_COMPRESSION.filter { it.first in kernelReads.orEmpty() })
                        .map { (v, label) -> SelectOption(v, label, COMPRESSION_DETAIL[v]) },
                // A compressor that takes levels starts at mkfs.erofs's own one.
                onSelect = { c -> set(c?.let { BuildFilesystems.defaultLevel(it)?.let { l -> "$it,$l" } ?: it }) },
                tag = "Compression_$p",
                disabledText = if (kept) AS_SHIPPED else "— ($fs)".takeUnless { erofs },
            )
            val levels = compressor?.let(BuildFilesystems::levels).orEmpty()
            if (!kept && erofs && levels.isNotEmpty()) {
                val level = own?.substringAfter(',', "")?.removePrefix("level=")?.toIntOrNull()
                SelectField(
                    selected = level,
                    options = levels.map { SelectOption<Int?>(it, "Level $it", levelDetail(it, levels)) },
                    onSelect = { set(if (it == null) compressor else "$compressor,$it") },
                    tag = "Level_$p",
                )
            }
        }
    }
    Cell(3) {
        SelectField(
            selected = settings.partitionDedupe[p],
            options = DEDUPE_OPTIONS,
            onSelect = { onChange(settings.copy(partitionDedupe = settings.partitionDedupe.with(p, it))) },
            tag = "Dedupe_$p",
            disabledText = if (kept) AS_SHIPPED else "—".takeUnless { compressed },
        )
    }
    Cell(4) {
        SelectField(
            selected = settings.partitionClusterKib[p],
            options = CLUSTER_OPTIONS,
            onSelect = { onChange(settings.copy(partitionClusterKib = settings.partitionClusterKib.with(p, it))) },
            tag = "Cluster_$p",
            disabledText = if (kept) AS_SHIPPED else "—".takeUnless { compressed },
        )
    }
    Cell(5) { if (kept) Body(AS_SHIPPED) else SizeCell(settings, p, fs, stockBytes, onChange) }
}

/**
 * [p]'s name, its stock image (filesystem and size), what differs from that stock image, and a button that puts the
 * partition back as it was extracted.
 */
@Composable
private fun NameCell(
    settings: BuildSettings,
    p: String,
    stockBytes: Long?,
    stockFs: String?,
    onChange: (BuildSettings) -> Unit,
) {
    val changed = changesFromStock(settings, p, stockFs, stockBytes)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
        Text(
            text = p,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = codeFontFamily(),
        )
        val stock = listOfNotNull(stockFs, stockBytes?.let(::formatBytes)).joinToString(" · ")
        if (stock.isNotEmpty()) Body("Stock: $stock")
        val kept = p in settings.keepStock
        GlassChip(
            label = if (kept) "Kept as shipped" else "Keep as shipped",
            selected = kept,
            onClick = {
                onChange(settings.copy(keepStock = if (kept) settings.keepStock - p else settings.keepStock + p))
            },
            modifier = Modifier.testTag("KeepStock_$p"),
        )
        if (changed.isNotEmpty()) {
            Text(
                text = "Changed: ${changed.joinToString()}",
                color = GlassTheme.diagnosticColors.warning,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
                modifier = Modifier.testTag("PartitionChanged_$p"),
            )
            GlassButton(
                onClick = { onChange(asExtracted(settings, p, stockFs)) },
                variant = GlassButtonVariant.Secondary,
                modifier = Modifier.testTag("PartitionReset_$p"),
            ) { Text("Reset to stock", fontSize = FontSize.Micro) }
        }
    }
}

/** What [p] is built with that its stock image was not: the filesystem, and every choice of its own. */
internal fun changesFromStock(settings: BuildSettings, p: String, stockFs: String?, stockBytes: Long?): List<String> =
    listOfNotNull(
        "filesystem".takeIf { stockFs != null && BuildFilesystems.of(settings, p) != stockFs },
        "compression".takeIf { p in settings.partitionCompression },
        "deduplication".takeIf { p in settings.partitionDedupe },
        "cluster".takeIf { p in settings.partitionClusterKib },
        "size".takeIf {
            val stock = stockBytes?.let { it - it % settings.blockSize }
            settings.partitionSizeBytes[p]?.let { it != stock } == true
        },
        "free space".takeIf { p in settings.partitionFreeMib },
    )

/** [settings] with [p] as extracted: its own choices dropped, and its stock filesystem when the stage's is another. */
internal fun asExtracted(settings: BuildSettings, p: String, stockFs: String?): BuildSettings = settings.copy(
    partitionFilesystem = settings.partitionFilesystem.with(
        p,
        stockFs?.takeIf { it != settings.filesystem.lowercase() },
    ),
    partitionCompression = settings.partitionCompression.with(p, null),
    partitionDedupe = settings.partitionDedupe.with(p, null),
    partitionClusterKib = settings.partitionClusterKib.with(p, null),
    partitionSizeBytes = settings.partitionSizeBytes.with(p, null),
    partitionFreeMib = settings.partitionFreeMib.with(p, null),
)

private fun levelDetail(level: Int, levels: List<Int>): String? = when (level) {
    levels.first() -> "Fastest to build, larger"
    levels.last() -> "Smallest, slowest to build"
    else -> null
}

/**
 * [p]'s image size. EROFS is always its content's size; ext4 and F2FS are automatic (content plus margin), a fixed
 * size (the stock image's, or typed in KiB), or for ext4 automatic with free space to write to later.
 */
@Composable
private fun SizeCell(
    settings: BuildSettings,
    p: String,
    fs: String,
    stockBytes: Long?,
    onChange: (BuildSettings) -> Unit,
) {
    val stock = stockBytes?.let { it - it % settings.blockSize }
    val mode = sizeMode(settings, p, stock)
    fun apply(bytes: Long?, mib: Int?) = onChange(
        settings.copy(
            partitionSizeBytes = settings.partitionSizeBytes.with(p, bytes),
            partitionFreeMib = settings.partitionFreeMib.with(p, mib),
        ),
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        modifier = Modifier.testTag("PartitionSize_$p"),
    ) {
        SelectField(
            selected = mode,
            options = sizeOptions(fs, stockBytes),
            onSelect = { m -> sizeChoice(m, settings, p, stock).let { (bytes, mib) -> apply(bytes, mib) } },
            tag = "Size_$p",
            disabledText = "Content size".takeIf { fs == BuildFilesystems.EROFS },
        )
        val fixed = settings.partitionSizeBytes[p]
        val free = settings.partitionFreeMib[p]
        if (mode == SIZE_FIXED && fixed != null) {
            UnitField((fixed / KIB).toInt(), "KiB", "SizeKib_$p") { kib -> apply(kib * KIB, null) }
        }
        if (mode == SIZE_FREE && free != null) {
            UnitField(free, "MiB free", "FreeMib_$p") { mib -> apply(null, mib) }
        }
    }
}

/** Which size choice [p] holds: its fixed size is the stock image's, another fixed size, free space, or none. */
private fun sizeMode(settings: BuildSettings, p: String, stock: Long?): String {
    val fixed = settings.partitionSizeBytes[p]
    return when {
        fixed != null && fixed == stock -> SIZE_STOCK
        fixed != null -> SIZE_FIXED
        settings.partitionFreeMib[p] != null -> SIZE_FREE
        else -> SIZE_AUTO
    }
}

private fun sizeOptions(fs: String, stockBytes: Long?): List<SelectOption<String>> = buildList {
    add(SelectOption(SIZE_AUTO, "Automatic", "Its content plus a margin"))
    stockBytes?.let { add(SelectOption(SIZE_STOCK, "Stock size (${it / KIB} KiB)", "The firmware image's size")) }
    add(SelectOption(SIZE_FIXED, "Fixed size…", "A size in KiB; too small stops the build"))
    if (fs == BuildFilesystems.EXT4) add(SelectOption(SIZE_FREE, "Automatic + free space…", "Room to write to later"))
}

/** The fixed size and free MiB that choosing [mode] for [p] stores (a fixed size and free space never both). */
private fun sizeChoice(mode: String, settings: BuildSettings, p: String, stock: Long?): Pair<Long?, Int?> =
    when (mode) {
        SIZE_STOCK -> stock to null
        SIZE_FIXED -> (settings.partitionSizeBytes[p] ?: stock ?: DEFAULT_FIXED) to null
        SIZE_FREE -> null to (settings.partitionFreeMib[p] ?: DEFAULT_FREE_MIB)
        else -> null to null
    }

/** This map with [key] set to [value], or without [key] when [value] is null (the stage-wide choice). */
private fun <V> Map<String, V>.with(key: String, value: V?): Map<String, V> =
    if (value == null) this - key else this + (key to value)

/** A number with its unit beside it; empty or zero is ignored rather than stored. */
@Composable
private fun UnitField(value: Int, unit: String, tag: String, onChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        GlassTextField(
            value = value.toString(),
            onValueChange = { text -> text.toIntOrNull()?.takeIf { it > 0 }?.let(onChange) },
            modifier = Modifier.weight(1f).testTag(tag),
        )
        Body(unit)
    }
}

/** What each column changes, for someone choosing (beside the table, as the other Build panes explain theirs). */
@Composable
private fun AboutPartitions() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        Heading("About these settings")
        ABOUT.forEach { (title, text) ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ideFontFamily(),
                    modifier = Modifier.weight(ABOUT_TITLE_WEIGHT),
                )
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.BodySmall,
                    fontFamily = ideFontFamily(),
                    modifier = Modifier.weight(ABOUT_TEXT_WEIGHT),
                )
            }
        }
    }
}

@Composable
private fun RowScope.Cell(column: Int, content: @Composable () -> Unit) {
    Column(modifier = Modifier.weight(COLUMNS[column].second).padding(end = Spacing.Small)) { content() }
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = FontSize.TitleSmall,
        fontWeight = FontWeight.SemiBold,
        fontFamily = ideFontFamily(),
    )
}

@Composable
private fun Body(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = FontSize.BodySmall,
        fontFamily = ideFontFamily(),
    )
}

private val COLUMNS = listOf(
    "Partition" to 0.9f,
    "Filesystem" to 1.3f,
    "Compression" to 1.5f,
    "Deduplicate" to 1.5f,
    "Cluster" to 1.1f,
    "Image size" to 1.7f,
)

private val FILESYSTEM_DETAIL = mapOf(
    BuildFilesystems.EROFS to "Smallest, read-only; what stock uses",
    BuildFilesystems.EXT4 to "Writable later; suits a GSI",
    BuildFilesystems.F2FS to "Built read-only; the kernel must mount F2FS",
)

private val COMPRESSION_DETAIL = mapOf(
    "lz4" to "Fastest to read and build, larger",
    "lz4hc" to "Smaller lz4, slower to build; same speed to read",
    "none" to "No compression: the largest image",
    "lzma" to "Smallest; slowest to read and build",
    "deflate" to "Between lz4hc and lzma",
    "zstd" to "About lz4hc's size, fast to read",
)

private val DEDUPE_OPTIONS = listOf(
    SelectOption<Boolean?>(null, "Automatic", "On when the boot kernel is 6.1 or newer"),
    SelectOption<Boolean?>(true, "On", "Identical data stored once; minutes per image"),
    SelectOption<Boolean?>(false, "Off", "Seconds per image"),
)

private val CLUSTER_DETAIL = mapOf(
    16 to "A little smaller",
    64 to "About 9% smaller (vendor)",
    128 to "Smallest; small reads decompress more",
)

private val CLUSTER_OPTIONS = listOf(SelectOption<Int?>(null, "4 KiB (stock)", "One block, as AOSP builds")) +
    BuildFilesystems.CLUSTER_KIB.map { SelectOption<Int?>(it, "$it KiB", CLUSTER_DETAIL[it]) }

private val ABOUT = listOf(
    "Filesystem" to "The phone needs an fstab line for it: the build adds one to vendor/etc/fstab.*, and " +
        "Assemble > Mount table adds it to the first-stage fstab in vendor_boot.",
    "Compression" to "Passed to mkfs.erofs as -z for that partition. lzma, deflate and zstd are offered only " +
        "when the boot kernel has their decoder (CONFIG_EROFS_FS_ZIP_*).",
    "Deduplicate" to "mkfs.erofs -Ededupe,fragments: vendor shrinks about a quarter, each image takes minutes " +
        "to build. A kernel older than 6.1 cannot read it, so forcing it on there stops the build.",
    "Cluster" to "mkfs.erofs -C: bigger compresses better in seconds; the device then decompresses a whole " +
        "cluster for each small read.",
    "Image size" to "EROFS is always its content's size. A fixed ext4 or F2FS size the content does not fit " +
        "stops the build and says how much it needs.",
)

private const val SIZE_AUTO = "auto"
private const val SIZE_STOCK = "stock"
private const val SIZE_FIXED = "fixed"
private const val SIZE_FREE = "free"
private const val KIB = 1024L
private const val DEFAULT_FIXED = 64L * 1024 * 1024
private const val DEFAULT_FREE_MIB = 64

/** What a cell of a partition kept as shipped says: its stock image is packaged, nothing is built. */
private const val AS_SHIPPED = "As shipped"
private const val ABOUT_TITLE_WEIGHT = 1f
private const val ABOUT_TEXT_WEIGHT = 5f
