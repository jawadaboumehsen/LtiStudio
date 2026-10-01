package io.ltirom.tooling.codegen.spec

import io.ltirom.tooling.core.ToolId
import io.ltirom.tooling.core.ToolRisk
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Phase3SpecificationTest {
    private val json = Json { ignoreUnknownKeys = false }

    private fun projectRoot(): File {
        var dir: File? = File(".").canonicalFile
        while (dir != null &&
            (!File(dir, "tool/specs").isDirectory || !File(dir, "settings.gradle.kts").isFile)
        ) dir = dir.parentFile
        return assertNotNull(dir)
    }

    @Test
    fun everyManagedToolHasAnInvokableSpecAndUseCase() {
        val root = projectRoot()
        val files = File(root, "tool/specs").listFiles { _, n -> n.endsWith(".json") }!!.toList()
        assertEquals(ToolId.entries.size, files.size)

        for (file in files) {
            val spec = json.decodeFromString(ToolSpecification.serializer(), file.readText())
            SpecValidator.validate(spec)
            assertFalse(spec.helpSha256.isNullOrBlank(), "${spec.tool} has no captured help fingerprint")
            assertFalse(spec.commands.isEmpty(), "${spec.tool} has no invokable command")
            assertEquals(spec.commands.size, spec.commands.map { it.subcommandPath }.toSet().size)

            val packageName = spec.tool.lowercase().replace("-", "").replace(".", "")
            val family = ToolFamily.forTool(spec.tool)
            for (cmd in spec.commands) {
                val useCaseName = cmd.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } + "UseCase.kt"
                val useCaseFile = File(root, "tool/adapter/${family.directoryName}/src/main/kotlin/io/ltirom/tooling/adapters/$packageName/$useCaseName")
                assertTrue(useCaseFile.isFile, "Missing generated use case $useCaseName for ${spec.tool}")
            }
        }
    }

    @Test
    fun knownMutatingToolsCannotFallBackToReadOnlyRisk() {
        val root = projectRoot()
        val mustWrite = setOf(
            "aapt2", "apktool", "append2simg", "avbtool", "e2fsdroid", "erofsfuse",
            "ext2simg", "fec", "fsck.erofs", "img2sdat", "img2simg", "lpadd",
            "lpmake", "lpunpack", "make_f2fs", "mkbootfs", "mkbootimg", "mkdtboimg",
            "mke2fs", "mkf2fsuserimg", "mkfs.erofs", "mkuserimg_mke2fs",
            "payload-dumper-go", "repack_bootimg", "signapk", "simg2img", "sload_f2fs",
            "unpack_bootimg", "zipalign"
        )
        for (tool in mustWrite) {
            val spec = json.decodeFromString(
                ToolSpecification.serializer(),
                File(root, "tool/specs/$tool.json").readText()
            )
            assertFalse(
                spec.risk == ToolRisk.READ_ONLY && spec.commands.all { (it.risk ?: spec.risk) == ToolRisk.READ_ONLY },
                "$tool has no host/device-write command risk"
            )
            assertFalse(
                spec.commands.any { it.name == "execute" && (it.risk ?: spec.risk) == ToolRisk.READ_ONLY },
                "$tool has a read-only generic execution command"
            )
        }
    }

    @Test
    fun requiredPathArgumentsAreNonNullInGeneratedModels() {
        val root = projectRoot()
        val generated = File(
            root,
            "tool/adapter/android-package/src/main/kotlin/io/ltirom/tooling/adapters/aapt2/DumpBadgingCommand.kt"
        ).readText()
        assertEquals(
            true,
            Regex("public val file: File\\r?\\n").containsMatchIn(generated),
            "required PATH arguments must not be emitted as nullable Kotlin values"
        )
        assertFalse(
            Regex("public val file: File\\?").containsMatchIn(generated),
            "a required PATH argument must never be serialized as the literal null"
        )
    }

    @Test
    fun helpParserRecognizesValueTakingOptionsAfterAliases() {
        val options = SpecDraftGenerator.parseOptions(
            listOf(
                " -j,--jobs <num>                Set the number of jobs.",
                " -l,--lib <package:file>         Use a shared library.",
                "                                Can be specified multiple times.",
                "    --output <dir>              Write output here.",
                "    --debuggable                 Set the debug flag."
            ).joinToString("\n")
        )

        assertEquals(4, options.size)
        assertEquals(OptionType.INT, options.first { it.name == "jobs" }.type)
        assertEquals(OptionType.STRING, options.first { it.name == "lib" }.type)
        assertTrue(options.first { it.name == "lib" }.repeatable)
        assertEquals(OptionType.PATH, options.first { it.name == "output" }.type)
        assertEquals(OptionType.BOOLEAN, options.first { it.name == "debuggable" }.type)
        assertTrue(options.first { it.name == "jobs" }.aliases.contains("-j"))
    }

    @Test
    fun helpParserCapturesRequiredPathOperands() {
        val arguments = SpecDraftGenerator.parseArguments(
            "apktool d|decode [options] <apk-file>"
        )
        assertEquals(1, arguments.size)
        assertEquals("apkFile", arguments.single().name)
        assertEquals(OptionType.PATH, arguments.single().type)
        assertTrue(arguments.single().required)
    }
}
