/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.sdk.patchmod

import org.ide.lti.core.domain.plugin.PluginPlanValidator
import org.ide.lti.core.model.plugin.AnchorPattern
import org.ide.lti.core.model.plugin.AssetRef
import org.ide.lti.core.model.plugin.BytecodeMatchSpec
import org.ide.lti.core.model.plugin.Compatibility
import org.ide.lti.core.model.plugin.Condition
import org.ide.lti.core.model.plugin.FileMetadata
import org.ide.lti.core.model.plugin.HookPlacement
import org.ide.lti.core.model.plugin.MethodMatch
import org.ide.lti.core.model.plugin.Operation
import org.ide.lti.core.model.plugin.PartitionPath
import org.ide.lti.core.model.plugin.PluginPlanTemplate
import org.ide.lti.core.model.plugin.SettingDefinition
import org.ide.lti.core.model.plugin.SmaliEdit
import org.ide.lti.core.model.plugin.ValueRef
import org.ide.lti.core.model.plugin.XmlChange
import org.ide.lti.core.model.plugin.XmlChangeKind
import org.ide.lti.core.model.plugin.XmlMatch

fun romMod(publisher: String, id: String, version: String, block: RomModScope.() -> Unit): PluginPlanTemplate {
    val scope = RomModScope().apply(block)
    val template = PluginPlanTemplate(
        schemaVersion = 1,
        publisher = publisher,
        id = id,
        version = version,
        settings = scope.buildSettings(),
        operations = scope.buildOperations(),
        compatibility = scope.buildCompatibility(),
    )
    val report = PluginPlanValidator.validate(template)
    if (report.hasBlockingErrors()) {
        val message = report.errors.joinToString("\n") {
            "${it.code}@${it.objectId ?: ""}.${it.fieldPath}: ${it.message}"
        }
        throw IllegalArgumentException(message)
    }
    return template
}

class RomModScope {
    private val settingsList = mutableListOf<SettingDefinition>()
    private val operationsList = mutableListOf<Operation>()
    private val compatibilityList = mutableListOf<Compatibility>()

    /**
     * Declares settings; each returns a handle to use in values and conditions
     * (`val strict = settings.boolean(…)`).
     */
    val settings: SettingsScope = SettingsScope(settingsList)

    fun settings(block: SettingsScope.() -> Unit) {
        settings.apply(block)
    }

    /**
     * The mod is made for [device] (its codename) and, when given, only these [firmwares] (their versions). Call it
     * once per device; a workspace for any other device or firmware refuses the mod before its build changes anything.
     */
    fun compatibleWith(device: String, vararg firmwares: String) {
        compatibilityList += Compatibility(device, firmwares.toList())
    }

    fun operations(block: OperationsScope.() -> Unit) {
        OperationsScope(operationsList).apply(block)
    }

    internal fun buildSettings(): List<SettingDefinition> = settingsList.toList()

    internal fun buildOperations(): List<Operation> = operationsList.toList()

    internal fun buildCompatibility(): List<Compatibility> = compatibilityList.toList()
}

/** A declared setting, typed by its value, for [ValueRef]s and conditions. */
class SettingRef<T> internal constructor(val id: String) {
    val ref: ValueRef.Setting get() = ValueRef.Setting(id)
}

/** The partitions an operation can target. */
enum class Partition(val id: String) {
    SYSTEM("system"),
    VENDOR("vendor"),
    PRODUCT("product"),
    SYSTEM_EXT("system_ext"),
    ODM("odm"),
    VENDOR_DLKM("vendor_dlkm"),
    ODM_DLKM("odm_dlkm"),
    SYSTEM_DLKM("system_dlkm"),
}

