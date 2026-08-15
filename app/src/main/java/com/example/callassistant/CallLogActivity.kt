package com.example.callassistant

import android.database.Cursor
import android.os.Bundle
import android.provider.CallLog
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallLogActivity : AppCompatActivity() {

    private data class LogItem(val label: String, val number: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val listView = ListView(this)
        setContentView(listView)

        val items = loadCallLog()
        val display = items.map { it.label }
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, display)

        listView.setOnItemClickListener { _, _, position, _ ->
            ContactsHelper.callNumber(this, items[position].number)
        }
    }

    private fun loadCallLog(): List<LogItem> {
        val result = mutableListOf<LogItem>()
        val cursor: Cursor? = contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.CACHED_NAME),
            null, null,
            "${CallLog.Calls.DATE} DESC LIMIT 100"
        )
        val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

        cursor?.use {
            val numberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
            val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
            val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
            val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)

            while (it.moveToNext()) {
                val number = it.getString(numberIdx) ?: continue
                val type = it.getInt(typeIdx)
                val date = it.getLong(dateIdx)
                val cachedName = it.getString(nameIdx)
                val name = cachedName ?: ContactsHelper.getNameForNumber(this, number)

                val typeLabel = when (type) {
                    CallLog.Calls.INCOMING_TYPE -> "واردة"
                    CallLog.Calls.OUTGOING_TYPE -> "صادرة"
                    CallLog.Calls.MISSED_TYPE -> "فائتة"
                    CallLog.Calls.REJECTED_TYPE -> "مرفوضة"
                    else -> "مكالمة"
                }

                val label = "$name ($typeLabel)\n${sdf.format(Date(date))}"
                result.add(LogItem(label, number))
            }
        }
        return result
    }
}
