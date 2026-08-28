// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.dependencyGuard) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.ktlint) apply false
    // Multiplatform plugins
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.wire) apply false
    alias(libs.plugins.ktrofit) apply false
}

object DynamicVersion {
    fun setDynamicVersion(file: File, version: String) {
        val cleanedVersion = version.split('+')[0]
        file.writeText(cleanedVersion)
    }
}

tasks.register("versionFile") {
    val file = File(projectDir, "version.txt")

    DynamicVersion.setDynamicVersion(file, project.version.toString())
}
allprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
        compilerOptions {
            freeCompilerArgs.add("-Xcontext-receivers")
            freeCompilerArgs.add("-Xskip-metadata-version-check")
        }
    }
}

/**
 * Fails the build when UI code hardcodes colors/dimensions/alpha/motion values that bypass the
 * centralized design-system tokens (core/designsystem theme/Color.kt, Dimens.kt, Alpha.kt, Motion.kt),
 * when a feature/app module hand-rolls a glass component directly from backdrop effect
 * primitives instead of composing an existing core:designsystem/core:ui component, or when a
 * token constant is defined but never referenced anywhere.
 */
tasks.register("verifyDesignSystemGuardrails") {
    group = "verification"
    description = "Enforces design-system token usage and component-location boundaries."
    outputs.upToDateWhen { false }
    val root = rootDir

    doLast {
        val violations = mutableListOf<String>()

        fun ktFiles(dir: File): List<File> =
            if (dir.exists()) {
                dir.walkTopDown()
                    .onEnter { it.name != "build" }
                    .filter { it.isFile && it.extension == "kt" }
                    .toList()
            } else {
                emptyList()
            }

        fun isCommentOrImport(line: String): Boolean {
            val t = line.trim()
            return t.isEmpty() || t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") || t.startsWith("import ")
        }

        // ---- 1a. Color/alpha bypass scan: everywhere UI code lives, excluding the theme/ package ----
        // ---- itself. Colors and alpha are fully centralized, so this applies repo-wide.         ----
        val colorAlphaScanRoots = listOf(
            File(root, "core/designsystem/src"),
            File(root, "core/ui/src"),
            File(root, "feature"),
            File(root, "lti-shared/src"),
            File(root, "lti-desktop/src"),
            File(root, "catalog/src"),
        )

        colorAlphaScanRoots.forEach { scanRoot ->
            ktFiles(scanRoot).forEach { file ->
                val path = file.absolutePath.replace('\\', '/')
                if (path.contains("/theme/")) return@forEach
                file.readLines().forEachIndexed { idx, rawLine ->
                    if (isCommentOrImport(rawLine)) return@forEachIndexed
                    val line = rawLine.trim()
                    if (Regex("""Color\(0x""").containsMatchIn(line)) {
                        violations += "${file.relativeTo(root).path}:${idx + 1}: raw color literal " +
                            "bypasses theme/Color.kt. [$line]"
                    }
                    if (Regex("""alpha\s*=\s*0\.\d""").containsMatchIn(line)) {
                        violations += "${file.relativeTo(root).path}:${idx + 1}: raw alpha literal " +
                            "bypasses theme/Alpha.kt (AlphaTokens). [$line]"
                    }
                }
            }
        }

        // ---- 1b. Dimension bypass scan: everywhere UI code lives, excluding the theme/ package ----
        // ---- itself. Dimensions are fully centralized, so this applies repo-wide.              ----
        val dimensionScanRoots = listOf(
            File(root, "core/designsystem/src"),
            File(root, "core/ui/src"),
            File(root, "feature"),
            File(root, "lti-shared/src"),
            File(root, "lti-desktop/src"),
            File(root, "catalog/src"),
        )

        dimensionScanRoots.forEach { scanRoot ->
            ktFiles(scanRoot).forEach { file ->
                val path = file.absolutePath.replace('\\', '/')
                if (path.contains("/theme/")) return@forEach
                file.readLines().forEachIndexed { idx, rawLine ->
                    if (isCommentOrImport(rawLine)) return@forEachIndexed
                    val line = rawLine.trim()
                    if (Regex("""\d+f?\.dp\b""").containsMatchIn(line)) {
                        violations += "${file.relativeTo(root).path}:${idx + 1}: raw dp literal " +
                            "bypasses theme/Dimens.kt. [$line]"
                    }
                }
            }
        }

        // ---- 1c. Motion bypass scan: everywhere UI code lives, excluding the theme/ package ----
        // ---- itself. Motion tokens are fully centralized, so this applies repo-wide.         ----
        val motionScanRoots = listOf(
            File(root, "core/designsystem/src"),
            File(root, "core/ui/src"),
            File(root, "feature"),
            File(root, "lti-shared/src"),
            File(root, "lti-desktop/src"),
            File(root, "catalog/src"),
        )

        motionScanRoots.forEach { scanRoot ->
            ktFiles(scanRoot).forEach { file ->
                val path = file.absolutePath.replace('\\', '/')
                if (path.contains("/theme/")) return@forEach
                file.readLines().forEachIndexed { idx, rawLine ->
                    if (isCommentOrImport(rawLine)) return@forEachIndexed
                    val line = rawLine.trim()
                    if (Regex("""durationMillis\s*=\s*[0-9]""").containsMatchIn(line)) {
                        violations += "${file.relativeTo(root).path}:${idx + 1}: raw durationMillis literal " +
                            "bypasses theme/Motion.kt (MotionDuration). [$line]"
                    }
                    if (Regex("""\btween\s*\(\s*[0-9]""").containsMatchIn(line)) {
                        violations += "${file.relativeTo(root).path}:${idx + 1}: raw tween duration literal " +
                            "bypasses theme/Motion.kt (MotionDuration). [$line]"
                    }
                    if (Regex("""\bspring\s*\(\s*[0-9]""").containsMatchIn(line)) {
                        violations += "${file.relativeTo(root).path}:${idx + 1}: raw spring numeric literal " +
                            "bypasses theme/Motion.kt (MotionSpring). [$line]"
                    }
                    if (Regex("""\b(?:dampingRatio|stiffness)\s*=\s*[0-9]""").containsMatchIn(line)) {
                        violations += "${file.relativeTo(root).path}:${idx + 1}: raw spring parameter literal " +
                            "bypasses theme/Motion.kt (MotionSpring). [$line]"
                    }
                    if (Regex("""\bCubicBezierEasing\s*\(\s*[0-9]""").containsMatchIn(line)) {
                        violations += "${file.relativeTo(root).path}:${idx + 1}: raw CubicBezierEasing literal " +
                            "bypasses theme/Motion.kt (MotionEasing). [$line]"
                    }
                }
            }
        }

        // ---- 2. Component-boundary scan: feature/*, lti-shared, lti-desktop must not hand-roll ----
        // ---- glass components directly from backdrop effect primitives.                        ----
        val boundaryScanRoots = listOf(
            File(root, "feature"),
            File(root, "lti-shared/src"),
            File(root, "lti-desktop/src"),
            File(root, "catalog/src"),
        )
        val forbiddenImportPrefixes = listOf(
            "import com.kashif_e.backdrop.effects.",
            "import com.kashif_e.backdrop.highlight.",
            "import com.kashif_e.backdrop.shadow.",
        )
        val forbiddenImportExact = setOf(
            "import com.kashif_e.backdrop.drawBackdrop",
            "import com.kashif_e.backdrop.BackdropEffectScope",
        )

        boundaryScanRoots.forEach { scanRoot ->
            ktFiles(scanRoot).forEach { file ->
                file.readLines().forEachIndexed { idx, rawLine ->
                    val line = rawLine.trim()
                    val isForbidden = forbiddenImportPrefixes.any { line.startsWith(it) } ||
                        forbiddenImportExact.contains(line)
                    if (isForbidden) {
                        violations += "${file.relativeTo(root).path}:${idx + 1}: this module may not build " +
                            "glass components directly from backdrop effect primitives - compose an " +
                            "existing core:designsystem/core:ui component instead. [$line]"
                    }
                }
            }
        }

        // ---- 2b. Material3-substitution scan: feature/*, lti-shared, lti-desktop, catalog must use the ----
        // ---- Glass equivalent instead of importing a raw Material3 component that has one.           ----
        val forbiddenMaterial3Import = mapOf(
            "import androidx.compose.material3.Button" to "GlassButton/GlassPrimaryButton",
            "import androidx.compose.material3.OutlinedButton" to "GlassButton",
            "import androidx.compose.material3.TextButton" to "GlassTextButton",
            "import androidx.compose.material3.ElevatedButton" to "GlassButton",
            "import androidx.compose.material3.FilledTonalButton" to "GlassButton",
            "import androidx.compose.material3.IconButton" to "GlassIconButton",
            "import androidx.compose.material3.FilledIconButton" to "GlassIconButton",
            "import androidx.compose.material3.OutlinedIconButton" to "GlassIconButton",
            "import androidx.compose.material3.IconToggleButton" to "GlassIconToggleButton",
            "import androidx.compose.material3.FilledIconToggleButton" to "GlassIconToggleButton",
            "import androidx.compose.material3.TextField" to "GlassTextField",
            "import androidx.compose.material3.OutlinedTextField" to "GlassTextField",
            "import androidx.compose.material3.Switch" to "GlassToggle",
            "import androidx.compose.material3.Slider" to "GlassSlider",
            "import androidx.compose.material3.RangeSlider" to "GlassSlider",
            "import androidx.compose.material3.AlertDialog" to "GlassDialog",
            "import androidx.compose.material3.Card" to "GlassCard",
            "import androidx.compose.material3.ElevatedCard" to "GlassCard",
            "import androidx.compose.material3.OutlinedCard" to "GlassCard",
            "import androidx.compose.material3.NavigationBar" to "GlassNavigationBar",
            "import androidx.compose.material3.NavigationBarItem" to "GlassNavigationBarItem",
            "import androidx.compose.material3.NavigationRail" to "GlassNavigationRail",
            "import androidx.compose.material3.NavigationRailItem" to "GlassNavigationRailItem",
            "import androidx.compose.material3.Tab" to "GlassTab/GlassTabBar",
            "import androidx.compose.material3.TabRow" to "GlassTabBar",
            "import androidx.compose.material3.ScrollableTabRow" to "GlassScrollableTabRow",
            "import androidx.compose.material3.ModalBottomSheet" to "GlassBottomSheet",
            "import androidx.compose.material3.BottomSheetScaffold" to "GlassBottomSheetScaffold",
        )

        boundaryScanRoots.forEach { scanRoot ->
            ktFiles(scanRoot).forEach { file ->
                file.readLines().forEachIndexed { idx, rawLine ->
                    val line = rawLine.trim()
                    val glassEquivalent = forbiddenMaterial3Import[line]
                    if (glassEquivalent != null) {
                        violations += "${file.relativeTo(root).path}:${idx + 1}: this module may not import " +
                            "a raw Material3 component that has a design-system equivalent - use " +
                            "$glassEquivalent instead. [$line]"
                    }
                }
            }
        }

        // ---- 3. Unused-token scan: every named constant in Color.kt/Dimens.kt/Alpha.kt/Motion.kt/Backdrop.kt must be ----
        // ---- referenced somewhere outside its own declaring file.                               ----
        val themeDir = File(root, "core/designsystem/src/commonMain/kotlin/org/ide/lti/core/designsystem/theme")
        val tokenFiles = listOf("Color.kt", "Dimens.kt", "Alpha.kt", "Motion.kt", "Backdrop.kt").map { File(themeDir, it) }

        val allSearchRoots = listOf(
            File(root, "core"),
            File(root, "feature"),
            File(root, "lti-shared"),
            File(root, "lti-desktop"),
            File(root, "catalog"),
        )
        val allSourceContents: Map<String, String> = allSearchRoots
            .flatMap { ktFiles(it) }
            .associate { it.absolutePath to it.readText() }

        tokenFiles.forEach { tf ->
            if (!tf.exists()) return@forEach
            var currentObject: String? = null
            var currentEnclosingClass: String? = null
            tf.readLines().forEach { rawLine ->
                val line = rawLine.trim()
                Regex("""^(?:data\s+class|class|sealed\s+class|abstract\s+class|interface)\s+(\w+)""").find(line)?.let {
                    currentEnclosingClass = it.groupValues[1]
                }
                Regex("""^object\s+(\w+)""").find(line)?.let {
                    currentObject = it.groupValues[1]
                }
                if (Regex("""^companion\s+object\b""").containsMatchIn(line)) {
                    currentObject = currentEnclosingClass
                }
                val obj = currentObject
                if (obj != null) {
                    Regex("""^(?:val|const val)\s+(\w+)\s*[:=]""").find(line)?.let { m ->
                        val token = "$obj.${m.groupValues[1]}"
                        val used = allSourceContents.entries.any { (path, content) ->
                            path != tf.absolutePath && content.contains(token)
                        }
                        if (!used) {
                            violations += "${tf.relativeTo(root).path}: $token is defined but never " +
                                "referenced anywhere. Remove it or wire it into a component."
                        }
                    }
                }
                if (line == "}") currentObject = null
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Design-system guardrail failed with ${violations.size} violation(s):\n" +
                    violations.joinToString("\n") { "  - $it" },
            )
        }
    }
}

allprojects {
    tasks.matching { it.name == "compileKotlinDesktop" }.configureEach {
        dependsOn(rootProject.tasks.named("verifyDesignSystemGuardrails"))
    }
}