/** Facts about the workspace's device and build that values and conditions can read. */
object Facts {
    val DEVICE_ID: ValueRef.TargetFact = fact("device.id")
    val DEVICE_CODENAME: ValueRef.TargetFact = fact("device.codename")
    val ANDROID_VERSION: ValueRef.TargetFact = fact("android.version")
    val FIRMWARE_VERSION: ValueRef.TargetFact = fact("firmware.version")
    val BUILD_TYPE: ValueRef.TargetFact = fact("build.type")
}

class SettingsScope internal constructor(private val settings: MutableList<SettingDefinition>) {

    fun boolean(
        id: String,
        label: String,
        default: Boolean,
        help: String? = null,
        required: Boolean = false,
        advanced: Boolean = false,
    ): SettingRef<Boolean> {
        settings.add(
            SettingDefinition.BooleanSetting(
                id = id,
                label = label,
                help = help,
                required = required,
                advanced = advanced,
                default = default,
            ),
        )
        return SettingRef(id)
    }

    fun int(
        id: String,
        label: String,
        default: Int,
        min: Int,
        max: Int,
        help: String? = null,
        required: Boolean = false,
        advanced: Boolean = false,
    ): SettingRef<Int> {
        settings.add(
            SettingDefinition.IntSetting(
                id = id,
                label = label,
                help = help,
                required = required,
                advanced = advanced,
                default = default,
                min = min,
                max = max,
            ),
        )
        return SettingRef(id)
    }

    fun text(
        id: String,
        label: String,
        default: String,
        maxLength: Int = 4096,
        help: String? = null,
        required: Boolean = false,
        advanced: Boolean = false,
    ): SettingRef<String> {
        settings.add(
            SettingDefinition.TextSetting(
                id = id,
                label = label,
                help = help,
                required = required,
                advanced = advanced,
                default = default,
                maxLength = maxLength,
            ),
        )
        return SettingRef(id)
    }

    fun enum(
        id: String,
        label: String,
        default: String,
        options: List<String>,
        help: String? = null,
        required: Boolean = false,
        advanced: Boolean = false,
    ): SettingRef<String> {
        settings.add(
            SettingDefinition.EnumSetting(
                id = id,
                label = label,
                help = help,
                required = required,
                advanced = advanced,
                default = default,
                options = options,
            ),
        )
        return SettingRef(id)
    }
}

class OperationsScope internal constructor(private val operations: MutableList<Operation>) {

    fun copy(
        id: String,
        source: String,
        partition: Partition,
        path: String,
        condition: Condition = Always,
        metadata: FileMetadata? = null,
    ) {
        operations.add(
            Operation.Copy(
                id = id,
                source = AssetRef(source),
                destination = PartitionPath(partition.id, path),
                condition = condition,
                metadata = metadata,
            ),
        )
    }

    fun replace(
        id: String,
        source: String,
        partition: Partition,
        path: String,
        expectedSha256: String,
        condition: Condition = Always,
    ) {
        operations.add(
            Operation.Replace(
                id = id,
                source = AssetRef(source),
                destination = PartitionPath(partition.id, path),
                expectedSha256 = expectedSha256,
                condition = condition,
            ),
        )
    }

    fun delete(id: String, partition: Partition, path: String, condition: Condition = Always) {
        operations.add(
            Operation.Delete(
                id = id,
                target = PartitionPath(partition.id, path),
                condition = condition,
            ),
        )
    }

    fun propertyPatch(
        id: String,
        partition: Partition,
        path: String,
        key: String,
        value: ValueRef,
        condition: Condition = Always,
    ) {
        operations.add(
            Operation.PropertyPatch(
                id = id,
                target = PartitionPath(partition.id, path),
                key = key,
                value = value,
                condition = condition,
            ),
        )
    }

    fun textPatch(
        id: String,
        partition: Partition,
        path: String,
        context: String,
        replacement: ValueRef,
        expectedSha256: String,
        condition: Condition = Always,
    ) {
        operations.add(
            Operation.TextPatch(
                id = id,
                target = PartitionPath(partition.id, path),
                context = context,
                replacement = replacement,
                expectedSha256 = expectedSha256,
                condition = condition,
            ),
        )
    }

