package com.example.callassistant

import android.os.Bundle
import android.provider.ContactsContract
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

class ContactsListActivity : AppCompatActivity() {

    private data class ContactItem(val name: String, val number: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val listView = ListView(this)
        setContentView(listView)

        val contacts = loadContacts()
        val display = contacts.map { "${it.name}\n${it.number}" }
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, display)

        listView.setOnItemClickListener { _, _, position, _ ->
            val contact = contacts[position]
            ContactsHelper.callNumber(this, contact.number)
        }
    }

    private fun loadContacts(): List<ContactItem> {
        val result = mutableListOf<ContactItem>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val cursor = contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )
        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: continue
                val number = it.getString(numberIdx) ?: continue
                result.add(ContactItem(name, number))
            }
        }
        return result
    }
}
