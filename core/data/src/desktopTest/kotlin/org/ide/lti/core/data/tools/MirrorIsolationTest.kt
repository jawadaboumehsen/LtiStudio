/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.tools

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Runs the generated mirror script against real git repositories (needs `sh` and `git`). */
class MirrorIsolationTest {
    private lateinit var root: File

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("mirror-isolation").toFile()
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun sh(script: String, dir: File = root): Pair<Int, String> {
        // From a file: a script with double quotes does not survive Windows command-line quoting as an argument.
        val file = File.createTempFile("script", ".sh", root).also { it.writeText(script.replace("\r\n", "\n")) }
        val builder =
            ProcessBuilder("sh", file.absolutePath.replace('\\', '/')).directory(dir).redirectErrorStream(true)
        // Local repositories stand in for remote ones; recent git refuses file:// submodules unless told otherwise.
        builder.environment().putAll(
            mapOf(
                "GIT_CONFIG_COUNT" to "1",
                "GIT_CONFIG_KEY_0" to "protocol.file.allow",
                "GIT_CONFIG_VALUE_0" to "always",
            ),
        )
        val process = builder.start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(120, TimeUnit.SECONDS)
        return process.exitValue() to output
    }

    private fun shellAndGitAvailable(): Boolean = runCatching { sh("git --version && sh -c true").first == 0 }
        .getOrDefault(false)

    private fun posix(file: File): String = file.absolutePath.replace('\\', '/')

    /** An upstream with a tag the fork never had, and a fork that has a tag of its own. */
    private fun makeRepos(): Triple<String, String, String> {
        val git = "git -c user.name=t -c user.email=t@t -c commit.gpgsign=false"
        val upstream = File(root, "upstream").also { it.mkdirs() }
        val up = posix(upstream)
        check(
            sh(
                "cd '$up' && git init -q . && echo a > f && git add f && $git commit -q -m a && git tag v1 && " +
                    "cd '${posix(root)}' && git clone -q '$up' fork && " +
                    "cd '$up' && echo c > g && git add g && $git commit -q -m upstream-only && git tag only-upstream",
            ).first == 0,
        )
        val fork = posix(File(root, "fork"))
        check(
            sh(
                "cd '$fork' && git tag -d v1 && echo b > h && git add h && " +
                    "$git commit -q -m fork && git tag fork-tag",
            ).first == 0,
        )
        val onlyUpstreamCommit = sh("cd '$up' && git rev-parse only-upstream").second.trim()
        return Triple(up, fork, onlyUpstreamCommit)
    }

    @Test
    fun `a fork's mirror cannot resolve what exists only upstream`() {
        org.junit.Assume.assumeTrue("needs sh and git", shellAndGitAvailable())
        val (upstream, fork, upstreamOnlyCommit) = makeRepos()
        val base = posix(File(root, "store"))

        assertNotEquals(
            KotlinStoreLayout.mirrorPath(base, "erofs-utils", upstream),
            KotlinStoreLayout.mirrorPath(base, "erofs-utils", fork),
            "each repository gets its own mirror",
        )
        for (url in listOf(upstream, fork)) {
            val result = sh(KotlinStoreLayout.buildMirrorScript(base, "erofs-utils", url, lenientFetch = false))
            assertEquals(0, result.first, result.second)
        }
        val upstreamMirror = KotlinStoreLayout.mirrorPath(base, "erofs-utils", upstream)
        val forkMirror = KotlinStoreLayout.mirrorPath(base, "erofs-utils", fork)

        assertEquals(0, sh("git --git-dir='$upstreamMirror' rev-parse 'refs/tags/only-upstream^{commit}'").first)
        assertTrue(sh("git --git-dir='$forkMirror' rev-parse 'refs/tags/only-upstream^{commit}'").first != 0, "tag")
        assertTrue(sh("git --git-dir='$forkMirror' rev-parse 'refs/tags/v1^{commit}'").first != 0, "tag v1")
        assertTrue(sh("git --git-dir='$forkMirror' cat-file -e $upstreamOnlyCommit^{commit}").first != 0, "commit")
        assertEquals(0, sh("git --git-dir='$forkMirror' rev-parse 'refs/tags/fork-tag^{commit}'").first)
    }

