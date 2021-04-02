/*
 * Copyright 2020 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.app

import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.ricoh.livestreaming.*
import com.ricoh.livestreaming.theta.ThetaVideoEncoderFactory
import com.ricoh.livestreaming.uvc.UvcVideoCapturer
import com.ricoh.livestreaming.webrtc.CodecUtils
import kotlinx.android.synthetic.main.activity_uvc_camera.*
import org.slf4j.LoggerFactory
import org.webrtc.*
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class UvcCameraActivity : AppCompatActivity() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(UvcCameraActivity::class.java)
        private val LOCK = Object()
    }

    private val executor = Executors.newSingleThreadExecutor()

    private var mEgl: EglBase? = null

    private var mClient: Client? = null

    private var mRtcStatsLogger: RTCStatsLogger? = null

    private var mVideoCapturer: UvcVideoCapturer? = null

    private var mStatsTimer: Timer? = null

    private var mAdapter: UvcFormatListAdapter? = null

    private val mCameraFormats = arrayListOf<UvcVideoCapturer.Format>()

    private var mHandler = Handler()

    private var mViewLayoutManager: ViewLayoutManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_uvc_camera)

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

        roomId.setText(Config.getRoomId())
        connect_button.isEnabled = false
        mVideoCapturer = UvcVideoCapturer(applicationContext)
                .apply {
                    setEventListener(UvcVideoCapturerListener())
                }

        // Camera list Spinner
        mAdapter = UvcFormatListAdapter(this)
        cap_spinner.adapter = mAdapter
        cap_spinner.isEnabled = true
    }

    override fun onDestroy() {
        super.onDestroy()

        mEgl?.release()
        mEgl = null
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (mClient?.state == Client.State.OPEN) {
            controls_layout.visibility = View.VISIBLE

            mHandler.removeCallbacksAndMessages(null)
            mHandler.postDelayed(Runnable {
                controls_layout.visibility = View.GONE
            }, 3000)
        }

        return super.dispatchTouchEvent(ev)
    }


    private fun connect() = executor.safeSubmit {
        var roomId = roomId.text.toString()

        val capWidth: Int
        val capHeight: Int
        val videoBitrate = BuildConfig.VIDEO_BITRATE

        val format = mCameraFormats.get(cap_spinner.selectedItemPosition)
        LOGGER.info("Try to connect. RoomType={} CameraFormat={}", Config.getRoomType(), format)
        mVideoCapturer?.setConfig(format)

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
        val lsTracks = arrayListOf<LSTrack>()
        for (track in stream.audioTracks) {
            val trackOption = LSTrackOption.Builder()
                    .meta(mapOf("track_metadata" to "audio"))
                    .build()
            lsTracks.add(LSTrack(track, stream, trackOption))
        }
        for (track in stream.videoTracks) {
            val trackOption = LSTrackOption.Builder()
                    .meta(mapOf("track_metadata" to "video"))
                    .build()
            lsTracks.add(LSTrack(track, stream, trackOption))
        }

        val option = Option.Builder()
                .loggingSeverity(Config.getLoggingSeverity())
                .localLSTracks(lsTracks)
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
                cap_spinner.isEnabled = false
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
                    controls_layout.visibility = View.GONE
                }, 3000)
            }
        }

        override fun onClosing() {
            LOGGER.debug("Client#onClosing")

            runOnUiThread {
                connect_button.isEnabled = false
                connect_button.text = getString(R.string.disconnecting)
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

                mVideoCapturer = UvcVideoCapturer(applicationContext)
                        .apply {
                            setEventListener(UvcVideoCapturerListener())
                        }
            }

            runOnUiThread {
                mHandler.removeCallbacksAndMessages(null)
                controls_layout.visibility = View.VISIBLE

                connect_button.text = getString(R.string.connect)
                connect_button.isEnabled = true
                cap_spinner.isEnabled = true
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
            LOGGER.error("Client#onError({})", error.toReportString())
        }
    }

    inner class UvcVideoCapturerListener : UvcVideoCapturer.Listener {
        override fun onDeviceOpened(formats: List<UvcVideoCapturer.Format>) {
            LOGGER.debug("UvcVideoCapturerListener#onDeviceOpened()")

            for (format in formats) {
                LOGGER.debug("device format={}", format)
            }

            runOnUiThread {
                mCameraFormats.clear()
                mCameraFormats.addAll(formats)
                mAdapter!!.clear()
                mAdapter!!.addAll(formats)

                connect_button.isEnabled = true
            }
        }

        override fun onDeviceDetached() {
            LOGGER.debug("UvcVideoCapturerListener#onDeviceDetached()")
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
