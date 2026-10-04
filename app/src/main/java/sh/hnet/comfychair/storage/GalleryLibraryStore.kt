package sh.hnet.comfychair.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import sh.hnet.comfychair.util.DebugLogger
import sh.hnet.comfychair.viewmodel.GalleryItem
import java.io.File

/**
 * Per-server gallery organisation that is not an album:
 * - trash: items deleted by the user (file id -> time moved to trash)
 * - purged: items deleted from the trash; hidden for good
 * - order: custom item order set by drag and drop (file ids, first = top)
 *
 * Items are identified by their file ([fileId]) rather than by prompt, so an image
 * keeps its state whether it comes from the history or straight from the output folder.
 *
 * Stored in filesDir/local_gallery/{serverId}/library.json
 */
data class GalleryLibrary(
    val trash: Map<String, Long> = emptyMap(),
    val purged: Set<String> = emptySet(),
    val order: List<String> = emptyList()
)

object GalleryLibraryStore {
    private const val TAG = "GalleryLibrary"
    private const val FILE = "library.json"

    /** Stable identity of the file behind a gallery item. */
    fun fileId(item: GalleryItem): String = "${item.type}/${item.subfolder}/${item.filename}"

    private fun file(context: Context, serverId: String): File =
        File(File(File(context.filesDir, "local_gallery"), serverId).apply { mkdirs() }, FILE)

    fun load(context: Context, serverId: String): GalleryLibrary {
        return try {
            val f = file(context, serverId)
            if (!f.exists()) return GalleryLibrary()
            val o = JSONObject(f.readText())
            val trash = mutableMapOf<String, Long>()
            o.optJSONObject("trash")?.let { t -> t.keys().forEach { k -> trash[k] = t.optLong(k) } }
            val purged = o.optJSONArray("purged")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                ?: emptySet()
            val order = o.optJSONArray("order")?.let { a -> (0 until a.length()).map { a.getString(it) } }
                ?: emptyList()
            GalleryLibrary(trash, purged, order)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Failed to load library: ${e.message}")
            GalleryLibrary()
        }
    }

    fun save(context: Context, serverId: String, library: GalleryLibrary) {
        try {
            val o = JSONObject().apply {
                put("trash", JSONObject().apply { library.trash.forEach { (k, v) -> put(k, v) } })
                put("purged", JSONArray(library.purged.toList()))
                put("order", JSONArray(library.order))
            }
            val f = file(context, serverId)
            val tmp = File(f.parentFile, "$FILE.tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(f)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Failed to save library: ${e.message}")
        }
    }
}
