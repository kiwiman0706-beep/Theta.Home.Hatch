package jp.yaegaki.theta.home.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract

data class Person(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val starred: Boolean,
    val phone: String?,
    val email: String?,
    /** 連絡先の顔写真（小さい版）。無ければ null。 */
    val photo: String? = null,
) {
    val initial: String get() = name.trim().take(1).ifEmpty { "?" }
}

/** 端末の連絡先を読む。READ_CONTACTS が必要。スター付き（お気に入り）を先に並べる。 */
object Contacts {
    const val LINE_PACKAGE = "jp.naver.line.android"

    fun load(context: Context, limit: Int = 200): List<Person> {
        val cr = context.contentResolver
        val people = LinkedHashMap<Long, Person>()
        val cols = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.STARRED,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
        )
        cr.query(
            ContactsContract.Contacts.CONTENT_URI, cols,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} IS NOT NULL", null,
            "${ContactsContract.Contacts.STARRED} DESC, ${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
        )?.use { c ->
            while (c.moveToNext() && people.size < limit) {
                val id = c.getLong(0)
                people[id] = Person(id, c.getString(1) ?: "", c.getString(2) ?: "", c.getInt(3) == 1, null, null, c.getString(4))
            }
        }
        if (people.isEmpty()) return emptyList()
        val ids = people.keys.joinToString(",")
        cr.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1),
            "${ContactsContract.Data.CONTACT_ID} IN ($ids) AND ${ContactsContract.Data.MIMETYPE} IN (?, ?)",
            arrayOf(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE),
            "${ContactsContract.Data.IS_PRIMARY} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val p = people[c.getLong(0)] ?: continue
                val value = c.getString(2) ?: continue
                people[p.id] = if (c.getString(1) == ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE) {
                    if (p.phone == null) p.copy(phone = value) else p
                } else {
                    if (p.email == null) p.copy(email = value) else p
                }
            }
        }
        return people.values.toList()
    }

    fun viewIntent(p: Person): Intent =
        Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(p.id, p.lookupKey))

    fun dialIntent(phone: String): Intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))

    fun mailIntent(email: String): Intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", email, null))
}
