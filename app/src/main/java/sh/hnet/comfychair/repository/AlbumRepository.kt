package sh.hnet.comfychair.repository

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sh.hnet.comfychair.connection.ConnectionManager
import sh.hnet.comfychair.storage.AppSettings
import sh.hnet.comfychair.storage.GalleryAlbum
import sh.hnet.comfychair.storage.GalleryAlbumStore

/**
 * Albums of the current server, and the album currently selected.
 * Shared by the gallery and the generation screens so their album selection stays in sync:
 * opening an album in the gallery selects it for generation, and new images generated while
 * an album is selected are added to it.
 */
object AlbumRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _albums = MutableStateFlow<List<GalleryAlbum>>(emptyList())
    val albums: StateFlow<List<GalleryAlbum>> = _albums.asStateFlow()

    /** Selected album ID, or null for none (the whole gallery) */
    private val _currentAlbumId = MutableStateFlow<String?>(null)
    val currentAlbumId: StateFlow<String?> = _currentAlbumId.asStateFlow()

    private var appContext: Context? = null
    private var serverId: String? = null

    /** Load albums for the current server (no-op if already loaded). Safe to call often. */
    @Synchronized
    fun ensureLoaded(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        val id = ConnectionManager.currentServerId ?: return
        if (id == serverId) return
        serverId = id
        val loaded = GalleryAlbumStore.load(ctx, id)
        _albums.value = loaded
        _currentAlbumId.value = AppSettings.getCurrentAlbumId(ctx, id)?.takeIf { a -> loaded.any { it.id == a } }
    }

    fun select(albumId: String?) {
        val id = albumId?.takeIf { a -> _albums.value.any { it.id == a } }
        _currentAlbumId.value = id
        val ctx = appContext ?: return
        val server = serverId ?: return
        AppSettings.setCurrentAlbumId(ctx, server, id)
    }

    fun update(transform: (List<GalleryAlbum>) -> List<GalleryAlbum>) {
        val albums = synchronized(this) {
            transform(_albums.value).also { _albums.value = it }
        }
        if (_currentAlbumId.value != null && albums.none { it.id == _currentAlbumId.value }) select(null)
        val ctx = appContext ?: return
        val server = serverId ?: return
        scope.launch { GalleryAlbumStore.save(ctx, server, albums) }
    }

    /** Put everything a prompt generates into the selected album (if any). */
    fun addPromptToCurrent(promptId: String) {
        val albumId = _currentAlbumId.value ?: return
        update { list -> list.map { if (it.id == albumId) it.copy(prompts = it.prompts + promptId) else it } }
    }
}