    fun apkJarPatch(
        id: String,
        partition: Partition,
        path: String,
        condition: Condition = Always,
        block: ApkJarScope.() -> Unit,
    ) {
        val edits = ApkJarScope().apply(block).buildEdits()
        operations.add(
            Operation.ApkJarPatch(
                id = id,
                target = PartitionPath(partition.id, path),
                edits = edits,
                condition = condition,
            ),
        )
    }

    fun compiledClassMerge(
        id: String,
        partition: Partition,
        path: String,
        payload: String,
        classDescriptors: List<String>,
        dependencies: List<String> = emptyList(),
        condition: Condition = Always,
    ) {
        operations.add(
            Operation.CompiledClassMerge(
                id = id,
                target = PartitionPath(partition.id, path),
                payload = AssetRef(payload),
                classDescriptors = classDescriptors,
                dependencies = dependencies,
                condition = condition,
            ),
        )
    }

    fun hookInjection(
        id: String,
        partition: Partition,
        path: String,
        classDescriptor: String,
        methodDescriptor: String,
        opcodeSequence: List<String>,
        expectedPreimageSha256: String,
        hookDescriptor: String,
        placement: HookPlacement = HookPlacement.BEFORE,
        stringConstants: List<String> = emptyList(),
        invokeSignature: String? = null,
        condition: Condition = Always,
    ) {
        operations.add(
            Operation.HookInjection(
                id = id,
                target = PartitionPath(partition.id, path),
                match = BytecodeMatchSpec(
                    classDescriptor = classDescriptor,
                    methodDescriptor = methodDescriptor,
                    anchor = AnchorPattern(
                        opcodeSequence = opcodeSequence,
                        stringConstants = stringConstants,
                        invokeSignature = invokeSignature,
                    ),
                    expectedPreimageSha256 = expectedPreimageSha256,
                ),
                placement = placement,
                hookDescriptor = hookDescriptor,
                condition = condition,
            ),
        )
    }
}

/**
 * The members of one decoded APK or JAR (`smali/…/Foo.smali`, `res/…`) a mod adds or changes. Changes are exact text:
 * an anchor must occur exactly once in the member, or the build stops before writing anything.
 */
class ApkJarScope {
    private val editsList = mutableListOf<SmaliEdit>()

    /** Adds a new member (a smali class, a resource file); the build stops if it already exists. */
    fun addFile(member: String, content: String) =
        edit(member, EMPTY_SHA256, "@@ -0,0 +1 @@\n" + mark('+', content.replace("\r\n", "\n").removeSuffix("\n")))

    /**
     * Changes an existing member, in the order the [block] lists. [preimage] is the member's sha256 before this
     * change: a firmware whose member differs is refused. Leave it out when another mod may have changed it first.
     */
    fun change(member: String, preimage: String? = null, block: TextChangeScope.() -> Unit) {
        val hunks = TextChangeScope().apply(block).hunks
        require(hunks.isNotEmpty()) { "change($member) lists no change" }
        edit(member, preimage.orEmpty(), hunks.joinToString("\n"))
    }

    /**
     * Changes one method of [classDescriptor] (`Lcom/example/Foo;`), wherever the firmware keeps the class. The
     * method is picked by [name] (or a whole `name(parameters)return` signature) and the [block]'s `parameters`,
     * `returns` and `strings` when a name is not enough. Its changes are matched inside that method only; see
     * [MethodScope]. Prefer this over [change]: it keeps working when the rest of the class changes.
     */
    fun method(classDescriptor: String, name: String? = null, block: MethodScope.() -> Unit) {
        val scope = MethodScope().apply(block)
        require(scope.hunks.isNotEmpty()) { "method($classDescriptor, $name) lists no change" }
        val signature = name?.takeIf { '(' in it }
        val match = MethodMatch(
            name = signature?.substringBefore('(') ?: name,
            parameters = signature?.substringAfter('(')?.substringBefore(')') ?: scope.parameters,
            returns = signature?.substringAfter(')') ?: scope.returns,
            strings = scope.strings,
            references = scope.references,
            opcodes = scope.opcodes,
        )
        edit(classDescriptor, "", scope.hunks.joinToString("\n"), match)
    }

