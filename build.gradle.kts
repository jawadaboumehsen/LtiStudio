buildscript {
    dependencies {
        classpath(libs.android.gradlePlugin)
    }
}

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
    alias(libs.plugins.roborazzi) apply false
}

object DynamicVersion {
    fun setDynamicVersion(file: File, version: String) {
        val cleanedVersion = version.split('+')[0]
        file.writeText(cleanedVersion)
    }
}

tasks.register("versionFile") {
    val file = File(projectDir, "version.txt")
    val versionProvider = provider { project.version.toString() }
    outputs.file(file)

    doLast {
        DynamicVersion.setDynamicVersion(file, versionProvider.get())
    }
}
allprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
        compilerOptions {
            freeCompilerArgs.add("-Xcontext-parameters")
            freeCompilerArgs.add("-Xskip-metadata-version-check")
        }
    }

    configurations.all {
        resolutionStrategy {
            // com.materialkolor:material-kolor transitively pulls an alpha
            // org.jetbrains.compose.material3:material3 build that depends on
            // kotlinx-datetime 0.7.x, which removed kotlinx.datetime.Instant in favor of
            // kotlin.time.Instant. Gradle's default "highest wins" conflict resolution
            // would otherwise silently upgrade the whole runtime classpath to that
            // incompatible version, causing a NoClassDefFoundError at runtime for any code
            // (e.g. GlassMessageBubble.kt) still using kotlinx.datetime.Instant. Force the
            // version this app's code is actually written against.
            force("org.jetbrains.kotlinx:kotlinx-datetime:${libs.versions.kotlinxDatetime.get()}")
        }
    }
}

/**
 * Fails the build when UI code hardcodes colors/dimensions/alpha/motion values that bypass the
 * centralized design-system tokens (core/designsystem theme/Color.kt, Dimens.kt, Alpha.kt, Motion.kt),
 * when a feature/app module imports a raw Material3 component that has a Glass equivalent, or
 * when a token constant is defined but never referenced anywhere.
 */
