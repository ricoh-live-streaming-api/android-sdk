/*
 * Copyright 2021 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.app

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.ricoh.livestreaming.*
import com.ricoh.livestreaming.theta.ThetaVideoEncoderFactory
import com.ricoh.livestreaming.webrtc.CodecUtils
import com.ricoh.livestreaming.webrtc.ScreenCapturer
import org.slf4j.LoggerFactory
import org.webrtc.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class MediaProjectionService : Service() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MediaProjectionService::class.java)
        private const val NOTIFICATION_ID = 1
        private val LOCK = Object()
        private val LOCAL_VIDEO_TRACK_LOCK = Object()
    }

    private lateinit var mEgl: EglBase

    private var mClient: Client? = null

    private var mClientListener: Client.Listener? = null

    private var mRtcStatsLogger: RTCStatsLogger? = null

    private var mVideoCapturer: ScreenCapturer? = null

    private var mStatsTimer: Timer? = null

    private val executor = Executors.newSingleThreadExecutor()

    private var mLocalVideoTrack: VideoTrack? = null
    private val mRemoteVideoTracks: ConcurrentHashMap<String, Pair<VideoTrack, VideoRequirement>> = ConcurrentHashMap()

    inner class MediaProjectionServiceBinder : Binder() {
        fun getService(): MediaProjectionService = this@MediaProjectionService
    }

    override fun onBind(intent: Intent?): IBinder {
        return MediaProjectionServiceBinder()
    }

    override fun onCreate() {
        super.onCreate()
        LOGGER.debug("onCreate()")

        mEgl = EglBase.create()
        showNotification()
    }

    override fun onDestroy() {
        super.onDestroy()
        LOGGER.debug("onDestroy()")

        executor.safeSubmit {
            mClient?.disconnect()
        }.get()

        mEgl.release()

        mRemoteVideoTracks.clear()
        clearNotification()
    }

    fun connect(roomId: String, mediaProjectionPermissionResultData: Intent) = executor.safeSubmit {
        val videoBitrate = BuildConfig.VIDEO_BITRATE

        LOGGER.info("Try to connect. RoomType={}", Config.getRoomType())
        val eglContext = mEgl.eglBaseContext as EglBase14.Context
        val outputVideoWidth = 1920
        val outputVideoHeight = 1080

        mVideoCapturer = ScreenCapturer(
                applicationContext, mediaProjectionPermissionResultData, outputVideoWidth, outputVideoHeight)

        val roomSpec = RoomSpec(Config.getRoomType())
        val accessToken = JwtAccessToken.createAccessToken(BuildConfig.CLIENT_SECRET, roomId, roomSpec)

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
        val localLSTracks = arrayListOf<LSTrack>()
        for (track in stream.audioTracks) {
            val trackOption = LSTrackOption.Builder()
                    .meta(mapOf("track_metadata" to "audio"))
                    .build()
            localLSTracks.add(LSTrack(track, stream, trackOption))
        }
        for (track in stream.videoTracks) {
            val trackOption = LSTrackOption.Builder()
                    .meta(mapOf("track_metadata" to "video"))
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
                .iceServersProtocol(Config.getIceServersProtocol())
                .build()

        mClient!!.connect(
                BuildConfig.CLIENT_ID,
                accessToken,
                option)
    }

    fun disconnect() = executor.safeSubmit {
        mClient?.disconnect()
    }

    fun getEglBase(): EglBase {
        return mEgl
    }

    fun changeMediaRequirements(connectionId: String, videoRequirement: VideoRequirement) {
        mClient?.changeMediaRequirements(connectionId, videoRequirement)

        mRemoteVideoTracks[connectionId]?.let {
            mRemoteVideoTracks.replace(connectionId, Pair(it.first, videoRequirement))
        }
    }

    fun setClientListener(listener: Client.Listener?) {
        mClientListener = listener
    }

    fun getClientState(): Client.State {
        synchronized(LOCK) {
            return if (mClient == null) {
                Client.State.CLOSED
            } else {
                mClient!!.state
            }
        }
    }

    fun getLocalVideoTrack(): VideoTrack? {
        synchronized(LOCAL_VIDEO_TRACK_LOCK) {
            return mLocalVideoTrack
        }
    }

    fun getRemoteVideoTracks(): Map<String, Pair<VideoTrack, VideoRequirement>> {
        return Collections.unmodifiableMap(mRemoteVideoTracks)
    }

    private fun showNotification() {
        val notificationHelper = NotificationHelper(applicationContext)

        // Notificationの表示
        val notification = notificationHelper.getNotification(
                getString(R.string.app_name),
                getString(R.string.running))
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun clearNotification() {
        stopForeground(true)
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

    inner class ClientListener : Client.Listener {

        override fun onConnecting(event: LSConnectingEvent) {
            LOGGER.debug("Client#onConnecting")
            mClientListener?.onConnecting(event)
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

            mClientListener?.onOpen(event)
        }

        override fun onClosing(event: LSClosingEvent) {
            LOGGER.debug("Client#onClosing")
            mClientListener?.onClosing(event)
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

            synchronized(LOCAL_VIDEO_TRACK_LOCK) {
                mLocalVideoTrack = null
            }
            mRemoteVideoTracks.clear()
            mClientListener?.onClosed(event)
        }

        override fun onAddLocalTrack(event: LSAddLocalTrackEvent) {
            val track = event.mediaStreamTrack
            LOGGER.debug("Client#onAddLocalTrack({})", track.id())

            if (track is VideoTrack) {
                synchronized(LOCAL_VIDEO_TRACK_LOCK) {
                    mLocalVideoTrack = track
                }
            }
            mClientListener?.onAddLocalTrack(event)
        }

        override fun onAddRemoteConnection(event: LSAddRemoteConnectionEvent) {
            LOGGER.debug("Client#onAddRemoteConnection(connectionId = ${event.connectionId})")
            mClientListener?.onAddRemoteConnection(event)
        }

        override fun onRemoveRemoteConnection(event: LSRemoveRemoteConnectionEvent) {
            LOGGER.debug("Client#onRemoveRemoteConnection(connectionId = ${event.connectionId})")

            mRemoteVideoTracks.remove(event.connectionId)
            mClientListener?.onRemoveRemoteConnection(event)
        }

        override fun onAddRemoteTrack(event: LSAddRemoteTrackEvent) {
            val track = event.mediaStreamTrack
            LOGGER.debug("Client#onAddRemoteTrack({} {}, {}, {})", event.connectionId, event.stream.id, track.id(), event.mute)

            if (track is VideoTrack) {
                mRemoteVideoTracks[event.connectionId] = Pair(track, VideoRequirement.REQUIRED)
            }
            mClientListener?.onAddRemoteTrack(event)
        }

        override fun onUpdateRemoteConnection(event: LSUpdateRemoteConnectionEvent) {
            LOGGER.debug("Client#onUpdateRemoteConnection(connectionId = ${event.connectionId})")
            mClientListener?.onUpdateRemoteConnection(event)
        }

        override fun onUpdateRemoteTrack(event: LSUpdateRemoteTrackEvent) {
            LOGGER.debug("Client#onUpdateRemoteTrack({} {}, {})", event.connectionId, event.stream.id, event.mediaStreamTrack.id())
            mClientListener?.onUpdateRemoteTrack(event)
        }

        override fun onUpdateMute(event: LSUpdateMuteEvent) {
            LOGGER.debug("Client#onUpdateMute({} {}, {}, {})", event.connectionId, event.stream.id, event.mediaStreamTrack.id(), event.mute)
            mClientListener?.onUpdateMute(event)
        }

        override fun onChangeStability(event: LSChangeStabilityEvent) {
            LOGGER.debug("Client#onChangeStability({}, {})", event.connectionId, event.stability)
            mClientListener?.onChangeStability(event)
        }

        override fun onError(error: SDKErrorEvent) {
            LOGGER.error("Client#onError({}:{}:{}:{})", error.detail.type, error.detail.code, error.detail.error, error.toReportString())
            mClientListener?.onError(error)
        }
    }
}