    @Test
    fun `the submodule cache is per repository as well`() {
        val base = "/home/u/LtiRomTools"
        val a = KotlinStoreLayout.submoduleCachePath(base, "android-tools", "https://github.com/a/tools.git")
        val b = KotlinStoreLayout.submoduleCachePath(base, "android-tools", "https://github.com/b/tools.git")

        assertNotEquals(a, b)
        assertTrue(a.endsWith("-modules") && a.startsWith("$base/src/android-tools-"), a)
        assertEquals(a, KotlinStoreLayout.submoduleCachePath(base, "android-tools", " https://github.com/a/tools.git "))
    }

    @Test
    fun `the git prep script reads and writes only its own repository's mirror and cache`() {
        val script =
            KotlinStoreLayout.buildGitPrepScript("/h/T", "g", "https://x/y.git", "a".repeat(40), "/h/T/work/o/src")

        assertTrue(KotlinStoreLayout.mirrorPath("/h/T", "g", "https://x/y.git") in script)
        assertTrue(KotlinStoreLayout.submoduleCachePath("/h/T", "g", "https://x/y.git") in script)
        assertTrue("/h/T/src/g'" !in script && "/h/T/src/g-modules" !in script, "no shared per-group path is left")
    }

    @Test
    fun `a reviewed submodule that is not in the checkout fails the preparation`() {
        org.junit.Assume.assumeTrue("needs sh and git", shellAndGitAvailable())
        val git = "git -c user.name=t -c user.email=t@t -c commit.gpgsign=false"
        val r = posix(root)
        check(
            sh("cd '$r' && git init -q plain && cd plain && echo a > a && git add a && $git commit -q -m a").first == 0,
        )
        val commit = sh("cd '$r/plain' && git rev-parse HEAD").second.trim()

        val result =
            sh(
                KotlinStoreLayout.buildGitPrepScript(
                    "$r/store",
                    "g",
                    "$r/plain",
                    commit,
                    "$r/work",
                    mapOf("vendor/sub" to "a".repeat(40)),
                ),
            )

        assertEquals(5, result.first, result.second)
        assertTrue("vendor/sub was not checked out" in result.second, result.second)
    }

    @Test
    fun `a cached submodule follows the URL that the new revision's gitmodules names`() {
        org.junit.Assume.assumeTrue("needs sh and git", shellAndGitAvailable())
        val git = "git -c user.name=t -c user.email=t@t -c commit.gpgsign=false"
        val r = posix(root)
        check(
            sh(
                "cd '$r' && git init -q sub1 && cd sub1 && echo s > s && git add s && " +
                    "$git commit -q -m s && cd .. && " +
                    "git clone -q sub1 sub2 && git init -q up && cd up && echo a > a && git add a && " +
                    "$git commit -q -m a && $git submodule add -q '$r/sub1' vendor/sub && $git commit -q -m c1",
            ).first == 0,
        )
        val c1 = sh("cd '$r/up' && git rev-parse HEAD").second.trim()
        val submoduleCommit = sh("cd '$r/sub1' && git rev-parse HEAD").second.trim()
        // The next revision points the same submodule (same commit) at another URL.
        check(
            sh(
                "cd '$r/up' && git config -f .gitmodules submodule.vendor/sub.url '$r/sub2' && " +
                    "git add .gitmodules && $git commit -q -m c2",
            ).first == 0,
        )
        val c2 = sh("cd '$r/up' && git rev-parse HEAD").second.trim()
        val base = "$r/store"

        fun prep(commit: String, work: String) = sh(
            KotlinStoreLayout.buildGitPrepScript(
                base,
                "g",
                "$r/up",
                commit,
                "$r/$work",
                mapOf("vendor/sub" to submoduleCommit),
            ),
        )

        val first = prep(c1, "work1")
        assertEquals(0, first.first, first.second)
        assertEquals("$r/sub1", sh("git -C '$r/work1/vendor/sub' remote get-url origin").second.trim())

        val second = prep(c2, "work2") // reuses the cached clone made from sub1
        assertEquals(0, second.first, second.second)
        assertEquals(
            "$r/sub2",
            sh("git -C '$r/work2/vendor/sub' remote get-url origin").second.trim(),
            "the cached submodule must be repointed, not keep fetching from the old URL",
        )
    }
}