tasks.register("verifyDesignSystemGuardrails") {
    group = "verification"
    description = "Enforces design-system token usage and component-location boundaries."
    val root = rootDir
    inputs.files(
        fileTree(root) {
            include("**/*.kt")
            exclude("**/build/**", ".gradle/**")
        }
    ).withPathSensitivity(PathSensitivity.RELATIVE)

    val markerFile = layout.buildDirectory.file("reports/guardrails/verified.txt")
    outputs.file(markerFile)
    mustRunAfter(":tool:codegen:generateToolAdapters")

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

        // ---- 1d. Shape bypass scan: UI code may not instantiate shapes inline or import shape ----
        // ---- primitives directly — use canonical GlassShapes/GlassTheme.shapes tokens.         ----
        val shapeScanRoots = listOf(
            File(root, "core/designsystem/src"),
            File(root, "core/ui/src"),
            File(root, "feature"),
            File(root, "lti-shared/src"),
            File(root, "lti-desktop/src"),
            File(root, "catalog/src"),
        )
        val forbiddenShapeImports = setOf(
            "import androidx.compose.foundation.shape.RoundedCornerShape",
            "import androidx.compose.foundation.shape.CutCornerShape",
            "import com.kyant.capsule.ContinuousRoundedRectangle",
            "import com.kyant.capsule.ContinuousCapsule",
            "import com.kyant.capsule.Continuity",
            "import com.kyant.capsule.continuities.G2Continuity",
            "import com.kyant.capsule.continuities.G1Continuity",
            "import com.kyant.capsule.concentricInset",
            "import com.kyant.capsule.concentricOutset",
            "import com.kyant.capsule.lerp",
        )

        shapeScanRoots.forEach { scanRoot ->
            ktFiles(scanRoot).forEach { file ->
                val path = file.absolutePath.replace('\\', '/')
                if (path.contains("/theme/") || path.contains("/desktopTest/") || path.contains("/test/")) return@forEach
                file.readLines().forEachIndexed { idx, rawLine ->
                    val line = rawLine.trim()
                    if (forbiddenShapeImports.contains(line)) {
                        violations += "${file.relativeTo(root).path}:${idx + 1}: forbidden raw shape import " +
                            "bypasses theme/Shape.kt (GlassShapes). [$line]"
                        return@forEachIndexed
                    }
                    if (isCommentOrImport(rawLine)) return@forEachIndexed
                    if (Regex("""\b(?:RoundedCornerShape|ContinuousRoundedRectangle|CutCornerShape)\s*\(""").containsMatchIn(line)) {
                        violations += "${file.relativeTo(root).path}:${idx + 1}: raw inline shape constructor " +
                            "bypasses theme/Shape.kt (use GlassShapes.<Preset> instead). [$line]"
                    }
                    if (Regex("""\bContinuousCapsule\b""").containsMatchIn(line)) {
                        violations += "${file.relativeTo(root).path}:${idx + 1}: ContinuousCapsule direct usage " +
                            "bypasses theme/Shape.kt (use GlassShapes.Capsule instead). [$line]"
                    }
                }
            }
        }

        // ---- 1e. Font/typography bypass scan: UI code may not declare raw .sp literals or raw font ----
        // ---- families inline — use theme/Type.kt tokens (FontSize, LineHeight, codeFontFamily). ----
        val fontScanRoots = listOf(
            File(root, "core/designsystem/src"),
            File(root, "core/ui/src"),
            File(root, "feature"),
            File(root, "lti-shared/src"),
            File(root, "lti-desktop/src"),
            File(root, "catalog/src"),
        )
        val forbiddenFontImports = setOf(
            "import androidx.compose.ui.unit.sp",
            "import androidx.compose.ui.text.font.FontFamily",
        )

        fontScanRoots.forEach { scanRoot ->
            ktFiles(scanRoot).forEach { file ->
                val path = file.absolutePath.replace('\\', '/')
                if (path.contains("/theme/") || path.contains("/desktopTest/") || path.contains("/test/")) return@forEach
                file.readLines().forEachIndexed { idx, rawLine ->
                    val line = rawLine.trim()
                    if (forbiddenFontImports.contains(line)) {
                        violations += "${file.relativeTo(root).path}:${idx + 1}: forbidden raw font/unit import " +
                            "bypasses theme/Type.kt tokens (FontSize/LineHeight/codeFontFamily). [$line]"
                        return@forEachIndexed
                    }
                    if (isCommentOrImport(rawLine)) return@forEachIndexed
                    if (Regex("""\b[0-9]+(?:\.[0-9]+)?f?\.sp\b""").containsMatchIn(line)) {
                        violations += "${file.relativeTo(root).path}:${idx + 1}: raw .sp literal bypasses " +
                            "theme/Type.kt tokens (FontSize/LineHeight/LetterSpacing). [$line]"
                    }
                    if (Regex("""\bFontFamily\.(?:Monospace|Default|SansSerif|Serif|Cursive)\b""").containsMatchIn(line)) {
                        violations += "${file.relativeTo(root).path}:${idx + 1}: direct FontFamily reference bypasses " +
                            "theme/Type.kt (use codeFontFamily() or GlassTheme.typography). [$line]"
                    }
                }
            }
        }

        // ---- 1f. Named-color bypass scan: UI code may not reference a raw Compose named color ----
        // ---- constant (Color.White, Color.Black, Color.Red, ...) outside theme/ - these have  ----
        // ---- the exact same "silently wrong in the other theme mode" failure as a raw hex      ----
        // ---- literal (1a), but slip past that regex since they're not Color(0x...). Transparent ----
        // ---- and Unspecified are load-bearing sentinel values (not real colors) and allowlisted. ----
        val namedColorScanRoots = listOf(
            File(root, "core/designsystem/src"),
            File(root, "core/ui/src"),
            File(root, "feature"),
            File(root, "lti-shared/src"),
            File(root, "lti-desktop/src"),
            File(root, "catalog/src"),
        )
        val namedColorRegex = Regex(
            """\bColor\.(White|Black|Red|Green|Blue|Yellow|Cyan|Magenta|Gray|LightGray|DarkGray)\b""",
        )

        namedColorScanRoots.forEach { scanRoot ->
            ktFiles(scanRoot).forEach { file ->
                val path = file.absolutePath.replace('\\', '/')
                if (path.contains("/theme/")) return@forEach
                file.readLines().forEachIndexed { idx, rawLine ->
                    if (isCommentOrImport(rawLine)) return@forEachIndexed
                    val line = rawLine.trim()
                    if (namedColorRegex.containsMatchIn(line)) {
                        violations += "${file.relativeTo(root).path}:${idx + 1}: raw named color constant " +
                            "bypasses theme/Color.kt - add a real token (e.g. BrandColors/ThemeColors/" +
                            "InteractionColors) instead. [$line]"
                    }
                }
            }
        }

        // ---- 2. Component-boundary roots: feature/*, lti-shared, lti-desktop and catalog consume ----
        // ---- design-system components; they are scanned by the Material3 check below.          ----
        val boundaryScanRoots = listOf(
            File(root, "feature"),
            File(root, "lti-shared/src"),
            File(root, "lti-desktop/src"),
            File(root, "catalog/src"),
        )

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

        // ---- 3. Unused-token scan: every named constant in Color.kt/Dimens.kt/Alpha.kt/Motion.kt must be ----
        // ---- referenced somewhere outside its own declaring file.                               ----
        val themeDir = File(root, "core/designsystem/src/commonMain/kotlin/org/ide/lti/core/designsystem/theme")
        val tokenFiles = listOf("Color.kt", "Dimens.kt", "Alpha.kt", "Motion.kt").map { File(themeDir, it) }

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

        val marker = markerFile.get().asFile
        marker.parentFile.mkdirs()
        marker.writeText("VERIFIED\n")
    }
}

/**
 * Fails the build when a feature module violates architectural boundaries by directly depending
 * on forbidden infrastructure modules (:core:data, :core:datastore, :core:network, :core:cli, :tool:runtime, :tool:adapter:*).
 * Features must interact with infrastructure exclusively through domain ports in :core:domain or -api modules.
 */
