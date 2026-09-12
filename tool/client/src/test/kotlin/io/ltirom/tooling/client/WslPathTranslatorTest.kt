package io.ltirom.tooling.client

import kotlin.test.Test
import kotlin.test.assertEquals

class WslPathTranslatorTest {

    private val translator = WslPathTranslator(distroName = "Ubuntu")

    @Test
    fun `translates Windows backslash path to WSL path`() {
        val win = """C:\Users\Mohammad\Documents\file.img"""
        val expected = "/mnt/c/Users/Mohammad/Documents/file.img"
        assertEquals(expected, translator.toWslPath(win))
    }

    @Test
    fun `translates lowercase Windows drive letter to WSL path`() {
        val win = """d:\roms\system.img"""
        val expected = "/mnt/d/roms/system.img"
        assertEquals(expected, translator.toWslPath(win))
    }

    @Test
    fun `translates drive root to WSL mount point`() {
        assertEquals("/mnt/c", translator.toWslPath("C:\\"))
        assertEquals("/mnt/c", translator.toWslPath("c:/"))
    }

    @Test
    fun `translates UNC WSL paths to absolute Linux paths`() {
        val unc1 = """\\wsl.localhost\Ubuntu\home\lti\rom.img"""
        assertEquals("/home/lti/rom.img", translator.toWslPath(unc1))

        val unc2 = """\\wsl$\Ubuntu\tmp\build"""
        assertEquals("/tmp/build", translator.toWslPath(unc2))
    }

    @Test
    fun `translates WSL mount path to Windows path`() {
        val wsl = "/mnt/c/Users/Mohammad/Documents/file.img"
        val expected = """C:\Users\Mohammad\Documents\file.img"""
        assertEquals(expected, translator.toWindowsPath(wsl))
    }

    @Test
    fun `translates WSL root and home path to Windows UNC path`() {
        val wsl = "/home/lti/project"
        val expected = """\\wsl.localhost\Ubuntu\home\lti\project"""
        assertEquals(expected, translator.toWindowsPath(wsl))
    }

    @Test
    fun `preserves relative paths`() {
        assertEquals("subdir/file.txt", translator.toWslPath("""subdir\file.txt"""))
        assertEquals("""subdir\file.txt""", translator.toWindowsPath("subdir/file.txt"))
    }
}
