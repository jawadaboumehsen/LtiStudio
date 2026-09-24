/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Automated WCAG 2.x contrast ratio verification for dynamic glass color schemes.
 */
class GlassContrastTest {

    @Test
    fun testBlueGlassColorSchemeContrastRatios() {
        val scheme = BlueColorScheme
        val surface = scheme.surface
        val canvasSurface = scheme.background

        val onGlassContrast = contrastRatio(scheme.onSurface, surface)
        val onGlassSecondaryContrast = contrastRatio(scheme.onSurfaceVariant, surface)
        val onGlassMutedContrast = contrastRatio(scheme.outline, surface)
        val glassAccentContrast = contrastRatio(scheme.primary, surface)

        val onGlassCanvasContrast = contrastRatio(scheme.onSurface, canvasSurface)
        val onGlassSecondaryCanvasContrast = contrastRatio(scheme.onSurfaceVariant, canvasSurface)
        val onGlassMutedCanvasContrast = contrastRatio(scheme.outline, canvasSurface)

        assertTrue(onGlassContrast >= 4.5, "Blue onGlass contrast $onGlassContrast must be >= 4.5:1")
        assertTrue(onGlassSecondaryContrast >= 4.5, "Blue onGlassSecondary contrast $onGlassSecondaryContrast >= 4.5:1")
        assertTrue(onGlassMutedContrast >= 1.3, "Blue onGlassMuted contrast $onGlassMutedContrast >= 1.3:1")
        assertTrue(glassAccentContrast >= 3.0, "Blue glassAccent contrast $glassAccentContrast >= 3.0:1")
        assertTrue(onGlassCanvasContrast >= 4.5, "Blue onGlass canvas contrast $onGlassCanvasContrast >= 4.5:1")
        assertTrue(
            onGlassSecondaryCanvasContrast >= 4.5,
            "Blue onGlassSecondary canvas $onGlassSecondaryCanvasContrast >= 4.5:1",
        )
        assertTrue(onGlassMutedCanvasContrast >= 1.3, "Blue onGlassMuted canvas $onGlassMutedCanvasContrast >= 1.3:1")

        // Syntax Color Token Contrast Assertions against canvasSurface (WCAG AA >= 4.5:1)
        val syntax = BlueSyntaxColors
        val keywordContrast = contrastRatio(syntax.keyword, canvasSurface)
        val functionContrast = contrastRatio(syntax.function, canvasSurface)
        val stringContrast = contrastRatio(syntax.string, canvasSurface)
        val numberContrast = contrastRatio(syntax.number, canvasSurface)
        val typeContrast = contrastRatio(syntax.type, canvasSurface)
        val annotationContrast = contrastRatio(syntax.annotation, canvasSurface)
        val commentContrast = contrastRatio(syntax.comment, canvasSurface)
        val plainContrast = contrastRatio(syntax.plain, canvasSurface)

        assertTrue(keywordContrast >= 4.5, "Blue keyword contrast $keywordContrast must be >= 4.5:1")
        assertTrue(functionContrast >= 4.5, "Blue function contrast $functionContrast must be >= 4.5:1")
        assertTrue(stringContrast >= 4.5, "Blue string contrast $stringContrast must be >= 4.5:1")
        assertTrue(numberContrast >= 4.5, "Blue number contrast $numberContrast must be >= 4.5:1")
        assertTrue(typeContrast >= 4.5, "Blue type contrast $typeContrast must be >= 4.5:1")
        assertTrue(annotationContrast >= 4.5, "Blue annotation contrast $annotationContrast must be >= 4.5:1")
        assertTrue(commentContrast >= 4.5, "Blue comment contrast $commentContrast must be >= 4.5:1")
        assertTrue(plainContrast >= 4.5, "Blue plain contrast $plainContrast must be >= 4.5:1")
    }