    /**
     * Changes an XML [member] (`res/values/strings.xml`, `res/xml/…`) as a document: elements are picked by tag and
     * attributes, never by line, so the rest of the file may change between firmwares. See [XmlScope].
     */
    fun xml(member: String, block: XmlScope.() -> Unit) {
        val changes = XmlScope().apply(block).changes
        require(changes.isNotEmpty()) { "xml($member) lists no change" }
        editsList.add(SmaliEdit(memberDescriptor = member, expectedSha256 = "", unifiedDiff = "", xml = changes))
    }

    internal fun edit(
        memberDescriptor: String,
        expectedSha256: String,
        unifiedDiff: String,
        method: MethodMatch? = null,
    ) {
        editsList.add(
            SmaliEdit(
                memberDescriptor = memberDescriptor,
                expectedSha256 = expectedSha256,
                unifiedDiff = unifiedDiff,
                method = method,
            ),
        )
    }

    internal fun buildEdits(): List<SmaliEdit> = editsList.toList()

    private companion object {
        /** sha256 of nothing: the preimage of a created file. */
        const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }
}

/**
 * Text changes to one member. Every text is whole lines, matched exactly (indentation included): its lines are
 * what it holds between newlines, so a trailing newline adds an empty last line and `""` is one empty line. Each
 * anchor must occur once in the member as the earlier changes left it.
 */
open class TextChangeScope {
    internal val hunks = mutableListOf<String>()

    /**
     * Replaces [find] with [with]. To delete lines, keep a neighbouring line in both: an empty [with] leaves an
     * empty line where [find] was.
     */
    fun replace(find: String, with: String) = hunk(mark('-', find), mark('+', with))

    /** Inserts [text] after [anchor]. */
    fun insertAfter(anchor: String, text: String) = hunk(mark(' ', anchor), mark('+', text))

    /** Inserts [text] before [anchor]. */
    fun insertBefore(anchor: String, text: String) = hunk(mark('+', text), mark(' ', anchor))

    private fun hunk(vararg parts: String) {
        // No line numbers: the engine places the hunk by its (unique) text.
        hunks += "@@ @@\n" + parts.joinToString("\n")
    }
}

/**
 * Changes inside one method. Its lines are compared trimmed, and blank lines and `.line` debug lines are ignored, so
 * only the instructions must match. An anchor must occur once in the method. `@{name}` in an anchor or replaced line
 * stands for any register (`v3`, `p1`); in inserted text it is written as the register it matched. `@{*}` stands for
 * any text and is not captured, and a `...` line skips any number of lines between two anchor lines:
 *
 * ```
 * insertAfter("invoke-virtual {@{app}, @{ctx}}, Landroid/app/Application;->attach(Landroid/content/Context;)V",
 *     "invoke-static {@{ctx}}, Lio/lti/Hooks;->onAttach(Landroid/content/Context;)V")
 * insertAfter("const-string @{*}, \"ro.debuggable\"\n...\nmove-result @{res}", "const/4 @{res}, 0x0")
 * ```
 */
class MethodScope internal constructor() : TextChangeScope() {
    internal var parameters: String? = null
    internal var returns: String? = null
    internal val strings = mutableListOf<String>()
    internal val references = mutableListOf<String>()
    internal val opcodes = mutableListOf<String>()

    /** The method's parameter types, as descriptors (`"I"`, `"Ljava/lang/String;"`); none for `()`. */
    fun parameters(vararg types: String) {
        parameters = types.joinToString("")
    }

