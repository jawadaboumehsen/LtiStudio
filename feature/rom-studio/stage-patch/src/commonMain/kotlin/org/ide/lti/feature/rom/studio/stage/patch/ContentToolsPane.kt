/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.patch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.pipeline.toolkit.CodePackageDiff
import org.ide.lti.core.domain.pipeline.toolkit.ConfigKind
import org.ide.lti.core.domain.pipeline.toolkit.DeodexStatus
import org.ide.lti.core.domain.pipeline.toolkit.TreeChange
import org.ide.lti.core.domain.pipeline.toolkit.TreeChangeKind
import org.ide.lti.core.domain.workbench.RiskyFeature
import org.ide.lti.core.ui.GlassCodeEditor
import org.ide.lti.core.ui.toolkit.AcknowledgementDialog
import org.ide.lti.core.ui.toolkit.RefusalView

/** The content tool named by [toolId] (one of [ContentTools.ALL]) over the assembled work tree. */
@Composable
public fun ContentToolsPane(
    toolId: String,
    state: ContentToolsState,
    viewModel: ContentToolsViewModel,
    modifier: Modifier = Modifier,
) {
    // A message or refusal belongs to the tool that made it: another tool's tab starts clean.
    LaunchedEffect(toolId) { viewModel.dismiss() }
    Box(modifier.fillMaxSize().testTag("ContentToolsPane"), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = GlassDimens.OverviewMaxContentWidth)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.ExtraLarge, vertical = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Feedback(state, viewModel)
            when (toolId) {
                ContentTools.DEODEX -> DeodexCard(state, viewModel)

                ContentTools.PACKAGES -> PackageCard(state, viewModel)

                ContentTools.LIBRARY -> LibraryCard(state, viewModel)

                ContentTools.CONFIGS -> ConfigCard(state, viewModel)

                ContentTools.COMPARE -> {
                    StockCompareCard(state, viewModel)
                    CompareCard(state, viewModel)
                }

                ContentTools.CSC -> CscCard(state, viewModel)
            }
        }
    }
    state.pendingAck?.let { feature ->
        AcknowledgementDialog(
            featureId = feature,
            warningMessage = ACK_TEXT[feature] ?: "This changes files the device runs.",
            onAcknowledge = viewModel::acknowledge,
            onDismiss = viewModel::dismissAck,
        )
    }
}

@Composable
private fun DeodexCard(state: ContentToolsState, viewModel: ContentToolsViewModel) {
    Card("Deodex (FR-028)") {
        Hint(
            "Checks every app of the assembled tree: an APK that carries its dex is already standalone (a stale oat/ " +
                "next to it is removed); an app whose code is only in a vdex is listed.",
        )
        GlassButton(onClick = viewModel::deodex, enabled = state.busy == null, variant = GlassButtonVariant.Primary) {
            Text("Check and deodex the work tree", fontSize = FontSize.BodySmall)
        }
        state.deodex?.let { results ->
            DeodexStatus.entries.forEach { s ->
                val count = results.count { it.status == s }
                if (count > 0) Mono("$s: $count")
            }
            LazyColumn(Modifier.fillMaxWidth().height(ComponentSize.MaxPanelHeight)) {
                items(results.filter { it.status != DeodexStatus.AlreadyDone }) { r ->
                    Mono("${r.status}  ${r.app}  ${r.reason.orEmpty()}")
                }
            }
        }
    }
}

