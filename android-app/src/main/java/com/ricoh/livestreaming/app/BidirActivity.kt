/*
 * Copyright 2019 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.app

import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import com.ricoh.livestreaming.*
import com.ricoh.livestreaming.app.AppAudioManager.AudioManagerEvents
import com.ricoh.livestreaming.theta.ThetaVideoEncoderFactory
import com.ricoh.livestreaming.webrtc.Camera2VideoCapturer
import com.ricoh.livestreaming.webrtc.CodecUtils
import kotlinx.android.synthetic.main.activity_bidir.*
import org.slf4j.LoggerFactory
import org.webrtc.*
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bidir)

        supportActionBar!!.hide()
        mEgl = EglBase.create()
        mViewLayoutManager = ViewLayoutManager(applicationContext, mEgl, view_layout)

        connect_button.setOnClickListener {
            if (mClient == null) {
                connect_button.text = getString(R.string.connecting)
                connect()
            } else {
                connect_button.text = getString(R.string.disconnecting)
                disconnect()
            }
        }

        update_connection_meta_button.setOnClickListener {
            try {
                mClient?.updateMeta(mapOf("connect_meta" to "new_connection_metadata"))
            } catch (e: SDKError) {
                LOGGER.error(e.toReportString())
            }
        }

        update_track_meta_button.setOnClickListener {
            if (localLSTracks.size > 0) {
                try {
                    mClient?.updateTrackMeta(localLSTracks[0], mapOf("track_metadata" to "new_track_metadata"))
                } catch (e: SDKError) {
                    LOGGER.error(e.toReportString())
                }
            }
        }

        roomId.setText(Config.getRoomId())

        // Camera list Spinner
        camera_list_spinner.adapter = CameraListAdapter(this)
        camera_list_spinner.isEnabled = true
        camera_list_spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // nothing to do.
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val cameraInfo: CameraInfo = camera_list_spinner.selectedItem as CameraInfo
                LOGGER.info("onItemSelected: ${cameraInfo.getName()}")

                if (mClient?.state == Client.State.OPEN) {
                    val track = localLSTracks.find { it.mediaStreamTrack is VideoTrack }
                    if (track == null) {
                        LOGGER.info("Not found video track")
                        return;
                    }

                    mVideoCapturer?.stop()
                    mVideoCapturer?.release()

                    var capWidth: Int
                    var capHeight: Int
                    if ((cap_spinner.getSelectedItem()) == "4K") {
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
        audio_list_spinner.adapter = mAudioListAdapter
        audio_list_spinner.isEnabled = true
        audio_list_spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // nothing to do.
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val audioDevice = audio_list_spinner.selectedItem as AppAudioManager.AudioDevice
                LOGGER.info("onItemSelected: ${audioDevice.deviceName}")

                mAppAudioManager!!.selectAudioDevice(audioDevice)
                if (mClient?.state == Client.State.OPEN) {
                    val track = localLSTracks.find { it.mediaStreamTrack is AudioTrack }
                    if (track == null) {
                        LOGGER.info("Not found audio track")
                        return;
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
        mic_mute_radio.setOnCheckedChangeListener { _, checkedId ->
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
        video_mute_radio.setOnCheckedChangeListener { _, checkedId ->
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
    }

    override fun onDestroy() {
        super.onDestroy()

        mEgl?.release()
        mEgl = null

        mAppAudioManager!!.stop()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (mClient?.state == Client.State.OPEN) {
            if (Config.getRoomType() == RoomSpec.RoomType.SFU) {
                update_meta_layout.visibility = View.VISIBLE
            }
            controls_layout.visibility = View.VISIBLE

            mHandler.removeCallbacksAndMessages(null)
            mHandler.postDelayed(Runnable {
                update_meta_layout.visibility = View.GONE
                controls_layout.visibility = View.GONE
            }, 3000)
        }

        return super.dispatchTouchEvent(ev)
    }


    private fun connect() = executor.safeSubmit {
        var roomId = roomId.text.toString()

        var capWidth = 0
        var capHeight = 0
        var videoBitrate = BuildConfig.VIDEO_BITRATE

        if ((cap_spinner.getSelectedItem()) == "4K") {
            capWidth = 3840
            capHeight = 2160
        } else {
            capWidth = 1920
            capHeight = 1080
            videoBitrate = videoBitrate / 4
        }

        LOGGER.info("Try to connect. RoomType={}", Config.getRoomType())
        val cameraInfo: CameraInfo = camera_list_spinner.selectedItem as CameraInfo
        mVideoCapturer = Camera2VideoCapturer(applicationContext, cameraInfo.cameraId, capWidth, capHeight, 30)

        val roomSpec = RoomSpec(Config.getRoomType())
        val accessToken = JwtAccessToken.createAccessToken(BuildConfig.CLIENT_SECRET, roomId, roomSpec)

        val eglContext = mEgl!!.eglBaseContext as EglBase14.Context
        mClient = Client(
                applicationContext,
                eglContext,
                ThetaVideoEncoderFactory(
                        eglContext,
                        listOf(
                                CodecUtils.VIDEO_CODEC_INFO_VP8,
                                CodecUtils.VIDEO_CODEC_INFO_H264,
                                CodecUtils.VIDEO_CODEC_INFO_H264_HIGH_PROFILE
                        )
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
            val muteType = when (mic_mute_radio.checkedRadioButtonId) {
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
            val muteType = when (video_mute_radio.checkedRadioButtonId) {
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

            val trackOption = LSTrackOption.Builder()
                    .meta(mapOf("track_metadata" to "video"))
                    .muteType(muteType)
                    .build()
            localLSTracks.add(LSTrack(track, stream, trackOption))
        }

        val option = Option.Builder()
                .loggingSeverity(Config.getLoggingSeverity())
                .localLSTracks(localLSTracks)
                .meta(mapOf("connect_meta" to "android"))
                .sending(SendingOption(
                        SendingVideoOption.Builder()
                                .videoCodecType(SendingVideoOption.VideoCodecType.H264)
                                .sendingPriority(SendingVideoOption.SendingPriority.HIGH)
                                .maxBitrateKbps(videoBitrate)
                                .build()))
                .build()

        mClient!!.connect(
                BuildConfig.CLIENT_ID,
                accessToken,
                option)
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

        override fun onConnecting() {
            LOGGER.debug("Client#onConnecting")

            runOnUiThread {
                connect_button.isEnabled = false
                connect_button.text = getString(R.string.connecting)
                camera_list_spinner.isEnabled = false
                audio_list_spinner.isEnabled = false
            }
        }

        override fun onOpen() {
            LOGGER.debug("Client#onOpen")

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
                connect_button.text = getString(R.string.disconnect)
                connect_button.isEnabled = true
                mHandler.postDelayed(Runnable {
                    update_meta_layout.visibility = View.GONE
                    controls_layout.visibility = View.GONE
                }, 3000)
            }
        }

        override fun onClosing() {
            LOGGER.debug("Client#onClosing")

            runOnUiThread {
                mHandler.removeCallbacksAndMessages(null)
                update_meta_layout.visibility = View.GONE
                controls_layout.visibility = View.VISIBLE
                connect_button.isEnabled = false
                connect_button.text = getString(R.string.disconnecting)
                camera_list_spinner.isEnabled = false
                audio_list_spinner.isEnabled = false
            }
        }

        override fun onClosed() {
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
                connect_button.text = getString(R.string.connect)
                connect_button.isEnabled = true
                camera_list_spinner.isEnabled = true
                audio_list_spinner.isEnabled = true
                mViewLayoutManager!!.clear()
            }
        }

        override fun onAddLocalTrack(track: MediaStreamTrack, stream: MediaStream) {
            LOGGER.debug("Client#onAddLocalTrack({})", track.id())

            if (track is VideoTrack) {
                runOnUiThread {
                    mViewLayoutManager!!.addLocalTrack(track)
                }
            }
        }

        override fun onAddRemoteConnection(connectionId: String, metadata: Map<String, Any>) {
            LOGGER.debug("Client#onAddRemoteConnection(connectionId = ${connectionId})")

            for ((key, value) in metadata) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }

            runOnUiThread {
                camera_list_spinner.isEnabled = true
                audio_list_spinner.isEnabled = true
            }
        }

        override fun onRemoveRemoteConnection(connectionId: String, metadata: Map<String, Any>, mediaStreamTracks: List<MediaStreamTrack>) {
            LOGGER.debug("Client#onRemoveRemoteConnection(connectionId = ${connectionId})")

            for ((key, value) in metadata) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }

            for (mediaStreamTrack in mediaStreamTracks) {
                LOGGER.debug("mediaStreamTrack={}", mediaStreamTrack)
            }

            runOnUiThread {
                mViewLayoutManager!!.removeRemoteTrack(connectionId)
            }
        }

        override fun onAddRemoteTrack(connectionId: String, stream: MediaStream, track: MediaStreamTrack, metadata: Map<String, Any>, muteType: MuteType) {
            LOGGER.debug("Client#onAddRemoteTrack({} {}, {}, {})", connectionId, stream.id, track.id(), muteType)

            for ((key, value) in metadata) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }

            if (track is VideoTrack) {
                runOnUiThread {
                    mViewLayoutManager!!.addRemoteTrack(connectionId, track)
                }
            }
        }

        override fun onUpdateRemoteConnection(connectionId: String, metadata: Map<String, Any>) {
            LOGGER.debug("Client#onUpdateRemoteConnection(connectionId = ${connectionId})")

            for ((key, value) in metadata) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }
        }

        override fun onUpdateRemoteTrack(connectionId: String, stream: MediaStream, track: MediaStreamTrack, metadata: Map<String, Any>) {
            LOGGER.debug("Client#onUpdateRemoteTrack({} {}, {})", connectionId, stream.id, track.id())

            for ((key, value) in metadata) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }
        }

        override fun onUpdateMute(connectionId: String, stream: MediaStream, track: MediaStreamTrack, muteType: MuteType) {
            LOGGER.debug("Client#onUpdateMute({} {}, {}, {})", connectionId, stream.id, track.id(), muteType)
        }

        override fun onChangeStability(connectionId: String, stability: Stability) {
            LOGGER.debug("Client#onChangeStability({}, {})", connectionId, stability)
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
}