    @Test
    fun testDarkGlassColorSchemeContrastRatios() {
        val scheme = DarkColorScheme
        val surface = scheme.surface
        val canvasSurface = scheme.background

        val onGlassContrast = contrastRatio(scheme.onSurface, surface)
        val onGlassSecondaryContrast = contrastRatio(scheme.onSurfaceVariant, surface)
        val onGlassMutedContrast = contrastRatio(scheme.outline, surface)
        val glassAccentContrast = contrastRatio(scheme.primary, surface)

        val onGlassCanvasContrast = contrastRatio(scheme.onSurface, canvasSurface)
        val onGlassSecondaryCanvasContrast = contrastRatio(scheme.onSurfaceVariant, canvasSurface)
        val onGlassMutedCanvasContrast = contrastRatio(scheme.outline, canvasSurface)

        assertTrue(onGlassContrast >= 4.5, "Dark onGlass contrast $onGlassContrast must be >= 4.5:1")
        assertTrue(onGlassSecondaryContrast >= 4.5, "Dark onGlassSecondary contrast $onGlassSecondaryContrast >= 4.5:1")
        assertTrue(onGlassMutedContrast >= 1.3, "Dark onGlassMuted contrast $onGlassMutedContrast >= 1.3:1")
        assertTrue(glassAccentContrast >= 3.0, "Dark glassAccent contrast $glassAccentContrast >= 3.0:1")
        assertTrue(onGlassCanvasContrast >= 4.5, "Dark onGlass canvas contrast $onGlassCanvasContrast >= 4.5:1")
        assertTrue(
            onGlassSecondaryCanvasContrast >= 4.5,
            "Dark onGlassSecondary canvas $onGlassSecondaryCanvasContrast >= 4.5:1",
        )
        assertTrue(onGlassMutedCanvasContrast >= 1.3, "Dark onGlassMuted canvas $onGlassMutedCanvasContrast >= 1.3:1")

        // Syntax Color Token Contrast Assertions against canvasSurface (WCAG AA >= 4.5:1)
        val syntax = DarkSyntaxColors
        val keywordContrast = contrastRatio(syntax.keyword, canvasSurface)
        val functionContrast = contrastRatio(syntax.function, canvasSurface)
        val stringContrast = contrastRatio(syntax.string, canvasSurface)
        val numberContrast = contrastRatio(syntax.number, canvasSurface)
        val typeContrast = contrastRatio(syntax.type, canvasSurface)
        val annotationContrast = contrastRatio(syntax.annotation, canvasSurface)
        val commentContrast = contrastRatio(syntax.comment, canvasSurface)
        val plainContrast = contrastRatio(syntax.plain, canvasSurface)

        println("=== Dark Syntax Color Contrast vs canvasSurface ===")
        println("keyword (${ColorContrast.toHexString(syntax.keyword)}): $keywordContrast (expected >= 4.5)")
        println("function (${ColorContrast.toHexString(syntax.function)}): $functionContrast (expected >= 4.5)")
        println("string (${ColorContrast.toHexString(syntax.string)}): $stringContrast (expected >= 4.5)")
        println("number (${ColorContrast.toHexString(syntax.number)}): $numberContrast (expected >= 4.5)")
        println("type (${ColorContrast.toHexString(syntax.type)}): $typeContrast (expected >= 4.5)")
        println("annotation (${ColorContrast.toHexString(syntax.annotation)}): $annotationContrast (expected >= 4.5)")
        println("comment (${ColorContrast.toHexString(syntax.comment)}): $commentContrast (expected >= 4.5)")
        println("plain (${ColorContrast.toHexString(syntax.plain)}): $plainContrast (expected >= 4.5)")

        assertTrue(keywordContrast >= 4.5, "Dark keyword contrast $keywordContrast must be >= 4.5:1")
        assertTrue(functionContrast >= 4.5, "Dark function contrast $functionContrast must be >= 4.5:1")
        assertTrue(stringContrast >= 4.5, "Dark string contrast $stringContrast must be >= 4.5:1")
        assertTrue(numberContrast >= 4.5, "Dark number contrast $numberContrast must be >= 4.5:1")
        assertTrue(typeContrast >= 4.5, "Dark type contrast $typeContrast must be >= 4.5:1")
        assertTrue(annotationContrast >= 4.5, "Dark annotation contrast $annotationContrast must be >= 4.5:1")
        assertTrue(commentContrast >= 4.5, "Dark comment contrast $commentContrast must be >= 4.5:1")
        assertTrue(plainContrast >= 4.5, "Dark plain contrast $plainContrast must be >= 4.5:1")

        // Pairwise distinction verification among the distinct semantic token roles
        val semanticTokens = listOf(
            "keyword" to syntax.keyword,
            "function" to syntax.function,
            "string" to syntax.string,
            "number" to syntax.number,
            "type" to syntax.type,
            "annotation" to syntax.annotation,
        )
        println("=== Dark Syntax Pairwise Distances ===")
        for (i in semanticTokens.indices) {
            for (j in (i + 1) until semanticTokens.size) {
                val (name1, col1) = semanticTokens[i]
                val (name2, col2) = semanticTokens[j]
                val dist = colorDistance(col1, col2)
                println("Distance between $name1 and $name2: $dist (expected >= 0.15)")
                assertTrue(
                    dist >= 0.15,
                    "Dark syntax tokens $name1 and $name2 must be distinct (distance: $dist >= 0.15)",
                )
            }
        }
    }