tasks.register("checkArchitecture") {
    group = "verification"
    description = "Enforces architectural modularization rules and prevents forbidden dependency edges."
    val root = rootDir

    inputs.files(
        subprojects.filter { it.path.startsWith(":feature:") }.map { it.buildFile }
    ).withPathSensitivity(PathSensitivity.RELATIVE)

    val markerFile = layout.buildDirectory.file("reports/architecture/verified.txt")
    outputs.file(markerFile)

    doLast {
        val forbiddenModules = listOf(
            "core.data",
            "core:data",
            "core.datastore",
            "core:datastore",
            "core.network",
            "core:network",
            "core.cli",
            "core:cli",
            "tool.runtime",
            "tool:runtime",
            "tool.adapter",
            "tool:adapter",
        )

        val violations = mutableListOf<String>()
        val featureDir = File(root, "feature")
        if (featureDir.exists()) {
            featureDir.walkTopDown()
                .filter { it.isFile && it.name == "build.gradle.kts" }
                .forEach { buildFile ->
                    val relPath = buildFile.relativeTo(root).path.replace('\\', '/')
                    val lines = buildFile.readLines()
                    lines.forEachIndexed { idx, rawLine ->
                        val line = rawLine.trim()
                        if (line.startsWith("//")) return@forEachIndexed
                        for (forbidden in forbiddenModules) {
                            if (line.contains("projects.$forbidden") || line.contains("project(\":$forbidden\")")) {
                                violations += "$relPath:${idx + 1}: Feature module has forbidden dependency on '$forbidden'. Feature modules must depend on domain ports instead."
                            }
                        }

                        // Rule 1: No stage imports another stage
                        if (relPath.contains("feature/rom-studio/stage-")) {
                            val currentStage = relPath.substringAfter("feature/rom-studio/").substringBefore('/')
                            if (Regex("""projects\.feature\.romStudio\.stage[A-Z]\w+""").containsMatchIn(line) ||
                                Regex("""project\([\"']:feature:rom-studio:stage-[^\"']+[\"']\)""").containsMatchIn(line)) {
                                violations += "$relPath:${idx + 1}: Stage module '$currentStage' has forbidden dependency on another stage. Stages must remain isolated."
                            }
                        }

                        // Rule 2: stage-patch has no sdk:patch-mod dependency
                        if (relPath.contains("feature/rom-studio/stage-patch")) {
                            if (line.contains("patchMod") || line.contains("patch-mod")) {
                                violations += "$relPath:${idx + 1}: stage-patch must not depend on sdk:patch-mod. Connect via domain models only."
                            }
                        }

                        // Rule 3: feature:workspace depends only on romStudioApi (no shell, no stages)
                        if (relPath.startsWith("feature/workspace/build.gradle.kts")) {
                            if (line.contains("feature.romStudio.shell") || line.contains("feature.romStudio.stage")) {
                                violations += "$relPath:${idx + 1}: feature:workspace must depend only on romStudioApi, not concrete shell or stages."
                            }
                        }

                        // Rule 4: romStudio.shell depends only on romStudioApi (no concrete stages)
                        if (relPath.contains("feature/rom-studio/shell/")) {
                            if (line.contains("feature.romStudio.stage")) {
                                violations += "$relPath:${idx + 1}: rom-studio shell must not depend directly on concrete stage modules. Stages are discovered via DI."
                            }
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Architecture guardrail failed with ${violations.size} violation(s):\n" +
                    violations.joinToString("\n") { "  - $it" },
            )
        }

        val marker = markerFile.get().asFile
        marker.parentFile.mkdirs()
        marker.writeText("VERIFIED\n")
    }
}

allprojects {
    tasks.matching { it.name == "compileKotlinDesktop" }.configureEach {
        dependsOn(rootProject.tasks.named("verifyDesignSystemGuardrails"))
        dependsOn(rootProject.tasks.named("checkArchitecture"))
    }
}

// Points git at the versioned hooks in scripts/git-hooks (the pre-push rule gate). Nothing is copied into
// .git/hooks, so hook edits apply immediately. Running, building or cleaning any module installs them, so a
// fresh clone is covered without a manual step (the app is usually started with :lti-desktop:run).
val installGitHooks by tasks.registering(Exec::class) {
    description = "Points core.hooksPath at scripts/git-hooks."
    group = "git hooks"
    workingDir = rootDir
    commandLine("git", "config", "core.hooksPath", "scripts/git-hooks")
}
allprojects {
    tasks.matching { it.name in setOf("run", "build", "assemble", "clean") }.configureEach {
        dependsOn(installGitHooks)
    }
}

// Tests must never write into the developer's real app data (setup logs, journals, run files):
// point LOCALAPPDATA, the Windows root used by resolveAppDataDir(), at each module's build folder.
allprojects {
    tasks.withType<Test>().configureEach {
        environment("LOCALAPPDATA", layout.buildDirectory.dir("test-appdata").get().asFile.absolutePath)
    }
}
