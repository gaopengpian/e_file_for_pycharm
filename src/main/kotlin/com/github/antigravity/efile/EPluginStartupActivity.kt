package com.github.antigravity.efile

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.ide.util.PropertiesComponent

/**
 * 插件启动时显示版本信息
 */
class EPluginStartupActivity : ProjectActivity {
    companion object {
        const val VERSION_KEY = "e_file_plugin_last_version"
    }

    override suspend fun execute(project: Project) {
        val pluginId = PluginId.getId("com.github.antigravity.efile")
        val plugin = PluginManagerCore.getPlugin(pluginId)
        val currentVersion = plugin?.version ?: "Unknown"

        val properties = PropertiesComponent.getInstance()
        val lastVersion = properties.getValue(VERSION_KEY, "")

        // 如果版本更新了，显示通知
        if (lastVersion != currentVersion) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("E-File Notification")
                .createNotification(
                    "E-File Plugin",
                    "Current Version: $currentVersion",
                    NotificationType.INFORMATION
                )
                .notify(project)

            // 保存当前版本
            properties.setValue(VERSION_KEY, currentVersion)
        }
    }
}