@Composable
private fun PackageCard(state: ContentToolsState, viewModel: ContentToolsViewModel) {
    var path by remember { mutableStateOf("work/system/system/framework/services.jar") }
    val idle = state.busy == null
    Card("APK and JAR (FR-029)") {
        Hint(
            "Decode a package of the work tree with apktool, edit its smali or resources in the folder that opens, " +
                "then rebuild: an APK is aligned and signed with the workspace platform key, the archive is tested, " +
                "and the original is kept as a restore point. This changes the assembled tree only: a build that " +
                "assembles again replaces it, so an edit for every build belongs in a mod or a Patches script.",
        )
        GlassTextField(value = path, onValueChange = { path = it }, placeholder = "work/…/Name.apk or .jar")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            GlassButton(
                onClick = { viewModel.decode(path) },
                enabled = idle,
            ) { Text("Decode", fontSize = FontSize.BodySmall) }
            GlassButton(
                onClick = { viewModel.rebuild(path) },
                enabled = idle,
            ) { Text("Rebuild into the tree", fontSize = FontSize.BodySmall) }
        }
        state.decodedFolder?.let { Mono("Decoded: $it") }
        state.rebuilt?.let { Mono("${it.status}: ${it.file}") }
    }
}

@Composable
private fun LibraryCard(state: ContentToolsState, viewModel: ContentToolsViewModel) {
    var apex by remember { mutableStateOf("work/system/system/apex/com.android.btservices.apex") }
    var library by remember { mutableStateOf("lib64/libbluetooth_jni.so") }
    var find by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    var dest by remember { mutableStateOf("work/system/system/lib64/libbluetooth_jni.so") }
    Card("Library patch in an APEX (FR-030)") {
        Hint(
            "Takes a library out of an APEX payload, replaces the find bytes with the replace bytes (hex; \".\" in " +
                "find matches any digit) and puts the patched copy in the tree, as a module overlay does. A library " +
                "without the find bytes is refused, also one patched before. The patterns depend on the device and " +
                "Android version (for Samsung Bluetooth, see the BluetoothLibraryPatcher project). Like every edit " +
                "here, it lasts until a build assembles again.",
        )
        GlassTextField(value = apex, onValueChange = { apex = it }, label = "APEX in the work tree")
        GlassTextField(value = library, onValueChange = { library = it }, label = "Library inside the APEX payload")
        GlassTextField(value = find, onValueChange = { find = it }, label = "Find (hex)")
        GlassTextField(value = replace, onValueChange = { replace = it }, label = "Replace (hex)")
        GlassTextField(value = dest, onValueChange = { dest = it }, label = "Patched copy goes to")
        GlassButton(
            onClick = { viewModel.patchLibrary(apex, library, find, replace, dest) },
            enabled = state.busy == null && find.isNotBlank() && replace.isNotBlank(),
        ) {
            Text("Patch", fontSize = FontSize.BodySmall)
        }
        state.library?.let { Mono(it.message) }
    }
}

@Composable
private fun ConfigCard(state: ContentToolsState, viewModel: ContentToolsViewModel) {
    var path by remember { mutableStateOf(state.configPath) }
    Card("Configuration files (FR-031)") {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            CONFIG_DEFAULTS.forEach { (kind, default) ->
                GlassChip(
                    label = kind.name.lowercase().replace('_', ' '),
                    selected = state.configKind == kind,
                    onClick = {
                        path = default
                        viewModel.loadConfig(kind, default)
                    },
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassTextField(value = path, onValueChange = { path = it }, modifier = Modifier.weight(1f))
            GlassButton(onClick = { viewModel.loadConfig(state.configKind, path) }, enabled = state.busy == null) {
                Text("Open", fontSize = FontSize.BodySmall)
            }
        }
        state.configText?.let { text ->
            GlassCodeEditor(
                content = text,
                onContentChange = viewModel::editConfig,
                modifier = Modifier.fillMaxWidth().height(ComponentSize.MaxPanelHeight),
            )
            state.configErrors.take(MAX_LINES).forEach { Problem(it) }
            GlassButton(
                onClick = viewModel::saveConfig,
                enabled = state.busy == null && state.configErrors.isEmpty(),
                variant = GlassButtonVariant.Primary,
            ) {
                Text("Save", fontSize = FontSize.BodySmall)
            }
        }
        state.configSaved?.differences?.take(MAX_LINES)?.forEach { Mono(it) }
    }
}

@Composable
private fun CompareCard(state: ContentToolsState, viewModel: ContentToolsViewModel) {
    var kind by remember { mutableStateOf(CompareKind.FILES) }
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    Card("Compare (FR-033)") {
        Hint(
            "Paths in the workspace. Code compares two folders decoded with apktool, " +
                "class by class and method by method.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            CompareKind.entries.forEach { k ->
                GlassChip(label = k.name.lowercase(), selected = kind == k, onClick = { kind = k })
            }
        }
        GlassTextField(value = a, onValueChange = { a = it }, label = "A")
        GlassTextField(value = b, onValueChange = { b = it }, label = "B")
        GlassButton(
            onClick = { viewModel.compare(kind, a, b) },
            enabled = state.busy == null && a.isNotBlank() && b.isNotBlank(),
        ) {
            Text("Compare", fontSize = FontSize.BodySmall)
        }
        val lines = compareLines(kind, state)
        lines?.take(MAX_LINES)?.forEach { Mono(it) }
        if ((lines?.size ?: 0) > MAX_LINES) Hint("…and ${lines!!.size - MAX_LINES} more")
    }
}

