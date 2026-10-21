package org.ide.lti.sdk.patchmod.hooks

import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.JdkClassFileProvider
import com.android.tools.r8.OutputMode
import java.io.File
import java.nio.file.Files
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HookCompilerTest {
    private val dir = Files.createTempDirectory("hooks").toFile()

    private fun write(path: String, text: String) = dir.resolve(path).apply {
        parentFile.mkdirs()
        writeText(text.trimIndent())
    }

    private fun compile(roots: List<HookCompiler.Root>, firmware: List<File> = emptyList()) =
        HookCompiler.compile(roots, firmware, work = dir.resolve("work"))

    /** A firmware jar holding `com.oem.Secret` as dex only, like a real firmware's framework.jar. */
    private fun firmwareJar(): File {
        val source = write(
            "oem/com/oem/Secret.java",
            "package com.oem; public class Secret { public static int value() { return 7; } }",
        )
        val classes = dir.resolve("oem-classes").apply { mkdirs() }
        ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", classes.path, "--release", "17", source.path)
        val jar = dir.resolve("framework.jar")
        D8.run(
            D8Command.builder()
                .addProgramFiles(classes.walkTopDown().filter { it.extension == "class" }.map { it.toPath() }.toList())
                .addLibraryResourceProvider(JdkClassFileProvider.fromSystemJdk())
                .setMinApiLevel(HookCompiler.DEFAULT_MIN_API)
                .setOutput(jar.toPath(), OutputMode.DexIndexed)
                .build(),
        )
        return jar
    }

    @Test
    fun hooksCompileAgainstTheFirmwaresOwnClassesIntoSmaliPerRoot() {
        write(
            "hooks/a/io/lti/Hooks.java",
            """
            package io.lti;
            public final class Hooks {
                public static int answer() {
                    Runnable r = () -> {};
                    r.run();
                    return com.oem.Secret.value() + Other.one();
                }
            }
            """,
        )
        write(
            "hooks/b/io/lti/Other.java",
            "package io.lti; public final class Other { public static int one() { return 1; } }",
        )
        val outA = dir.resolve("files/a/smali_classes6")
        val outB = dir.resolve("files/b/smali")
        // A file generated before is replaced; a hand-written one is kept.
        write("files/a/smali_classes6/io/lti/Stale.smali", HookCompiler.GENERATED_HEADER + "\n.class LStale;")
        write("files/a/smali_classes6/io/lti/Mine.smali", ".class LMine;")

        val problems = compile(
            listOf(HookCompiler.Root(dir.resolve("hooks/a"), outA), HookCompiler.Root(dir.resolve("hooks/b"), outB)),
            firmware = listOf(firmwareJar()),
        )

        assertEquals(emptyList(), problems)
        val hooks = outA.resolve("io/lti/Hooks.smali").readText()
        assertTrue(hooks.startsWith(HookCompiler.GENERATED_HEADER + "\n.class public final Lio/lti/Hooks;"), hooks)
        assertTrue("Lcom/oem/Secret;->value()I" in hooks && "Lio/lti/Other;->one()I" in hooks, hooks)
        // Each root gets only its own classes (and its lambdas); the other root's class is referenced, not copied.
        val inA = outA.walkTopDown().filter { it.isFile }.map { it.name }.toSet()
        assertTrue("Other.smali" !in inA && "Mine.smali" in inA && "Stale.smali" !in inA, inA.toString())
        assertTrue(inA.any { it.startsWith("Hooks$") }, inA.toString())
        assertTrue(outB.resolve("io/lti/Other.smali").isFile)
    }

    @Test
    fun aClassInAPlatformPackageOrACompileErrorWritesNothing() {
        write("hooks/android/os/Evil.java", "package android.os; public final class Evil {}")
        val out = dir.resolve("out")
        val shadow = compile(listOf(HookCompiler.Root(dir.resolve("hooks"), out)))
        assertTrue(shadow.single().contains("platform package"), shadow.toString())
        assertTrue(!out.exists())

        write("broken/io/lti/B.java", "package io.lti; public final class B { int x = missing(); }")
        val broken = compile(listOf(HookCompiler.Root(dir.resolve("broken"), out)))
        assertTrue(broken.single().startsWith("javac failed") && "missing" in broken.single(), broken.toString())
    }
}
