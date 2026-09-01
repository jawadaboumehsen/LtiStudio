/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.ide.lti.core.designsystem.generated.resources.Res
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesActionScript
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesAddAny
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesAnyType
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesArchive
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesAspectJ
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesBazel
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesBinaryData
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesC
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesChangedFile
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesChangedFiles
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesConfig
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesContexts
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesContextsModifier
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesCpp
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesCsharp
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesCss
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesCsv
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesDiagram
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesDocker
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesEditorConfig
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesFont
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesGitignore
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesGradle
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesGraphql
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesGroovy
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesH
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesHprof
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesHtml
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesHttp
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesI18n
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesIdeaModule
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesIdl
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesIgnored
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesImage
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesJava
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesJavaClass
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesJavaScript
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesJenkins
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesJfr
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesJson
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesJsonSchema
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesJsp
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesJspx
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesJupyter
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesManifest
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesMarkdown
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesMicrosoftWindows
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesModified
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesPatch
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesPerl
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesProperties
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesRegexp
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesRst
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesScratch
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesScratches
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesShell
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesSourceMap
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesSql
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesSwiftLang
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesTerraform
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesText
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesToml
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesUiForm
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesUnknown
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesVue
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesWsdl
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesXhtml
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesXml
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesXsd
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesYaml
import org.jetbrains.compose.resources.painterResource

/**
 * Extension accessors for the JetBrains IntelliJ Platform "fileTypes" icon category.
 * Auto-generated from the bulk-imported expui/fileTypes/ icon set - see ICON_MANIFEST.md.
 */
val AppIcons.ideFileTypesActionScript: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesActionScript) }
val AppIcons.ideFileTypesAddAny: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesAddAny) }
val AppIcons.ideFileTypesAnyType: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesAnyType) }
val AppIcons.ideFileTypesArchive: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesArchive) }
val AppIcons.ideFileTypesAspectJ: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesAspectJ) }
val AppIcons.ideFileTypesBazel: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesBazel) }
val AppIcons.ideFileTypesBinaryData: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesBinaryData) }
val AppIcons.ideFileTypesC: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesC) }
val AppIcons.ideFileTypesChangedFile: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesChangedFile) }
val AppIcons.ideFileTypesChangedFiles: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesChangedFiles) }
val AppIcons.ideFileTypesConfig: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesConfig) }
val AppIcons.ideFileTypesContexts: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesContexts) }
val AppIcons.ideFileTypesContextsModifier: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesContextsModifier) }
val AppIcons.ideFileTypesCpp: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesCpp) }
val AppIcons.ideFileTypesCsharp: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesCsharp) }
val AppIcons.ideFileTypesCss: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesCss) }
val AppIcons.ideFileTypesCsv: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesCsv) }
val AppIcons.ideFileTypesDiagram: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesDiagram) }
val AppIcons.ideFileTypesDocker: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesDocker) }
val AppIcons.ideFileTypesEditorConfig: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesEditorConfig) }
val AppIcons.ideFileTypesFont: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesFont) }
val AppIcons.ideFileTypesGitignore: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesGitignore) }
val AppIcons.ideFileTypesGradle: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesGradle) }
val AppIcons.ideFileTypesGraphql: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesGraphql) }
val AppIcons.ideFileTypesGroovy: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesGroovy) }
val AppIcons.ideFileTypesH: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesH) }
val AppIcons.ideFileTypesHprof: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesHprof) }
val AppIcons.ideFileTypesHtml: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesHtml) }
val AppIcons.ideFileTypesHttp: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesHttp) }
val AppIcons.ideFileTypesI18n: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesI18n) }
val AppIcons.ideFileTypesIdeaModule: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesIdeaModule) }
val AppIcons.ideFileTypesIdl: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesIdl) }
val AppIcons.ideFileTypesIgnored: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesIgnored) }
val AppIcons.ideFileTypesImage: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesImage) }
val AppIcons.ideFileTypesJava: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesJava) }
val AppIcons.ideFileTypesJavaClass: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesJavaClass) }
val AppIcons.ideFileTypesJavaScript: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesJavaScript) }
val AppIcons.ideFileTypesJenkins: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesJenkins) }
val AppIcons.ideFileTypesJfr: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesJfr) }
val AppIcons.ideFileTypesJson: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesJson) }
val AppIcons.ideFileTypesJsonSchema: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesJsonSchema) }
val AppIcons.ideFileTypesJsp: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesJsp) }
val AppIcons.ideFileTypesJspx: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesJspx) }
val AppIcons.ideFileTypesJupyter: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesJupyter) }
val AppIcons.ideFileTypesManifest: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesManifest) }
val AppIcons.ideFileTypesMarkdown: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesMarkdown) }
val AppIcons.ideFileTypesMicrosoftWindows: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesMicrosoftWindows) }
val AppIcons.ideFileTypesModified: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesModified) }
val AppIcons.ideFileTypesPatch: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesPatch) }
val AppIcons.ideFileTypesPerl: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesPerl) }
val AppIcons.ideFileTypesProperties: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesProperties) }
val AppIcons.ideFileTypesRegexp: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesRegexp) }
val AppIcons.ideFileTypesRst: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesRst) }
val AppIcons.ideFileTypesScratch: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesScratch) }
val AppIcons.ideFileTypesScratches: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesScratches) }
val AppIcons.ideFileTypesShell: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesShell) }
val AppIcons.ideFileTypesSourceMap: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesSourceMap) }
val AppIcons.ideFileTypesSql: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesSql) }
val AppIcons.ideFileTypesSwiftLang: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesSwiftLang) }
val AppIcons.ideFileTypesTerraform: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesTerraform) }
val AppIcons.ideFileTypesText: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesText) }
val AppIcons.ideFileTypesToml: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesToml) }
val AppIcons.ideFileTypesUiForm: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesUiForm) }
val AppIcons.ideFileTypesUnknown: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesUnknown) }
val AppIcons.ideFileTypesVue: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesVue) }
val AppIcons.ideFileTypesWsdl: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesWsdl) }
val AppIcons.ideFileTypesXhtml: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesXhtml) }
val AppIcons.ideFileTypesXml: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesXml) }
val AppIcons.ideFileTypesXsd: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesXsd) }
val AppIcons.ideFileTypesYaml: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideFileTypesYaml) }
