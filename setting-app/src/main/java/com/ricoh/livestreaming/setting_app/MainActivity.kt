/*
 * Copyright 2020 RICOH Company, Ltd. All rights reserved.
 */
package com.ricoh.livestreaming.setting_app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONObject

class MainActivity : AppCompatActivity(), TextWatcher {
    companion object {
        const val SSID_KEY = "SSID"
        const val SECURITY_KEY = "SECURITY"
        const val PASSWORD_KEY = "PASSWORD"
        const val ROOM_ID_KEY = "ROOM_ID"
        const val SEND_RESOLUTION_KEY = "RESOLUTION_KEY"
        const val RESOLUTION_4K = 0
        const val RESOLUTION_2K = 1
        const val BITRATE_KEY = "BITRATE"

        const val INTENT_PARAM = "param"

        const val BITRATE_DEFAULT_VALUE = "7000"    // 7Mbps
        const val RESOLUTION_DEFAULT_VALUE = RESOLUTION_4K
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ssid_edit.text = Editable.Factory.getInstance().newEditable(getSavedStringData(SSID_KEY))
        password_edit.text = Editable.Factory.getInstance().newEditable(getSavedStringData(PASSWORD_KEY))
        room_id_edit.text = Editable.Factory.getInstance().newEditable(getSavedStringData(ROOM_ID_KEY))
        bitrate_edit.text = Editable.Factory.getInstance().newEditable(getSavedStringData(BITRATE_KEY, BITRATE_DEFAULT_VALUE))
        ssid_edit.addTextChangedListener(this)
        password_edit.addTextChangedListener(this)
        room_id_edit.addTextChangedListener(this)
        bitrate_edit.addTextChangedListener(this)

        security_spinner.setSelection(getSavedIntData(SECURITY_KEY))
        security_spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
            ) {
                if (position == 0) {
                    password_layout.visibility = GONE
                } else {
                    password_layout.visibility = VISIBLE
                }
                create_button.isEnabled = isButtonEnabled()
            }
        }

        create_button.setOnClickListener {
            saveData(SSID_KEY, ssid_edit.text.toString())
            saveData(PASSWORD_KEY, password_edit.text.toString())
            saveData(SECURITY_KEY, security_spinner.selectedItemId.toInt())
            saveData(ROOM_ID_KEY, room_id_edit.text.toString())
            val resolution = if (send_resolution_group.checkedRadioButtonId == R.id.send_2k_radio) {
                RESOLUTION_2K
            } else {
                RESOLUTION_4K
            }
            saveData(SEND_RESOLUTION_KEY, resolution)
            saveData(BITRATE_KEY, bitrate_edit.text.toString())

            val json = JSONObject().apply {
                put(SSID_KEY, ssid_edit.text.toString())
                put(PASSWORD_KEY, password_edit.text.toString())
                put(SECURITY_KEY, security_spinner.selectedItemId.toInt())
                put(ROOM_ID_KEY, room_id_edit.text.toString())
                put(SEND_RESOLUTION_KEY, resolution)
                put(BITRATE_KEY, Integer.parseInt(bitrate_edit.text.toString()))
            }
            val intent = Intent(applicationContext, QRCodeActivity::class.java)
            intent.putExtra(INTENT_PARAM, json.toString())
            startActivity(intent)
        }

        show_password.setOnCheckedChangeListener { button, isChecked ->
            val pos = password_edit.selectionEnd
            if (isChecked) {
                password_edit.inputType = InputType.TYPE_CLASS_TEXT +
                        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                password_edit.inputType = InputType.TYPE_CLASS_TEXT +
                        InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            if (pos > 0) {
                password_edit.setSelection(pos)
            }
        }

        when (getSavedIntData(SEND_RESOLUTION_KEY, RESOLUTION_DEFAULT_VALUE)) {
            RESOLUTION_4K -> {
                send_resolution_group.check(R.id.send_4k_radio)
            }
            RESOLUTION_2K -> {
                send_resolution_group.check(R.id.send_2k_radio)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        create_button.isEnabled = isButtonEnabled()
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        Log.d("MainActivity", "onTextChanged")
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        Log.d("MainActivity", "beforeTextChanged")
    }

    override fun afterTextChanged(s: Editable?) {
        Log.d("MainActivity", "afterTextChanged")
        create_button.isEnabled = isButtonEnabled()
    }


    private fun isButtonEnabled(): Boolean {
        if (ssid_edit.text.isEmpty() || room_id_edit.text.isEmpty() || bitrate_edit.text.isEmpty()) {
            return false
        }

        if (security_spinner.selectedItemId != 0L && password_edit.text.isEmpty()) {
            return false
        }

        return true
    }

    private fun getSavedStringData(key: String, defaultValue: String = ""): String {
        val pref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        return pref.getString(key, defaultValue)!!
    }

    private fun saveData(key: String, data: String) {
        val edit = PreferenceManager.getDefaultSharedPreferences(applicationContext).edit()
        edit.putString(key, data)
        edit.apply()
    }

    private fun getSavedIntData(key: String, defaultValue: Int = 0): Int {
        val pref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        return pref.getInt(key, defaultValue)
    }

    private fun saveData(key: String, data: Int) {
        val edit = PreferenceManager.getDefaultSharedPreferences(applicationContext).edit()
        edit.putInt(key, data)
        edit.apply()
    }

}
