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
import org.ide.lti.core.designsystem.generated.resources.ideNodesAbstractException
import org.ide.lti.core.designsystem.generated.resources.ideNodesAccessLocal
import org.ide.lti.core.designsystem.generated.resources.ideNodesAccessPrivate
import org.ide.lti.core.designsystem.generated.resources.ideNodesAccessProtected
import org.ide.lti.core.designsystem.generated.resources.ideNodesAccessPublic
import org.ide.lti.core.designsystem.generated.resources.ideNodesAlias
import org.ide.lti.core.designsystem.generated.resources.ideNodesAnnotation
import org.ide.lti.core.designsystem.generated.resources.ideNodesAnnotationFolder
import org.ide.lti.core.designsystem.generated.resources.ideNodesArtifact
import org.ide.lti.core.designsystem.generated.resources.ideNodesAttribute
import org.ide.lti.core.designsystem.generated.resources.ideNodesClass
import org.ide.lti.core.designsystem.generated.resources.ideNodesClassAbstract
import org.ide.lti.core.designsystem.generated.resources.ideNodesClassAnonymous
import org.ide.lti.core.designsystem.generated.resources.ideNodesClassInitializer
import org.ide.lti.core.designsystem.generated.resources.ideNodesCompiledClassesFolder
import org.ide.lti.core.designsystem.generated.resources.ideNodesConstant
import org.ide.lti.core.designsystem.generated.resources.ideNodesConstructor
import org.ide.lti.core.designsystem.generated.resources.ideNodesController
import org.ide.lti.core.designsystem.generated.resources.ideNodesCopyOfFolder
import org.ide.lti.core.designsystem.generated.resources.ideNodesDataColumn
import org.ide.lti.core.designsystem.generated.resources.ideNodesDataSchema
import org.ide.lti.core.designsystem.generated.resources.ideNodesDataTables
import org.ide.lti.core.designsystem.generated.resources.ideNodesDesktop
import org.ide.lti.core.designsystem.generated.resources.ideNodesEditFolder
import org.ide.lti.core.designsystem.generated.resources.ideNodesEntryPoints
import org.ide.lti.core.designsystem.generated.resources.ideNodesEnum
import org.ide.lti.core.designsystem.generated.resources.ideNodesErrorIntroduction
import org.ide.lti.core.designsystem.generated.resources.ideNodesException
import org.ide.lti.core.designsystem.generated.resources.ideNodesExcludeRoot
import org.ide.lti.core.designsystem.generated.resources.ideNodesExcludedGenerated
import org.ide.lti.core.designsystem.generated.resources.ideNodesExtractedFolder
import org.ide.lti.core.designsystem.generated.resources.ideNodesField
import org.ide.lti.core.designsystem.generated.resources.ideNodesFinalMark
import org.ide.lti.core.designsystem.generated.resources.ideNodesFolderGithub
import org.ide.lti.core.designsystem.generated.resources.ideNodesFunction
import org.ide.lti.core.designsystem.generated.resources.ideNodesGenerated
import org.ide.lti.core.designsystem.generated.resources.ideNodesGeneratedSource
import org.ide.lti.core.designsystem.generated.resources.ideNodesGeneratedTestRoot
import org.ide.lti.core.designsystem.generated.resources.ideNodesGvariable
import org.ide.lti.core.designsystem.generated.resources.ideNodesHomeFolder
import org.ide.lti.core.designsystem.generated.resources.ideNodesIdeaProject
import org.ide.lti.core.designsystem.generated.resources.ideNodesInclude
import org.ide.lti.core.designsystem.generated.resources.ideNodesInterface
import org.ide.lti.core.designsystem.generated.resources.ideNodesJarDirectory
import org.ide.lti.core.designsystem.generated.resources.ideNodesJavaDocFolder
import org.ide.lti.core.designsystem.generated.resources.ideNodesJdk
import org.ide.lti.core.designsystem.generated.resources.ideNodesJunitTestMark
import org.ide.lti.core.designsystem.generated.resources.ideNodesLambda
import org.ide.lti.core.designsystem.generated.resources.ideNodesLibrary
import org.ide.lti.core.designsystem.generated.resources.ideNodesLibraryFolder
import org.ide.lti.core.designsystem.generated.resources.ideNodesLocked
import org.ide.lti.core.designsystem.generated.resources.ideNodesLogFolder
import org.ide.lti.core.designsystem.generated.resources.ideNodesMcpServer
import org.ide.lti.core.designsystem.generated.resources.ideNodesMcpServerWidget
import org.ide.lti.core.designsystem.generated.resources.ideNodesMethod
import org.ide.lti.core.designsystem.generated.resources.ideNodesMethodAbstract
import org.ide.lti.core.designsystem.generated.resources.ideNodesMethodReference
import org.ide.lti.core.designsystem.generated.resources.ideNodesModelClass
import org.ide.lti.core.designsystem.generated.resources.ideNodesModels
import org.ide.lti.core.designsystem.generated.resources.ideNodesModule
import org.ide.lti.core.designsystem.generated.resources.ideNodesModule8x8
import org.ide.lti.core.designsystem.generated.resources.ideNodesModuleGroup
import org.ide.lti.core.designsystem.generated.resources.ideNodesModuleJava
import org.ide.lti.core.designsystem.generated.resources.ideNodesMultipleTypeDefinitions
import org.ide.lti.core.designsystem.generated.resources.ideNodesNativeLibrariesFolder
import org.ide.lti.core.designsystem.generated.resources.ideNodesPackage
import org.ide.lti.core.designsystem.generated.resources.ideNodesParameter
import org.ide.lti.core.designsystem.generated.resources.ideNodesPlugin
import org.ide.lti.core.designsystem.generated.resources.ideNodesPluginJB
import org.ide.lti.core.designsystem.generated.resources.ideNodesPluginLogo
import org.ide.lti.core.designsystem.generated.resources.ideNodesPluginLogoDisabled
import org.ide.lti.core.designsystem.generated.resources.ideNodesPpInvalid
import org.ide.lti.core.designsystem.generated.resources.ideNodesPpWeb
import org.ide.lti.core.designsystem.generated.resources.ideNodesProcessMark
import org.ide.lti.core.designsystem.generated.resources.ideNodesProperty
import org.ide.lti.core.designsystem.generated.resources.ideNodesRecord
import org.ide.lti.core.designsystem.generated.resources.ideNodesRelated
import org.ide.lti.core.designsystem.generated.resources.ideNodesResourceBundle
import org.ide.lti.core.designsystem.generated.resources.ideNodesResourcesRoot
import org.ide.lti.core.designsystem.generated.resources.ideNodesRunnableMark
import org.ide.lti.core.designsystem.generated.resources.ideNodesSecurityRole
import org.ide.lti.core.designsystem.generated.resources.ideNodesServices
import org.ide.lti.core.designsystem.generated.resources.ideNodesServlet
import org.ide.lti.core.designsystem.generated.resources.ideNodesShared
import org.ide.lti.core.designsystem.generated.resources.ideNodesSortBySeverity
import org.ide.lti.core.designsystem.generated.resources.ideNodesSourceRoot
import org.ide.lti.core.designsystem.generated.resources.ideNodesSourceRootFileLayer
import org.ide.lti.core.designsystem.generated.resources.ideNodesSsh
import org.ide.lti.core.designsystem.generated.resources.ideNodesStar
import org.ide.lti.core.designsystem.generated.resources.ideNodesStarEmpty
import org.ide.lti.core.designsystem.generated.resources.ideNodesStatic
import org.ide.lti.core.designsystem.generated.resources.ideNodesStaticMark
import org.ide.lti.core.designsystem.generated.resources.ideNodesSymlink
import org.ide.lti.core.designsystem.generated.resources.ideNodesTabAlert
import org.ide.lti.core.designsystem.generated.resources.ideNodesTag
import org.ide.lti.core.designsystem.generated.resources.ideNodesTemplate
import org.ide.lti.core.designsystem.generated.resources.ideNodesTemplateRoot
import org.ide.lti.core.designsystem.generated.resources.ideNodesTest
import org.ide.lti.core.designsystem.generated.resources.ideNodesTestGroup
import org.ide.lti.core.designsystem.generated.resources.ideNodesTestIgnored
import org.ide.lti.core.designsystem.generated.resources.ideNodesTestResourcesRoot
import org.ide.lti.core.designsystem.generated.resources.ideNodesTestRoot
import org.ide.lti.core.designsystem.generated.resources.ideNodesTestSourceFolder
import org.ide.lti.core.designsystem.generated.resources.ideNodesTextArea
import org.ide.lti.core.designsystem.generated.resources.ideNodesType
import org.ide.lti.core.designsystem.generated.resources.ideNodesUnloadedModule
import org.ide.lti.core.designsystem.generated.resources.ideNodesUnloadedProject
import org.ide.lti.core.designsystem.generated.resources.ideNodesUnmarkWebRoot
import org.ide.lti.core.designsystem.generated.resources.ideNodesUpFolder
import org.ide.lti.core.designsystem.generated.resources.ideNodesUpLevel
import org.ide.lti.core.designsystem.generated.resources.ideNodesVariable
import org.ide.lti.core.designsystem.generated.resources.ideNodesWarningIntroduction
import org.ide.lti.core.designsystem.generated.resources.ideNodesWebFolder
import org.ide.lti.core.designsystem.generated.resources.ideNodesWord
import org.ide.lti.core.designsystem.generated.resources.ideNodesWorkspace
import org.jetbrains.compose.resources.painterResource