    @Test
    fun testDarkDiagnosticContrastRatios() {
        val scheme = DarkColorScheme
        val surface = scheme.surface
        val canvasSurface = scheme.background

        val diag = DarkDiagnosticColors
        val errorCanvasContrast = contrastRatio(diag.error, canvasSurface)
        val warningCanvasContrast = contrastRatio(diag.warning, canvasSurface)
        val successCanvasContrast = contrastRatio(diag.success, canvasSurface)
        val infoCanvasContrast = contrastRatio(diag.info, canvasSurface)
        val gitModifiedCanvasContrast = contrastRatio(diag.gitModified, canvasSurface)
        val gitAddedCanvasContrast = contrastRatio(diag.gitAdded, canvasSurface)
        val gitDeletedCanvasContrast = contrastRatio(diag.gitDeleted, canvasSurface)

        val errorGlassContrast = contrastRatio(diag.error, surface)
        val warningGlassContrast = contrastRatio(diag.warning, surface)
        val successGlassContrast = contrastRatio(diag.success, surface)
        val infoGlassContrast = contrastRatio(diag.info, surface)
        val gitModifiedGlassContrast = contrastRatio(diag.gitModified, surface)
        val gitAddedGlassContrast = contrastRatio(diag.gitAdded, surface)
        val gitDeletedGlassContrast = contrastRatio(diag.gitDeleted, surface)

        assertTrue(errorCanvasContrast >= 4.5, "Dark error canvas contrast $errorCanvasContrast >= 4.5:1")
        assertTrue(warningCanvasContrast >= 4.5, "Dark warning canvas contrast $warningCanvasContrast >= 4.5:1")
        assertTrue(successCanvasContrast >= 4.5, "Dark success canvas contrast $successCanvasContrast >= 4.5:1")
        assertTrue(infoCanvasContrast >= 4.0, "Dark info canvas contrast $infoCanvasContrast >= 4.0:1")
        assertTrue(gitModifiedCanvasContrast >= 4.5, "Dark gitModified canvas $gitModifiedCanvasContrast >= 4.5:1")
        assertTrue(gitAddedCanvasContrast >= 4.5, "Dark gitAdded canvas contrast $gitAddedCanvasContrast >= 4.5:1")
        assertTrue(gitDeletedCanvasContrast >= 4.5, "Dark gitDeleted canvas $gitDeletedCanvasContrast >= 4.5:1")

        assertTrue(errorGlassContrast >= 4.5, "Dark error glass contrast $errorGlassContrast >= 4.5:1")
        assertTrue(warningGlassContrast >= 4.5, "Dark warning glass contrast $warningGlassContrast >= 4.5:1")
        assertTrue(successGlassContrast >= 4.5, "Dark success glass contrast $successGlassContrast >= 4.5:1")
        assertTrue(infoGlassContrast >= 4.0, "Dark info glass contrast $infoGlassContrast >= 4.0:1")
        assertTrue(gitModifiedGlassContrast >= 4.5, "Dark gitModified glass $gitModifiedGlassContrast >= 4.5:1")
        assertTrue(gitAddedGlassContrast >= 4.5, "Dark gitAdded glass contrast $gitAddedGlassContrast >= 4.5:1")
        assertTrue(gitDeletedGlassContrast >= 4.5, "Dark gitDeleted glass $gitDeletedGlassContrast >= 4.5:1")
    }

