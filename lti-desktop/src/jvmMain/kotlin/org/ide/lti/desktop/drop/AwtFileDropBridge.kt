/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.desktop.drop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.desktop.WorkspaceDropCoordinator
import java.awt.Window
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.awt.dnd.DropTargetListener
import java.io.File

/**
 * Bridges native OS AWT file drag-and-drop events from a Compose desktop [Window]
 * directly into [WorkspaceDropCoordinator].
 */
object AwtFileDropBridge {
    /** Attaches a OS file [DropTarget] to [window] and returns a detach handle. */
    fun attach(
        window: Window,
        coordinator: WorkspaceDropCoordinator,
        coroutineScope: CoroutineScope,
    ): () -> Unit {
        val listener = object : DropTargetListener {
            override fun dragEnter(dtde: DropTargetDragEvent) {
                if (supportsFileDrop(dtde)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY)
                    val files = extractFilesSafely(dtde.transferable)
                    coordinator.onDragEntered(files.map { it.absolutePath })
                } else {
                    dtde.rejectDrag()
                }
            }

            override fun dragOver(dtde: DropTargetDragEvent) {
                if (supportsFileDrop(dtde)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY)
                } else {
                    dtde.rejectDrag()
                }
            }

            override fun dropActionChanged(dtde: DropTargetDragEvent) {
                if (supportsFileDrop(dtde)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY)
                }
            }

            override fun dragExit(dte: DropTargetEvent) {
                coordinator.onDragExited()
            }

            override fun drop(dtde: DropTargetDropEvent) {
                if (!dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    coordinator.onDragExited()
                    dtde.rejectDrop()
                    return
                }
                dtde.acceptDrop(DnDConstants.ACTION_COPY)
                val files = extractFilesSafely(dtde.transferable)
                if (files.isEmpty()) {
                    coordinator.onDragExited()
                    dtde.dropComplete(false)
                    return
                }
                val flags = files.associate { it.absolutePath to it.isDirectory }
                val paths = files.map { it.absolutePath }
                dtde.dropComplete(true)
                coroutineScope.launch {
                    coordinator.handleDroppedPaths(
                        paths = paths,
                        directoryFlags = flags,
                    )
                }
            }
        }

        val previous = window.dropTarget
        val target = DropTarget(window, DnDConstants.ACTION_COPY_OR_MOVE, listener, true)
        window.dropTarget = target
        return {
            target.isActive = false
            window.dropTarget = previous
        }
    }

    private fun supportsFileDrop(dtde: DropTargetDragEvent): Boolean =
        dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)

    @Suppress("UNCHECKED_CAST", "TooGenericExceptionCaught")
    private fun extractFilesSafely(transferable: java.awt.datatransfer.Transferable): List<File> =
        try {
            if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                emptyList()
            } else {
                (transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
                    ?.filterIsInstance<File>()
                    .orEmpty()
            }
        } catch (_: Exception) {
            emptyList()
        }
}
