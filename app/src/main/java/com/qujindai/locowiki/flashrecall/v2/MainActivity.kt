package com.qujindai.locowiki.flashrecall.v2

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.qujindai.locowiki.flashrecall.v2.ui.FlashRecallScreen

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MeetingViewModel>()

    private val meetingPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startMeeting() else viewModel.onError("没有麦克风权限，仍可使用文字查询")
    }
    private val selfEnrollmentPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startSelfEnrollmentSample() else viewModel.onError("没有麦克风权限，无法登记SELF声纹")
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::prepareImport) }
    private val exportLatencyLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let(viewModel::exportLatency) }
    private val exportMeetingLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(viewModel::exportLatestMeeting) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsState()
            MaterialTheme(colorScheme = lightColorScheme()) {
                FlashRecallScreen(
                    state = state,
                    onContextChange = viewModel::updateContext,
                    onTypedQuestionChange = viewModel::updateTypedQuestion,
                    onCurrentQueryTextChange = viewModel::updateCurrentQueryText,
                    onRecordModeChange = viewModel::updateRecordMode,
                    onSpeakerModeChange = viewModel::updateSpeakerMode,
                    onStartSelfEnrollment = { selfEnrollmentPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onFinishSelfEnrollment = viewModel::finishSelfEnrollmentSample,
                    onDeleteSelfProfile = viewModel::deleteSelfProfile,
                    onRelabelSpeaker = viewModel::relabelSpeaker,
                    onReclusterSpeakers = viewModel::reclusterLatestMeeting,
                    onStartMeeting = { meetingPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onStopMeeting = viewModel::stopMeeting,
                    onQueryTyped = viewModel::queryTyped,
                    onQueryLast = viewModel::queryLastSentence,
                    onQuerySelected = viewModel::querySelected,
                    onToggleUtterance = viewModel::toggleUtteranceSelection,
                    onSelectOnly = viewModel::selectOnly,
                    onMoveCandidate = viewModel::moveCandidate,
                    onOpenImport = { importLauncher.launch(arrayOf("application/json", "text/csv", "text/plain")) },
                    onConfirmImport = viewModel::confirmImport,
                    onCancelImport = viewModel::cancelImport,
                    onResetSeed = viewModel::resetSeed,
                    onExportLatency = { exportLatencyLauncher.launch("locowiki-latency-v0.4.csv") },
                    onExportMeeting = { exportMeetingLauncher.launch("locowiki-meeting-v0.4.zip") },
                )
            }
        }
    }
}
