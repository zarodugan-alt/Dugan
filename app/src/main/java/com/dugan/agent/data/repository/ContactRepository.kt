package com.dugan.agent.data.repository

import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ContactEntry(
    val id: String,
    val displayName: String,
    val number: String,
    val photoUri: String? = null,
)

enum class CallDirection { Incoming, Outgoing, Missed, Unknown }

data class RecentCall(
    val id: Long,
    val number: String,
    val displayName: String?,
    val direction: CallDirection,
    val timestampMs: Long,
    val durationSeconds: Long,
)

/**
 * Read-only access to the system contact list and call log. Both are permission
 * gated; every query degrades to an empty list rather than throwing when the
 * permission is missing, so the dialer still renders.
 */
@Singleton
class ContactRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun contacts(query: String = ""): List<ContactEntry> = withContext(Dispatchers.IO) {
        val out = mutableListOf<ContactEntry>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
        )
        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameCol = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numCol = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoCol = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                while (c.moveToNext()) {
                    val name = c.getString(nameCol).orEmpty()
                    val number = c.getString(numCol).orEmpty()
                    if (query.isNotBlank() && !matches(name, number, query)) continue
                    out += ContactEntry(
                        id = c.getString(idCol).orEmpty(),
                        displayName = name,
                        number = number,
                        photoUri = c.getString(photoCol),
                    )
                }
            }
        }
        out.sortedBy { it.displayName.lowercase() }
    }

    /** Case- and punctuation-insensitive substring match, good enough for a dialer. */
    private fun matches(name: String, number: String, query: String): Boolean {
        val q = query.lowercase().filter { it.isLetterOrDigit() }
        if (q.isEmpty()) return true
        if (name.lowercase().contains(query.lowercase())) return true
        val digits = number.filter { it.isDigit() }
        return digits.contains(q)
    }

    suspend fun recentCalls(limit: Int = 50): List<RecentCall> = withContext(Dispatchers.IO) {
        val out = mutableListOf<RecentCall>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
        )
        runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT $limit",
            )?.use { c ->
                while (c.moveToNext()) {
                    out += RecentCall(
                        id = c.getLong(0),
                        number = c.getString(1).orEmpty(),
                        displayName = c.getString(2),
                        direction = when (c.getInt(3)) {
                            CallLog.Calls.INCOMING_TYPE -> CallDirection.Incoming
                            CallLog.Calls.OUTGOING_TYPE -> CallDirection.Outgoing
                            CallLog.Calls.MISSED_TYPE -> CallDirection.Missed
                            else -> CallDirection.Unknown
                        },
                        timestampMs = c.getLong(4),
                        durationSeconds = c.getLong(5),
                    )
                }
            }
        }
        out
    }

    suspend fun deleteRecentCall(id: Long) = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls._ID} = ?",
                arrayOf(id.toString()),
            )
        }
    }

    /** Resolve a spoken name ("call John") against contacts, then the call log. */
    suspend fun resolveByName(spoken: String): ContactEntry? {
        val exact = contacts(spoken).firstOrNull { it.displayName.equals(spoken, ignoreCase = true) }
        if (exact != null) return exact
        val partial = contacts(spoken).firstOrNull()
        if (partial != null) return partial
        // Fall back to recent calls, which carries cached display names.
        val fromLog = recentCalls().firstOrNull {
            it.displayName?.equals(spoken, ignoreCase = true) == true
        }
        return fromLog?.let { ContactEntry(it.id.toString(), it.displayName.orEmpty(), it.number) }
    }
}