/** The last result of a [kind] comparison as lines, or null before one ran. */
private fun compareLines(kind: CompareKind, state: ContentToolsState): List<String>? = when (kind) {
    CompareKind.FILES -> state.fileDiff?.let { if (it.identical) listOf("Identical") else it.lineDifferences }
    CompareKind.ARCHIVES -> state.archiveDiff?.let { d -> sets(d.missingInB, d.extraInB, d.modified) }
    CompareKind.FOLDERS -> state.folderDiff?.let { d -> sets(d.missingInB, d.extraInB, d.modified) }
    CompareKind.CODE -> state.codeDiff?.let(::codeLines)
}

/** A code comparison as lines: classes added or removed, then each changed method with its changed instructions. */
private fun codeLines(d: CodePackageDiff): List<String> = if (d.identical) {
    listOf("Identical")
} else {
    d.classDifferences + d.methodDifferences.flatMap { m ->
        listOf("${m.changeKind} ${m.className} ${m.methodName}") + m.lines.map { "    $it" }
    }
}

/** What the build changed in one partition compared with the stock firmware. */
@Composable
private fun StockCompareCard(state: ContentToolsState, viewModel: ContentToolsViewModel) {
    LaunchedEffect(Unit) { if (state.comparable == null) viewModel.loadComparable() }
    var partition by remember { mutableStateOf<String?>(null) }
    var shown by remember { mutableStateOf<TreeChangeKind?>(null) }
    Card("What the build changed") {
        Hint(
            "A partition of the assembled tree against the stock firmware: files added, removed or changed, and " +
                "changes of owner, mode, capabilities, SELinux label or link target. A changed jar or APK can be " +
                "compared method by method.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            state.comparable.orEmpty().forEach { p ->
                GlassChip(label = p, selected = partition == p, onClick = { partition = p })
            }
        }
        if (state.comparable?.isEmpty() == true) Hint("Run the build through Assemble first.")
        GlassButton(
            onClick = { partition?.let(viewModel::compareWithStock) },
            enabled = state.busy == null && partition != null,
            modifier = Modifier.testTag("CompareWithStockButton"),
        ) {
            Text("Compare with stock", fontSize = FontSize.BodySmall)
        }
        state.stockDiff?.let { diff ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                TreeChangeKind.entries.forEach { k ->
                    GlassChip(
                        label = "${k.name.lowercase()} ${diff.count(k)}",
                        selected = shown == k,
                        onClick = { shown = if (shown == k) null else k },
                    )
                }
            }
            Hint("${diff.partition}: ${diff.changes.size} changed, ${diff.unchanged} unchanged")
            val rows = diff.changes.filter { shown == null || it.kind == shown }
            rows.take(MAX_LINES).forEach { change -> ChangeRow(diff.partition, change, state, viewModel) }
            if (rows.size > MAX_LINES) Hint("…and ${rows.size - MAX_LINES} more")
        }
        val code = state.stockCode
        if (code != null) {
            Hint("Code of ${code.first}, stock against the build:")
            val lines = codeLines(code.second)
            lines.take(MAX_LINES).forEach { Mono(it) }
            if (lines.size > MAX_LINES) Hint("…and ${lines.size - MAX_LINES} more")
        }
    }
}

