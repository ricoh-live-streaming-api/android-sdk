/*
 * Copyright 2019 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.app

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import com.ricoh.livestreaming.*
import com.ricoh.livestreaming.app.AppAudioManager.AudioManagerEvents
import com.ricoh.livestreaming.app.databinding.ActivityBidirBinding
import com.ricoh.livestreaming.theta.ThetaVideoEncoderFactory
import com.ricoh.livestreaming.webrtc.Camera2VideoCapturer
import com.ricoh.livestreaming.webrtc.CodecUtils
import org.slf4j.LoggerFactory
import org.webrtc.*
import java.io.File
import java.text.SimpleDateFormat
import java.net.URLEncoder
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class BidirActivity : AppCompatActivity() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(BidirActivity::class.java)
        private val LOCK = Object()
    }

    private val executor = Executors.newSingleThreadExecutor()

    private var mEgl: EglBase? = null

    private var mClient: Client? = null

    private var mRtcStatsLogger: RTCStatsLogger? = null

    private var mVideoCapturer: Camera2VideoCapturer? = null

    private var mStatsTimer: Timer? = null

    private val localLSTracks = arrayListOf<LSTrack>()

    private var mHandler = Handler()

    private var mAppAudioManager: AppAudioManager? = null

    private var mAudioListAdapter: AudioListAdapter? = null

    private var mViewLayoutManager: ViewLayoutManager? = null

    /** View Binding */
    private lateinit var mActivityBidirBinding: ActivityBidirBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mActivityBidirBinding = ActivityBidirBinding.inflate(layoutInflater)
        setContentView(mActivityBidirBinding.root)

        supportActionBar!!.hide()
        mEgl = EglBase.create()
        mViewLayoutManager = ViewLayoutManager(
                applicationContext,
                window,
                mEgl,
                mActivityBidirBinding.viewLayout,
                this.isKindOfSfu(Config.getRoomType()),
                object : ViewLayoutManager.Listener {
                    override fun onVideoReceiveCheckedChanged(connectionId: String, isChecked: Boolean) {
                        try {
                            val videoRequirement = if (isChecked) {
                                VideoRequirement.REQUIRED
                            } else {
                                VideoRequirement.UNREQUIRED
                            }
                            mClient?.changeMediaRequirements(connectionId, videoRequirement)
                        } catch (e: SDKError) {
                            LOGGER.error(e.toReportString())
                        }
                    }
                }
        )

        mActivityBidirBinding.connectButton.setOnClickListener {
            if (mClient == null) {
                mActivityBidirBinding.connectButton.text = getString(R.string.connecting)
                connect()
            } else {
                mActivityBidirBinding.connectButton.text = getString(R.string.disconnecting)
                disconnect()
            }
        }

        mActivityBidirBinding.updateConnectionMetaButton.setOnClickListener {
            try {
                mClient?.updateMeta(mapOf("connect_meta" to "new_connection_metadata"))
            } catch (e: SDKError) {
                LOGGER.error(e.toReportString())
            }
        }

        mActivityBidirBinding.updateTrackMetaButton.setOnClickListener {
            if (localLSTracks.size > 0) {
                try {
                    mClient?.updateTrackMeta(localLSTracks[0], mapOf("track_metadata" to "new_track_metadata"))
                } catch (e: SDKError) {
                    LOGGER.error(e.toReportString())
                }
            }
        }

        mActivityBidirBinding.roomId.setText(Config.getRoomId())

        // Camera list Spinner
        mActivityBidirBinding.cameraListSpinner.adapter = CameraListAdapter(this)
        mActivityBidirBinding.cameraListSpinner.isEnabled = true
        mActivityBidirBinding.cameraListSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // nothing to do.
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val cameraInfo: CameraInfo = mActivityBidirBinding.cameraListSpinner.selectedItem as CameraInfo
                LOGGER.info("onItemSelected: ${cameraInfo.getName()}")

                if (mClient?.state == Client.State.OPEN) {
                    val track = localLSTracks.find { it.mediaStreamTrack is VideoTrack }
                    if (track == null) {
                        LOGGER.info("Not found video track")
                        return
                    }

                    mVideoCapturer?.stop()
                    mVideoCapturer?.release()

                    val capWidth: Int
                    val capHeight: Int
                    if ((mActivityBidirBinding.resolutionSpinner.selectedItem) == "4K") {
                        capWidth = 3840
                        capHeight = 2160
                    } else {
                        capWidth = 1920
                        capHeight = 1080
                    }
                    mVideoCapturer = Camera2VideoCapturer(applicationContext, cameraInfo.cameraId, capWidth, capHeight, 30)
                    val constraints = MediaStreamConstraints.Builder()
                            .videoCapturer(mVideoCapturer!!)
                            .build()
                    try {
                        val stream = mClient!!.getUserMedia(constraints)
                        mClient?.replaceMediaStreamTrack(track, stream.videoTracks[0])
                        mVideoCapturer!!.start()
                        stream.videoTracks[0].addSink(mViewLayoutManager!!.getLocalView())
                    } catch (e: SDKError) {
                        LOGGER.error("Failed to replace media stream.{}", e.toReportString())
                    }
                }
            }
        }

        mAudioListAdapter = AudioListAdapter(this)
        mAppAudioManager = AppAudioManager(applicationContext)
        mAppAudioManager!!.start(object : AudioManagerEvents {
            override fun onAudioDeviceChanged(
                    selectedAudioDevice: AppAudioManager.AudioDevice?,
                    availableAudioDevices: Set<AppAudioManager.AudioDevice>) {
                mAudioListAdapter!!.clear()
                mAudioListAdapter!!.addAll(availableAudioDevices)
            }
        })
        mActivityBidirBinding.audioListSpinner.adapter = mAudioListAdapter
        mActivityBidirBinding.audioListSpinner.isEnabled = true
        mActivityBidirBinding.audioListSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // nothing to do.
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val audioDevice = mActivityBidirBinding.audioListSpinner.selectedItem as AppAudioManager.AudioDevice
                LOGGER.info("onItemSelected: ${audioDevice.deviceName}")

                mAppAudioManager!!.selectAudioDevice(audioDevice)
                if (mClient?.state == Client.State.OPEN) {
                    val track = localLSTracks.find { it.mediaStreamTrack is AudioTrack }
                    if (track == null) {
                        LOGGER.info("Not found audio track")
                        return
                    }

                    val constraints = MediaStreamConstraints.Builder()
                            .audio(true)
                            .build()
                    try {
                        val stream = mClient!!.getUserMedia(constraints)
                        mClient?.replaceMediaStreamTrack(track, stream.audioTracks[0])
                    } catch (e: SDKError) {
                        LOGGER.error("Failed to replace media stream.{}", e.toReportString())
                    }
                }
            }
        }

        // mic mute
        mActivityBidirBinding.micMuteRadio.setOnCheckedChangeListener { _, checkedId ->
            if (mClient?.state == Client.State.OPEN) {
                val muteType = when (checkedId) {
                    R.id.mic_unmute -> {
                        MuteType.UNMUTE
                    }
                    R.id.mic_soft_mute -> {
                        MuteType.SOFT_MUTE
                    }
                    else -> {
                        MuteType.HARD_MUTE
                    }
                }
                val track = localLSTracks.find { it.mediaStreamTrack is AudioTrack }
                if (track == null) {
                    LOGGER.info("Not found audio track")
                } else {
                    try {
                        mClient?.changeMute(track, muteType)
                    } catch (e: SDKError) {
                        LOGGER.error(e.toReportString())
                    }
                }
            }
        }

        // video mute
        mActivityBidirBinding.videoMuteRadio.setOnCheckedChangeListener { _, checkedId ->
            if (mClient?.state == Client.State.OPEN) {
                val muteType = when (checkedId) {
                    R.id.video_unmute -> {
                        MuteType.UNMUTE
                    }
                    R.id.video_soft_mute -> {
                        MuteType.SOFT_MUTE
                    }
                    else -> {
                        MuteType.HARD_MUTE
                    }
                }
                val track = localLSTracks.find { it.mediaStreamTrack is VideoTrack }
                if (track == null) {
                    LOGGER.info("Not found video track")
                } else {
                    try {
                        mClient?.changeMute(track, muteType)
                    } catch (e: SDKError) {
                        LOGGER.error(e.toReportString())
                    }
                }
            }
        }

        // video bitrate
        mActivityBidirBinding.videoBitrateSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // nothing to do.
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val bitrate = mActivityBidirBinding.videoBitrateSpinner.selectedItem.toString().toInt()
                LOGGER.info("onItemSelected: Bitrate=$bitrate")

                if (mClient?.state == Client.State.OPEN) {
                    val track = localLSTracks.find { it.mediaStreamTrack is VideoTrack }
                    if (track == null) {
                        LOGGER.info("Not found video track")
                        return
                    }
                    try {
                        mClient!!.changeVideoSendBitrate(bitrate)
                    } catch (e: SDKError) {
                        LOGGER.error("Failed to change video bitrate.{}", e.toReportString())
                    }
                }
            }
        }

        // video framerate
        mActivityBidirBinding.videoFramerateSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // nothing to do.
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val framerate = mActivityBidirBinding.videoFramerateSpinner.selectedItem.toString().toInt()
                LOGGER.info("onItemSelected: Framerate=$framerate")

                if (mClient?.state == Client.State.OPEN) {
                    if (localLSTracks.find { it.mediaStreamTrack is VideoTrack } == null) {
                        LOGGER.info("Not found video track")
                        return
                    }
                    try {
                        mClient!!.changeVideoSendFramerate(framerate)
                    } catch (e: SDKError) {
                        LOGGER.error("Failed to change video framerate.{}", e.toReportString())
                    }
                }
            }
        }

        // proxy
        mActivityBidirBinding.proxyAddress.isEnabled = false
        mActivityBidirBinding.proxyPort.isEnabled = false
        mActivityBidirBinding.proxyAuthentication.isEnabled = false
        mActivityBidirBinding.proxyUser.isEnabled = false
        mActivityBidirBinding.proxyPassword.isEnabled = false
        mActivityBidirBinding.useProxy.setOnClickListener() {
            if (mActivityBidirBinding.useProxy.isChecked) {
                mActivityBidirBinding.proxyAddress.isEnabled = true
                mActivityBidirBinding.proxyPort.isEnabled = true
                mActivityBidirBinding.proxyAuthentication.isEnabled = true
                if (mActivityBidirBinding.proxyAuthentication.isChecked) {
                    mActivityBidirBinding.proxyUser.isEnabled = true
                    mActivityBidirBinding.proxyPassword.isEnabled = true
                }
            } else {
                mActivityBidirBinding.proxyAddress.isEnabled = false
                mActivityBidirBinding.proxyPort.isEnabled = false
                mActivityBidirBinding.proxyAuthentication.isEnabled = false
                mActivityBidirBinding.proxyUser.isEnabled = false
                mActivityBidirBinding.proxyPassword.isEnabled = false
            }
        }
        mActivityBidirBinding.proxyAuthentication.setOnClickListener() {
            if (mActivityBidirBinding.proxyAuthentication.isChecked) {
                mActivityBidirBinding.proxyUser.isEnabled = true
                mActivityBidirBinding.proxyPassword.isEnabled = true
            } else {
                mActivityBidirBinding.proxyUser.isEnabled = false
                mActivityBidirBinding.proxyPassword.isEnabled = false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        mEgl?.release()
        mEgl = null

        mAppAudioManager!!.stop()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (mClient?.state == Client.State.OPEN) {
            if (this.isKindOfSfu(Config.getRoomType())) {
                mActivityBidirBinding.updateMetaLayout.visibility = View.VISIBLE
            }
            mActivityBidirBinding.controlsLayout.visibility = View.VISIBLE

            mHandler.removeCallbacksAndMessages(null)
            mHandler.postDelayed({
                mActivityBidirBinding.updateMetaLayout.visibility = View.GONE
                mActivityBidirBinding.controlsLayout.visibility = View.GONE
            }, 3000)
        }

        return super.dispatchTouchEvent(ev)
    }

    /**
     * libwebrtcログはConnectするごとにログを削除して再作成する仕組みのため
     * 過去実行時のログを残すには退避処理が必要になります。
     */
    @SuppressLint("SimpleDateFormat")
    private fun saveLibWebrtcLog() {
        getExternalFilesDir(null)!!.resolve("logs").resolve("libwebrtc").let { libWebrtcLogDir ->
            libWebrtcLogDir.listFiles()?.forEach {
                // libwebrtcログファイル名は"webrtc_log_{連番}"として作成されるため、
                // 過去実行時ログを見つけるために"webrtc"で始まるログを検索します。
                if (it.isFile && it.name.startsWith("webrtc")) {
                    it.renameTo(File("${libWebrtcLogDir.absolutePath}/${SimpleDateFormat("yyyyMMdd'T'HHmmss").format(it.lastModified())}_${it.name}"))
                }
            }
        }
    }

    private fun connect() = executor.safeSubmit {
        // 過去実行時のlibwebrtcログがある場合は退避します。
        saveLibWebrtcLog()

        val roomId = mActivityBidirBinding.roomId.text.toString()
        val proxy: String? = createURL()

        val capWidth: Int
        val capHeight: Int
        val videoBitrate = getMaxBitrate()

        if ((mActivityBidirBinding.resolutionSpinner.selectedItem) == "4K") {
            capWidth = 3840
            capHeight = 2160
        } else {
            capWidth = 1920
            capHeight = 1080
        }

        LOGGER.info("Try to connect. RoomType={}", Config.getRoomType())
        val cameraInfo: CameraInfo = mActivityBidirBinding.cameraListSpinner.selectedItem as CameraInfo
        mVideoCapturer = Camera2VideoCapturer(applicationContext, cameraInfo.cameraId, capWidth, capHeight, 30)

        val roomSpec = RoomSpec(Config.getRoomType())
        val accessToken = JwtAccessToken.createAccessToken(BuildConfig.CLIENT_SECRET, roomId, roomSpec)

        val eglContext = mEgl!!.eglBaseContext as EglBase14.Context
        mClient = Client(
                applicationContext,
                eglContext,
                ThetaVideoEncoderFactory(
                        eglContext,
                        CodecUtils.getSupportedEncoderCodecInfo(applicationContext)
                )
        ).apply {
            setEventListener(ClientListener())
        }

        val constraints = MediaStreamConstraints.Builder()
                .videoCapturer(mVideoCapturer!!)
                .audio(true)
                .build()

        val stream = mClient!!.getUserMedia(constraints)
        localLSTracks.clear()
        for (track in stream.audioTracks) {
            val muteType = when (mActivityBidirBinding.micMuteRadio.checkedRadioButtonId) {
                R.id.mic_unmute -> {
                    MuteType.UNMUTE
                }
                R.id.mic_soft_mute -> {
                    MuteType.SOFT_MUTE
                }
                else -> {
                    MuteType.HARD_MUTE
                }
            }

            val trackOption = LSTrackOption.Builder()
                    .meta(mapOf("track_metadata" to "audio"))
                    .muteType(muteType)
                    .build()
            localLSTracks.add(LSTrack(track, stream, trackOption))
        }
        for (track in stream.videoTracks) {
            val trackOption = LSTrackOption.Builder()
                    .meta(mapOf("track_metadata" to "video"))
                    .muteType(getMuteType())
                    .build()
            localLSTracks.add(LSTrack(track, stream, trackOption))
        }

        val (isSending, isReceiving) = when (mActivityBidirBinding.sendRecvRadio.checkedRadioButtonId) {
            R.id.send_recv -> Pair(first = true, second = true)
            R.id.send_only -> Pair(first = true, second = false)
            else -> Pair(first = false, second = true)
        }

        val libWebrtcLogFilePath = getExternalFilesDir(null)!!
                .resolve("logs")
                .resolve("libwebrtc")
                .absolutePath
        val libWebrtcLogOptions = LibWebrtcLogOption.Builder(libWebrtcLogFilePath)
                .maxTotalFileSize(4)
                .logLevel(LibWebrtcLogLevel.INFO)
                .build()
        try {
            mClient!!.setLibWebrtcLogOption(libWebrtcLogOptions)
        } catch (e: SDKError) {
            LOGGER.error(e.toReportString())
            return@safeSubmit
        }

        val option = Option.Builder()
                .loggingSeverity(Config.getLoggingSeverity())
                .apply{
                    if (isSending) {
                        this.localLSTracks(localLSTracks)
                    }
                }
                .meta(mapOf("connect_meta" to "android"))
                .sending(SendingOption(
                        SendingVideoOption.Builder()
                                .videoCodecType(SendingVideoOption.VideoCodecType.H264)
                                .sendingPriority(SendingVideoOption.SendingPriority.HIGH)
                                .maxBitrateKbps(videoBitrate)
                                .muteType(getMuteType())
                                .build(), isSending))
                .receiving(ReceivingOption(isReceiving))
                .iceServersProtocol(Config.getIceServersProtocol())
                .apply {
                    if (isProxy(proxy)) {
                        this.proxy(proxy)
                    }
                }
                .build()

        mClient!!.connect(
                BuildConfig.CLIENT_ID,
                accessToken,
                option)
    }

    private fun getMaxBitrate(): Int {
        return mActivityBidirBinding.videoBitrateSpinner.adapter.let {
            var maxBitrate = it.getItem(0).toString().toInt()
            for (i in 0 until it.count) {
                maxBitrate = maxOf(maxBitrate, it.getItem(i).toString().toInt())
            }
            maxBitrate
        }
    }

    private fun getMuteType(): MuteType {
        return when (mActivityBidirBinding.videoMuteRadio.checkedRadioButtonId) {
            R.id.video_unmute -> {
                MuteType.UNMUTE
            }
            R.id.video_soft_mute -> {
                MuteType.SOFT_MUTE
            }
            else -> {
                MuteType.HARD_MUTE
            }
        }
    }

    private fun disconnect() = executor.safeSubmit {
        mClient!!.disconnect()
    }

    override fun onPause() {
        super.onPause()

        executor.safeSubmit {
            mClient?.disconnect()
        }.get()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        mViewLayoutManager!!.onConfigurationChanged()
    }

    inner class ClientListener : Client.Listener {

        override fun onConnecting(event: LSConnectingEvent) {
            LOGGER.debug("Client#onConnecting")

            runOnUiThread {
                mActivityBidirBinding.connectButton.isEnabled = false
                mActivityBidirBinding.connectButton.text = getString(R.string.connecting)
                mActivityBidirBinding.cameraListSpinner.isEnabled = false
                mActivityBidirBinding.audioListSpinner.isEnabled = false
                mActivityBidirBinding.sendRecv.isEnabled = false
                mActivityBidirBinding.sendOnly.isEnabled = false
                mActivityBidirBinding.recvOnly.isEnabled = false
            }
        }

        override fun onOpen(event: LSOpenEvent) {
            LOGGER.debug("Client#onOpen(accessTokenJson = ${event.accessTokenJson})")

            // == For WebRTC Internal Tracing Capture.
            // == "/sdcard/{LOGS_DIR}/{date}T{time}.log.json" will be created.
            //
            // if (!PeerConnectionFactory.startInternalTracingCapture(createLogFile().absolutePath + ".json")) {
            //     LOGGER.error("failed to start internal tracing capture")
            // }

            val file = createLogFile()
            LOGGER.info("create log file: ${file.path}")
            mRtcStatsLogger = RTCStatsLogger(file)
            mVideoCapturer?.start()

            mStatsTimer = Timer(true)
            mStatsTimer?.schedule(object : TimerTask() {
                override fun run() {
                    synchronized(LOCK) {
                        if (mClient != null) {
                            val stats = mClient!!.stats
                            for ((key, value) in stats) {
                                mRtcStatsLogger?.log(key, value)
                            }
                        }
                    }
                }
            }, 0, 1000)

            runOnUiThread {
                mActivityBidirBinding.connectButton.text = getString(R.string.disconnect)
                mActivityBidirBinding.connectButton.isEnabled = true
                mHandler.postDelayed({
                    mActivityBidirBinding.updateMetaLayout.visibility = View.GONE
                    mActivityBidirBinding.controlsLayout.visibility = View.GONE
                }, 3000)
            }
        }

        override fun onClosing(event: LSClosingEvent) {
            LOGGER.debug("Client#onClosing")

            runOnUiThread {
                mHandler.removeCallbacksAndMessages(null)
                mActivityBidirBinding.updateMetaLayout.visibility = View.GONE
                mActivityBidirBinding.controlsLayout.visibility = View.VISIBLE
                mActivityBidirBinding.connectButton.isEnabled = false
                mActivityBidirBinding.connectButton.text = getString(R.string.disconnecting)
                mActivityBidirBinding.cameraListSpinner.isEnabled = false
                mActivityBidirBinding.audioListSpinner.isEnabled = false
            }
        }

        override fun onClosed(event: LSClosedEvent) {
            LOGGER.debug("Client#onClosed")

            // == For WebRTC Internal Tracing
            //
            // PeerConnectionFactory.stopInternalTracingCapture()

            mStatsTimer?.cancel()
            mStatsTimer = null

            synchronized(LOCK) {
                mRtcStatsLogger?.close()
                mRtcStatsLogger = null

                mVideoCapturer?.stop()
                mVideoCapturer?.release()

                mClient?.setEventListener(null)
                mClient = null
            }

            runOnUiThread {
                mActivityBidirBinding.connectButton.text = getString(R.string.connect)
                mActivityBidirBinding.connectButton.isEnabled = true
                mActivityBidirBinding.cameraListSpinner.isEnabled = true
                mActivityBidirBinding.audioListSpinner.isEnabled = true
                mActivityBidirBinding.sendRecv.isEnabled = true
                mActivityBidirBinding.sendOnly.isEnabled = true
                mActivityBidirBinding.recvOnly.isEnabled = true
                mViewLayoutManager!!.clear()
            }
        }

        override fun onAddLocalTrack(event: LSAddLocalTrackEvent) {
            val track = event.mediaStreamTrack
            LOGGER.debug("Client#onAddLocalTrack({})", track.id())

            if (track is VideoTrack) {
                runOnUiThread {
                    mViewLayoutManager!!.addLocalTrack(track)
                }
            }
        }

        override fun onAddRemoteConnection(event: LSAddRemoteConnectionEvent) {
            LOGGER.debug("Client#onAddRemoteConnection(connectionId = ${event.connectionId})")

            for ((key, value) in event.meta) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }

            runOnUiThread {
                mActivityBidirBinding.cameraListSpinner.isEnabled = true
                mActivityBidirBinding.audioListSpinner.isEnabled = true
            }
        }

        override fun onRemoveRemoteConnection(event: LSRemoveRemoteConnectionEvent) {
            LOGGER.debug("Client#onRemoveRemoteConnection(connectionId = ${event.connectionId})")

            for ((key, value) in event.meta) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }

            for (mediaStreamTrack in event.mediaStreamTracks) {
                LOGGER.debug("mediaStreamTrack={}", mediaStreamTrack)
            }

            runOnUiThread {
                mViewLayoutManager!!.removeRemoteTrack(event.connectionId)
            }
        }

        override fun onAddRemoteTrack(event: LSAddRemoteTrackEvent) {
            val track = event.mediaStreamTrack
            LOGGER.debug("Client#onAddRemoteTrack({} {}, {}, {})", event.connectionId, event.stream.id, track.id(), event.mute)

            for ((key, value) in event.meta) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }

            if (track is VideoTrack) {
                runOnUiThread {
                    mViewLayoutManager!!.addRemoteTrack(event.connectionId, track)
                }
            }
        }

        override fun onUpdateRemoteConnection(event: LSUpdateRemoteConnectionEvent) {
            LOGGER.debug("Client#onUpdateRemoteConnection(connectionId = ${event.connectionId})")

            for ((key, value) in event.meta) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }
        }

        override fun onUpdateRemoteTrack(event: LSUpdateRemoteTrackEvent) {
            LOGGER.debug("Client#onUpdateRemoteTrack({} {}, {})", event.connectionId, event.stream.id, event.mediaStreamTrack.id())

            for ((key, value) in event.meta) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }
        }

        override fun onUpdateMute(event: LSUpdateMuteEvent) {
            LOGGER.debug("Client#onUpdateMute({} {}, {}, {})", event.connectionId, event.stream.id, event.mediaStreamTrack.id(), event.mute)
        }

        override fun onChangeStability(event: LSChangeStabilityEvent) {
            LOGGER.debug("Client#onChangeStability({}, {})", event.connectionId, event.stability)
        }

        override fun onError(error: SDKErrorEvent) {
            LOGGER.error("Client#onError({}:{}:{}:{})", error.detail.type, error.detail.code, error.detail.error, error.toReportString())
        }
    }

    private fun ExecutorService.safeSubmit(action: () -> Unit): Future<*> {
        return submit {
            try {
                action()
            } catch (e: Exception) {
                LOGGER.error("Uncaught Exception in Executor", e)
            }
        }
    }

    private fun isKindOfSfu(roomType: RoomSpec.RoomType): Boolean {
        return roomType == RoomSpec.RoomType.SFU || roomType == RoomSpec.RoomType.SFU_LARGE
    }

    private fun createURL(): String? {
        val useProxy: Boolean = mActivityBidirBinding.useProxy.isChecked
        val address: String = mActivityBidirBinding.proxyAddress.text.toString()
        val port: String = mActivityBidirBinding.proxyPort.text.toString()
        val authentication: Boolean = mActivityBidirBinding.proxyAuthentication.isChecked
        val user: String = mActivityBidirBinding.proxyUser.text.toString()
        val password: String = mActivityBidirBinding.proxyPassword.text.toString()
        return if (useProxy) {
            if (authentication) {
                val encodedUser: String = URLEncoder.encode(user, "UTF-8")
                val encodedPassword: String = URLEncoder.encode(password, "UTF-8")
                "http://$encodedUser:$encodedPassword@$address:$port/"
            } else {
                "http://$address:$port/"
            }
        } else {
            null
        }
    }

    private fun isProxy(proxy: String?): Boolean {
        return proxy != null
    }
}
