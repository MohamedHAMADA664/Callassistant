package com.example.callassistant

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

/**
 * كل العمليات الخاصة بجهات الاتصال: البحث بالاسم، البحث بالرقم، حفظ رقم جديد، الاتصال
 */
object ContactsHelper {

    /** رجّع اسم صاحب رقم معين، أو الرقم نفسه لو مش محفوظ */
    fun getNameForNumber(context: Context, phoneNumber: String): String {
        val uri = android.net.Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            android.net.Uri.encode(phoneNumber)
        )
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null, null, null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getString(0)
            }
        }
        return phoneNumber
    }

    /** دور على رقم باسم معين (مطابقة تقريبية) */
    fun findNumberByName(context: Context, name: String): String? {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )
        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val contactName = it.getString(nameIdx) ?: continue
                if (contactName.contains(name, ignoreCase = true) ||
                    name.contains(contactName, ignoreCase = true)
                ) {
                    return it.getString(numberIdx)
                }
            }
        }
        return null
    }

    /** حفظ رقم جديد باسم معين */
    fun saveContact(context: Context, name: String, number: String): Boolean {
        return try {
            val values = arrayListOf<ContentValues>()

            val rawContactValues = ContentValues()
            // نضيف raw contact فارغ الأول
            val rawContactUri = context.contentResolver.insert(
                ContactsContract.RawContacts.CONTENT_URI, rawContactValues
            ) ?: return false
            val rawContactId = ContentUris.parseId(rawContactUri)

            val nameValues = ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
            }
            context.contentResolver.insert(ContactsContract.Data.CONTENT_URI, nameValues)

            val phoneValues = ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
            }
            context.contentResolver.insert(ContactsContract.Data.CONTENT_URI, phoneValues)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** اتصال مباشر برقم معين */
    fun callNumber(context: Context, number: String) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CALL_PHONE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val uri = android.net.Uri.fromParts("tel", number, null)
        try {
            telecomManager.placeCall(uri, null)
        } catch (e: SecurityException) {
            // fallback لو مفيش صلاحية telecom الكاملة
            val intent = android.content.Intent(android.content.Intent.ACTION_CALL, uri)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }
}