/**
 * Extension accessors for the JetBrains IntelliJ Platform "nodes" icon category.
 * Auto-generated from the bulk-imported expui/nodes/ icon set - see ICON_MANIFEST.md.
 */
val AppIcons.ideNodesAbstractException: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesAbstractException) }
val AppIcons.ideNodesAccessLocal: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesAccessLocal) }
val AppIcons.ideNodesAccessPrivate: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesAccessPrivate) }
val AppIcons.ideNodesAccessProtected: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesAccessProtected) }
val AppIcons.ideNodesAccessPublic: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesAccessPublic) }
val AppIcons.ideNodesAlias: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesAlias) }
val AppIcons.ideNodesAnnotation: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesAnnotation) }
val AppIcons.ideNodesAnnotationFolder: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesAnnotationFolder) }
val AppIcons.ideNodesArtifact: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesArtifact) }
val AppIcons.ideNodesAttribute: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesAttribute) }
val AppIcons.ideNodesClass: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesClass) }
val AppIcons.ideNodesClassAbstract: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesClassAbstract) }
val AppIcons.ideNodesClassAnonymous: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesClassAnonymous) }
val AppIcons.ideNodesClassInitializer: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesClassInitializer) }
val AppIcons.ideNodesCompiledClassesFolder: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesCompiledClassesFolder) }
val AppIcons.ideNodesConstant: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesConstant) }
val AppIcons.ideNodesConstructor: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesConstructor) }
val AppIcons.ideNodesController: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesController) }
val AppIcons.ideNodesCopyOfFolder: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesCopyOfFolder) }
val AppIcons.ideNodesDataColumn: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesDataColumn) }
val AppIcons.ideNodesDataSchema: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesDataSchema) }
val AppIcons.ideNodesDataTables: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesDataTables) }
val AppIcons.ideNodesDesktop: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesDesktop) }
val AppIcons.ideNodesEditFolder: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesEditFolder) }
val AppIcons.ideNodesEntryPoints: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesEntryPoints) }
val AppIcons.ideNodesEnum: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesEnum) }
val AppIcons.ideNodesErrorIntroduction: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesErrorIntroduction) }
val AppIcons.ideNodesException: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesException) }
val AppIcons.ideNodesExcludeRoot: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesExcludeRoot) }
val AppIcons.ideNodesExcludedGenerated: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesExcludedGenerated) }
val AppIcons.ideNodesExtractedFolder: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesExtractedFolder) }
val AppIcons.ideNodesField: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesField) }
val AppIcons.ideNodesFinalMark: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesFinalMark) }
val AppIcons.ideNodesFolderGithub: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesFolderGithub) }
val AppIcons.ideNodesFunction: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesFunction) }
val AppIcons.ideNodesGenerated: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesGenerated) }
val AppIcons.ideNodesGeneratedSource: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesGeneratedSource) }
val AppIcons.ideNodesGeneratedTestRoot: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesGeneratedTestRoot) }
val AppIcons.ideNodesGvariable: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesGvariable) }
val AppIcons.ideNodesHomeFolder: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesHomeFolder) }
val AppIcons.ideNodesIdeaProject: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesIdeaProject) }
val AppIcons.ideNodesInclude: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesInclude) }
val AppIcons.ideNodesInterface: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesInterface) }
val AppIcons.ideNodesJarDirectory: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesJarDirectory) }
val AppIcons.ideNodesJavaDocFolder: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesJavaDocFolder) }
val AppIcons.ideNodesJdk: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesJdk) }
val AppIcons.ideNodesJunitTestMark: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesJunitTestMark) }
val AppIcons.ideNodesLambda: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesLambda) }
val AppIcons.ideNodesLibrary: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesLibrary) }
val AppIcons.ideNodesLibraryFolder: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesLibraryFolder) }
val AppIcons.ideNodesLocked: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesLocked) }
val AppIcons.ideNodesLogFolder: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesLogFolder) }
val AppIcons.ideNodesMcpServer: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesMcpServer) }
val AppIcons.ideNodesMcpServerWidget: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesMcpServerWidget) }
val AppIcons.ideNodesMethod: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesMethod) }
val AppIcons.ideNodesMethodAbstract: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesMethodAbstract) }
val AppIcons.ideNodesMethodReference: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesMethodReference) }
val AppIcons.ideNodesModelClass: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesModelClass) }
val AppIcons.ideNodesModels: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesModels) }
val AppIcons.ideNodesModule: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesModule) }
val AppIcons.ideNodesModule8x8: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesModule8x8) }
val AppIcons.ideNodesModuleGroup: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesModuleGroup) }
val AppIcons.ideNodesModuleJava: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesModuleJava) }
val AppIcons.ideNodesMultipleTypeDefinitions: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesMultipleTypeDefinitions) }
val AppIcons.ideNodesNativeLibrariesFolder: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesNativeLibrariesFolder) }
val AppIcons.ideNodesPackage: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesPackage) }
val AppIcons.ideNodesParameter: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesParameter) }
val AppIcons.ideNodesPlugin: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesPlugin) }
val AppIcons.ideNodesPluginJB: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesPluginJB) }
val AppIcons.ideNodesPluginLogo: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesPluginLogo) }
val AppIcons.ideNodesPluginLogoDisabled: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesPluginLogoDisabled) }
val AppIcons.ideNodesPpInvalid: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesPpInvalid) }
val AppIcons.ideNodesPpWeb: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesPpWeb) }
val AppIcons.ideNodesProcessMark: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesProcessMark) }
val AppIcons.ideNodesProperty: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesProperty) }
val AppIcons.ideNodesRecord: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesRecord) }
val AppIcons.ideNodesRelated: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesRelated) }
val AppIcons.ideNodesResourceBundle: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesResourceBundle) }
val AppIcons.ideNodesResourcesRoot: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesResourcesRoot) }
val AppIcons.ideNodesRunnableMark: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesRunnableMark) }
val AppIcons.ideNodesSecurityRole: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesSecurityRole) }
val AppIcons.ideNodesServices: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesServices) }
val AppIcons.ideNodesServlet: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesServlet) }
val AppIcons.ideNodesShared: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesShared) }
val AppIcons.ideNodesSortBySeverity: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesSortBySeverity) }
val AppIcons.ideNodesSourceRoot: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesSourceRoot) }
val AppIcons.ideNodesSourceRootFileLayer: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesSourceRootFileLayer) }
val AppIcons.ideNodesSsh: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesSsh) }
val AppIcons.ideNodesStar: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesStar) }
val AppIcons.ideNodesStarEmpty: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesStarEmpty) }
val AppIcons.ideNodesStatic: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesStatic) }
val AppIcons.ideNodesStaticMark: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesStaticMark) }
val AppIcons.ideNodesSymlink: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesSymlink) }
val AppIcons.ideNodesTabAlert: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesTabAlert) }
val AppIcons.ideNodesTag: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesTag) }
val AppIcons.ideNodesTemplate: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesTemplate) }
val AppIcons.ideNodesTemplateRoot: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesTemplateRoot) }
val AppIcons.ideNodesTest: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesTest) }
val AppIcons.ideNodesTestGroup: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesTestGroup) }
val AppIcons.ideNodesTestIgnored: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesTestIgnored) }
val AppIcons.ideNodesTestResourcesRoot: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesTestResourcesRoot) }
val AppIcons.ideNodesTestRoot: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesTestRoot) }
val AppIcons.ideNodesTestSourceFolder: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesTestSourceFolder) }
val AppIcons.ideNodesTextArea: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesTextArea) }
val AppIcons.ideNodesType: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesType) }
val AppIcons.ideNodesUnloadedModule: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesUnloadedModule) }
val AppIcons.ideNodesUnloadedProject: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesUnloadedProject) }
val AppIcons.ideNodesUnmarkWebRoot: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesUnmarkWebRoot) }
val AppIcons.ideNodesUpFolder: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesUpFolder) }
val AppIcons.ideNodesUpLevel: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesUpLevel) }
val AppIcons.ideNodesVariable: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesVariable) }
val AppIcons.ideNodesWarningIntroduction: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesWarningIntroduction) }
val AppIcons.ideNodesWebFolder: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesWebFolder) }
val AppIcons.ideNodesWord: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesWord) }
val AppIcons.ideNodesWorkspace: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideNodesWorkspace) }
