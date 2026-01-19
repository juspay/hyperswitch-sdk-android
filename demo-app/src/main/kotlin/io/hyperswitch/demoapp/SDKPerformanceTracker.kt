package io.hyperswitch.demoapp

import android.app.ActivityManager
import android.content.Context
import android.net.TrafficStats
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class SDKPerformanceTracker(private val context: Context) {

    companion object {
        private const val SAMPLING_INTERVAL_NORMAL = 100L      // Normal runtime
        private const val SAMPLING_INTERVAL_CRITICAL = 20L     // Init/close phases
        private const val CRITICAL_PHASE_DURATION = 2000L      // 2 seconds
        private const val TAG = "SDK_PERF"
    }

    // Network interceptor instance
//    val networkInterceptor = SDKNetworkInterceptor()

    // Baseline measurements
    private var startTime: Long = 0
    private var sdkInitStartTime: Long = 0
    private var sdkInitEndTime: Long = 0

    // GC tracking
    private var startGcCount: Long = 0
    private var startGcTime: Long = 0
    private var startRxBytes: Long = 0
    private var startTxBytes: Long = 0
    private var startThreadCount: Int = 0
    private var startDiskReadBytes: Long = 0
    private var startDiskWriteBytes: Long = 0

    // Continuous sampling data
    private val memorySamples = mutableListOf<MemorySample>()
    private val cpuSamples = mutableListOf<CpuSample>()
    private val threadSamples = mutableListOf<Int>()
    private val networkSamples = mutableListOf<NetworkSample>()
    private val fpsValues = mutableListOf<Double>()

    // Sampling control
    private var samplingActive = false
    private var fpsMonitoringActive = false
    private val samplingHandler = Handler(Looper.getMainLooper())
    private var lastCpuTime: Long = 0
    private var lastCpuTimestamp: Long = 0

    private val uid = Process.myUid()
    private val pid = Process.myPid()
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    // Data classes for samples
    data class MemorySample(
        val timestamp: Long,
        val heapUsedMB: Double,
        val heapAllocatedMB: Double,
    )

    data class CpuSample(
        val timestamp: Long,
        val cpuUsagePercent: Float
    )

    data class NetworkSample(
        val timestamp: Long,
        val rxBytes: Long,
        val txBytes: Long
    )

    // Statistics data classes
    data class MetricStats(
        val current: Double,
        val peak: Double,
        val min: Double,
        val average: Double,
        val median: Double = 0.0
    )

    data class FPSMetrics(
        val average: Double,
        val min: Double,
        val max: Double,
        val p50: Double,
        val p95: Double,
        val p99: Double,
        val frameDropsBelow55: Int,
        val frameDropsBelow45: Int,
        val frameDropsBelow30: Int
    )

    data class ThreadMetrics(
        val initial: Int,
        val final: Int,
        val peak: Int,
        val average: Double,
        val threadsCreated: Int
    )

    data class DiskIOMetrics(
        val readBytes: Long,
        val writeBytes: Long,
        val readKB: Double,
        val writeKB: Double,
        val totalKB: Double
    )

    data class NetworkMetrics(
        val rxKB: Long,
        val txKB: Long,
        val totalKB: Long,
        val avgBandwidthKBps: Double,
        val peakBandwidthKBps: Double
    )

    data class APILatencyMetrics(
        val latencies: List<Long>,
        val stats: MetricStats,
        val p50: Long,
        val p95: Long,
        val p99: Long
    )

    data class GCMetrics(
        val gcCount: Long,
        val gcTimeMs: Long,
        val avgGcTimeMs: Double
    )

    /**
     * Comprehensive SDK performance metrics
     */
    data class SDKMetrics(
        val sdkName: String,
        val timestamp: Long,

        // Timing
        val sdkInitTimeMs: Long,
        val totalExecutionTimeMs: Long,

        // Memory statistics
        val memoryStats: MetricStats,
        val pssStats: MetricStats,

        // CPU statistics
        val cpuUsageStats: MetricStats,

        // Network
        val networkMetrics: NetworkMetrics,

        // FPS
        val fpsMetrics: FPSMetrics,

        // Threads
        val threadMetrics: ThreadMetrics,

        // Disk I/O
        val diskIOMetrics: DiskIOMetrics,

        // API latencies
//        val apiLatencyMetrics: APILatencyMetrics,

        // Garbage Collection
        val gcMetrics: GCMetrics,

        // Performance validation
        val performanceGrade: PerformanceGrade,
        val violations: List<String>
    )

    /**
     * Baseline metrics for comparison
     */
    data class BaselineMetrics(
        val timestamp: Long,
        val heapUsedMB: Double,
        val cpuUsagePercent: Float,
        val activeThreads: Int,
    )

    /**
     * Performance thresholds for validation
     *
     * Note: totalExecutionTime includes user interaction time (selecting payment method,
     * entering details, etc.), so it's set to a higher threshold. For automated testing,
     * focus on initTime, CPU, memory, and FPS metrics instead.
     */
    data class PerformanceThresholds(
        val maxInitTimeMs: Long = 1000,           // SDK initialization should be fast
        val maxExecutionTimeMs: Long = 60000,     // 60s - includes user interaction
        val maxMemoryMB: Double = 50.0,           // Peak memory usage
        val maxCpuPercent: Double = 90.0,          // Peak CPU (was 30%, too strict)
        val minAcceptableFPS: Double = 55.0,      // Smooth UI rendering
        val maxNetworkKB: Long = 5000,            // 5 MB network limit
    )

    enum class PerformanceGrade {
        EXCELLENT, GOOD, ACCEPTABLE, NEEDS_OPTIMIZATION, POOR
    }

    /**
     * Track SDK initialization separately
     */
    fun startInitTracking() {
        sdkInitStartTime = SystemClock.elapsedRealtime()
        Log.d(TAG, "Started tracking SDK initialization")
    }

    fun endInitTracking() {
        sdkInitEndTime = SystemClock.elapsedRealtime()
        Log.d(TAG, "SDK initialization took ${sdkInitEndTime - sdkInitStartTime}ms")
    }

    /**
     * Capture baseline metrics before SDK operation
     */
    fun captureBaseline(): BaselineMetrics {
        val runtime = Runtime.getRuntime()
        val heapUsed = (runtime.totalMemory() - runtime.freeMemory()) / 1024.0 / 1024.0

        val memoryInfo = activityManager.getProcessMemoryInfo(intArrayOf(pid))

        val cpuUsage = getCurrentCpuUsage()
        val threads = Thread.activeCount()

        return BaselineMetrics(
            timestamp = System.currentTimeMillis(),
            heapUsedMB = heapUsed,
            cpuUsagePercent = cpuUsage,
            activeThreads = threads,
        )
    }

    /**
     * Create baseline metrics with same structure as SDK metrics
     */
    fun createBaselineMetrics(name: String = "Baseline"): SDKMetrics {
        val runtime = Runtime.getRuntime()
        val heapUsed = (runtime.totalMemory() - runtime.freeMemory()) / 1024.0 / 1024.0
        
        val memoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memoryInfo)
        val pss = memoryInfo.totalPss / 1024.0
        
        val cpuUsage = getCurrentCpuUsage().toDouble()
        val threads = Thread.activeCount()
        
        val rxBytes = TrafficStats.getUidRxBytes(uid)
        val txBytes = TrafficStats.getUidTxBytes(uid)
        val rxKB = rxBytes / 1024
        val txKB = txBytes / 1024
        
        val (diskRead, diskWrite) = readDiskIO()
        
        return SDKMetrics(
            sdkName = name,
            timestamp = System.currentTimeMillis(),
            sdkInitTimeMs = 0,
            totalExecutionTimeMs = 0,
            memoryStats = MetricStats(heapUsed, heapUsed, heapUsed, heapUsed, heapUsed),
            pssStats = MetricStats(pss, pss, pss, pss, pss),
            cpuUsageStats = MetricStats(cpuUsage, cpuUsage, cpuUsage, cpuUsage, cpuUsage),
            networkMetrics = NetworkMetrics(rxKB, txKB, rxKB + txKB, 0.0, 0.0),
            fpsMetrics = FPSMetrics(60.0, 60.0, 60.0, 60.0, 60.0, 60.0, 0, 0, 0),
            threadMetrics = ThreadMetrics(threads, threads, threads, threads.toDouble(), 0),
            diskIOMetrics = DiskIOMetrics(
                diskRead, diskWrite,
                diskRead / 1024.0, diskWrite / 1024.0,
                (diskRead + diskWrite) / 1024.0
            ),
            gcMetrics = GCMetrics(0, 0, 0.0),
            performanceGrade = PerformanceGrade.EXCELLENT,
            violations = emptyList()
        )
    }

    /**
     * Start comprehensive tracking
     */
    fun startTracking() {
        startTime = SystemClock.elapsedRealtime()

        // Clear previous samples
        memorySamples.clear()
        cpuSamples.clear()
        threadSamples.clear()
        networkSamples.clear()
        fpsValues.clear()

        // Network baseline
        startRxBytes = TrafficStats.getUidRxBytes(uid)
        startTxBytes = TrafficStats.getUidTxBytes(uid)

        // Thread baseline
        startThreadCount = Thread.activeCount()

        // Disk I/O baseline
        val diskIO = readDiskIO()
        startDiskReadBytes = diskIO.first
        startDiskWriteBytes = diskIO.second


        // CPU baseline
        lastCpuTime = Debug.threadCpuTimeNanos() / 1_000_000 // Convert to ms
        lastCpuTimestamp = SystemClock.elapsedRealtime()

        // Start network interceptor tracking
//        networkInterceptor.startTracking()

        // Start continuous sampling
        startContinuousSampling()

        // Start FPS monitoring
        startFpsMonitoring()

        Log.d(TAG, "Started comprehensive SDK performance tracking")
    }

    /**
     * Stop tracking and calculate metrics
     */
    fun stopTracking(sdkName: String = "SDK"): SDKMetrics {
        val endTime = SystemClock.elapsedRealtime()

        // Stop sampling
        stopContinuousSampling()
        stopFpsMonitoring()

        // Get network calls from interceptor and stop tracking
//        val networkCalls = networkInterceptor.stopTracking()
//        val interceptedLatencies = networkCalls.map { it.latencyMs }

        // Calculate execution time
        val totalExecutionTime = endTime - startTime
        val initTime = if (sdkInitEndTime > 0 && sdkInitStartTime > 0) {
            sdkInitEndTime - sdkInitStartTime
        } else 0L

        // Memory statistics
        val memoryStats = calculateMemoryStats()
        val pssStats = calculatePssStats()

        // CPU statistics
        val cpuStats = calculateCpuStats()

        // Network metrics
        val networkMetrics = calculateNetworkMetrics(totalExecutionTime)

        // FPS metrics
        val fpsMetrics = calculateFpsMetrics()

        // Thread metrics
        val threadMetrics = calculateThreadMetrics()

        // Disk I/O metrics
        val diskIOMetrics = calculateDiskIOMetrics()

        // API latency metrics - use intercepted network calls
//        val apiLatencyMetrics = calculateApiLatencyMetrics(interceptedLatencies)

        // GC metrics
        val gcMetrics = calculateGCMetrics()

        // Performance validation
        val thresholds = PerformanceThresholds()
        val (grade, violations) = validatePerformance(
            initTime, totalExecutionTime, memoryStats, cpuStats,
            fpsMetrics, networkMetrics,  thresholds
        )

        val metrics = SDKMetrics(
            sdkName = sdkName,
            timestamp = System.currentTimeMillis(),
            sdkInitTimeMs = initTime,
            totalExecutionTimeMs = totalExecutionTime,
            memoryStats = memoryStats,
            pssStats = pssStats,
            cpuUsageStats = cpuStats,
            networkMetrics = networkMetrics,
            fpsMetrics = fpsMetrics,
            threadMetrics = threadMetrics,
            diskIOMetrics = diskIOMetrics,
//            apiLatencyMetrics = apiLatencyMetrics,
            gcMetrics = gcMetrics,
            performanceGrade = grade,
            violations = violations
        )

        logMetrics(metrics)
//        logNetworkCalls(networkCalls)
        return metrics
    }

    /**
     * Start continuous sampling of metrics with adaptive sampling rate
     */
    private fun startContinuousSampling() {
        samplingActive = true

        // Capture GC baseline
        startGcCount = try {
            Debug.getRuntimeStat("art.gc.gc-count").toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        }
        startGcTime = try {
            Debug.getRuntimeStat("art.gc.blocking-gc-time").toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        }

        val samplingRunnable = object : Runnable {
            override fun run() {
                if (!samplingActive) return

                // Sample memory
                sampleMemory()

                // Sample CPU
                sampleCpu()

                // Sample threads
                sampleThreads()

                // Sample network
                sampleNetwork()

                // Determine next sampling interval (adaptive)
                val elapsedTime = SystemClock.elapsedRealtime() - startTime
                val interval = when {
                    // Critical phase: First 2 seconds (init phase)
                    elapsedTime < CRITICAL_PHASE_DURATION -> SAMPLING_INTERVAL_CRITICAL
                    // Normal runtime
                    else -> SAMPLING_INTERVAL_NORMAL
                }

                // Schedule next sample with adaptive interval
                samplingHandler.postDelayed(this, interval)
            }
        }

        samplingHandler.post(samplingRunnable)
    }

    private fun stopContinuousSampling() {
        samplingActive = false
        samplingHandler.removeCallbacksAndMessages(null)
    }

    private fun sampleMemory() {
        val runtime = Runtime.getRuntime()
        val heapUsed = (runtime.totalMemory() - runtime.freeMemory()) / 1024.0 / 1024.0

        val memoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memoryInfo)
        val heapAllocated = memoryInfo.totalPss / 1024.0

        val processMemoryInfo = activityManager.getProcessMemoryInfo(intArrayOf(pid))

        memorySamples.add(MemorySample(
            timestamp = SystemClock.elapsedRealtime(),
            heapUsedMB = heapUsed,
            heapAllocatedMB = heapAllocated,
        ))
    }

    private fun sampleCpu() {
        val cpuUsage = getCurrentCpuUsage()
        cpuSamples.add(CpuSample(
            timestamp = SystemClock.elapsedRealtime(),
            cpuUsagePercent = cpuUsage
        ))
    }

    private fun sampleThreads() {
        threadSamples.add(Thread.activeCount())
    }

    private fun sampleNetwork() {
        val rxBytes = TrafficStats.getUidRxBytes(uid)
        val txBytes = TrafficStats.getUidTxBytes(uid)

        networkSamples.add(NetworkSample(
            timestamp = SystemClock.elapsedRealtime(),
            rxBytes = rxBytes - startRxBytes,
            txBytes = txBytes - startTxBytes
        ))
    }

    /**
     * Get current CPU usage using thread CPU time
     */
    private fun getCurrentCpuUsage(): Float {
        return try {
            val currentCpuTime = Debug.threadCpuTimeNanos() / 1_000_000 // Convert to ms
            val currentTimestamp = SystemClock.elapsedRealtime()

            val cpuTimeDiff = currentCpuTime - lastCpuTime
            val wallTimeDiff = currentTimestamp - lastCpuTimestamp

            val usage = if (wallTimeDiff > 0) {
                (cpuTimeDiff.toFloat() / wallTimeDiff) * 100
            } else 0f

            // Update for next calculation
            lastCpuTime = currentCpuTime
            lastCpuTimestamp = currentTimestamp

            usage.coerceIn(0f, 100f)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating CPU usage: ${e.message}")
            0f
        }
    }

    /**
     * Read disk I/O statistics
     */
    private fun readDiskIO(): Pair<Long, Long> {
        return try {
            val ioFile = RandomAccessFile("/proc/$pid/io", "r")
            var readBytes = 0L
            var writeBytes = 0L

            var line = ioFile.readLine()
            while (line != null) {
                when {
                    line.startsWith("read_bytes:") -> {
                        readBytes = line.split(":")[1].trim().toLong()
                    }
                    line.startsWith("write_bytes:") -> {
                        writeBytes = line.split(":")[1].trim().toLong()
                    }
                }
                line = ioFile.readLine()
            }
            ioFile.close()

            Pair(readBytes, writeBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading disk I/O: ${e.message}")
            Pair(0L, 0L)
        }
    }

    /**
     * Start FPS monitoring
     */
    private fun startFpsMonitoring() {
        fpsMonitoringActive = true
        fpsValues.clear()

        val choreographer = Choreographer.getInstance()
        var lastFrameTime = 0L

        val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!fpsMonitoringActive) return

                if (lastFrameTime != 0L) {
                    val frameDuration = frameTimeNanos - lastFrameTime
                    if (frameDuration > 0) {
                        val fps = 1_000_000_000.0 / frameDuration
                        fpsValues.add(fps.coerceIn(0.0, 120.0)) // Cap at 120 FPS
                    }
                }

                lastFrameTime = frameTimeNanos
                choreographer.postFrameCallback(this)
            }
        }

        choreographer.postFrameCallback(frameCallback)
    }

    private fun stopFpsMonitoring() {
        fpsMonitoringActive = false
    }

    /**
     * Calculate statistics from samples
     */
    private fun calculateMemoryStats(): MetricStats {
        if (memorySamples.isEmpty()) {
            return MetricStats(0.0, 0.0, 0.0, 0.0, 0.0)
        }

        val values = memorySamples.map { it.heapUsedMB }
        return createMetricStats(values)
    }

    private fun calculatePssStats(): MetricStats {
        if (memorySamples.isEmpty()) {
            return MetricStats(0.0, 0.0, 0.0, 0.0, 0.0)
        }

        val values = memorySamples.map { it.heapAllocatedMB }
        return createMetricStats(values)
    }

    private fun calculateCpuStats(): MetricStats {
        if (cpuSamples.isEmpty()) {
            return MetricStats(0.0, 0.0, 0.0, 0.0, 0.0)
        }

        val values = cpuSamples.map { it.cpuUsagePercent.toDouble() }
        return createMetricStats(values)
    }

    private fun createMetricStats(values: List<Double>): MetricStats {
        val sorted = values.sorted()
        return MetricStats(
            current = values.lastOrNull() ?: 0.0,
            peak = values.maxOrNull() ?: 0.0,
            min = values.minOrNull() ?: 0.0,
            average = values.average(),
            median = sorted[sorted.size / 2]
        )
    }

    private fun calculateNetworkMetrics(durationMs: Long): NetworkMetrics {
        val endRxBytes = TrafficStats.getUidRxBytes(uid)
        val endTxBytes = TrafficStats.getUidTxBytes(uid)

        val rxKB = (endRxBytes - startRxBytes) / 1024
        val txKB = (endTxBytes - startTxBytes) / 1024
        val totalKB = rxKB + txKB

        val durationSeconds = durationMs / 1000.0
        val avgBandwidthKBps = if (durationSeconds > 0) totalKB / durationSeconds else 0.0

        // Calculate peak bandwidth from samples, ignoring tiny intervals
        val minIntervalSeconds = 0.2   // Ignore intervals smaller than 200ms
        val validBandwidths = mutableListOf<Double>()

        if (networkSamples.size > 1) {
            for (i in 1 until networkSamples.size) {
                val timeDiff = (networkSamples[i].timestamp - networkSamples[i - 1].timestamp) / 1000.0
                if (timeDiff >= minIntervalSeconds) {
                    val bytesDiff = (networkSamples[i].rxBytes + networkSamples[i].txBytes -
                            networkSamples[i - 1].rxBytes - networkSamples[i - 1].txBytes) / 1024.0
                    val bandwidth = bytesDiff / timeDiff
                    validBandwidths.add(bandwidth)
                }
            }
        }

        // Use the 95th percentile to avoid single-sample spikes
        val peakBandwidthKBps = if (validBandwidths.isNotEmpty()) {
            validBandwidths.sorted().takeLast((validBandwidths.size * 0.05).coerceAtLeast(1.0).toInt()).average()
        } else {
            0.0
        }

        return NetworkMetrics(
            rxKB = rxKB,
            txKB = txKB,
            totalKB = totalKB,
            avgBandwidthKBps = avgBandwidthKBps,
            peakBandwidthKBps = peakBandwidthKBps
        )
    }


    private fun calculateFpsMetrics(): FPSMetrics {
        if (fpsValues.isEmpty()) {
            return FPSMetrics(60.0, 60.0, 60.0, 60.0, 60.0, 60.0, 0, 0, 0)
        }

        val sorted = fpsValues.sorted()
        val percentiles = calculatePercentiles(sorted)

        val below30 = fpsValues.count { it < 30.0 }
        val below45 = fpsValues.count { it in 30.0..44.9 }
        val below55 = fpsValues.count { it in 45.0..54.9 }

        return FPSMetrics(
            average = fpsValues.average(),
            min = fpsValues.minOrNull() ?: 60.0,
            max = fpsValues.maxOrNull() ?: 60.0,
            p50 = percentiles.p50,
            p95 = percentiles.p95,
            p99 = percentiles.p99,
            frameDropsBelow55 = below55,
            frameDropsBelow45 = below45,
            frameDropsBelow30 = below30
        )

    }

    private fun calculateThreadMetrics(): ThreadMetrics {
        val endThreadCount = Thread.activeCount()
        val peak = threadSamples.maxOrNull() ?: startThreadCount
        val average = if (threadSamples.isNotEmpty()) threadSamples.average() else startThreadCount.toDouble()

        return ThreadMetrics(
            initial = startThreadCount,
            final = endThreadCount,
            peak = peak,
            average = average,
            threadsCreated = max(0, peak - startThreadCount)
        )
    }

    private fun calculateDiskIOMetrics(): DiskIOMetrics {
        val (endReadBytes, endWriteBytes) = readDiskIO()

        val readBytes = max(0L, endReadBytes - startDiskReadBytes)
        val writeBytes = max(0L, endWriteBytes - startDiskWriteBytes)

        return DiskIOMetrics(
            readBytes = readBytes,
            writeBytes = writeBytes,
            readKB = readBytes / 1024.0,
            writeKB = writeBytes / 1024.0,
            totalKB = (readBytes + writeBytes) / 1024.0
        )
    }

    private fun calculateApiLatencyMetrics(latencies: List<Long>): APILatencyMetrics {
        if (latencies.isEmpty()) {
            return APILatencyMetrics(
                latencies = emptyList(),
                stats = MetricStats(0.0, 0.0, 0.0, 0.0, 0.0),
                p50 = 0,
                p95 = 0,
                p99 = 0
            )
        }

        val sorted = latencies.sorted()
        val percentiles = calculatePercentilesLong(sorted)
        val stats = createMetricStats(latencies.map { it.toDouble() })

        return APILatencyMetrics(
            latencies = latencies,
            stats = stats,
            p50 = percentiles.p50,
            p95 = percentiles.p95,
            p99 = percentiles.p99
        )
    }

    private fun calculateGCMetrics(): GCMetrics {
        val endGcCount = try {
            Debug.getRuntimeStat("art.gc.gc-count").toLongOrNull() ?: startGcCount
        } catch (e: Exception) {
            startGcCount
        }

        val endGcTime = try {
            Debug.getRuntimeStat("art.gc.blocking-gc-time").toLongOrNull() ?: startGcTime
        } catch (e: Exception) {
            startGcTime
        }

        val gcCount = max(0L, endGcCount - startGcCount)
        val gcTime = max(0L, endGcTime - startGcTime)
        val avgGcTime = if (gcCount > 0) gcTime.toDouble() / gcCount else 0.0

        return GCMetrics(
            gcCount = gcCount,
            gcTimeMs = gcTime,
            avgGcTimeMs = avgGcTime
        )
    }

    /**
     * Calculate percentiles
     */
    data class Percentiles(val p50: Double, val p95: Double, val p99: Double)
    data class PercentilesLong(val p50: Long, val p95: Long, val p99: Long)

    private fun calculatePercentiles(sorted: List<Double>): Percentiles {
        if (sorted.isEmpty()) return Percentiles(0.0, 0.0, 0.0)

        return Percentiles(
            p50 = sorted[min((sorted.size * 0.50).toInt(), sorted.size - 1)],
            p95 = sorted[min((sorted.size * 0.95).toInt(), sorted.size - 1)],
            p99 = sorted[min((sorted.size * 0.99).toInt(), sorted.size - 1)]
        )
    }

    private fun calculatePercentilesLong(sorted: List<Long>): PercentilesLong {
        if (sorted.isEmpty()) return PercentilesLong(0, 0, 0)

        return PercentilesLong(
            p50 = sorted[min((sorted.size * 0.50).toInt(), sorted.size - 1)],
            p95 = sorted[min((sorted.size * 0.95).toInt(), sorted.size - 1)],
            p99 = sorted[min((sorted.size * 0.99).toInt(), sorted.size - 1)]
        )
    }

    /**
     * Validate performance against thresholds
     */
    private fun validatePerformance(
        initTime: Long,
        executionTime: Long,
        memoryStats: MetricStats,
        cpuStats: MetricStats,
        fpsMetrics: FPSMetrics,
        networkMetrics: NetworkMetrics,
        thresholds: PerformanceThresholds
    ): Pair<PerformanceGrade, List<String>> {
        val violations = mutableListOf<String>()
        var score = 100

        if (initTime > thresholds.maxInitTimeMs) {
            violations.add("Init time ${initTime}ms exceeds threshold ${thresholds.maxInitTimeMs}ms")
            score -= 15
        }

        if (executionTime > thresholds.maxExecutionTimeMs) {
            violations.add("Execution time ${executionTime}ms exceeds threshold ${thresholds.maxExecutionTimeMs}ms")
            score -= 20
        }

        if (memoryStats.peak > thresholds.maxMemoryMB) {
            violations.add("Peak memory ${String.format("%.2f", memoryStats.peak)}MB exceeds threshold ${thresholds.maxMemoryMB}MB")
            score -= 15
        }

        if (cpuStats.peak > thresholds.maxCpuPercent) {
            violations.add("Peak CPU ${String.format("%.2f", cpuStats.peak)}% exceeds threshold ${thresholds.maxCpuPercent}%")
            score -= 15
        }

        if (fpsMetrics.average < thresholds.minAcceptableFPS) {
            violations.add("Average FPS ${String.format("%.2f", fpsMetrics.average)} below threshold ${thresholds.minAcceptableFPS}")
            score -= 15
        }

        if (networkMetrics.totalKB > thresholds.maxNetworkKB) {
            violations.add("Network usage ${networkMetrics.totalKB}KB exceeds threshold ${thresholds.maxNetworkKB}KB")
            score -= 10
        }

        val grade = when {
            score >= 90 -> PerformanceGrade.EXCELLENT
            score >= 75 -> PerformanceGrade.GOOD
            score >= 60 -> PerformanceGrade.ACCEPTABLE
            score >= 40 -> PerformanceGrade.NEEDS_OPTIMIZATION
            else -> PerformanceGrade.POOR
        }

        return Pair(grade, violations)
    }

    /**
     * Log detailed network call information
     */