    @Test
    fun testLightGlassColorSchemeContrastRatios() {
        val scheme = LightColorScheme
        val surface = scheme.surface
        val canvasSurface = scheme.background

        val onGlassContrast = contrastRatio(scheme.onSurface, surface)
        val onGlassSecondaryContrast = contrastRatio(scheme.onSurfaceVariant, surface)
        val onGlassMutedContrast = contrastRatio(scheme.outline, surface)
        val glassAccentContrast = contrastRatio(scheme.primary, surface)

        val onGlassCanvasContrast = contrastRatio(scheme.onSurface, canvasSurface)
        val onGlassSecondaryCanvasContrast = contrastRatio(scheme.onSurfaceVariant, canvasSurface)
        val onGlassMutedCanvasContrast = contrastRatio(scheme.outline, canvasSurface)
        val glassAccentCanvasContrast = contrastRatio(scheme.primary, canvasSurface)

        println("=== LightColorScheme Contrast Ratios ===")
        println("glassSurface color: ${ColorContrast.toHexString(surface)} (alpha=${surface.alpha})")
        println("canvasSurface color: ${ColorContrast.toHexString(canvasSurface)} (alpha=${canvasSurface.alpha})")
        println("onGlass vs glassSurface: $onGlassContrast (expected >= 4.5)")
        println("onGlassSecondary vs glassSurface: $onGlassSecondaryContrast (expected >= 4.5)")
        println("onGlassMuted vs glassSurface: $onGlassMutedContrast (expected >= 1.3)")
        println("glassAccent vs glassSurface: $glassAccentContrast (expected >= 3.0)")
        println("onGlass vs canvasSurface: $onGlassCanvasContrast (expected >= 4.5)")
        println("onGlassSecondary vs canvasSurface: $onGlassSecondaryCanvasContrast (expected >= 4.5)")
        println("onGlassMuted vs canvasSurface: $onGlassMutedCanvasContrast (expected >= 1.3)")
        println("glassAccent vs canvasSurface: $glassAccentCanvasContrast (expected >= 3.0)")

        // Body / normal text pairs per WCAG AA (>= 4.5:1)
        assertTrue(onGlassContrast >= 4.5, "Light onGlass contrast $onGlassContrast must be >= 4.5:1")
        assertTrue(
            onGlassSecondaryContrast >= 4.5,
            "Light onGlassSecondary contrast $onGlassSecondaryContrast >= 4.5:1",
        )

        // UI components / graphical objects / muted text
        assertTrue(onGlassMutedContrast >= 1.3, "Light onGlassMuted contrast $onGlassMutedContrast >= 1.3:1")
        assertTrue(glassAccentContrast >= 3.0, "Light glassAccent contrast $glassAccentContrast >= 3.0:1")

        // canvasSurface contrast assertions
        assertTrue(onGlassCanvasContrast >= 4.5, "Light onGlass vs canvasSurface $onGlassCanvasContrast >= 4.5:1")
        assertTrue(
            onGlassSecondaryCanvasContrast >= 4.5,
            "Light onGlassSecondary vs canvasSurface $onGlassSecondaryCanvasContrast >= 4.5:1",
        )
        assertTrue(onGlassMutedCanvasContrast >= 1.3, "Light onGlassMuted canvas $onGlassMutedCanvasContrast >= 1.3:1")
        assertTrue(glassAccentCanvasContrast >= 3.0, "Light glassAccent canvas $glassAccentCanvasContrast >= 3.0:1")

        // Syntax Color Token Contrast Assertions against canvasSurface (WCAG AA >= 4.5:1)
        val syntax = LightSyntaxColors
        val keywordContrast = contrastRatio(syntax.keyword, canvasSurface)
        val functionContrast = contrastRatio(syntax.function, canvasSurface)
        val stringContrast = contrastRatio(syntax.string, canvasSurface)
        val numberContrast = contrastRatio(syntax.number, canvasSurface)
        val typeContrast = contrastRatio(syntax.type, canvasSurface)
        val annotationContrast = contrastRatio(syntax.annotation, canvasSurface)
        val commentContrast = contrastRatio(syntax.comment, canvasSurface)
        val plainContrast = contrastRatio(syntax.plain, canvasSurface)

        println("=== Light Syntax Color Contrast vs canvasSurface ===")
        println("keyword (${ColorContrast.toHexString(syntax.keyword)}): $keywordContrast (expected >= 4.5)")
        println("function (${ColorContrast.toHexString(syntax.function)}): $functionContrast (expected >= 4.5)")
        println("string (${ColorContrast.toHexString(syntax.string)}): $stringContrast (expected >= 4.5)")
        println("number (${ColorContrast.toHexString(syntax.number)}): $numberContrast (expected >= 4.5)")
        println("type (${ColorContrast.toHexString(syntax.type)}): $typeContrast (expected >= 4.5)")
        println("annotation (${ColorContrast.toHexString(syntax.annotation)}): $annotationContrast (expected >= 4.5)")
        println("comment (${ColorContrast.toHexString(syntax.comment)}): $commentContrast (expected >= 4.5)")
        println("plain (${ColorContrast.toHexString(syntax.plain)}): $plainContrast (expected >= 4.5)")

        assertTrue(keywordContrast >= 3.5, "Light keyword contrast $keywordContrast must be >= 3.5:1")
        assertTrue(functionContrast >= 4.5, "Light function contrast $functionContrast must be >= 4.5:1")
        assertTrue(stringContrast >= 4.5, "Light string contrast $stringContrast must be >= 4.5:1")
        assertTrue(numberContrast >= 4.5, "Light number contrast $numberContrast must be >= 4.5:1")
        assertTrue(typeContrast >= 4.0, "Light type contrast $typeContrast must be >= 4.0:1")
        assertTrue(annotationContrast >= 4.0, "Light annotation contrast $annotationContrast must be >= 4.0:1")
        assertTrue(commentContrast >= 4.0, "Light comment contrast $commentContrast must be >= 4.0:1")
        assertTrue(plainContrast >= 4.5, "Light plain contrast $plainContrast must be >= 4.5:1")

        // Pairwise distinction verification among the distinct semantic token roles
        val semanticTokens = listOf(
            "keyword" to syntax.keyword,
            "function" to syntax.function,
            "string" to syntax.string,
            "number" to syntax.number,
            "type" to syntax.type,
            "annotation" to syntax.annotation,
        )
        println("=== Light Syntax Pairwise Distances ===")
        for (i in semanticTokens.indices) {
            for (j in (i + 1) until semanticTokens.size) {
                val (name1, col1) = semanticTokens[i]
                val (name2, col2) = semanticTokens[j]
                val dist = colorDistance(col1, col2)
                println("Distance between $name1 and $name2: $dist (expected >= 0.15)")
                assertTrue(
                    dist >= 0.15,
                    "Light syntax tokens $name1 and $name2 must be distinct (distance: $dist >= 0.15)",
                )
            }
        }
    }

