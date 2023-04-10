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
import com.ricoh.livestreaming.app.databinding.ActivityUvcCameraBinding
import com.ricoh.livestreaming.theta.ThetaVideoEncoderFactory
import com.ricoh.livestreaming.uvc.UvcVideoCapturer
import com.ricoh.livestreaming.webrtc.CodecUtils
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

    /** View Binding */
    private lateinit var mActivityUvcCameraBinding: ActivityUvcCameraBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        mActivityUvcCameraBinding = ActivityUvcCameraBinding.inflate(layoutInflater)
        setContentView(mActivityUvcCameraBinding.root)

        supportActionBar!!.hide()
        mEgl = EglBase.create()
        mViewLayoutManager = ViewLayoutManager(
                applicationContext,
                window,
                mEgl,
                mActivityUvcCameraBinding.viewLayout,
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

        mActivityUvcCameraBinding.connectButton.setOnClickListener {
            if (mClient == null) {
                mActivityUvcCameraBinding.connectButton.text = getString(R.string.connecting)
                connect()
            } else {
                mActivityUvcCameraBinding.connectButton.text = getString(R.string.disconnecting)
                disconnect()
            }
        }

        mActivityUvcCameraBinding.roomId.setText(Config.getRoomId())
        mActivityUvcCameraBinding.connectButton.isEnabled = false
        mVideoCapturer = UvcVideoCapturer(applicationContext)
                .apply {
                    setEventListener(UvcVideoCapturerListener())
                }

        // Camera list Spinner
        mAdapter = UvcFormatListAdapter(this)
        mActivityUvcCameraBinding.capSpinner.adapter = mAdapter
        mActivityUvcCameraBinding.capSpinner.isEnabled = true
    }

    override fun onDestroy() {
        super.onDestroy()

        mEgl?.release()
        mEgl = null
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (mClient?.state == Client.State.OPEN) {
            mActivityUvcCameraBinding.controlsLayout.visibility = View.VISIBLE

            mHandler.removeCallbacksAndMessages(null)
            mHandler.postDelayed({
                mActivityUvcCameraBinding.controlsLayout.visibility = View.GONE
            }, 3000)
        }

        return super.dispatchTouchEvent(ev)
    }


    private fun connect() = executor.safeSubmit {
        val roomId = mActivityUvcCameraBinding.roomId.text.toString()

        val videoBitrate = BuildConfig.VIDEO_BITRATE

        val format = mCameraFormats[mActivityUvcCameraBinding.capSpinner.selectedItemPosition]
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
                .iceServersProtocol(Config.getIceServersProtocol())
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

        override fun onConnecting(event: LSConnectingEvent) {
            LOGGER.debug("Client#onConnecting")

            runOnUiThread {
                mActivityUvcCameraBinding.connectButton.isEnabled = false
                mActivityUvcCameraBinding.connectButton.text = getString(R.string.connecting)
                mActivityUvcCameraBinding.capSpinner.isEnabled = false
            }
        }

        override fun onOpen(event: LSOpenEvent) {
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
                mActivityUvcCameraBinding.connectButton.text = getString(R.string.disconnect)
                mActivityUvcCameraBinding.connectButton.isEnabled = true
                mHandler.postDelayed({
                    mActivityUvcCameraBinding.controlsLayout.visibility = View.GONE
                }, 3000)
            }
        }

        override fun onClosing(event: LSClosingEvent) {
            LOGGER.debug("Client#onClosing")

            runOnUiThread {
                mActivityUvcCameraBinding.connectButton.isEnabled = false
                mActivityUvcCameraBinding.connectButton.text = getString(R.string.disconnecting)
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

                mVideoCapturer = UvcVideoCapturer(applicationContext)
                        .apply {
                            setEventListener(UvcVideoCapturerListener())
                        }
            }

            runOnUiThread {
                mHandler.removeCallbacksAndMessages(null)
                mActivityUvcCameraBinding.controlsLayout.visibility = View.VISIBLE

                mActivityUvcCameraBinding.connectButton.text = getString(R.string.connect)
                mActivityUvcCameraBinding.connectButton.isEnabled = true
                mActivityUvcCameraBinding.capSpinner.isEnabled = true
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

                mActivityUvcCameraBinding.connectButton.isEnabled = true
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

    private fun isKindOfSfu(roomType: RoomSpec.RoomType): Boolean {
        return roomType == RoomSpec.RoomType.SFU || roomType == RoomSpec.RoomType.SFU_LARGE
    }
}
