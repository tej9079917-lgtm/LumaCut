package com.example.media

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.example.data.AppDatabase
import com.example.data.LocalMediaStorage
import com.example.data.RenderJobEntity
import com.example.data.RenderJobStatus
import com.example.data.StudioRepository
import com.example.model.AspectRatio
import com.example.model.ExportConfig
import com.example.model.ExportResolution
import com.example.model.ExportState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume

data class RenderQueueBannerState(
    val isRunning: Boolean = false,
    val activeJobId: String? = null,
    val activeTitle: String = "",
    val progress: Float = 0f,
    val stageMessage: String = "",
    val queuedCount: Int = 0
)

/**
 * Application-scoped FIFO Background Rendering Queue coordinator.
 * Runs sequential Media3 Transformer exports off the main UI lifecycle so users can queue
 * multiple projects, switch projects, or keep editing smoothly while renders finish in the background.
 */
@OptIn(UnstableApi::class)
class RenderQueueManager private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = AppDatabase.getInstance(appContext)
    private val dao = db.renderJobDao()
    private val repository = StudioRepository(
        projectDao = db.projectDao(),
        customFontDao = db.customFontDao(),
        mediaStorage = LocalMediaStorage(appContext)
    )
    private val exportEngine = Media3ExportEngine(appContext)

    val jobs: StateFlow<List<RenderJobEntity>> = dao.observeAllJobs()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _bannerState = MutableStateFlow(RenderQueueBannerState())
    val bannerState: StateFlow<RenderQueueBannerState> = _bannerState.asStateFlow()

    @Volatile
    private var processorJob: Job? = null

    @Volatile
    private var activeRenderJobId: String? = null

    @Volatile
    private var cancelRequestedJobId: String? = null

    init {
        scope.launch {
            dao.resetStaleRenderingJobs(System.currentTimeMillis())
            kickQueueIfNeeded()
        }
    }

    fun enqueueProject(
        projectId: String,
        projectTitle: String,
        aspectRatio: AspectRatio,
        config: ExportConfig = ExportConfig()
    ) {
        scope.launch {
            val now = System.currentTimeMillis()
            val entity = RenderJobEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                projectTitle = projectTitle.ifBlank { "Untitled Edit" },
                aspectRatioName = aspectRatio.name,
                resolutionName = config.resolution.name,
                frameRate = config.fps,
                statusName = RenderJobStatus.QUEUED.name,
                progress = 0f,
                stageMessage = "Queued • ${config.resolution.label} ${config.fps}fps",
                outputUri = null,
                outputFileName = null,
                fileSizeBytes = 0L,
                errorMessage = null,
                createdAtMs = now,
                updatedAtMs = now
            )
            dao.upsertJob(entity)
            kickQueueIfNeeded()
        }
    }

    fun cancelJob(jobId: String) {
        scope.launch {
            val existing = dao.getJobById(jobId) ?: return@launch
            when (existing.status) {
                RenderJobStatus.QUEUED -> {
                    dao.updateJobResult(
                        id = jobId,
                        statusName = RenderJobStatus.CANCELLED.name,
                        progress = 0f,
                        stage = "Cancelled",
                        outputUri = null,
                        outputFileName = null,
                        fileSizeBytes = 0L,
                        errorMessage = null,
                        nowMs = System.currentTimeMillis()
                    )
                    updateBannerCounts()
                }
                RenderJobStatus.RENDERING -> {
                    cancelRequestedJobId = jobId
                    exportEngine.cancelExport()
                    dao.updateJobResult(
                        id = jobId,
                        statusName = RenderJobStatus.CANCELLED.name,
                        progress = existing.progress,
                        stage = "Cancelled by user",
                        outputUri = null,
                        outputFileName = null,
                        fileSizeBytes = 0L,
                        errorMessage = null,
                        nowMs = System.currentTimeMillis()
                    )
                    updateBannerCounts()
                }
                else -> Unit
            }
        }
    }

    fun cancelActiveRender() {
        val currentId = activeRenderJobId ?: return
        cancelJob(currentId)
    }

    fun retryJob(jobId: String) {
        scope.launch {
            val existing = dao.getJobById(jobId) ?: return@launch
            dao.upsertJob(
                existing.copy(
                    statusName = RenderJobStatus.QUEUED.name,
                    progress = 0f,
                    stageMessage = "Re-queued for export",
                    errorMessage = null,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
            kickQueueIfNeeded()
        }
    }

    fun removeJob(jobId: String) {
        scope.launch {
            if (activeRenderJobId == jobId) {
                cancelJob(jobId)
            }
            dao.deleteJobById(jobId)
            updateBannerCounts()
        }
    }

    fun clearFinished() {
        scope.launch {
            dao.clearFinishedJobs()
            updateBannerCounts()
        }
    }

    private fun kickQueueIfNeeded() {
        if (processorJob?.isActive == true) {
            updateBannerCounts()
            return
        }
        processorJob = scope.launch {
            try {
                while (true) {
                    val next = dao.getNextQueuedJob() ?: break
                    processSingleJob(next)
                }
            } finally {
                activeRenderJobId = null
                _bannerState.value = RenderQueueBannerState(isRunning = false)
                stopForegroundRenderService()
            }
        }
    }

    private suspend fun processSingleJob(job: RenderJobEntity) {
        activeRenderJobId = job.id
        cancelRequestedJobId = null
        val now = System.currentTimeMillis()
        dao.updateJobResult(
            id = job.id,
            statusName = RenderJobStatus.RENDERING.name,
            progress = 0.02f,
            stage = "Loading project timeline…",
            outputUri = null,
            outputFileName = null,
            fileSizeBytes = 0L,
            errorMessage = null,
            nowMs = now
        )
        startOrUpdateForegroundService(job.projectTitle, 0.02f, "Loading project timeline…")

        val loadedProject = repository.loadProject(job.projectId)
        if (loadedProject == null) {
            dao.updateJobResult(
                id = job.id,
                statusName = RenderJobStatus.FAILED.name,
                progress = 0f,
                stage = "Failed",
                outputUri = null,
                outputFileName = null,
                fileSizeBytes = 0L,
                errorMessage = "Project no longer exists.",
                nowMs = System.currentTimeMillis()
            )
            return
        }

        if (loadedProject.timeline.mainClips.isEmpty()) {
            dao.updateJobResult(
                id = job.id,
                statusName = RenderJobStatus.FAILED.name,
                progress = 0f,
                stage = "Failed",
                outputUri = null,
                outputFileName = null,
                fileSizeBytes = 0L,
                errorMessage = "Project has no video or photo clips on the timeline.",
                nowMs = System.currentTimeMillis()
            )
            return
        }

        val resolution = ExportResolution.entries.firstOrNull { it.name == job.resolutionName }
            ?: ExportResolution.FHD_1080P
        val config = ExportConfig(
            resolution = resolution,
            fps = job.frameRate,
            bitrateMbps = resolution.recommendedBitrateMbps
        )
        val fonts = repository.allFonts.first()

        val finalState = try {
            runExportSuspend(
                projectTitle = loadedProject.summary.title,
                aspectRatio = loadedProject.summary.aspectRatio,
                timeline = loadedProject.timeline,
                fonts = fonts,
                config = config,
                onProgress = { progress, stage ->
                    if (cancelRequestedJobId == job.id) return@runExportSuspend
                    scope.launch {
                        dao.updateProgress(job.id, progress, stage, System.currentTimeMillis())
                    }
                    startOrUpdateForegroundService(loadedProject.summary.title, progress, stage)
                }
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            ExportState.Error(e.localizedMessage ?: "Background export failed.")
        }

        if (cancelRequestedJobId == job.id) {
            return
        }

        when (finalState) {
            is ExportState.Success -> {
                dao.updateJobResult(
                    id = job.id,
                    statusName = RenderJobStatus.COMPLETED.name,
                    progress = 1f,
                    stage = "Saved to Gallery (${finalState.displayName})",
                    outputUri = finalState.contentUri,
                    outputFileName = finalState.displayName,
                    fileSizeBytes = (finalState.sizeMb * 1024f * 1024f).toLong(),
                    errorMessage = null,
                    nowMs = System.currentTimeMillis()
                )
            }
            is ExportState.Error -> {
                dao.updateJobResult(
                    id = job.id,
                    statusName = RenderJobStatus.FAILED.name,
                    progress = 0f,
                    stage = "Export failed",
                    outputUri = null,
                    outputFileName = null,
                    fileSizeBytes = 0L,
                    errorMessage = finalState.message,
                    nowMs = System.currentTimeMillis()
                )
            }
            else -> {
                dao.updateJobResult(
                    id = job.id,
                    statusName = RenderJobStatus.CANCELLED.name,
                    progress = 0f,
                    stage = "Cancelled",
                    outputUri = null,
                    outputFileName = null,
                    fileSizeBytes = 0L,
                    errorMessage = null,
                    nowMs = System.currentTimeMillis()
                )
            }
        }
    }

    private suspend fun runExportSuspend(
        projectTitle: String,
        aspectRatio: AspectRatio,
        timeline: com.example.model.TimelineDocument,
        fonts: List<com.example.model.StudioFont>,
        config: ExportConfig,
        onProgress: (Float, String) -> Unit
    ): ExportState = suspendCancellableCoroutine { cont ->
        exportEngine.startExport(
            scope = scope,
            projectTitle = projectTitle,
            aspectRatio = aspectRatio,
            timeline = timeline,
            availableFonts = fonts,
            config = config
        ) { state ->
            when (state) {
                is ExportState.Exporting -> onProgress(
                    (state.progressPercent / 100f).coerceIn(0f, 1f),
                    state.stageLabel
                )
                is ExportState.Success, is ExportState.Error -> {
                    if (cont.isActive) cont.resume(state)
                }
                ExportState.Idle -> {
                    if (cont.isActive && cancelRequestedJobId != null) {
                        cont.resume(ExportState.Idle)
                    }
                }
            }
        }
        cont.invokeOnCancellation {
            exportEngine.cancelExport()
        }
    }

    private fun startOrUpdateForegroundService(title: String, progress: Float, stage: String) {
        val all = jobs.value
        val remainingQueued = all.count { it.status == RenderJobStatus.QUEUED }
        _bannerState.value = RenderQueueBannerState(
            isRunning = true,
            activeJobId = activeRenderJobId,
            activeTitle = title,
            progress = progress,
            stageMessage = stage,
            queuedCount = remainingQueued
        )
        runCatching {
            val intent = Intent(appContext, BackgroundRenderService::class.java).apply {
                action = BackgroundRenderService.ACTION_UPDATE
                putExtra(BackgroundRenderService.EXTRA_TITLE, title)
                putExtra(BackgroundRenderService.EXTRA_PROGRESS, (progress * 100f).toInt().coerceIn(0, 100))
                putExtra(BackgroundRenderService.EXTRA_STAGE, stage)
                putExtra(BackgroundRenderService.EXTRA_QUEUED_COUNT, remainingQueued)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        }
    }

    private fun stopForegroundRenderService() {
        runCatching {
            val intent = Intent(appContext, BackgroundRenderService::class.java).apply {
                action = BackgroundRenderService.ACTION_STOP
            }
            appContext.startService(intent)
        }
    }

    private fun updateBannerCounts() {
        val current = _bannerState.value
        val queued = jobs.value.count { it.status == RenderJobStatus.QUEUED }
        _bannerState.value = current.copy(queuedCount = queued)
    }

    companion object {
        @Volatile
        private var INSTANCE: RenderQueueManager? = null

        fun getInstance(context: Context): RenderQueueManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: RenderQueueManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}