@Composable
private fun ChangeRow(
    partition: String,
    change: TreeChange,
    state: ContentToolsState,
    viewModel: ContentToolsViewModel,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Box(Modifier.weight(1f)) {
            Mono("${change.kind.name.lowercase()} ${change.path}" + change.details.joinToString("") { " · $it" })
        }
        val code = change.kind == TreeChangeKind.CONTENT && (
            change.path.endsWith(
                ".jar",
            ) || change.path.endsWith(".apk")
            )
        if (code) {
            GlassButton(
                onClick = { viewModel.compareStockCode(partition, change.path) },
                enabled = state.busy == null,
            ) {
                Text("Compare code", fontSize = FontSize.BodySmall)
            }
        }
    }
}

@Composable
private fun CscCard(state: ContentToolsState, viewModel: ContentToolsViewModel) {
    Card("Samsung region and carrier (FR-032)") {
        Hint(
            "Reads sales_code.dat, customer.xml and cscfeature.xml from the work tree. " +
                "Encrypted CSC files are refused.",
        )
        GlassButton(
            onClick = viewModel::readCsc,
            enabled = state.busy == null,
        ) { Text("Read", fontSize = FontSize.BodySmall) }
        state.csc?.let { csc ->
            Mono("Sales code ${csc.salesCode}" + (csc.country?.let { ", $it" } ?: ""))
            csc.networks.forEach { n -> Mono("${n.mcc}-${n.mnc} ${n.networkName}") }
            csc.features.entries.take(MAX_LINES).forEach { (k, v) -> Mono("$k = $v") }
        }
    }
}

@Composable
private fun Feedback(state: ContentToolsState, viewModel: ContentToolsViewModel) {
    state.busy?.let { busy ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Mono("$busy…")
            GlassButton(
                onClick = viewModel::cancel,
                variant = GlassButtonVariant.Secondary,
            ) { Text("Cancel", fontSize = FontSize.BodySmall) }
        }
    }
    state.refusal?.let { RefusalView(refusal = it, actionLabel = "Dismiss", onAction = viewModel::dismiss) }
    state.message?.let { GlassBanner(severity = GlassBannerSeverity.INFO, title = it, onDismiss = viewModel::dismiss) }
}

private fun sets(missing: List<String>, extra: List<String>, modified: List<String>): List<String> =
    if (missing.isEmpty() && extra.isEmpty() && modified.isEmpty()) {
        listOf("Identical")
    } else {
        missing.map { "only in A: $it" } + extra.map { "only in B: $it" } + modified.map { "changed: $it" }
    }

private val CONFIG_DEFAULTS = listOf(
    ConfigKind.BUILD_PROP to "work/system/system/build.prop",
    ConfigKind.FSTAB to "work/vendor/etc/fstab.qcom",
    ConfigKind.FS_CONFIG to "work/configs/fs_config-system",
    ConfigKind.FILE_CONTEXTS to "work/configs/file_context-system",
)
private const val MAX_LINES = 200
private val ACK_TEXT = mapOf(
    RiskyFeature.PACKAGE_REBUILD to "The rebuilt package replaces the original in the work tree and is signed with " +
        "the workspace platform key. A broken package can stop the device from booting.",
    RiskyFeature.LIBRARY_PATCH to "A patched system library is loaded by the device; a wrong pattern can break the " +
        "feature that uses it.",
)

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.TitleSmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
        )
        content()
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = FontSize.BodySmall,
        fontFamily = ideFontFamily(),
    )
}

@Composable
private fun Mono(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = FontSize.BodySmall,
        fontFamily = codeFontFamily(),
    )
}

@Composable
private fun Problem(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
        fontSize = FontSize.BodySmall,
        fontFamily = codeFontFamily(),
    )
}