    /** The method's return type descriptor (`"V"`, `"Z"`, `"Ljava/lang/String;"`). */
    fun returns(type: String) {
        returns = type
    }

    /** Strings the method loads with `const-string`, as written in smali (escaped). */
    fun strings(vararg values: String) {
        strings += values
    }

    /**
     * Methods, fields or types the method's instructions use, whole or in part:
     * `"Landroid/app/Instrumentation;->newApplication"`, `"Landroid/os/Build;->FINGERPRINT:"`.
     */
    fun references(vararg values: String) {
        references += values
    }

    /** A run of consecutive instructions in the method, by opcode (`"const-string"`, `"*"` for any one). */
    fun opcodes(vararg values: String) {
        opcodes += values
    }
}

/**
 * Changes to one XML document. [element] picks elements by tag (qualified or local name) and attributes; a change
 * other than an [append] to the root must pick exactly one. A fragment may hold several elements and use the
 * document's namespace prefixes (`android:`). A resource appended to the root (`<string name="…">`,
 * `<public … id="…">`) is refused when that name or public id is already declared.
 */
class XmlScope internal constructor() {
    internal val changes = mutableListOf<XmlChange>()

    /** Adds [fragment] at the end of the document's root (a values file's `<resources>`). */
    fun append(fragment: String) {
        changes += XmlChange(XmlChangeKind.APPEND, null, fragment)
    }

    /** Adds [fragment] as the last children of the element [into] picks. */
    fun append(into: XmlMatch, fragment: String) {
        changes += XmlChange(XmlChangeKind.APPEND, into, fragment)
    }

    fun insertBefore(element: XmlMatch, fragment: String) {
        changes += XmlChange(XmlChangeKind.INSERT_BEFORE, element, fragment)
    }

    fun insertAfter(element: XmlMatch, fragment: String) {
        changes += XmlChange(XmlChangeKind.INSERT_AFTER, element, fragment)
    }

    fun replace(element: XmlMatch, fragment: String) {
        changes += XmlChange(XmlChangeKind.REPLACE, element, fragment)
    }

    fun remove(element: XmlMatch) {
        changes += XmlChange(XmlChangeKind.REMOVE, element)
    }
}

/** Elements with this [tag] (any when null) and all these [attributes]: `element("intent", "android:action" to a)`. */
fun element(tag: String? = null, vararg attributes: Pair<String, String>): XmlMatch = XmlMatch(tag, attributes.toMap())

/** [text]'s lines, each prefixed with [prefix] (a unified-diff line marker). */
private fun mark(prefix: Char, text: String): String =
    text.replace("\r\n", "\n").split("\n").joinToString("\n") { "$prefix$it" }

fun literal(v: String): ValueRef.Literal = ValueRef.Literal(v)

fun setting(id: String): ValueRef.Setting = ValueRef.Setting(id)

fun fact(id: String): ValueRef.TargetFact = ValueRef.TargetFact(id)

infix fun ValueRef.eq(other: ValueRef): Condition.Equals = Condition.Equals(this, other)

fun ValueRef.inOptions(vararg options: ValueRef): Condition.In = Condition.In(this, options.toList())

fun and(vararg conditions: Condition): Condition.And = Condition.And(conditions.toList())

fun or(vararg conditions: Condition): Condition.Or = Condition.Or(conditions.toList())

fun not(condition: Condition): Condition.Not = Condition.Not(condition)

val Always: Condition.Always = Condition.Always

infix fun SettingRef<Boolean>.eq(value: Boolean): Condition.Equals = Condition.Equals(ref, literal(value.toString()))

infix fun SettingRef<Int>.eq(value: Int): Condition.Equals = Condition.Equals(ref, literal(value.toString()))

infix fun SettingRef<String>.eq(value: String): Condition.Equals = Condition.Equals(ref, literal(value))

/** The boolean setting is on. */
val SettingRef<Boolean>.isOn: Condition.Equals get() = this eq true
