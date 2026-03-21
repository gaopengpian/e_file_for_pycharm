package com.github.antigravity.efile.ui

import com.github.antigravity.efile.EFileType
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class EFileEditorListener : FileEditorManagerListener {
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (file.fileType == EFileType.INSTANCE) {
            // Auto Remove Read-Only
            if (!file.isWritable) {
                 com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction {
                     try {
                         file.isWritable = true
                         NotificationGroupManager.getInstance()
                            .getNotificationGroup("E-File Notification")
                            .createNotification("Read-Only Removed", "Automatically removed read-only attribute for E-File.", NotificationType.INFORMATION)
                            .notify(source.project)
                     } catch (e: Exception) {
                         NotificationGroupManager.getInstance()
                            .getNotificationGroup("E-File Notification")
                            .createNotification("Read-Only Removal Failed", "Could not remove read-only attribute: ${e.message}", NotificationType.WARNING)
                            .notify(source.project)
                     }
                 }
            }

            val notification = NotificationGroupManager.getInstance()
                .getNotificationGroup("E-File Notification")
                .createNotification("E-File Plugin Active", "E-Language support is enabled for this file. Encoding: GBK", NotificationType.INFORMATION)
            
            notification.notify(source.project)
        }
    }
}
