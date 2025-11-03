package io.hyperswitch.demo_app_lite

import android.content.Context
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles exporting SDK performance metrics to various formats
 */
class PerformanceExporter(private val context: Context) {

    companion object {
        private const val TAG = "SDK_PERF_EXPORT"
    }

    /**
     * Export metrics as JSON
     */
    fun exportAsJson(metrics: SDKPerformanceTracker.SDKMetrics): String {
        return buildString {
            append("{\n")
            append("  \"sdkName\": \"${metrics.sdkName}\",\n")
            append("  \"timestamp\": ${metrics.timestamp},\n")
            append("  \"performanceGrade\": \"${metrics.performanceGrade}\",\n")
            
            // Timing
            append("  \"timing\": {\n")
            append("    \"sdkInitTimeMs\": ${metrics.sdkInitTimeMs},\n")
            append("    \"totalExecutionTimeMs\": ${metrics.totalExecutionTimeMs}\n")
            append("  },\n")
            
            // Memory
            append("  \"memory\": ${formatMetricStats(metrics.memoryStats)},\n")
            append("  \"pss\": ${formatMetricStats(metrics.pssStats)},\n")

            // CPU
            append("  \"cpu\": ${formatMetricStats(metrics.cpuUsageStats)},\n")
            
            // Network
            append("  \"network\": {\n")
            append("    \"rxKB\": ${metrics.networkMetrics.rxKB},\n")
            append("    \"txKB\": ${metrics.networkMetrics.txKB},\n")
            append("    \"totalKB\": ${metrics.networkMetrics.totalKB},\n")
            append("    \"avgBandwidthKBps\": ${"%.2f".format(metrics.networkMetrics.avgBandwidthKBps)},\n")
            append("    \"peakBandwidthKBps\": ${"%.2f".format(metrics.networkMetrics.peakBandwidthKBps)}\n")
            append("  },\n")
            
            // FPS
            append("  \"fps\": {\n")
            append("    \"average\": ${"%.2f".format(metrics.fpsMetrics.average)},\n")
            append("    \"min\": ${"%.2f".format(metrics.fpsMetrics.min)},\n")
            append("    \"max\": ${"%.2f".format(metrics.fpsMetrics.max)},\n")
            append("    \"p50\": ${"%.2f".format(metrics.fpsMetrics.p50)},\n")
            append("    \"p95\": ${"%.2f".format(metrics.fpsMetrics.p95)},\n")
            append("    \"p99\": ${"%.2f".format(metrics.fpsMetrics.p99)},\n")
            append("    \"frameDropsBelow55\": ${metrics.fpsMetrics.frameDropsBelow55},\n")
            append("    \"frameDropsBelow45\": ${metrics.fpsMetrics.frameDropsBelow45},\n")
            append("    \"frameDropsBelow30\": ${metrics.fpsMetrics.frameDropsBelow30}\n")
            append("  },\n")
            
            // Threads
            append("  \"threads\": {\n")
            append("    \"initial\": ${metrics.threadMetrics.initial},\n")
            append("    \"final\": ${metrics.threadMetrics.final},\n")
            append("    \"peak\": ${metrics.threadMetrics.peak},\n")
            append("    \"average\": ${"%.2f".format(metrics.threadMetrics.average)},\n")
            append("    \"threadsCreated\": ${metrics.threadMetrics.threadsCreated}\n")
            append("  },\n")
            
            // Disk I/O
            append("  \"diskIO\": {\n")
            append("    \"readKB\": ${"%.2f".format(metrics.diskIOMetrics.readKB)},\n")
            append("    \"writeKB\": ${"%.2f".format(metrics.diskIOMetrics.writeKB)},\n")
            append("    \"totalKB\": ${"%.2f".format(metrics.diskIOMetrics.totalKB)}\n")
            append("  },\n")

            // Garbage Collection
            append("  \"gc\": {\n")
            append("    \"gcCount\": ${metrics.gcMetrics.gcCount},\n")
            append("    \"gcTimeMs\": ${metrics.gcMetrics.gcTimeMs},\n")
            append("    \"avgGcTimeMs\": ${"%.2f".format(metrics.gcMetrics.avgGcTimeMs)}\n")
            append("  },\n")
            
            // Violations
            append("  \"violations\": [\n")
            append(metrics.violations.joinToString(",\n") { "    \"$it\"" })
            append("\n  ]\n")
            
            append("}")
        }
    }

