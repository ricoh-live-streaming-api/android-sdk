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

        override fun onConnecting() {
            LOGGER.debug("Client#onConnecting")
            mClientListener?.onConnecting()
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

            mClientListener?.onOpen()
        }

        override fun onClosing() {
            LOGGER.debug("Client#onClosing")
            mClientListener?.onClosing()
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

            synchronized(LOCAL_VIDEO_TRACK_LOCK) {
                mLocalVideoTrack = null
            }
            mRemoteVideoTracks.clear()
            mClientListener?.onClosed()
        }

        override fun onAddLocalTrack(track: MediaStreamTrack, stream: MediaStream) {
            LOGGER.debug("Client#onAddLocalTrack({})", track.id())

            if (track is VideoTrack) {
                synchronized(LOCAL_VIDEO_TRACK_LOCK) {
                    mLocalVideoTrack = track
                }
            }
            mClientListener?.onAddLocalTrack(track, stream)
        }

        override fun onAddRemoteConnection(connectionId: String, metadata: Map<String, Any>) {
            LOGGER.debug("Client#onAddRemoteConnection(connectionId = ${connectionId})")
            mClientListener?.onAddRemoteConnection(connectionId, metadata)
        }

        override fun onRemoveRemoteConnection(connectionId: String, metadata: Map<String, Any>, mediaStreamTracks: List<MediaStreamTrack>) {
            LOGGER.debug("Client#onRemoveRemoteConnection(connectionId = ${connectionId})")

            mRemoteVideoTracks.remove(connectionId)
            mClientListener?.onRemoveRemoteConnection(connectionId, metadata, mediaStreamTracks)
        }

        override fun onAddRemoteTrack(connectionId: String, stream: MediaStream, track: MediaStreamTrack, metadata: Map<String, Any>, muteType: MuteType) {
            LOGGER.debug("Client#onAddRemoteTrack({} {}, {}, {})", connectionId, stream.id, track.id(), muteType)

            if (track is VideoTrack) {
                mRemoteVideoTracks[connectionId] = Pair(track, VideoRequirement.REQUIRED)
            }
            mClientListener?.onAddRemoteTrack(connectionId, stream, track, metadata, muteType)
        }

        override fun onUpdateRemoteConnection(connectionId: String, metadata: Map<String, Any>) {
            LOGGER.debug("Client#onUpdateRemoteConnection(connectionId = ${connectionId})")
            mClientListener?.onUpdateRemoteConnection(connectionId, metadata)
        }

        override fun onUpdateRemoteTrack(connectionId: String, stream: MediaStream, track: MediaStreamTrack, metadata: Map<String, Any>) {
            LOGGER.debug("Client#onUpdateRemoteTrack({} {}, {})", connectionId, stream.id, track.id())
            mClientListener?.onUpdateRemoteTrack(connectionId, stream, track, metadata)
        }

        override fun onUpdateMute(connectionId: String, stream: MediaStream, track: MediaStreamTrack, muteType: MuteType) {
            LOGGER.debug("Client#onUpdateMute({} {}, {}, {})", connectionId, stream.id, track.id(), muteType)
            mClientListener?.onUpdateMute(connectionId, stream, track, muteType)
        }

        override fun onChangeStability(connectionId: String, stability: Stability) {
            LOGGER.debug("Client#onChangeStability({}, {})", connectionId, stability)
            mClientListener?.onChangeStability(connectionId, stability)
        }

        override fun onError(error: SDKErrorEvent) {
            LOGGER.error("Client#onError({}:{}:{}:{})", error.detail.type, error.detail.code, error.detail.error, error.toReportString())
            mClientListener?.onError(error)
        }
    }
}
