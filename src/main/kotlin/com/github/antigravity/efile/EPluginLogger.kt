package com.github.antigravity.efile

import java.io.FileWriter
import java.io.PrintWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 插件日志工具类
 */
object EPluginLogger {
    private val LOG_FILE = "${System.getProperty("user.home")}/e-file-plugin.log"
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    fun log(tag: String, message: String) {
        try {
            val timestamp = LocalDateTime.now().format(dateFormatter)
            val logLine = "[$timestamp] [$tag] $message\n"

            // 输出到文件
            PrintWriter(FileWriter(LOG_FILE, true)).use { it.print(logLine) }

            // 也输出到控制台（用于调试）
            println(logLine)
        } catch (e: Exception) {
            println("Failed to write log: ${e.message}")
        }
    }

    fun info(message: String) = log("INFO", message)
    fun debug(message: String) = log("DEBUG", message)
    fun warn(message: String) = log("WARN", message)
    fun error(message: String) = log("ERROR", message)

    /**
     * 标记重要操作，用于追踪问题
     */
    fun marker(message: String) = log("★★★ MARKER ★★★", message)

    /**
     * 清空日志文件
     */
    fun clear() {
        try {
            PrintWriter(FileWriter(LOG_FILE, false)).use { it.print("") }
        } catch (e: Exception) {
            println("Failed to clear log: ${e.message}")
        }
    }
}