package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.text.InputType
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.utils.DataStoreHelper.currentAccount
import com.lagradost.cloudstream3.utils.DataStoreHelper.toYear
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showMultiDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showNginxTextInputDialog
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * User made named collections ("Horror Night", "All-Time Favorites", ...).
 * Stored locally per account as one small JSON string, shown as extra tabs in the library.
 */
object CustomCollections {
    private const val KEY = "custom_collections_v1"

    data class Item(
        val id: Int,
        val name: String,
        val url: String,
        val apiName: String,
        val type: String?,
        val posterUrl: String?,
        val year: Int?,
        val addedTime: Long,
    ) {
        fun toLibraryItem() = SyncAPI.LibraryItem(
            name,
            url,
            id.toString(),
            null,
            null,
            null,
            addedTime,
            apiName,
            type?.let { runCatching { TvType.valueOf(it) }.getOrNull() },
            posterUrl,
            null,
            null,
            year?.toYear(),
            id,
        )
    }

    data class Collection(val id: String, val name: String, val items: List<Item>)

    private fun storageKey() = "$currentAccount/$KEY"

    fun getAll(): List<Collection> {
        val raw = getKey<String>(storageKey()) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val items = o.optJSONArray("items") ?: JSONArray()
                Collection(
                    o.getString("id"),
                    o.getString("name"),
                    (0 until items.length()).map { j ->
                        val it = items.getJSONObject(j)
                        Item(
                            it.getInt("id"),
                            it.getString("name"),
                            it.getString("url"),
                            it.getString("apiName"),
                            if (it.isNull("type")) null else it.getString("type"),
                            if (it.isNull("posterUrl")) null else it.getString("posterUrl"),
                            if (it.isNull("year")) null else it.getInt("year"),
                            it.optLong("addedTime", 0L),
                        )
                    }
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun save(list: List<Collection>) {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("items", JSONArray().also { ia ->
                    c.items.forEach { i ->
                        ia.put(JSONObject().apply {
                            put("id", i.id)
                            put("name", i.name)
                            put("url", i.url)
                            put("apiName", i.apiName)
                            put("type", i.type ?: JSONObject.NULL)
                            put("posterUrl", i.posterUrl ?: JSONObject.NULL)
                            put("year", i.year ?: JSONObject.NULL)
                            put("addedTime", i.addedTime)
                        })
                    }
                })
            })
        }
        setKey(storageKey(), arr.toString())
        MainActivity.reloadLibraryEvent(true)
    }

    fun nameTaken(name: String, exceptId: String? = null) =
        getAll().any { it.id != exceptId && it.name.equals(name.trim(), ignoreCase = true) }

    /** Returns null if the name is blank or already used */
    fun create(name: String): Collection? {
        val clean = name.trim()
        if (clean.isBlank() || nameTaken(clean)) return null
        val c = Collection(System.currentTimeMillis().toString(), clean, emptyList())
        save(getAll() + c)
        return c
    }

    fun rename(id: String, name: String): Boolean {
        val clean = name.trim()
        if (clean.isBlank() || nameTaken(clean, id)) return false
        save(getAll().map { if (it.id == id) it.copy(name = clean) else it })
        return true
    }

    fun delete(id: String) = save(getAll().filter { it.id != id })

    fun setMembership(collectionId: String, item: Item, member: Boolean) {
        save(getAll().map { c ->
            if (c.id != collectionId) return@map c
            val without = c.items.filter { it.id != item.id }
            c.copy(items = if (member) without + item else without)
        })
    }

    // ----- Sharing -----

    const val LINK_SCHEME = "cscollection"
    private val linkRegex = Regex("""cscollection(?:://|%3A%2F%2F)([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)

    /** Compact gzip + base64url encoding so a whole collection fits in a link */
    fun encodeLink(c: Collection): String {
        val root = JSONObject()
        root.put("n", c.name)
        root.put("i", JSONArray().also { arr ->
            c.items.forEach { i ->
                arr.put(JSONArray().apply {
                    put(i.id)
                    put(i.name)
                    put(i.url)
                    put(i.apiName)
                    put(i.type ?: JSONObject.NULL)
                    put(i.posterUrl ?: JSONObject.NULL)
                    put(i.year ?: JSONObject.NULL)
                })
            }
        })
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(root.toString().toByteArray(Charsets.UTF_8)) }
        val b64 = Base64.encodeToString(
            bos.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return "$LINK_SCHEME://$b64"
    }

    /** Finds a collection link anywhere in [text] (raw link or the https redirect form) */
    fun decodeLink(text: String): Collection? {
        val payload = linkRegex.find(text)?.groupValues?.getOrNull(1) ?: return null
        return runCatching {
            val bytes = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP)
            val json = GZIPInputStream(ByteArrayInputStream(bytes)).use {
                String(it.readBytes(), Charsets.UTF_8)
            }
            val root = JSONObject(json)
            val arr = root.getJSONArray("i")
            val now = System.currentTimeMillis()
            Collection(
                "",
                root.getString("n"),
                (0 until arr.length()).map { idx ->
                    val a = arr.getJSONArray(idx)
                    Item(
                        a.getInt(0),
                        a.getString(1),
                        a.getString(2),
                        a.getString(3),
                        if (a.isNull(4)) null else a.getString(4),
                        if (a.isNull(5)) null else a.getString(5),
                        if (a.isNull(6)) null else a.getInt(6),
                        now,
                    )
                }
            )
        }.getOrNull()
    }

    /** Adds [incoming] as a new collection, renaming it if the name is already used */
    fun importCollection(incoming: Collection): Collection {
        var name = incoming.name.trim().ifBlank { "Shared collection" }
        var n = 2
        val base = name
        while (nameTaken(name)) name = "$base (${n++})"
        val c = Collection(System.currentTimeMillis().toString(), name, incoming.items)
        save(getAll() + c)
        return c
    }

    fun Activity.shareCollection(c: Collection) {
        val link = encodeLink(c)
        val redirect = "https://recloudstream.github.io/csredirect?redirectto=" +
            URLEncoder.encode(link, "UTF-8")
        val text = getString(R.string.collection_share_text, c.name, c.items.size) +
            "\n" + redirect + "\n\n" +
            getString(R.string.collection_share_fallback) + "\n" + link
        val i = Intent(Intent.ACTION_SEND)
        i.type = "text/plain"
        i.putExtra(Intent.EXTRA_SUBJECT, c.name)
        i.putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(i, c.name))
    }

    /** Parses [text] and asks before importing. Returns true if it was a collection link. */
    fun Activity.importFromText(text: String?, silentIfInvalid: Boolean = false): Boolean {
        val parsed = text?.let { decodeLink(it) }
        if (parsed == null) {
            if (!silentIfInvalid) showToast(R.string.collection_import_invalid)
            return false
        }
        showBottomDialog(
            listOf(getString(R.string.collection_import_confirm, parsed.name, parsed.items.size)),
            -1,
            getString(R.string.collections_title),
            false,
            {}
        ) {
            val c = importCollection(parsed)
            showToast(getString(R.string.collection_imported, c.name))
        }
        return true
    }

    fun Activity.importFromClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)
        importFromText(text?.toString())
    }

    // ----- UI -----

    private fun Activity.askName(title: String, initial: String, onName: (String) -> Unit) {
        showNginxTextInputDialog(title, initial, InputType.TYPE_CLASS_TEXT, {}) { text ->
            onName(text)
        }
    }

    /** Create dialog, calls [done] with the new collection on success */
    fun Activity.promptCreate(done: (Collection) -> Unit = {}) {
        askName(getString(R.string.collection_new), "") { text ->
            val c = create(text)
            if (c == null) showToast(R.string.collection_name_invalid) else done(c)
        }
    }

    /** Bookmark style picker, lets the user tick the collections a title belongs to */
    fun Activity.showCollectionPicker(item: Item) {
        val cols = getAll()
        if (cols.isEmpty()) {
            promptCreate { setMembership(it.id, item, true) }
            return
        }
        val newLabel = getString(R.string.collection_new_item)
        val names = cols.map { it.name } + newLabel
        val selected = cols.indices.filter { i -> cols[i].items.any { it.id == item.id } }
        showMultiDialog(names, selected, getString(R.string.collections_title), {}) { picked ->
            cols.forEachIndexed { i, c ->
                val now = picked.contains(i)
                if (now != selected.contains(i)) setMembership(c.id, item, now)
            }
            if (picked.contains(cols.size)) {
                promptCreate { setMembership(it.id, item, true) }
            }
        }
    }

    /** Library menu: create, rename and delete collections */
    fun Activity.showCollectionManager() {
        val actions = listOf(
            getString(R.string.collection_new),
            getString(R.string.collection_rename),
            getString(R.string.collection_delete),
            getString(R.string.collection_share),
            getString(R.string.collection_import),
        )
        showBottomDialog(actions, -1, getString(R.string.collections_title), false, {}) { which ->
            val cols = getAll()
            when (which) {
                0 -> promptCreate()
                4 -> importFromClipboard()
                1, 2, 3 -> {
                    if (cols.isEmpty()) {
                        showToast(R.string.collections_none)
                        return@showBottomDialog
                    }
                    showBottomDialog(
                        cols.map { it.name }, -1, actions[which], false, {}
                    ) { index ->
                        val c = cols.getOrNull(index) ?: return@showBottomDialog
                        if (which == 3) {
                            shareCollection(c)
                        } else if (which == 1) {
                            askName(actions[1], c.name) { text ->
                                if (!rename(c.id, text)) showToast(R.string.collection_name_invalid)
                            }
                        } else {
                            showBottomDialog(
                                listOf(getString(R.string.collection_delete_confirm, c.name)),
                                -1,
                                actions[2],
                                false,
                                {}
                            ) { delete(c.id) }
                        }
                    }
                }
            }
        }
    }
}
