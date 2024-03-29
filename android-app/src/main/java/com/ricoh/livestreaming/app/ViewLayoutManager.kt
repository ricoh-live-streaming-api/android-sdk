/*
 * Copyright 2020 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.app

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.FrameLayout
import org.slf4j.LoggerFactory
import org.webrtc.EglBase
import org.webrtc.EglBase14
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

class ViewLayoutManager(
        private val mContext: Context,
        private val mWindow: Window,
        private val mEgl: EglBase?,
        private val mParentLayout: FrameLayout,
        private val isNeedVideoReceiveCheckBox: Boolean,
        private val mListener: Listener) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ViewLayoutManager::class.java)
    }

    interface Listener {
        fun onVideoReceiveCheckedChanged(connectionId: String, isChecked: Boolean)
    }

    private val mWindowManager: WindowManager = mContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mLocalView: SurfaceViewRenderer = SurfaceViewRenderer(mContext)
    private val mRemoteViews: MutableMap<String, RemoteView> = mutableMapOf()

    init {
        val eglContext = mEgl!!.eglBaseContext as EglBase14.Context
        mLocalView.init(eglContext, null)
    }

    @Synchronized
    fun addLocalTrack(videoTrack: VideoTrack) {
        LOGGER.info("addLocalTrack()")
        videoTrack.addSink(mLocalView)
        try {
            mParentLayout.addView(mLocalView)
        } catch (e: IllegalStateException) {
            LOGGER.debug("Already add view to parent.")
        }

        val params = mLocalView.layoutParams as FrameLayout.LayoutParams
        params.width = mContext.resources.getDimension(R.dimen.local_view_width).toInt()
        params.height = mContext.resources.getDimension(R.dimen.local_view_height).toInt()
        params.marginEnd = mContext.resources.getDimension(R.dimen.local_view_margin_end).toInt()
        params.bottomMargin = mContext.resources.getDimension(R.dimen.local_view_margin_bottom).toInt()
        params.gravity = Gravity.BOTTOM or Gravity.END
        mLocalView.layoutParams = params
    }

    @Synchronized
    fun getLocalView(): SurfaceViewRenderer {
        return mLocalView
    }

    @Synchronized
    fun addRemoteTrack(connectionId: String, videoTrack: VideoTrack, isNeedVideoReceived: Boolean = true) {
        LOGGER.info("addRemoteTrack(connectionId={})", connectionId)
        val renderer = SurfaceViewRenderer(mContext)
        val eglContext = mEgl!!.eglBaseContext as EglBase14.Context

        renderer.init(eglContext, null)
        videoTrack.addSink(renderer)
        val layout = FrameLayout(mContext)
        layout.addView(renderer)
        if (isNeedVideoReceiveCheckBox) {
            val checkBox = CheckBox(mContext).apply {
                text = mContext.resources.getText(R.string.video_receive)
                setBackgroundColor(mContext.getColor(R.color.video_receive_checkbox_background))
                setTextColor(mContext.getColor(android.R.color.white))
                isChecked = isNeedVideoReceived
                val padding = mContext.resources.getDimension(R.dimen.video_receive_checkbox_padding).toInt()
                setPadding(padding, padding, padding, padding)
            }
            val layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                marginStart = mContext.resources.getDimension(R.dimen.video_receive_checkbox_margin_start).toInt()
                bottomMargin = mContext.resources.getDimension(R.dimen.video_receive_checkbox_margin_bottom).toInt()
            }
            checkBox.text = mContext.resources.getText(R.string.video_receive)
            checkBox.isChecked = isNeedVideoReceived
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                mListener.onVideoReceiveCheckedChanged(connectionId, isChecked)
            }
            layout.addView(checkBox, layoutParams)
        }
        mParentLayout.addView(layout)

        mRemoteViews[connectionId] = RemoteView(layout, renderer, videoTrack)
        updateLayout()
    }

    @Synchronized
    fun removeRemoteTrack(connectionId: String) {
        LOGGER.info("removeRemoteTrack(connectionId={})", connectionId)
        val view = mRemoteViews[connectionId]
        if (view != null) {
            view.videoTrack.removeSink(view.render)
            view.render.release()
            mParentLayout.removeView(view.layout)
        }
        mRemoteViews.remove(connectionId)
        updateLayout()
    }

    @Synchronized
    fun clear() {
        LOGGER.info("clear()")
        for ((k, v) in mRemoteViews) {
            v.render.release()
        }
        mRemoteViews.clear()
        mParentLayout.removeAllViews()
    }

    @Synchronized
    fun onConfigurationChanged() {
        if (mRemoteViews.isNotEmpty()) {
            updateLayout()
        }
    }

    private fun updateLayout() {
        val count = mRemoteViews.size
        var index = 0

        // Get screen size.
        val dm = DisplayMetrics()
        mWindowManager.defaultDisplay.getMetrics(dm)
        // Get status bar height
        val rect = Rect()
        mWindow.decorView.getWindowVisibleDisplayFrame(rect)

        val size = Point(dm.widthPixels, dm.heightPixels - rect.top)
        LOGGER.info("### {}x{}", mParentLayout.width, mParentLayout.height)

        for ((k, v) in mRemoteViews) {
            val params = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            if (size.x < size.y) {
                // The orientation of device is portrait.

                // calc layout size.
                when (count) {
                    1 -> {
                        params.width = size.x
                        params.height = size.y
                    }
                    2 -> {
                        params.width = size.x
                        params.height = size.y / 2
                    }
                    else -> {
                        if ((count % 2) == 1 && index == count - 1) {
                            params.width = size.x
                        } else {
                            params.width = size.x / 2
                        }
                        params.height = size.y / ((count + 1) / 2)
                    }
                }

                // calc layout position.
                when (index) {
                    0 -> {
                        params.leftMargin = 0
                        params.topMargin = 0
                    }
                    1 -> {
                        if (count == 2) {
                            params.leftMargin = 0
                            params.topMargin = size.y / 2
                        } else {
                            params.leftMargin = size.x / 2
                            params.topMargin = 0
                        }
                    }
                    else -> {
                        if ((index % 2) == 1) {
                            params.leftMargin = size.x / 2
                        } else {
                            params.leftMargin = 0
                        }
                        params.topMargin = params.height * (((index - 2) / 2) + 1)
                    }
                }
            } else {
                // The orientation of device is landscape.

                // calc layout size.
                when (count) {
                    1 -> {
                        params.width = size.x
                        params.height = size.y
                    }
                    2 -> {
                        params.width = size.x / 2
                        params.height = size.y
                    }
                    else -> {
                        if ((count % 2) == 1 && index == count - 1) {
                            params.height = size.y
                        } else {
                            params.height = size.y / 2
                        }
                        params.width = size.x / ((count + 1) / 2)
                    }
                }

                // calc layout position.
                when (index) {
                    0 -> {
                        params.leftMargin = 0
                        params.topMargin = 0
                    }
                    1 -> {
                        if (count == 2) {
                            params.leftMargin = size.x / 2
                            params.topMargin = 0
                        } else {
                            params.leftMargin = 0
                            params.topMargin = size.y / 2
                        }
                    }
                    else -> {
                        if ((index % 2) == 1) {
                            params.topMargin = size.y / 2
                        } else {
                            params.topMargin = 0
                        }
                        params.leftMargin = params.width * (((index - 2) / 2) + 1)
                    }
                }
            }
            v.layout.layoutParams = params
            index++
        }

        mParentLayout.removeView(mLocalView)
        mParentLayout.addView(mLocalView)
    }

    inner class RemoteView(val layout: FrameLayout, val render: SurfaceViewRenderer, val videoTrack: VideoTrack)
}