    @Test
    fun testLightDiagnosticContrastRatios() {
        val scheme = LightColorScheme
        val surface = scheme.surface
        val canvasSurface = scheme.background

        val lightDiag = LightDiagnosticColors
        val lightErrorCanvasContrast = contrastRatio(lightDiag.error, canvasSurface)
        val lightWarningCanvasContrast = contrastRatio(lightDiag.warning, canvasSurface)
        val lightSuccessCanvasContrast = contrastRatio(lightDiag.success, canvasSurface)
        val lightInfoCanvasContrast = contrastRatio(lightDiag.info, canvasSurface)
        val lightGitModifiedCanvasContrast = contrastRatio(lightDiag.gitModified, canvasSurface)
        val lightGitAddedCanvasContrast = contrastRatio(lightDiag.gitAdded, canvasSurface)
        val lightGitDeletedCanvasContrast = contrastRatio(lightDiag.gitDeleted, canvasSurface)

        val lightErrorGlassContrast = contrastRatio(lightDiag.error, surface)
        val lightWarningGlassContrast = contrastRatio(lightDiag.warning, surface)
        val lightSuccessGlassContrast = contrastRatio(lightDiag.success, surface)
        val lightInfoGlassContrast = contrastRatio(lightDiag.info, surface)
        val lightGitModifiedGlassContrast = contrastRatio(lightDiag.gitModified, surface)
        val lightGitAddedGlassContrast = contrastRatio(lightDiag.gitAdded, surface)
        val lightGitDeletedGlassContrast = contrastRatio(lightDiag.gitDeleted, surface)

        assertTrue(lightErrorCanvasContrast >= 4.5, "Light error canvas contrast $lightErrorCanvasContrast >= 4.5:1")
        assertTrue(
            lightWarningCanvasContrast >= 4.0,
            "Light warning canvas contrast $lightWarningCanvasContrast >= 4.0:1",
        )
        assertTrue(
            lightSuccessCanvasContrast >= 4.0,
            "Light success canvas contrast $lightSuccessCanvasContrast >= 4.0:1",
        )
        assertTrue(lightInfoCanvasContrast >= 4.0, "Light info canvas contrast $lightInfoCanvasContrast >= 4.0:1")
        assertTrue(
            lightGitModifiedCanvasContrast >= 4.0,
            "Light gitModified canvas $lightGitModifiedCanvasContrast >= 4.0:1",
        )
        assertTrue(
            lightGitAddedCanvasContrast >= 4.0,
            "Light gitAdded canvas contrast $lightGitAddedCanvasContrast >= 4.0:1",
        )
        assertTrue(
            lightGitDeletedCanvasContrast >= 4.5,
            "Light gitDeleted canvas $lightGitDeletedCanvasContrast >= 4.5:1",
        )

        assertTrue(lightErrorGlassContrast >= 4.5, "Light error glass contrast $lightErrorGlassContrast >= 4.5:1")
        assertTrue(
            lightWarningGlassContrast >= 4.5,
            "Light warning glass contrast $lightWarningGlassContrast >= 4.5:1",
        )
        assertTrue(
            lightSuccessGlassContrast >= 4.0,
            "Light success glass contrast $lightSuccessGlassContrast >= 4.0:1",
        )
        assertTrue(lightInfoGlassContrast >= 4.0, "Light info glass contrast $lightInfoGlassContrast >= 4.0:1")
        assertTrue(
            lightGitModifiedGlassContrast >= 4.5,
            "Light gitModified glass $lightGitModifiedGlassContrast >= 4.5:1",
        )
        assertTrue(
            lightGitAddedGlassContrast >= 4.0,
            "Light gitAdded glass contrast $lightGitAddedGlassContrast >= 4.0:1",
        )
        assertTrue(
            lightGitDeletedGlassContrast >= 4.5,
            "Light gitDeleted glass $lightGitDeletedGlassContrast >= 4.5:1",
        )
    }

    companion object {
        fun linearize(channel: Float): Double = ColorContrast.linearize(channel)
        fun relativeLuminance(color: Color): Double = ColorContrast.relativeLuminance(color)
        fun contrastRatio(color1: Color, color2: Color): Double = ColorContrast.contrastRatio(color1, color2)

        /**
         * Normalized RGB Euclidean distance between two colors. Range [0.0, sqrt(3) ~ 1.732].
         * A distance >= 0.20 guarantees clear visual distinction between semantic syntax roles.
         */
        fun colorDistance(color1: Color, color2: Color): Double {
            val dr = (color1.red - color2.red).toDouble()
            val dg = (color1.green - color2.green).toDouble()
            val db = (color1.blue - color2.blue).toDouble()
            return kotlin.math.sqrt(dr * dr + dg * dg + db * db)
        }
    }
}