    /**
     * Generate comparison report for two SDKs
     */
    fun generateComparisonReport(
        mainMetrics: SDKPerformanceTracker.SDKMetrics,
        liteMetrics: SDKPerformanceTracker.SDKMetrics
    ): String {
        return buildString {
            appendLine("# SDK Performance Comparison: ${mainMetrics.sdkName} vs ${liteMetrics.sdkName}")
            appendLine()
            
            appendLine("## Execution Time")
            appendLine("| SDK | Init Time | Total Time | Winner |")
            appendLine("|-----|-----------|------------|--------|")
            appendLine("| ${mainMetrics.sdkName} | ${mainMetrics.sdkInitTimeMs}ms | ${mainMetrics.totalExecutionTimeMs}ms | ${if (mainMetrics.totalExecutionTimeMs <= liteMetrics.totalExecutionTimeMs) "✅" else ""} |")
            appendLine("| ${liteMetrics.sdkName} | ${liteMetrics.sdkInitTimeMs}ms | ${liteMetrics.totalExecutionTimeMs}ms | ${if (liteMetrics.totalExecutionTimeMs < mainMetrics.totalExecutionTimeMs) "✅" else ""} |")
            appendLine()
            
            appendLine("## Memory Usage (Peak)")
            appendLine("| SDK | Heap | RAM | Winner |")
            appendLine("|-----|------|-----|--------|")
            appendLine()
            
            appendLine("## CPU Usage (Peak)")
            appendLine("| SDK | CPU % | Winner |")
            appendLine("|-----|-------|--------|")
            appendLine("| ${mainMetrics.sdkName} | ${"%.2f".format(mainMetrics.cpuUsageStats.peak)}% | ${if (mainMetrics.cpuUsageStats.peak <= liteMetrics.cpuUsageStats.peak) "✅" else ""} |")
            appendLine("| ${liteMetrics.sdkName} | ${"%.2f".format(liteMetrics.cpuUsageStats.peak)}% | ${if (liteMetrics.cpuUsageStats.peak < mainMetrics.cpuUsageStats.peak) "✅" else ""} |")
            appendLine()
            
            appendLine("## Network Usage")
            appendLine("| SDK | Total KB | Winner |")
            appendLine("|-----|----------|--------|")
            appendLine("| ${mainMetrics.sdkName} | ${mainMetrics.networkMetrics.totalKB}KB | ${if (mainMetrics.networkMetrics.totalKB <= liteMetrics.networkMetrics.totalKB) "✅" else ""} |")
            appendLine("| ${liteMetrics.sdkName} | ${liteMetrics.networkMetrics.totalKB}KB | ${if (liteMetrics.networkMetrics.totalKB < mainMetrics.networkMetrics.totalKB) "✅" else ""} |")
            appendLine()
            
            appendLine("## Overall Grade")
            appendLine("| SDK | Grade |")
            appendLine("|-----|-------|")
            appendLine("| ${mainMetrics.sdkName} | ${getGradeEmoji(mainMetrics.performanceGrade)} ${mainMetrics.performanceGrade} |")
            appendLine("| ${liteMetrics.sdkName} | ${getGradeEmoji(liteMetrics.performanceGrade)} ${liteMetrics.performanceGrade} |")
        }
    }

    /**
     * Save metrics to file in Downloads folder for easy PC access
     */
    fun saveToFile(content: String, filename: String): File? {
        return try {
            // Save to Downloads folder which is easily accessible from PC
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val perfFolder = File(downloadsDir, "HyperswitchPerformance")
            
            // Create folder if it doesn't exist
            if (!perfFolder.exists()) {
                perfFolder.mkdirs()
            }
            
            val file = File(perfFolder, filename)
            file.writeText(content)
            Log.d(TAG, "Saved to Downloads: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Error saving file: ${e.message}")
            // Fallback to app directory if Downloads fails
            try {
                val file = File(context.getExternalFilesDir(null), filename)
                file.writeText(content)
                Log.d(TAG, "Saved to app directory (fallback): ${file.absolutePath}")
                file
            } catch (e2: Exception) {
                Log.e(TAG, "Error saving to fallback: ${e2.message}")
                null
            }
        }
    }

    private fun formatMetricStats(stats: SDKPerformanceTracker.MetricStats): String {
        return buildString {
            append("{\n")
            append("    \"current\": ${"%.2f".format(stats.current)},\n")
            append("    \"peak\": ${"%.2f".format(stats.peak)},\n")
            append("    \"min\": ${"%.2f".format(stats.min)},\n")
            append("    \"average\": ${"%.2f".format(stats.average)},\n")
            append("    \"median\": ${"%.2f".format(stats.median)}\n")
            append("  }")
        }
    }

    private fun getGradeEmoji(grade: SDKPerformanceTracker.PerformanceGrade): String {
        return when (grade) {
            SDKPerformanceTracker.PerformanceGrade.EXCELLENT -> "🌟"
            SDKPerformanceTracker.PerformanceGrade.GOOD -> "✅"
            SDKPerformanceTracker.PerformanceGrade.ACCEPTABLE -> "⚠️"
            SDKPerformanceTracker.PerformanceGrade.NEEDS_OPTIMIZATION -> "❌"
            SDKPerformanceTracker.PerformanceGrade.POOR -> "🚫"
        }
    }

    private fun getPerformanceSummary(metrics: SDKPerformanceTracker.SDKMetrics): String {
        return buildString {
            // Timing summary
            when {
                metrics.totalExecutionTimeMs < 1000 -> appendLine("✅ **Excellent timing** - under 1 second")
                metrics.totalExecutionTimeMs < 2000 -> appendLine("✅ **Good timing** - under 2 seconds")
                metrics.totalExecutionTimeMs < 3000 -> appendLine("⚠️ **Acceptable timing** - under 3 seconds")
                else -> appendLine("❌ **Slow execution** - over 3 seconds")
            }
            
            // Memory summary
            when {
                metrics.memoryStats.peak < 20 -> appendLine("✅ **Low memory footprint** - excellent")
                metrics.memoryStats.peak < 50 -> appendLine("⚠️ **Moderate memory usage** - acceptable")
                else -> appendLine("❌ **High memory usage** - needs optimization")
            }
            
            // FPS summary
            when {
                metrics.fpsMetrics.average >= 55 -> appendLine("✅ **Smooth UI rendering** - no issues")
                metrics.fpsMetrics.average >= 45 -> appendLine("⚠️ **Some frame drops** - monitor closely")
                else -> appendLine("❌ **Significant frame drops** - optimize immediately")
            }
            
            // Network summary
            when {
                metrics.networkMetrics.totalKB < 1000 -> appendLine("✅ **Low network usage** - excellent")
                metrics.networkMetrics.totalKB < 5000 -> appendLine("⚠️ **Moderate network usage** - acceptable")
                else -> appendLine("❌ **High network usage** - review data transfers")
            }
        }
    }
}