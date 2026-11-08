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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Real smali: PQ84P01 com.android.location.provider.jar, `apktool d -r --no-debug-info`. */
class SmaliCompareTest {
    private val service = "Lcom/android/location/provider/LocationProviderBase\$Service;"
    private val base = "Lcom/android/location/provider/LocationProviderBase;"

    private val constructor = """
        .method constructor <init>(Lcom/android/location/provider/LocationProviderBase;)V
            .locals 0
            .annotation system Ldalvik/annotation/MethodParameters;
                accessFlags = {
                    0x1010
                }
                names = {
                    null
                }
            .end annotation

            iput-object p1, p0, $service->this${'$'}0:$base

            invoke-direct {p0}, Landroid/location/provider/ILocationProvider${'$'}Stub;-><init>()V

            return-void
        .end method
    """.trimIndent()

    private val flush = """
        .method private onFlushComplete()V
            .locals 3

            iget-object v0, p0, $service->this${'$'}0:$base

            iget-object v0, v0, $base->mManager:Landroid/location/provider/ILocationProviderManager;

            if-eqz v0, :cond_0

            :try_start_0
            invoke-interface {v0}, Landroid/location/provider/ILocationProviderManager;->onFlushComplete()V
            :try_end_0
            .catch Landroid/os/RemoteException; {:try_start_0 .. :try_end_0} :catch_1
            .catch Ljava/lang/RuntimeException; {:try_start_0 .. :try_end_0} :catch_0

            goto :goto_0

            :catch_0
            move-exception v1

            iget-object v2, p0, $service->this${'$'}0:$base

            iget-object v2, v2, $base->mTag:Ljava/lang/String;

            invoke-static {v2, v1}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/Throwable;)I

            goto :goto_0

            :catch_1
            move-exception v1

            invoke-virtual {v1}, Landroid/os/RemoteException;->rethrowFromSystemServer()Ljava/lang/RuntimeException;

            move-result-object v2

            throw v2

            :cond_0
            :goto_0
            return-void
        .end method
    """.trimIndent()

    /** The same method as another compile numbers it: catch labels swapped, v1 and v2 exchanged, one more local. */
    private val flushRenumbered = Regex(""":catch_([01])\b|\bv([12])\b""").replace(flush) { m ->
        when {
            m.value == ":catch_0" -> ":catch_1"
            m.value == ":catch_1" -> ":catch_0"
            m.value == "v1" -> "v2"
            else -> "v1"
        }
    }.replace(".locals 3", ".locals 4")

    private fun cls(vararg methods: String) = ".class public L$service\n.super Ljava/lang/Object;\n\n" +
        methods.joinToString("\n\n") + "\n"

    @Test
    fun `instructions are counted without directives, labels or annotation values`() {
        assertEquals(3, SmaliCompare.instructionCount(constructor))
        assertEquals(15, SmaliCompare.instructionCount(flush))
    }

    @Test
    fun `a consistent renumbering of labels and registers is reported apart`() {
        val diff = SmaliCompare.compare(
            mapOf("smali/com/a/S.smali" to cls(constructor, flush)),
            mapOf("smali/com/a/S.smali" to cls(constructor, flushRenumbered)),
        )
        val m = diff.methodDifferences.single()
        assertEquals("onFlushComplete()V", m.methodName)
        assertTrue(m.renumberedOnly)
        assertEquals(15, m.instructionsA)
        assertEquals(15, m.instructionsB)
        assertTrue(diff.realChanges.isEmpty())
    }

    @Test
    fun `a changed instruction is a real change`() {
        val changed = flush.replace("if-eqz v0, :cond_0", "if-nez v0, :cond_0")
        val m = SmaliCompare.compare(
            mapOf("smali/com/a/S.smali" to cls(flush)),
            mapOf("smali/com/a/S.smali" to cls(changed)),
        ).methodDifferences.single()
        assertFalse(m.renumberedOnly)
        assertEquals(listOf("-     if-eqz v0, :cond_0", "+     if-nez v0, :cond_0"), m.lines)
    }

    @Test
    fun `a field type or a string is never taken for numbering`() {
        val type = flush.replace("->mTag:Ljava/lang/String;", "->mTag:Ljava/lang/Object;")
        assertFalse(SmaliCompare.canonical(flush) == SmaliCompare.canonical(type))
        val a = "const-string v0, \"v1\"\nreturn-object v0"
        val b = "const-string v0, \"v2\"\nreturn-object v0"
        assertFalse(SmaliCompare.canonical(a) == SmaliCompare.canonical(b))
        val pkgA = "sget-object v0, Lcom/v1/A;->X:Lcom/v1/A;"
        val pkgB = "sget-object v0, Lcom/v2/A;->X:Lcom/v2/A;"
        assertFalse(SmaliCompare.canonical(pkgA) == SmaliCompare.canonical(pkgB))
    }

    @Test
    fun `a class moved to another dex is no change, an added method has only its new count`() {
        val withMethod = cls(constructor, flush)
        val diff = SmaliCompare.compare(
            mapOf("smali/com/a/S.smali" to cls(constructor)),
            mapOf("smali_classes2/com/a/S.smali" to withMethod),
        )
        assertTrue(diff.classDifferences.isEmpty())
        val added = diff.methodDifferences.single()
        assertEquals(MethodChangeKind.Added, added.changeKind)
        assertNull(added.instructionsA)
        assertEquals(15, added.instructionsB)
        assertEquals("classes2.dex", SmaliCompare.dexOf("smali_classes2/com/a/S.smali"))
        assertEquals("classes.dex", SmaliCompare.dexOf("smali/com/a/S.smali"))
    }

    @Test
    fun `superblocks of different filesystems line up by meaning`() {
        val erofs = Superblock(
            FsType.Erofs,
            4_096_000,
            listOf("Filesystem blocksize" to "4096", "Filesystem UUID" to "a"),
        )
        val ext4 = Superblock(FsType.Ext4, 4_096_000, listOf("Block size" to "4096", "Filesystem UUID" to "b"))
        val rows = Superblock.compare(erofs, ext4).associateBy { it.field }
        assertTrue(rows.getValue("Filesystem").differs)
        assertFalse(rows.getValue("Image size").differs)
        assertFalse(rows.getValue("Block size").differs)
        assertTrue(rows.getValue("UUID").differs)
    }

    @Test
    fun `files of the same size and time are hashed unless times are trusted`() {
        val a = mapOf("etc/x" to TreeCompare.Entry('f', 10, "1.0", ""))
        val b = mapOf("etc/x" to TreeCompare.Entry('f', 10, "1.0", ""))
        assertTrue(TreeCompare.hashCandidates(a, b, trustTimes = true).isEmpty())
        assertEquals(listOf("etc/x"), TreeCompare.hashCandidates(a, b, trustTimes = false))
        val none = TreeCompare.Sidecars("vendor", "", "")
        val diff = TreeCompare.compare("vendor", a, b, mapOf("etc/x" to "1"), mapOf("etc/x" to "2"), none, none)
        assertEquals(TreeChangeKind.CONTENT, diff.changes.single().kind)
    }
}
