package com.amcg.mcg.app.ui.screens.consultation

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amcg.mcg.app.data.services.consultation.ConsultationType
import com.amcg.mcg.app.data.services.consultation.SessionStatus
import com.amcg.mcg.app.data.services.consultation.SocketService
import com.amcg.mcg.app.data.services.consultation.WebRTCService
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VideoConsultationScreen(
    websocketUrl: String,
    token: String,
    consultationType: String = ConsultationType.COMPREHENSIVE,
    onNavigateBack: () -> Unit,
    onNavigateToResult: (String) -> Unit,
    viewModel: VideoConsultationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Request permissions
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    )

    // Surface view renderers
    var localRenderer: SurfaceViewRenderer? by remember { mutableStateOf(null) }
    var remoteRenderer: SurfaceViewRenderer? by remember { mutableStateOf(null) }

    // Collect events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is VideoConsultationEvent.AnalysisComplete -> {
                    onNavigateToResult(event.resultId)
                }
                is VideoConsultationEvent.AIResponseReceived -> {
                    // Play TTS audio if available
                    event.audio?.let {
                        // TODO: Play audio
                    }
                }
                else -> {}
            }
        }
    }

    // Start session when permissions granted
    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted && uiState.session == null) {
            viewModel.startSession(consultationType, websocketUrl, token)
        }
    }

    // Connect to room when socket connected and session ready
    LaunchedEffect(uiState.socketState, uiState.session) {
        if (uiState.socketState is SocketService.ConnectionState.Connected &&
            uiState.session != null &&
            localRenderer != null &&
            remoteRenderer != null
        ) {
            viewModel.connectToRoom(localRenderer!!, remoteRenderer!!)
        }
    }

    // Auto-activate when WebRTC connected
    LaunchedEffect(uiState.webRTCState) {
        if (uiState.webRTCState is WebRTCService.WebRTCState.Connected) {
            viewModel.activateSession()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (!permissionsState.allPermissionsGranted) {
            PermissionRequest(
                onRequestPermission = { permissionsState.launchMultiplePermissionRequest() }
            )
        } else {
            // Remote video (full screen)
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        setMirror(false)
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                        viewModel.getEglBase()?.let { egl ->
                            init(egl.eglBaseContext, null)
                        }
                        remoteRenderer = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Local video (picture-in-picture)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(120.dp, 160.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).apply {
                            setMirror(true)
                            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                            viewModel.getEglBase()?.let { egl ->
                                init(egl.eglBaseContext, null)
                            }
                            localRenderer = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (!uiState.isVideoEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.DarkGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = "Video Off",
                            tint = Color.White
                        )
                    }
                }
            }

            // Connection status
            ConnectionStatus(
                socketState = uiState.socketState,
                webRTCState = uiState.webRTCState,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )

            // AI Guidance overlay
            AnimatedVisibility(
                visible = uiState.currentGuidance != null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp),
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                uiState.currentGuidance?.let { guidance ->
                    AIGuidanceCard(
                        guidance = guidance,
                        onDismiss = { viewModel.dismissGuidance() }
                    )
                }
            }

            // Detection notifications
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(8.dp)
            ) {
                uiState.detections.takeLast(3).forEach { detection ->
                    DetectionChip(detection = detection)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // Analysis progress
            if (uiState.session?.status == SessionStatus.ANALYZING) {
                AnalysisProgressOverlay(
                    progress = uiState.analysisProgress,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Bottom controls
            BottomControls(
                isVideoEnabled = uiState.isVideoEnabled,
                isAudioEnabled = uiState.isAudioEnabled,
                isEnding = uiState.isEnding,
                onToggleVideo = { viewModel.toggleVideo() },
                onToggleAudio = { viewModel.toggleAudio() },
                onSwitchCamera = { viewModel.switchCamera() },
                onCaptureTongue = {
                    // TODO: Capture current frame as tongue
                },
                onCaptureFacial = {
                    // TODO: Capture current frame as facial
                },
                onEndCall = { viewModel.endSession() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
            )

            // Loading overlay
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            // Error snackbar
            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("닫기")
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            localRenderer?.release()
            remoteRenderer?.release()
        }
    }
}

@Composable
private fun PermissionRequest(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Videocam,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "카메라와 마이크 권한이 필요합니다",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "화상 상담을 진행하려면 카메라와 마이크 접근 권한을 허용해주세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = onRequestPermission) {
            Text("권한 허용하기")
        }
    }
}

