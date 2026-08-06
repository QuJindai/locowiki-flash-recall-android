package com.qujindai.locowiki.flashrecall.v2

import android.app.Application
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.speech.SpeechRecognizer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qujindai.locowiki.flashrecall.v2.audio.SelfEnrollmentAudioRecorder
import com.qujindai.locowiki.flashrecall.v2.audio.SherpaAudioEngine
import com.qujindai.locowiki.flashrecall.v2.data.FactRepository
import com.qujindai.locowiki.flashrecall.v2.domain.*
import com.qujindai.locowiki.flashrecall.v2.meeting.MeetingArchiveRepository
import com.qujindai.locowiki.flashrecall.v2.meeting.QuestionCandidateSelector
import com.qujindai.locowiki.flashrecall.v2.meeting.QuestionTargetClassifier
import com.qujindai.locowiki.flashrecall.v2.meeting.QuestionThreadAssembler
import com.qujindai.locowiki.flashrecall.v2.meeting.QuestionThreadDraft
import com.qujindai.locowiki.flashrecall.v2.speaker.SherpaSpeakerEngine
import com.qujindai.locowiki.flashrecall.v2.speaker.SpeakerAnnotation
import com.qujindai.locowiki.flashrecall.v2.speaker.SpeakerIdentity
import com.qujindai.locowiki.flashrecall.v2.speaker.SpeakerRepository
import com.qujindai.locowiki.flashrecall.v2.ui.FlashRecallUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MeetingViewModel(application: Application) : AndroidViewModel(application), SherpaAudioEngine.Callback {
    private val repo = FactRepository(application)
    private val archive = MeetingArchiveRepository(application)
    private val candidateSelector = QuestionCandidateSelector()
    private val targetClassifier = QuestionTargetClassifier()
    private val threadAssembler = QuestionThreadAssembler()
    private val speakerRepo = SpeakerRepository(application)
    private val speakerEngine = SherpaSpeakerEngine(application)
    private val powerManager = application.getSystemService(PowerManager::class.java)
    private val audio = SherpaAudioEngine(application, this)
    private val selfEnrollmentAudio = SelfEnrollmentAudioRecorder(application)
    private val _state = MutableStateFlow(FlashRecallUiState())
    val state: StateFlow<FlashRecallUiState> = _state.asStateFlow()

    private var firstQuery = true
    private var sessionStartElapsedNs = 0L
    private var currentSessionId: String? = null
    private var runtimeSpeakerSessionKey = "live_${UUID.randomUUID()}"
    private var pendingAudioFinalizeSessionId: String? = null

    init {
        viewModelScope.launch {
            runCatching {
                repo.ensureSeeded()
                audio.updateHotwords(repo.hotwordsText())
                val speakerInit = withContext(Dispatchers.IO) { speakerEngine.initialize() }
                val speakerState = withContext(Dispatchers.IO) {
                    speakerRepo.profileState(
                        modelReady = speakerInit.isSuccess,
                        message = speakerInit.fold(
                            onSuccess = { "本地声纹模型已就绪，维度$it" },
                            onFailure = { "声纹模型不可用：${it.message}" },
                        ),
                    )
                }
                refreshDataStatus()
                refreshArchiveSummary()
                refreshDiagnostic()
                _state.value = _state.value.copy(
                    initializing = false,
                    speakerProfile = speakerState,
                    message = "本地事实库、会议归档与声纹模块已初始化",
                )
                audio.initialize()
            }.onFailure { setError("初始化失败：${it.message}") }
        }
    }

    fun updateContext(context: MeetingContext) { _state.value = _state.value.copy(context = context) }
    fun updateTypedQuestion(value: String) { _state.value = _state.value.copy(typedQuestion = value) }
    fun updateCurrentQueryText(value: String) { _state.value = _state.value.copy(currentQueryText = value) }
    fun updateRecordMode(mode: RecordMode) {
        if (_state.value.listening || _state.value.selfEnrollmentPhase != SelfEnrollmentPhase.IDLE) return
        _state.value = _state.value.copy(recordMode = mode)
    }

    fun updateSpeakerMode(mode: SpeakerMode) {
        if (_state.value.listening || _state.value.selfEnrollmentPhase != SelfEnrollmentPhase.IDLE) return
        _state.value = _state.value.copy(speakerMode = mode)
    }

    fun startSelfEnrollmentSample() {
        val current = _state.value
        val profile = current.speakerProfile
        if (!SelfEnrollmentPolicy.canStart(
                profile.modelReady,
                current.listening,
                current.selfEnrollmentPhase,
                profile.acceptedSamples,
                profile.requiredSamples,
            )
        ) {
            return setError(when {
                !profile.modelReady -> "本地声纹模型尚未就绪"
                current.listening -> "请先停止会议，再登记SELF声纹"
                current.selfEnrollmentPhase != SelfEnrollmentPhase.IDLE -> "上一段SELF声纹仍在处理"
                else -> "SELF声纹已完成，如需重录请先删除"
            })
        }
        val sampleNumber = SelfEnrollmentPolicy.nextSampleNumber(profile.acceptedSamples, profile.requiredSamples)
        if (!selfEnrollmentAudio.start(sampleNumber)) return setError("麦克风启动失败，请稍后重试")
        _state.value = current.copy(
            selfEnrollmentPhase = SelfEnrollmentPhase.RECORDING,
            selfEnrollmentSampleNumber = sampleNumber,
            message = "正在录制第${sampleNumber}段SELF声纹，请连续说3到8秒",
            error = "",
            partialTranscript = "",
        )
    }

    fun finishSelfEnrollmentSample() {
        val current = _state.value
        if (current.selfEnrollmentPhase != SelfEnrollmentPhase.RECORDING) return
        val sampleNumber = current.selfEnrollmentSampleNumber
        _state.value = current.copy(
            selfEnrollmentPhase = SelfEnrollmentPhase.PROCESSING,
            vadSpeech = false,
            message = "正在处理第${sampleNumber}段SELF声纹…",
            error = "",
        )
        viewModelScope.launch {
            val samples = withContext(Dispatchers.IO) { selfEnrollmentAudio.stopAndTakeSamples() }
            if (samples.size < SherpaSpeakerEngine.SAMPLE_RATE * MIN_SELF_ENROLLMENT_SECONDS) {
                _state.value = _state.value.copy(selfEnrollmentPhase = SelfEnrollmentPhase.IDLE)
                return@launch setError("第${sampleNumber}段录音不足${MIN_SELF_ENROLLMENT_SECONDS}秒，请重新录制")
            }
            val embedding = withContext(Dispatchers.Default) { speakerEngine.compute(samples) }
            if (embedding == null) {
                _state.value = _state.value.copy(selfEnrollmentPhase = SelfEnrollmentPhase.IDLE)
                return@launch setError("第${sampleNumber}段声纹质量不足，请重新录制")
            }
            val updated = withContext(Dispatchers.IO) { speakerRepo.addSelfSample(embedding) }
            _state.value = _state.value.copy(
                speakerProfile = updated,
                selfEnrollmentPhase = SelfEnrollmentPhase.IDLE,
                selfEnrollmentSampleNumber = SelfEnrollmentPolicy.nextSampleNumber(updated.acceptedSamples, updated.requiredSamples),
                message = updated.message,
                error = "",
            )
        }
    }

    fun deleteSelfProfile() {
        if (_state.value.listening || _state.value.selfEnrollmentPhase != SelfEnrollmentPhase.IDLE) return setError("请先停止录音再删除SELF声纹")
        viewModelScope.launch {
            val profile = withContext(Dispatchers.IO) { speakerRepo.deleteSelfProfile() }
            _state.value = _state.value.copy(speakerProfile = profile, message = profile.message)
        }
    }

    fun startMeeting() {
        if (_state.value.listening) return
        if (_state.value.selfEnrollmentPhase != SelfEnrollmentPhase.IDLE) return setError("请先完成SELF声纹录制")
        viewModelScope.launch {
            val mode = _state.value.recordMode
            runCatching {
                val session = if (mode == RecordMode.NONE) null else withContext(Dispatchers.IO) {
                    archive.startSession(_state.value.context, mode)
                }
                currentSessionId = session?.sessionId
                runtimeSpeakerSessionKey = currentSessionId ?: "live_${UUID.randomUUID()}"
                sessionStartElapsedNs = System.nanoTime()
                val audioFile = if (mode == RecordMode.TEXT_AND_AUDIO && session != null) archive.audioFileFor(session.sessionId) else null
                val started = audio.start(audioFile)
                if (!started) {
                    session?.let { withContext(Dispatchers.IO) { archive.finishSession(it.sessionId, null) } }
                    currentSessionId = null
                    refreshArchiveSummary()
                    return@runCatching
                }
                _state.value = _state.value.copy(
                    currentSessionId = currentSessionId,
                    recentUtterances = emptyList(),
                    questionCandidates = emptyList(),
                    selectedUtteranceIds = emptySet(),
                    currentQueryText = "",
                    answer = null,
                    speakerClusters = emptyList(),
                    activeThread = null,
                    calibrationEndsAtEpochMs = if (_state.value.speakerMode == SpeakerMode.SELF_AND_ABCD) System.currentTimeMillis() + 60_000 else 0,
                    archiveMessage = when (mode) {
                        RecordMode.NONE -> "本场会议不保存；仅保留最近30秒内存音频"
                        RecordMode.TEXT_ONLY -> "本场会议保存完整文字；音频仅保留最近30秒内存缓冲"
                        RecordMode.TEXT_AND_AUDIO -> "本场会议保存完整文字与本地M4A音频"
                    },
                    message = if (_state.value.speakerMode == SpeakerMode.SELF_AND_ABCD) "会议模式已启动；前60秒用于A/B/C/D快速校准" else "会议模式已启动；仅识别SELF/OTHER",
                )
                refreshArchiveSummary()
                refreshDataStatus()
            }.onFailure { setError("启动会议失败：${it.message}") }
        }
    }

    fun stopMeeting() {
        val sessionId = currentSessionId
        val mode = _state.value.recordMode
        audio.stop()
        if (sessionId == null) {
            _state.value = _state.value.copy(currentSessionId = null, message = "会议模式已停止，30秒音频缓冲停止写入")
            return
        }
        if (mode == RecordMode.TEXT_AND_AUDIO) {
            pendingAudioFinalizeSessionId = sessionId
            _state.value = _state.value.copy(message = "会议模式已停止，正在封装本地音频…")
        } else {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { archive.finishSession(sessionId, null) }
                currentSessionId = null
                _state.value = _state.value.copy(currentSessionId = null, message = "会议已归档，可执行会后全局重聚类")
                refreshArchiveSummary()
                refreshDataStatus()
            }
        }
    }

    fun queryTyped() {
        val question = _state.value.typedQuestion.trim()
        if (question.isBlank()) return setError("请输入问题")
        _state.value = _state.value.copy(currentQueryText = question, selectedUtteranceIds = emptySet())
        executeQuery(question, AsrTiming(), emptySet())
    }

    fun queryLastSentence() {
        val candidate = defaultQuestionCandidate(_state.value.recentUtterances)
        if (candidate != null) {
            selectOnly(candidate.utteranceId)
            val queryText = candidate.threadId.takeIf { it.isNotBlank() }?.let { id ->
                _state.value.activeThread?.takeIf { it.threadId == id }?.canonicalQuestion
            } ?: candidate.text
            _state.value = _state.value.copy(currentQueryText = queryText)
            executeQuery(queryText, _state.value.asrTiming, setOf(candidate.utteranceId))
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            val (question, timing) = audio.latestQuestion()
            withContext(Dispatchers.Main) {
                if (question.isBlank()) {
                    setError("还没有可查询的完整语句")
                } else {
                    val utterance = createUtterance(question, timing)
                    addUtteranceToState(utterance)
                    selectOnly(utterance.utteranceId)
                    executeQuery(question, timing, setOf(utterance.utteranceId))
                }
            }
        }
    }

    fun querySelected() {
        val question = _state.value.currentQueryText.trim().ifBlank {
            candidateSelector.mergeSelected(_state.value.recentUtterances, _state.value.selectedUtteranceIds)
        }
        if (question.isBlank()) return setError("请先选择或编辑要查询的问题")
        executeQuery(question, _state.value.asrTiming, _state.value.selectedUtteranceIds)
    }

    fun toggleUtteranceSelection(utteranceId: String) {
        val selected = _state.value.selectedUtteranceIds.toMutableSet().apply {
            if (!add(utteranceId)) remove(utteranceId)
        }
        val merged = candidateSelector.mergeSelected(_state.value.recentUtterances, selected)
        _state.value = _state.value.copy(selectedUtteranceIds = selected, currentQueryText = merged)
    }

    fun selectOnly(utteranceId: String) {
        val item = _state.value.recentUtterances.firstOrNull { it.utteranceId == utteranceId } ?: return
        _state.value = _state.value.copy(selectedUtteranceIds = setOf(utteranceId), currentQueryText = item.text)
    }

    fun moveCandidate(delta: Int) {
        val candidates = _state.value.questionCandidates
        if (candidates.isEmpty()) return setError("当前没有别人问我的问题候选")
        val selectedId = _state.value.selectedUtteranceIds.firstOrNull()
        val currentIndex = candidates.indexOfFirst { it.utteranceId == selectedId }.takeIf { it >= 0 } ?: candidates.lastIndex
        val next = (currentIndex + delta).coerceIn(0, candidates.lastIndex)
        selectOnly(candidates[next].utteranceId)
    }

    fun relabelSpeaker(utteranceId: String) {
        val item = _state.value.recentUtterances.firstOrNull { it.utteranceId == utteranceId } ?: return
        val labels = listOf("UNKNOWN", "SELF", "A", "B", "C", "D")
        val next = labels[(labels.indexOf(item.speakerLabel).takeIf { it >= 0 } ?: 0).let { (it + 1) % labels.size }]
        viewModelScope.launch {
            currentSessionId?.let { withContext(Dispatchers.IO) { speakerRepo.manualRelabel(utteranceId, next) } }
            val previous = _state.value.recentUtterances.takeWhile { it.utteranceId != utteranceId }.lastOrNull()
            val assessment = targetClassifier.classify(
                item.text,
                next,
                previous?.speakerLabel,
                recentSelfContext = previous?.speakerLabel == "SELF",
                hasOpenThreadForSpeaker = _state.value.activeThread?.initiatorLabel == next,
            )
            var updated = item.copy(
                speakerId = if (next == "SELF") "SELF" else "manual_$next",
                speakerLabel = next,
                speakerConfidence = 1f,
                speakerManual = true,
                questionType = assessment.type,
                targetSelfScore = assessment.targetSelfScore,
                isQuestion = assessment.type in candidateTypes,
            )
            if (assessment.type == QuestionType.QUESTION_TO_SELF || assessment.type == QuestionType.FOLLOW_UP_CONDITION) {
                updated = attachThread(updated)
            }
            currentSessionId?.let { withContext(Dispatchers.IO) { archive.updateUtterance(updated) } }
            replaceUtteranceInState(updated, "说话人已手动标记为$next")
        }
    }

    fun reclusterLatestMeeting() {
        if (_state.value.listening) return setError("请先停止会议再重新聚类")
        val sessionId = _state.value.lastSessionSummary?.sessionId ?: return setError("还没有可重新聚类的会议")
        viewModelScope.launch {
            _state.value = _state.value.copy(speakerReclustering = true, message = "正在本机执行会后全局重聚类…")
            runCatching {
                withContext(Dispatchers.IO) { speakerRepo.reclusterSession(sessionId) }
                val recent = withContext(Dispatchers.IO) { archive.recentUtterances(sessionId, 20) }
                val clusters = withContext(Dispatchers.IO) { speakerRepo.clusterSummaries(sessionId) }
                _state.value = _state.value.copy(
                    recentUtterances = recent,
                    questionCandidates = questionCandidates(recent),
                    speakerClusters = clusters,
                    speakerReclustering = false,
                    message = "会后全局重聚类完成；人工锁定标签未被覆盖",
                )
            }.onFailure {
                _state.value = _state.value.copy(speakerReclustering = false)
                setError("重新聚类失败：${it.message}")
            }
        }
    }

    private fun executeQuery(question: String, timing: AsrTiming, sourceUtteranceIds: Set<String>) {
        viewModelScope.launch {
            val sessionId = currentSessionId
            _state.value = _state.value.copy(currentQueryText = question, message = "正在本机检索…", error = "")
            val queryStartedNs = System.nanoTime()
            val answer = withContext(Dispatchers.IO) { repo.search(question, _state.value.context) }
            val now = System.nanoTime()
            val endToEndMs = if (timing.speechEndNs > 0 && now >= timing.speechEndNs) {
                (now - timing.speechEndNs) / 1_000_000
            } else {
                (now - queryStartedNs) / 1_000_000
            }
            val coldOrWarm = if (firstQuery) "cold" else "warm"
            firstQuery = false
            withContext(Dispatchers.IO) {
                repo.logLatency(question, answer, timing, endToEndMs, powerManager.currentThermalStatus, coldOrWarm, sessionId)
                if (sessionId != null) archive.archiveQuery(sessionId, sourceUtteranceIds, question, answer, endToEndMs)
            }
            _state.value = _state.value.copy(
                answer = answer,
                endToEndMs = endToEndMs,
                asrTiming = timing,
                message = "查询完成；问题、答案和证据已显示${if (sessionId != null) "并归档" else ""}",
            )
            refreshDataStatus()
            refreshArchiveSummary()
        }
    }

    fun prepareImport(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val resolver = getApplication<Application>().contentResolver
                val name = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: "import.json"
                val content = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("无法打开文件") }
                val preview = withContext(Dispatchers.IO) { repo.previewImport(name, content) }
                _state.value = _state.value.copy(importPreview = preview, message = "导入预览已生成，请确认")
            }.onFailure { setError("导入解析失败：${it.message}") }
        }
    }

    fun confirmImport() {
        val preview = _state.value.importPreview ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repo.confirmImport(preview) }
                audio.updateHotwords(withContext(Dispatchers.IO) { repo.hotwordsText() })
                _state.value = _state.value.copy(importPreview = null, message = "导入已确认并更新别名与ASR热词")
                refreshDataStatus()
            }.onFailure { setError("导入失败：${it.message}") }
        }
    }

    fun cancelImport() { _state.value = _state.value.copy(importPreview = null, message = "已取消导入") }

    fun resetSeed() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repo.resetSeed() }
                audio.updateHotwords(withContext(Dispatchers.IO) { repo.hotwordsText() })
                _state.value = _state.value.copy(message = "已恢复脱敏样例数据", answer = null)
                refreshDataStatus()
            }.onFailure { setError("恢复失败：${it.message}") }
        }
    }

    fun exportLatency(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val csv = withContext(Dispatchers.IO) { repo.exportLatencyCsv() }
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
                        ?: error("无法写入目标文件")
                }
                _state.value = _state.value.copy(message = "延迟日志已导出")
            }.onFailure { setError("导出失败：${it.message}") }
        }
    }

    fun exportLatestMeeting(uri: Uri) {
        if (_state.value.listening) return setError("请先停止会议，再导出完整会议包")
        val sessionId = _state.value.lastSessionSummary?.sessionId ?: return setError("还没有可导出的会议记录")
        viewModelScope.launch {
            runCatching {
                val bundle = withContext(Dispatchers.IO) { archive.buildArchiveBundle(sessionId) }
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { archive.writeZip(it, bundle) }
                        ?: error("无法写入目标文件")
                }
                _state.value = _state.value.copy(
                    message = "完整会议包已导出",
                    archiveMessage = "包含metadata、transcript、speakers、question_threads、queries、evidence${if (bundle.audioPath != null) "和audio.m4a" else ""}",
                )
            }.onFailure { setError("会议导出失败：${it.message}") }
        }
    }

    private fun createUtterance(text: String, timing: AsrTiming): MeetingUtterance {
        val start = if (sessionStartElapsedNs > 0 && timing.speechStartNs >= sessionStartElapsedNs) {
            (timing.speechStartNs - sessionStartElapsedNs) / 1_000_000
        } else 0L
        val end = if (sessionStartElapsedNs > 0 && timing.speechEndNs >= sessionStartElapsedNs) {
            (timing.speechEndNs - sessionStartElapsedNs) / 1_000_000
        } else start
        val isQuestion = candidateSelector.isLikelyQuestion(text)
        return MeetingUtterance(
            utteranceId = "utt_${UUID.randomUUID()}",
            startMs = start.coerceAtLeast(0),
            endMs = end.coerceAtLeast(start),
            text = text,
            isQuestion = isQuestion,
            questionType = if (isQuestion) QuestionType.QUESTION_UNRESOLVED else QuestionType.STATEMENT,
        )
    }

    private fun addUtteranceToState(item: MeetingUtterance) {
        val recent = (_state.value.recentUtterances + item).takeLast(20)
        _state.value = _state.value.copy(
            recentUtterances = recent,
            questionCandidates = questionCandidates(recent),
            lastTranscript = item.text,
            partialTranscript = "",
            message = if (item.isQuestion) "识别到待判定问题；正在进行本地说话人判断" else "已保存最近发言",
        )
        val sessionId = currentSessionId
        if (sessionId != null) viewModelScope.launch {
            withContext(Dispatchers.IO) { archive.appendUtterance(sessionId, item) }
            refreshArchiveSummary()
            refreshDataStatus()
        }
    }

    private fun classifyUtterance(item: MeetingUtterance, samples: FloatArray) {
        viewModelScope.launch {
            val embedding = if (_state.value.speakerProfile.modelReady) {
                withContext(Dispatchers.Default) { speakerEngine.compute(samples) }
            } else null
            val annotation = if (embedding == null) {
                SpeakerAnnotation("UNKNOWN", "UNKNOWN", 0f, SpeakerIdentity.UNKNOWN)
            } else {
                val sessionId = currentSessionId
                if (sessionId != null) {
                    withContext(Dispatchers.IO) { speakerRepo.annotate(sessionId, item.utteranceId, item.startMs, embedding, _state.value.speakerMode) }
                } else {
                    withContext(Dispatchers.IO) { speakerRepo.classifyOnly(runtimeSpeakerSessionKey, embedding, _state.value.speakerMode) }
                }
            }
            val before = _state.value.recentUtterances.takeWhile { it.utteranceId != item.utteranceId }
            val previous = before.lastOrNull()
            val recentSelf = before.asReversed().firstOrNull { item.startMs - it.endMs <= 15_000 }?.speakerLabel == "SELF"
            val assessment = targetClassifier.classify(
                text = item.text,
                speakerLabel = annotation.speakerLabel,
                previousSpeakerLabel = previous?.speakerLabel,
                recentSelfContext = recentSelf,
                hasOpenThreadForSpeaker = _state.value.activeThread?.initiatorSpeakerId == annotation.speakerId,
            )
            var updated = item.copy(
                speakerId = annotation.speakerId,
                speakerLabel = annotation.speakerLabel,
                speakerConfidence = annotation.confidence,
                targetSelfScore = assessment.targetSelfScore,
                questionType = assessment.type,
                isQuestion = assessment.type in candidateTypes,
            )

            if (annotation.speakerLabel == "SELF") promoteUnresolvedBefore(updated)
            if (assessment.type == QuestionType.QUESTION_TO_SELF || assessment.type == QuestionType.FOLLOW_UP_CONDITION) {
                updated = attachThread(updated)
            }
            currentSessionId?.let { withContext(Dispatchers.IO) { archive.updateUtterance(updated) } }
            replaceUtteranceInState(
                updated,
                when (updated.questionType) {
                    QuestionType.QUESTION_TO_SELF -> "识别为${updated.speakerLabel}问我的问题"
                    QuestionType.SELF_QUESTION -> "识别为SELF提出的问题，已排除默认问题库"
                    QuestionType.QUESTION_UNRESOLVED -> "问题目标不确定，请按说话人标签手动修正"
                    else -> "说话人：${updated.speakerLabel}"
                },
            )
            refreshSpeakerClusters()
        }
    }

    private suspend fun promoteUnresolvedBefore(selfUtterance: MeetingUtterance) {
        val unresolved = _state.value.recentUtterances.asReversed().firstOrNull {
            it.utteranceId != selfUtterance.utteranceId &&
                targetClassifier.shouldPromoteWhenSelfReplies(
                    selfUtterance.startMs - it.endMs,
                    it.questionType,
                    "SELF",
                )
        } ?: return
        var promoted = unresolved.copy(
            questionType = QuestionType.QUESTION_TO_SELF,
            targetSelfScore = unresolved.targetSelfScore.coerceAtLeast(0.75f),
            isQuestion = true,
        )
        promoted = attachThread(promoted)
        currentSessionId?.let { withContext(Dispatchers.IO) { archive.updateUtterance(promoted) } }
        replaceUtteranceInState(promoted, "根据SELF随后作答，已将上一问题提升为问我的问题")
    }

    private suspend fun attachThread(item: MeetingUtterance): MeetingUtterance {
        val sessionId = currentSessionId
        val active = _state.value.activeThread?.takeIf {
            it.initiatorSpeakerId == item.speakerId && item.startMs - it.lastUpdatedAtMs in 0..90_000
        } ?: sessionId?.let { withContext(Dispatchers.IO) { archive.latestOpenThread(it, item.speakerId) } }
        val draft = active?.let {
            QuestionThreadDraft(
                threadId = it.threadId,
                initiatorSpeakerId = it.initiatorSpeakerId,
                initiatorLabel = it.initiatorLabel,
                canonicalQuestion = it.canonicalQuestion,
                utteranceIds = List(it.utteranceCount) { "" },
                startedAtMs = it.startedAtMs,
                lastUpdatedAtMs = it.lastUpdatedAtMs,
                confidence = it.confidence,
            )
        }
        val update = threadAssembler.consume(
            utteranceId = item.utteranceId,
            speakerLabel = item.speakerLabel,
            atMs = item.startMs,
            text = item.text,
            isQuestion = item.questionType != QuestionType.FOLLOW_UP_CONDITION,
            openThread = draft,
            speakerId = item.speakerId,
        )
        val summary = QuestionThreadSummary(
            threadId = update.thread.threadId,
            initiatorSpeakerId = update.thread.initiatorSpeakerId,
            initiatorLabel = update.thread.initiatorLabel,
            canonicalQuestion = update.thread.canonicalQuestion,
            status = "OPEN",
            utteranceCount = update.thread.utteranceIds.size,
            startedAtMs = update.thread.startedAtMs,
            lastUpdatedAtMs = update.thread.lastUpdatedAtMs,
            confidence = update.thread.confidence,
        )
        sessionId?.let { withContext(Dispatchers.IO) { archive.upsertThread(summary, item.utteranceId, update.relationType) } }
        _state.value = _state.value.copy(activeThread = summary, currentQueryText = summary.canonicalQuestion)
        return item.copy(threadId = summary.threadId)
    }

    private fun replaceUtteranceInState(item: MeetingUtterance, message: String) {
        val recent = _state.value.recentUtterances.map { if (it.utteranceId == item.utteranceId) item else it }
        _state.value = _state.value.copy(
            recentUtterances = recent,
            questionCandidates = questionCandidates(recent),
            message = message,
        )
    }

    private fun questionCandidates(items: List<MeetingUtterance>): List<MeetingUtterance> =
        items.filter { it.speakerLabel != "SELF" && it.questionType in candidateTypes }.takeLast(10)

    private fun defaultQuestionCandidate(items: List<MeetingUtterance>): MeetingUtterance? =
        questionCandidates(items).lastOrNull() ?: items.asReversed().firstOrNull { it.speakerLabel != "SELF" && it.isQuestion }

    private suspend fun refreshSpeakerClusters() {
        val sessionId = currentSessionId ?: return
        val clusters = withContext(Dispatchers.IO) { speakerRepo.clusterSummaries(sessionId) }
        _state.value = _state.value.copy(speakerClusters = clusters)
    }

    private suspend fun refreshDataStatus() {
        val status = withContext(Dispatchers.IO) { repo.dataStatus() }
        _state.value = _state.value.copy(dataStatus = status)
    }

    private suspend fun refreshArchiveSummary() {
        val summary = withContext(Dispatchers.IO) { archive.latestSummary() }
        _state.value = _state.value.copy(lastSessionSummary = summary)
    }

    private fun refreshDiagnostic() {
        val app = getApplication<Application>()
        val diagnostic = DeviceDiagnostic(
            model = Build.MODEL,
            androidVersion = "Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}",
            abi = Build.SUPPORTED_ABIS.joinToString(),
            processorCount = Runtime.getRuntime().availableProcessors(),
            asrModel = "${SherpaAudioEngine.MODEL_NAME} + ${SherpaSpeakerEngine.MODEL_ID}",
            asrReady = _state.value.modelReady,
            vadReady = _state.value.modelReady,
            thermalStatus = powerManager.currentThermalStatus,
            systemRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(app),
            systemOnDeviceRecognitionAvailable = SpeechRecognizer.isOnDeviceRecognitionAvailable(app),
        )
        _state.value = _state.value.copy(diagnostic = diagnostic)
    }

    private fun setError(message: String) { _state.value = _state.value.copy(error = message, message = message) }

    override fun onModelState(ready: Boolean, message: String) {
        _state.value = _state.value.copy(modelReady = ready, message = message, error = if (ready) "" else message)
        refreshDiagnostic()
    }

    override fun onListeningState(listening: Boolean, vadSpeech: Boolean) {
        _state.value = if (_state.value.selfEnrollmentPhase == SelfEnrollmentPhase.IDLE) {
            _state.value.copy(listening = listening, vadSpeech = vadSpeech)
        } else _state.value.copy(listening = false, vadSpeech = vadSpeech)
    }

    override fun onPartial(text: String) {
        if (_state.value.selfEnrollmentPhase != SelfEnrollmentPhase.IDLE) return
        _state.value = _state.value.copy(partialTranscript = text)
    }

    override fun onFinal(text: String, timing: AsrTiming, samples: FloatArray) {
        if (_state.value.selfEnrollmentPhase != SelfEnrollmentPhase.IDLE) return
        val item = createUtterance(text, timing)
        _state.value = _state.value.copy(asrTiming = timing)
        addUtteranceToState(item)
        classifyUtterance(item, samples)
    }

    override fun onArchiveWarning(message: String) {
        _state.value = _state.value.copy(archiveMessage = message, message = message)
    }

    override fun onAudioArchiveFinalized(path: String?) {
        val sessionId = pendingAudioFinalizeSessionId ?: return
        pendingAudioFinalizeSessionId = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) { archive.finishSession(sessionId, path) }
            currentSessionId = null
            _state.value = _state.value.copy(
                currentSessionId = null,
                message = if (path != null) "会议文字与音频已归档，可执行会后全局重聚类" else "会议文字已归档，音频未生成",
                archiveMessage = if (path != null) "audio.m4a已保存在应用私有目录" else "音频归档失败，但文字记录完整保留",
            )
            refreshArchiveSummary()
            refreshDataStatus()
        }
    }

    override fun onError(message: String) = setError(message)

    override fun onCleared() {
        selfEnrollmentAudio.close()
        audio.close()
        speakerEngine.close()
        super.onCleared()
    }

    companion object {
        private const val MIN_SELF_ENROLLMENT_SECONDS = 2
        private val candidateTypes = setOf(
            QuestionType.QUESTION_TO_SELF,
            QuestionType.QUESTION_UNRESOLVED,
            QuestionType.FOLLOW_UP_CONDITION,
        )
    }
}
