package com.amcg.mcg.app.data.services.consultation

import android.content.Context
import android.util.Log
import io.getstream.webrtc.android.ktx.stringify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebRTCService @Inject constructor(
    private val context: Context,
    private val socketService: SocketService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var eglBase: EglBase? = null

    private var localVideoSink: VideoSink? = null
    private var remoteVideoSink: VideoSink? = null

    private val _connectionState = MutableStateFlow<WebRTCState>(WebRTCState.Idle)
    val connectionState: StateFlow<WebRTCState> = _connectionState

    private val _localVideoEnabled = MutableStateFlow(true)
    val localVideoEnabled: StateFlow<Boolean> = _localVideoEnabled

    private val _localAudioEnabled = MutableStateFlow(true)
    val localAudioEnabled: StateFlow<Boolean> = _localAudioEnabled

    private var currentRoomId: String? = null
    private var currentSessionId: String? = null

    companion object {
        private const val TAG = "WebRTCService"
        private const val VIDEO_WIDTH = 640
        private const val VIDEO_HEIGHT = 480
        private const val VIDEO_FPS = 30
    }

    sealed class WebRTCState {
        object Idle : WebRTCState()
        object Initializing : WebRTCState()
        object Ready : WebRTCState()
        object Connecting : WebRTCState()
        object Connected : WebRTCState()
        data class Error(val message: String) : WebRTCState()
        object Disconnected : WebRTCState()
    }

    fun initialize() {
        if (peerConnectionFactory != null) {
            Log.d(TAG, "WebRTC already initialized")
            return
        }

        _connectionState.value = WebRTCState.Initializing

        try {
            // Initialize EGL
            eglBase = EglBase.create()

            // Initialize PeerConnectionFactory
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(true)
                    .createInitializationOptions()
            )

            val options = PeerConnectionFactory.Options()
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(
                    DefaultVideoEncoderFactory(
                        eglBase!!.eglBaseContext,
                        true,
                        true
                    )
                )
                .setVideoDecoderFactory(
                    DefaultVideoDecoderFactory(eglBase!!.eglBaseContext)
                )
                .createPeerConnectionFactory()

            _connectionState.value = WebRTCState.Ready
            Log.d(TAG, "WebRTC initialized successfully")

            // Setup socket listeners
            setupSocketListeners()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WebRTC", e)
            _connectionState.value = WebRTCState.Error(e.message ?: "Initialization failed")
        }
    }

    private fun setupSocketListeners() {
        scope.launch {
            // Handle incoming offer
            socketService.on("webrtc:offer").collect { data ->
                val peerId = data.optString("peerId")
                val offerObj = data.optJSONObject("offer")
                val sdp = offerObj?.optString("sdp") ?: return@collect

                Log.d(TAG, "Received offer from $peerId")
                handleRemoteOffer(sdp, peerId)
            }
        }

        scope.launch {
            // Handle incoming answer
            socketService.on("webrtc:answer").collect { data ->
                val answerObj = data.optJSONObject("answer")
                val sdp = answerObj?.optString("sdp") ?: return@collect

                Log.d(TAG, "Received answer")
                handleRemoteAnswer(sdp)
            }
        }

        scope.launch {
            // Handle ICE candidate
            socketService.on("webrtc:ice-candidate").collect { data ->
                val candidateObj = data.optJSONObject("candidate") ?: return@collect
                val candidate = candidateObj.optString("candidate")
                val sdpMid = candidateObj.optString("sdpMid")
                val sdpMLineIndex = candidateObj.optInt("sdpMLineIndex")

                Log.d(TAG, "Received ICE candidate")
                handleRemoteIceCandidate(candidate, sdpMid, sdpMLineIndex)
            }
        }

        scope.launch {
            // Handle peer joined
            socketService.on("webrtc:peer-joined").collect { data ->
                val peerId = data.optString("peerId")
                Log.d(TAG, "Peer joined: $peerId")
                // Create offer for the new peer
                createOffer()
            }
        }

        scope.launch {
            // Handle peer left
            socketService.on("webrtc:peer-left").collect { data ->
                val peerId = data.optString("peerId")
                Log.d(TAG, "Peer left: $peerId")
            }
        }

        scope.launch {
            // Handle room joined confirmation
            socketService.on("webrtc:room-joined").collect { data ->
                val existingPeers = data.optJSONArray("existingPeers")
                Log.d(TAG, "Joined room, existing peers: ${existingPeers?.length() ?: 0}")

                if ((existingPeers?.length() ?: 0) > 0) {
                    // Create offer to connect to existing peers
                    createOffer()
                }
            }
        }
    }

    fun startLocalMedia(localSink: VideoSink, remoteSink: VideoSink) {
        this.localVideoSink = localSink
        this.remoteVideoSink = remoteSink

        try {
            // Create video source
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase!!.eglBaseContext)
            val videoSource = peerConnectionFactory!!.createVideoSource(false)

            // Create video capturer (front camera)
            videoCapturer = createCameraCapturer()
            videoCapturer?.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
            videoCapturer?.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)

            // Create video track
            localVideoTrack = peerConnectionFactory!!.createVideoTrack("video0", videoSource)
            localVideoTrack?.setEnabled(true)
            localVideoTrack?.addSink(localSink)

            // Create audio source and track
            val audioConstraints = MediaConstraints()
            val audioSource = peerConnectionFactory!!.createAudioSource(audioConstraints)
            localAudioTrack = peerConnectionFactory!!.createAudioTrack("audio0", audioSource)
            localAudioTrack?.setEnabled(true)

            Log.d(TAG, "Local media started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start local media", e)
            _connectionState.value = WebRTCState.Error("Failed to start camera: ${e.message}")
        }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        // Try front camera first
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) {
                    return capturer
                }
            }
        }

        // Fallback to back camera
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) {
                    return capturer
                }
            }
        }

        return null
    }

    fun joinRoom(roomId: String, sessionId: String) {
        currentRoomId = roomId
        currentSessionId = sessionId

        _connectionState.value = WebRTCState.Connecting

        // Create peer connection
        createPeerConnection()

        // Join socket room
        socketService.joinRoom(roomId, sessionId)
    }

    private fun createPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                    Log.d(TAG, "Signaling state: $state")
                }

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Log.d(TAG, "ICE connection state: $state")
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED -> {
                            _connectionState.value = WebRTCState.Connected
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED,
                        PeerConnection.IceConnectionState.FAILED -> {
                            _connectionState.value = WebRTCState.Disconnected
                        }
                        else -> {}
                    }
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) {
                    Log.d(TAG, "ICE receiving: $receiving")
                }

                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                    Log.d(TAG, "ICE gathering state: $state")
                }

                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate?.let {
                        Log.d(TAG, "Local ICE candidate: ${it.sdp}")
                        currentRoomId?.let { roomId ->
                            socketService.sendIceCandidate(
                                roomId,
                                it.sdp,
                                it.sdpMid,
                                it.sdpMLineIndex
                            )
                        }
                    }
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
                    Log.d(TAG, "ICE candidates removed")
                }

                override fun onAddStream(stream: MediaStream?) {
                    Log.d(TAG, "Stream added: ${stream?.id}")
                }

                override fun onRemoveStream(stream: MediaStream?) {
                    Log.d(TAG, "Stream removed: ${stream?.id}")
                }

                override fun onDataChannel(channel: DataChannel?) {
                    Log.d(TAG, "Data channel: ${channel?.label()}")
                }

                override fun onRenegotiationNeeded() {
                    Log.d(TAG, "Renegotiation needed")
                }

                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                    Log.d(TAG, "Track added")
                    receiver?.track()?.let { track ->
                        if (track is VideoTrack) {
                            remoteVideoSink?.let { sink ->
                                track.addSink(sink)
                            }
                        }
                    }
                }
            }
        )

        // Add local tracks to peer connection
        localVideoTrack?.let { video ->
            peerConnection?.addTrack(video, listOf("stream0"))
        }
        localAudioTrack?.let { audio ->
            peerConnection?.addTrack(audio, listOf("stream0"))
        }
    }

    private fun createOffer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            currentRoomId?.let { roomId ->
                                socketService.sendOffer(roomId, it.description)
                            }
                        }
                        override fun onCreateFailure(error: String?) {
                            Log.e(TAG, "Set local description failed: $error")
                        }
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "Set local description failed: $error")
                        }
                    }, it)
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Create offer failed: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    private fun handleRemoteOffer(sdp: String, peerId: String) {
        val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, sdp)

        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                createAnswer(peerId)
            }
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Set remote offer failed: $error")
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Set remote offer failed: $error")
            }
        }, sessionDescription)
    }

    private fun createAnswer(peerId: String) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            currentRoomId?.let { roomId ->
                                socketService.sendAnswer(roomId, it.description, peerId)
                            }
                        }
                        override fun onCreateFailure(error: String?) {
                            Log.e(TAG, "Set local answer failed: $error")
                        }
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "Set local answer failed: $error")
                        }
                    }, it)
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Create answer failed: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    private fun handleRemoteAnswer(sdp: String) {
        val sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, sdp)

        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Remote answer set successfully")
            }
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Set remote answer failed: $error")
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Set remote answer failed: $error")
            }
        }, sessionDescription)
    }

    private fun handleRemoteIceCandidate(candidate: String, sdpMid: String, sdpMLineIndex: Int) {
        val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidate)
        peerConnection?.addIceCandidate(iceCandidate)
    }

    fun toggleVideo(): Boolean {
        val enabled = !(_localVideoEnabled.value)
        localVideoTrack?.setEnabled(enabled)
        _localVideoEnabled.value = enabled
        return enabled
    }

    fun toggleAudio(): Boolean {
        val enabled = !(_localAudioEnabled.value)
        localAudioTrack?.setEnabled(enabled)
        _localAudioEnabled.value = enabled
        return enabled
    }

    fun switchCamera() {
        (videoCapturer as? CameraVideoCapturer)?.switchCamera(null)
    }

    fun leaveRoom() {
        currentRoomId?.let { roomId ->
            socketService.leaveRoom(roomId)
        }

        peerConnection?.close()
        peerConnection = null

        _connectionState.value = WebRTCState.Disconnected
        currentRoomId = null
        currentSessionId = null
    }

    fun release() {
        leaveRoom()

        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        videoCapturer = null

        localVideoTrack?.dispose()
        localVideoTrack = null

        localAudioTrack?.dispose()
        localAudioTrack = null

        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null

        peerConnectionFactory?.dispose()
        peerConnectionFactory = null

        eglBase?.release()
        eglBase = null

        _connectionState.value = WebRTCState.Idle
    }

    fun getEglBase(): EglBase? = eglBase
}