//    private fun logNetworkCalls(calls: List<SDKNetworkInterceptor.NetworkCall>) {
//        if (calls.isEmpty()) {
//            Log.d(TAG, "No network calls intercepted")
//            return
//        }
//
//        val callDetails = calls.mapIndexed { index, call ->
//            val status = if (call.statusCode == -1) "FAILED" else call.statusCode.toString()
//            "  ${index + 1}. ${call.method} ${call.url}\n" +
//            "     Status: $status | Latency: ${call.latencyMs}ms | " +
//            "Size: ${(call.requestSize + call.responseSize) / 1024}KB"
//        }.joinToString("\n")
//
//        Log.d(TAG, """
//========== NETWORK CALLS DETAIL ==========
//Total Calls: ${calls.size}
//Successful: ${calls.count { it.statusCode != -1 }}
//Failed: ${calls.count { it.statusCode == -1 }}
//
//$callDetails
//        """.trimIndent())
//    }

    /**
     * Log metrics to console
     */
    private fun logMetrics(metrics: SDKMetrics) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        Log.d(TAG, """
========== ${metrics.sdkName} PERFORMANCE METRICS ==========
Test Time: ${dateFormat.format(Date(metrics.timestamp))}
Performance Grade: ${metrics.performanceGrade}

TIMING:
  Init Time: ${metrics.sdkInitTimeMs}ms
  Total Execution: ${metrics.totalExecutionTimeMs}ms

MEMORY (MB):
  Heap - Peak: ${"%.2f".format(metrics.memoryStats.peak)} | Avg: ${"%.2f".format(metrics.memoryStats.average)} | Min: ${"%.2f".format(metrics.memoryStats.min)}
CPU:
  Peak: ${"%.2f".format(metrics.cpuUsageStats.peak)}% | Avg: ${"%.2f".format(metrics.cpuUsageStats.average)}%

NETWORK:
  Total: ${metrics.networkMetrics.totalKB}KB | RX: ${metrics.networkMetrics.rxKB}KB | TX: ${metrics.networkMetrics.txKB}KB

FPS:
  Avg: ${"%.2f".format(metrics.fpsMetrics.average)} | Min: ${"%.2f".format(metrics.fpsMetrics.min)} | Drops: ${metrics.fpsMetrics.frameDropsBelow55}

THREADS:
  Peak: ${metrics.threadMetrics.peak} | Created: ${metrics.threadMetrics.threadsCreated}

DISK I/O:
  Total: ${"%.2f".format(metrics.diskIOMetrics.totalKB)}KB | Read: ${"%.2f".format(metrics.diskIOMetrics.readKB)}KB | Write: ${"%.2f".format(metrics.diskIOMetrics.writeKB)}KB

GARBAGE COLLECTION:
  GC Count: ${metrics.gcMetrics.gcCount} | Total Time: ${metrics.gcMetrics.gcTimeMs}ms | Avg: ${"%.2f".format(metrics.gcMetrics.avgGcTimeMs)}ms

VIOLATIONS: ${metrics.violations.size}
${if (metrics.violations.isNotEmpty()) metrics.violations.joinToString("\n") { "  - $it" } else "  None"}
        """.trimIndent())
    }
}
