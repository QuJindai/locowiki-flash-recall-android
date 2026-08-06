package com.qujindai.locowiki.flashrecall.v2.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qujindai.locowiki.flashrecall.v2.domain.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FlashRecallScreen(
    state: FlashRecallUiState,
    onContextChange: (MeetingContext) -> Unit,
    onTypedQuestionChange: (String) -> Unit,
    onCurrentQueryTextChange: (String) -> Unit,
    onRecordModeChange: (RecordMode) -> Unit,
    onSpeakerModeChange: (SpeakerMode) -> Unit,
    onStartSelfEnrollment: () -> Unit,
    onFinishSelfEnrollment: () -> Unit,
    onDeleteSelfProfile: () -> Unit,
    onRelabelSpeaker: (String) -> Unit,
    onReclusterSpeakers: () -> Unit,
    onStartMeeting: () -> Unit,
    onStopMeeting: () -> Unit,
    onQueryTyped: () -> Unit,
    onQueryLast: () -> Unit,
    onQuerySelected: () -> Unit,
    onToggleUtterance: (String) -> Unit,
    onSelectOnly: (String) -> Unit,
    onMoveCandidate: (Int) -> Unit,
    onOpenImport: () -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onResetSeed: () -> Unit,
    onExportLatency: () -> Unit,
    onExportMeeting: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("LocoWiki 极速召回 V0.4", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        PrivacyCard(state)
        ContextCard(state.context, onContextChange)
        SpeakerCard(
            state = state,
            onSpeakerModeChange = onSpeakerModeChange,
            onStartSelfEnrollment = onStartSelfEnrollment,
            onFinishSelfEnrollment = onFinishSelfEnrollment,
            onDeleteSelfProfile = onDeleteSelfProfile,
            onReclusterSpeakers = onReclusterSpeakers,
        )
        SpeechCard(
            state,
            onRecordModeChange,
            onStartMeeting,
            onStopMeeting,
            onQueryLast,
            onToggleUtterance,
            onSelectOnly,
            onRelabelSpeaker,
        )
        ActiveThreadCard(state)
        CurrentQueryCard(state, onCurrentQueryTextChange, onMoveCandidate, onQuerySelected)
        QueryCard(state, onTypedQuestionChange, onQueryTyped)
        AnswerCardView(state)
        ArchiveCard(state, onExportMeeting, onReclusterSpeakers)
        ImportCard(state, onOpenImport, onConfirmImport, onCancelImport, onResetSeed)
        StatusCard(state, onExportLatency)
        DiagnosticCard(state)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PrivacyCard(state: FlashRecallUiState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("私有模式", fontWeight = FontWeight.Bold)
            Text("无网络权限｜本地ASR与声纹｜Room/FTS5｜30秒内存缓冲｜会议归档由用户选择")
            Text(state.message, style = MaterialTheme.typography.bodySmall)
            if (state.archiveMessage.isNotBlank()) Text(state.archiveMessage, style = MaterialTheme.typography.bodySmall)
            if (state.error.isNotBlank()) Text(state.error, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ContextCard(context: MeetingContext, onChange: (MeetingContext) -> Unit) {
    SectionCard("会议上下文") {
        OutlinedTextField(context.project, { onChange(context.copy(project = it)) }, label = { Text("项目") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(context.vehicle, { onChange(context.copy(vehicle = it)) }, label = { Text("车型") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(context.factory, { onChange(context.copy(factory = it)) }, label = { Text("工厂") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(context.topic, { onChange(context.copy(topic = it)) }, label = { Text("主题") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(context.year.toString(), { value -> onChange(context.copy(year = value.toIntOrNull() ?: context.year)) }, label = { Text("会议年份") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(context.competitor, { onChange(context.copy(competitor = it)) }, label = { Text("竞品对象") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    }
}

@Composable
private fun SpeakerCard(
    state: FlashRecallUiState,
    onSpeakerModeChange: (SpeakerMode) -> Unit,
    onStartSelfEnrollment: () -> Unit,
    onFinishSelfEnrollment: () -> Unit,
    onDeleteSelfProfile: () -> Unit,
    onReclusterSpeakers: () -> Unit,
) {
    val profile = state.speakerProfile
    val phase = state.selfEnrollmentPhase
    val controlsFree = !state.listening && phase == SelfEnrollmentPhase.IDLE
    SectionCard("我的声纹（会前设置）") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = {}, label = { Text(if (profile.modelReady) "声纹模型已就绪" else "声纹模型不可用") })
            AssistChip(onClick = {}, label = { Text(if (profile.enrolled) "SELF已登记" else "SELF未完成") })
        }
        Text("SELF样本 ${profile.acceptedSamples}/${profile.requiredSamples}｜阈值 ${"%.2f".format(profile.threshold)}")
        LinearProgressIndicator(
            progress = { (profile.acceptedSamples.toFloat() / profile.requiredSamples.coerceAtLeast(1)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("SELF声纹是本机长期设置，可在会议前完成。每段单独录制3到8秒，只保存声纹向量，不保存登记原音频。", style = MaterialTheme.typography.bodySmall)
        if (phase == SelfEnrollmentPhase.RECORDING) Text("正在录制第${state.selfEnrollmentSampleNumber}段，完成后点击停止并保存。", color = MaterialTheme.colorScheme.primary)
        if (phase == SelfEnrollmentPhase.PROCESSING) Text("正在本机提取第${state.selfEnrollmentSampleNumber}段声纹…", color = MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = if (phase == SelfEnrollmentPhase.RECORDING) onFinishSelfEnrollment else onStartSelfEnrollment,
                enabled = when (phase) {
                    SelfEnrollmentPhase.RECORDING -> true
                    SelfEnrollmentPhase.PROCESSING -> false
                    SelfEnrollmentPhase.IDLE -> SelfEnrollmentPolicy.canStart(
                        profile.modelReady,
                        state.listening,
                        phase,
                        profile.acceptedSamples,
                        profile.requiredSamples,
                    )
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(when (phase) {
                    SelfEnrollmentPhase.RECORDING -> "停止并保存第${state.selfEnrollmentSampleNumber}段"
                    SelfEnrollmentPhase.PROCESSING -> "正在处理…"
                    SelfEnrollmentPhase.IDLE -> "录制第${SelfEnrollmentPolicy.nextSampleNumber(profile.acceptedSamples, profile.requiredSamples)}段"
                })
            }
            OutlinedButton(
                onClick = onDeleteSelfProfile,
                enabled = profile.acceptedSamples > 0 && controlsFree,
                modifier = Modifier.weight(1f),
            ) { Text("删除SELF声纹") }
        }
        Text("会议识别模式", fontWeight = FontWeight.SemiBold)
        Column(Modifier.selectableGroup()) {
            SpeakerMode.entries.forEach { mode ->
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = controlsFree) { onSpeakerModeChange(mode) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = state.speakerMode == mode, onClick = { onSpeakerModeChange(mode) }, enabled = controlsFree)
                    Text(if (mode == SpeakerMode.SELF_ONLY) "只区分SELF / OTHER" else "区分SELF并将其他人聚类为A/B/C/D")
                }
            }
        }
        if (state.calibrationEndsAtEpochMs > System.currentTimeMillis()) {
            Text("A/B/C/D快速校准进行中：会议前60秒自动形成临时说话人组。", color = MaterialTheme.colorScheme.primary)
        }
        if (state.speakerClusters.isNotEmpty()) {
            Text("当前说话人组", fontWeight = FontWeight.SemiBold)
            state.speakerClusters.forEach { cluster ->
                Text("${cluster.label}：${cluster.sampleCount}段｜置信 ${"%.2f".format(cluster.confidence)}${if (cluster.manualLocked) "｜人工锁定" else ""}")
            }
        }
        OutlinedButton(
            onClick = onReclusterSpeakers,
            enabled = controlsFree && state.lastSessionSummary != null && !state.speakerReclustering,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.speakerReclustering) "正在重新聚类…" else "会后全局重新聚类A/B/C/D") }
    }
}

@Composable
private fun SpeechCard(
    state: FlashRecallUiState,
    onRecordModeChange: (RecordMode) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onQueryLast: () -> Unit,
    onToggleUtterance: (String) -> Unit,
    onSelectOnly: (String) -> Unit,
    onRelabelSpeaker: (String) -> Unit,
) {
    SectionCard("会议听题与记录") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text(if (state.modelReady) "ASR已就绪" else "ASR未就绪") })
            AssistChip(onClick = {}, label = { Text(if (state.vadSpeech) "检测到人声" else "等待人声") })
        }
        Text("记录模式", fontWeight = FontWeight.SemiBold)
        Column(Modifier.selectableGroup()) {
            RecordMode.entries.forEach { mode ->
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !state.listening && state.selfEnrollmentPhase == SelfEnrollmentPhase.IDLE) { onRecordModeChange(mode) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = state.recordMode == mode, onClick = { onRecordModeChange(mode) }, enabled = !state.listening && state.selfEnrollmentPhase == SelfEnrollmentPhase.IDLE)
                    Text(
                        when (mode) {
                            RecordMode.NONE -> "不保存"
                            RecordMode.TEXT_ONLY -> "仅保存完整文字（默认）"
                            RecordMode.TEXT_AND_AUDIO -> "保存完整文字＋本地M4A音频"
                        }
                    )
                }
            }
        }
        Text("实时字幕：${state.partialTranscript.ifBlank { "—" }}")
        Text("最近一句：${state.lastTranscript.ifBlank { "—" }}", fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = if (state.listening) onStop else onStart,
                enabled = state.modelReady && state.selfEnrollmentPhase == SelfEnrollmentPhase.IDLE,
                modifier = Modifier.weight(1f),
            ) { Text(if (state.listening) "停止并归档" else "启动会议模式") }
            Button(onClick = onQueryLast, enabled = state.recentUtterances.isNotEmpty() || state.listening, modifier = Modifier.weight(1f)) {
                Text("查询最近问题")
            }
        }

        if (state.recentUtterances.isNotEmpty()) {
            HorizontalDivider()
            Text("最近发言（可多选；可手动修正说话人）", fontWeight = FontWeight.SemiBold)
            state.recentUtterances.asReversed().take(12).forEach { item ->
                UtteranceRow(
                    item = item,
                    selected = item.utteranceId in state.selectedUtteranceIds,
                    onToggle = { onToggleUtterance(item.utteranceId) },
                    onSelectOnly = { onSelectOnly(item.utteranceId) },
                    onRelabel = { onRelabelSpeaker(item.utteranceId) },
                )
            }
        }
    }
}

@Composable
private fun UtteranceRow(
    item: MeetingUtterance,
    selected: Boolean,
    onToggle: () -> Unit,
    onSelectOnly: () -> Unit,
    onRelabel: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(checked = selected, onCheckedChange = { onToggle() })
                Column(Modifier.weight(1f).clickable { onSelectOnly() }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${item.speakerLabel} · ${questionTypeText(item.questionType)} · ${formatOffset(item.startMs)}", style = MaterialTheme.typography.labelSmall)
                    Text(item.text)
                    Text("问我评分 ${"%.2f".format(item.targetSelfScore)}｜声纹 ${"%.2f".format(item.speakerConfidence)}${if (item.threadId.isNotBlank()) "｜线程 ${item.threadId.takeLast(6)}" else ""}", style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(onClick = onRelabel, modifier = Modifier.fillMaxWidth()) {
                Text("修正说话人：${item.speakerLabel} → 下一个标签")
            }
        }
    }
}

@Composable
private fun ActiveThreadCard(state: FlashRecallUiState) {
    val thread = state.activeThread ?: return
    SectionCard("当前连续问题线程") {
        Text("发起人：${thread.initiatorLabel}｜发言 ${thread.utteranceCount} 条｜状态 ${thread.status}")
        Text(thread.canonicalQuestion, fontWeight = FontWeight.SemiBold)
        Text("同一说话人90秒内的追问和补充条件会并入此线程；查询最近问题时优先使用线程完整问题。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CurrentQueryCard(
    state: FlashRecallUiState,
    onChange: (String) -> Unit,
    onMoveCandidate: (Int) -> Unit,
    onQuery: () -> Unit,
) {
    SectionCard("本次查询问题") {
        OutlinedTextField(
            value = state.currentQueryText,
            onValueChange = onChange,
            label = { Text("系统实际送入查询引擎的文字") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onMoveCandidate(-1) }, modifier = Modifier.weight(1f), enabled = state.questionCandidates.isNotEmpty()) { Text("上一问题") }
            OutlinedButton(onClick = { onMoveCandidate(1) }, modifier = Modifier.weight(1f), enabled = state.questionCandidates.isNotEmpty()) { Text("下一问题") }
        }
        Button(onClick = onQuery, modifier = Modifier.fillMaxWidth(), enabled = state.currentQueryText.isNotBlank()) { Text("查询当前显示问题") }
        Text("SELF自己提出的问题默认不进入“别人问我”候选；目标不确定的问题仍保留供人工确认。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun QueryCard(state: FlashRecallUiState, onChange: (String) -> Unit, onQuery: () -> Unit) {
    SectionCard("独立文字极速查询") {
        OutlinedTextField(
            value = state.typedQuestion,
            onValueChange = onChange,
            label = { Text("例如：去年那套设备多少钱？") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Button(onClick = onQuery, modifier = Modifier.fillMaxWidth()) { Text("极速查询") }
    }
}

@Composable
private fun AnswerCardView(state: FlashRecallUiState) {
    val answer = state.answer
    SectionCard("答案和证据") {
        if (state.currentQueryText.isNotBlank()) Text("问题：${state.currentQueryText}", fontWeight = FontWeight.SemiBold)
        Text(answer?.answer ?: "答案将在这里显示。", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("来源：${answer?.evidence ?: "—"}")
        if (!answer?.candidates.isNullOrEmpty()) {
            Text("候选：")
            answer!!.candidates.forEach { Text("• $it") }
        }
        val timing = answer?.timing
        Text("端到端：${state.endToEndMs.takeIf { it > 0 }?.let { "$it ms" } ?: "—"}")
        Text("ASR首字：${state.asrTiming.firstPartialMs} ms｜尾部：${state.asrTiming.endpointMs} ms")
        if (timing != null) {
            Text("解析 ${timing.parseMs} ms｜精确检索 ${timing.exactLookupMs} ms｜FTS5 ${timing.ftsMs} ms｜编译 ${timing.answerCompileMs} ms｜路径 ${timing.route}")
        }
    }
}

@Composable
private fun ArchiveCard(state: FlashRecallUiState, onExportMeeting: () -> Unit, onRecluster: () -> Unit) {
    val summary = state.lastSessionSummary
    SectionCard("完整会议归档") {
        if (summary == null) {
            Text("尚无会议记录。")
        } else {
            Text(summary.title, fontWeight = FontWeight.SemiBold)
            Text("开始：${formatEpoch(summary.startedAt)}")
            Text("发言 ${summary.utteranceCount}｜查询 ${summary.queryCount}｜音频 ${if (summary.audioAvailable) "有" else "无"}")
            Text("模式：${summary.recordMode.storageValue}｜状态：${if (summary.endedAt == null) "进行中" else "已完成"}")
            Button(onClick = onExportMeeting, enabled = !state.listening && summary.endedAt != null, modifier = Modifier.fillMaxWidth()) {
                Text("导出完整会议ZIP")
            }
            OutlinedButton(onClick = onRecluster, enabled = !state.listening && summary.endedAt != null && !state.speakerReclustering, modifier = Modifier.fillMaxWidth()) {
                Text("会后修正A/B/C/D")
            }
            Text("ZIP包含metadata、transcript、speakers、question_threads、queries、evidence；启用录音时还包含audio.m4a。", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ImportCard(
    state: FlashRecallUiState,
    onOpen: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
) {
    SectionCard("JSON / CSV 导入审核") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) { Text("选择导入文件") }
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("恢复脱敏样例") }
        }
        state.importPreview?.let { preview ->
            Text("${preview.fileName}：${preview.facts.size}条")
            Text("新增 ${preview.newCount}｜更新 ${preview.updateCount}｜冲突 ${preview.conflictCount}｜拒绝 ${preview.rejectedCount}")
            Text("缺少 status 的记录默认 draft；冲突记录不会作为确定答案。", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("确认导入") }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("取消") }
            }
        }
    }
}

@Composable
private fun StatusCard(state: FlashRecallUiState, onExport: () -> Unit) {
    val s = state.dataStatus
    SectionCard("数据与性能状态") {
        Text("实体 ${s.entities}｜别名 ${s.aliases}｜事实 ${s.facts}")
        Text("已核实 ${s.verified}｜草稿 ${s.drafts}｜冲突 ${s.conflicts}")
        Text("会议 ${s.meetings}｜发言 ${s.utterances}｜归档查询 ${s.archivedQueries}")
        Text("延迟样本 ${s.latencySamples}｜P50 ${s.p50Ms} ms｜P95 ${s.p95Ms} ms")
        OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("导出完整延迟CSV") }
    }
}

@Composable
private fun DiagnosticCard(state: FlashRecallUiState) {
    val d = state.diagnostic ?: return
    SectionCard("S24 Ultra 诊断") {
        Text("设备：${d.model}")
        Text("系统：${d.androidVersion}")
        Text("ABI：${d.abi}")
        Text("CPU逻辑核心：${d.processorCount}｜温控状态：${d.thermalStatus}")
        Text("模型：${d.asrModel}")
        Text("Sherpa ASR：${if (state.modelReady) "可用" else "未就绪"}｜声纹：${if (state.speakerProfile.modelReady) "可用" else "未就绪"}")
        Text("系统普通识别：${d.systemRecognitionAvailable}｜系统端侧识别：${d.systemOnDeviceRecognitionAvailable}")
        Text("正式主链路不依赖系统SpeechRecognizer；声纹失败会降级为UNKNOWN，不阻塞查询。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

private fun questionTypeText(type: QuestionType): String = when (type) {
    QuestionType.STATEMENT -> "陈述"
    QuestionType.SELF_QUESTION -> "SELF问题（排除）"
    QuestionType.QUESTION_TO_SELF -> "问我的问题"
    QuestionType.QUESTION_TO_OTHER -> "问其他人"
    QuestionType.QUESTION_UNRESOLVED -> "目标待确认"
    QuestionType.FOLLOW_UP_CONDITION -> "连续补充"
    QuestionType.ANSWER_BY_SELF -> "SELF回答"
    QuestionType.ANSWER_BY_OTHER -> "他人回答"
}

private fun formatOffset(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0) / 1000
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatEpoch(epochMs: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(epochMs))
