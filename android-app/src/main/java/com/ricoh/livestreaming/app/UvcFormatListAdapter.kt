/*
 * Copyright 2020 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.app

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.ricoh.livestreaming.uvc.UvcVideoCapturer
import org.slf4j.LoggerFactory

class UvcFormatListAdapter(context: Context) : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(UvcFormatListAdapter::class.java)
    }

    init {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val textView = super.getView(position, convertView, parent) as TextView
        textView.text = getItem(position)
        return textView
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val textView = super.getDropDownView(position, convertView, parent) as TextView
        textView.text = getItem(position)
        return textView
    }

    fun add(format: UvcVideoCapturer.Format) {
        val str = "${format.width}x${format.height}@%.1f ${format.payloadType}".format(10000000.0f / format.interval)
        add(str)
    }

    fun addAll(formats: List<UvcVideoCapturer.Format>) {
        for (format in formats) {
            add(format)
        }
    }
}