@Composable
private fun ConnectionStatus(
    socketState: SocketService.ConnectionState,
    webRTCState: WebRTCService.WebRTCState,
    modifier: Modifier = Modifier
) {
    val statusColor = when {
        webRTCState is WebRTCService.WebRTCState.Connected -> Color.Green
        webRTCState is WebRTCService.WebRTCState.Connecting ||
        socketState is SocketService.ConnectionState.Connecting -> Color.Yellow
        else -> Color.Red
    }

    val statusText = when {
        webRTCState is WebRTCService.WebRTCState.Connected -> "연결됨"
        webRTCState is WebRTCService.WebRTCState.Connecting -> "연결 중..."
        socketState is SocketService.ConnectionState.Connected -> "서버 연결됨"
        socketState is SocketService.ConnectionState.Connecting -> "서버 연결 중..."
        else -> "연결 대기"
    }

    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(statusColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = statusText,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun AIGuidanceCard(
    guidance: AIGuidance,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (guidance.type) {
                            "instruction" -> Icons.Default.Info
                            "question" -> Icons.Default.QuestionMark
                            "feedback" -> Icons.Default.Feedback
                            else -> Icons.Default.Assistant
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI 안내",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "닫기")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = guidance.textContent,
                style = MaterialTheme.typography.bodyMedium
            )

            if (guidance.expectedResponse != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (guidance.expectedResponse) {
                        "voice" -> "음성으로 응답해주세요"
                        "gesture" -> "고개를 끄덕이거나 저어주세요"
                        "show_body_part" -> "${guidance.targetBodyPart ?: "해당 부위"}를 보여주세요"
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun DetectionChip(detection: AIDetection) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = detection.detected,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun AnalysisProgressOverlay(
    progress: AnalysisProgress?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                progress = { (progress?.percentage ?: 0) / 100f },
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = progress?.step ?: "분석 중...",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = progress?.message ?: "잠시만 기다려주세요",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${progress?.percentage ?: 0}%",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun BottomControls(
    isVideoEnabled: Boolean,
    isAudioEnabled: Boolean,
    isEnding: Boolean,
    onToggleVideo: () -> Unit,
    onToggleAudio: () -> Unit,
    onSwitchCamera: () -> Unit,
    onCaptureTongue: () -> Unit,
    onCaptureFacial: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(32.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Toggle video
        IconButton(
            onClick = onToggleVideo,
            modifier = Modifier
                .size(56.dp)
                .background(
                    if (isVideoEnabled) Color.DarkGray else Color.Red.copy(alpha = 0.8f),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                contentDescription = "영상 토글",
                tint = Color.White
            )
        }

        // Toggle audio
        IconButton(
            onClick = onToggleAudio,
            modifier = Modifier
                .size(56.dp)
                .background(
                    if (isAudioEnabled) Color.DarkGray else Color.Red.copy(alpha = 0.8f),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = if (isAudioEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = "마이크 토글",
                tint = Color.White
            )
        }

        // Switch camera
        IconButton(
            onClick = onSwitchCamera,
            modifier = Modifier
                .size(56.dp)
                .background(Color.DarkGray, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "카메라 전환",
                tint = Color.White
            )
        }

        // Capture tongue
        IconButton(
            onClick = onCaptureTongue,
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Text("혀", color = Color.White, fontWeight = FontWeight.Bold)
        }

        // End call
        IconButton(
            onClick = onEndCall,
            enabled = !isEnding,
            modifier = Modifier
                .size(64.dp)
                .background(Color.Red, CircleShape)
        ) {
            if (isEnding) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "통화 종료",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
