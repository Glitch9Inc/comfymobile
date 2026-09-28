package sh.hnet.comfychair.viewmodel

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.hnet.comfychair.R
import sh.hnet.comfychair.cache.MediaCache
import sh.hnet.comfychair.cache.MediaCacheKey
import sh.hnet.comfychair.connection.ConnectionManager
import sh.hnet.comfychair.repository.GalleryRepository
import sh.hnet.comfychair.storage.AppSettings
import sh.hnet.comfychair.storage.GalleryAlbum
import sh.hnet.comfychair.storage.GalleryAlbumStore
import sh.hnet.comfychair.util.DebugLogger
import java.io.File

/**
 * Represents a gallery item (image or video).
 * Bitmaps are stored in MediaCache, not in this data class.
 */
data class GalleryItem(
    val promptId: String,
    val filename: String,
    val subfolder: String,
    val type: String,
    val isVideo: Boolean,
    val index: Int = 0 // For sorting
) {
    /** Create a cache key for this item */
    fun toCacheKey() = MediaCacheKey(promptId, filename)
}

/**
 * How the gallery grid is laid out.
 * [square] = thumbnails cropped to squares; otherwise original aspect ratio.
 */
enum class GalleryViewMode(val columns: Int, val square: Boolean) {
    GRID_2(2, true),
    GRID_3(3, true),
    GRID_4(4, true),
    MASONRY(2, false),
    SINGLE(1, false)
}

/**
 * UI state for the Gallery screen
 */
data class GalleryUiState(
    /** Items shown (filtered by the selected album) */
    val items: List<GalleryItem> = emptyList(),
    /** Total number of items before album filtering */
    val totalCount: Int = 0,
    val viewMode: GalleryViewMode = GalleryViewMode.GRID_2,
    val albums: List<GalleryAlbum> = emptyList(),
    /** null = all items */
    val selectedAlbumId: String? = null,
    /** Number of existing gallery items in each album (albumId -> count) */
    val albumCounts: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val selectedItems: Set<String> = emptySet(), // Set of "${promptId}_${filename}" keys
    val isSelectionMode: Boolean = false
)

/**
 * Events emitted by gallery operations
 */
sealed class GalleryEvent {
    data class ShowToast(val messageResId: Int) : GalleryEvent()
    data class ShowMedia(val item: GalleryItem, val bitmap: Bitmap?, val videoUri: Uri?) : GalleryEvent()
}

/**
 * ViewModel for the Gallery screen.
 * Uses GalleryRepository for data management and background loading.
 */
class GalleryViewModel : ViewModel() {

    // Constants
    companion object {
        private const val TAG = "Gallery"
    }

    // State
    private val repository = GalleryRepository.getInstance()

    // Selection state (local to this ViewModel)
    private val _selectedItems = MutableStateFlow<Set<String>>(emptySet())
    private val _isSelectionMode = MutableStateFlow(false)

    // View mode and albums
    private data class ViewState(
        val viewMode: GalleryViewMode = GalleryViewMode.GRID_2,
        val albums: List<GalleryAlbum> = emptyList(),
        val selectedAlbumId: String? = null
    )
    private val _viewState = MutableStateFlow(ViewState())
    private var appContext: Context? = null
    private var albumsServerId: String? = null

    // Combine repository state with local selection state
    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<GalleryEvent>()
    val events: SharedFlow<GalleryEvent> = _events.asSharedFlow()

    init {
        // Observe repository state and combine with local selection state
        viewModelScope.launch {
            combine(
                combine(
                    repository.galleryItems,
                    repository.isLoading,
                    repository.isManualRefreshing
                ) { items, isLoading, isManualRefreshing -> Triple(items, isLoading, isManualRefreshing) },
                _selectedItems,
                _isSelectionMode,
                _viewState
            ) { (items, isLoading, isManualRefreshing), selectedItems, isSelectionMode, view ->
                val album = view.albums.firstOrNull { it.id == view.selectedAlbumId }
                // "All" shows only items not yet sorted into any album
                val inAnyAlbum = view.albums.flatMapTo(HashSet()) { it.members }
                val unsorted = items.filter { getItemKey(it) !in inAnyAlbum }
                GalleryUiState(
                    items = if (album == null) unsorted else items.filter { getItemKey(it) in album.members },
                    totalCount = unsorted.size,
                    viewMode = view.viewMode,
                    albums = view.albums,
                    selectedAlbumId = album?.id,
                    albumCounts = view.albums.associate { a -> a.id to items.count { getItemKey(it) in a.members } },
                    isLoading = isLoading,
                    isRefreshing = isManualRefreshing, // Only show indicator for manual refresh
                    selectedItems = selectedItems,
                    isSelectionMode = isSelectionMode
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun initialize(context: Context) {
        // MediaCache is used for all fetch operations
        // Repository is already initialized by GenerationViewModel
        appContext = context.applicationContext
        val mode = AppSettings.getGalleryViewMode(context)
            ?.let { name -> GalleryViewMode.entries.firstOrNull { it.name == name } }
            ?: GalleryViewMode.GRID_2
        _viewState.value = _viewState.value.copy(viewMode = mode)
        reloadAlbums()
    }

    // View mode

    fun setViewMode(mode: GalleryViewMode) {
        _viewState.value = _viewState.value.copy(viewMode = mode)
        appContext?.let { AppSettings.setGalleryViewMode(it, mode.name) }
    }

    // Albums

    /** (Re)load albums for the current server. Safe to call often. */
    fun reloadAlbums() {
        val ctx = appContext ?: return
        val serverId = ConnectionManager.currentServerId ?: return
        if (serverId == albumsServerId) return
        albumsServerId = serverId
        viewModelScope.launch {
            val albums = withContext(Dispatchers.IO) { GalleryAlbumStore.load(ctx, serverId) }
            _viewState.value = _viewState.value.copy(albums = albums, selectedAlbumId = null)
        }
    }

    private fun updateAlbums(transform: (List<GalleryAlbum>) -> List<GalleryAlbum>) {
        val albums = transform(_viewState.value.albums)
        val selected = _viewState.value.selectedAlbumId?.takeIf { id -> albums.any { it.id == id } }
        _viewState.value = _viewState.value.copy(albums = albums, selectedAlbumId = selected)
        val ctx = appContext ?: return
        val serverId = albumsServerId ?: return
        viewModelScope.launch(Dispatchers.IO) { GalleryAlbumStore.save(ctx, serverId, albums) }
    }

    fun selectAlbum(albumId: String?) {
        clearSelection()
        _viewState.value = _viewState.value.copy(selectedAlbumId = albumId)
    }

    /** Create an album; if items are selected, they are added to it. */
    fun createAlbum(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val album = GalleryAlbum(
            id = java.util.UUID.randomUUID().toString(),
            name = trimmed,
            members = _selectedItems.value
        )
        updateAlbums { it + album }
        if (album.members.isNotEmpty()) {
            clearSelection()
            viewModelScope.launch { _events.emit(GalleryEvent.ShowToast(R.string.msg_added_to_album)) }
        }
    }

    fun renameAlbum(albumId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        updateAlbums { list -> list.map { if (it.id == albumId) it.copy(name = trimmed) else it } }
    }

    /** Deletes only the album; the items stay in the gallery. */
    fun deleteAlbum(albumId: String) {
        updateAlbums { list -> list.filterNot { it.id == albumId } }
    }

    fun addSelectedToAlbum(albumId: String) {
        val keys = _selectedItems.value
        if (keys.isEmpty()) return
        updateAlbums { list -> list.map { if (it.id == albumId) it.copy(members = it.members + keys) else it } }
        clearSelection()
        viewModelScope.launch { _events.emit(GalleryEvent.ShowToast(R.string.msg_added_to_album)) }
    }

    fun removeSelectedFromAlbum(albumId: String) {
        val keys = _selectedItems.value
        if (keys.isEmpty()) return
        updateAlbums { list -> list.map { if (it.id == albumId) it.copy(members = it.members - keys) else it } }
        clearSelection()
        viewModelScope.launch { _events.emit(GalleryEvent.ShowToast(R.string.msg_removed_from_album)) }
    }

    /**
     * Load gallery - delegates to repository.
     * If repository already has data, this will return immediately with cached data.
     * The repository handles background refreshing.
     */
    fun loadGallery() {
        DebugLogger.d(TAG, "Loading gallery")
        // Repository already has data from background preload, no need to reload
        // unless explicitly refreshed by user
        if (!repository.hasData()) {
            repository.refresh()
        }
    }

    /**
     * Manual refresh triggered by user (pull-to-refresh).
     * Shows the refresh indicator and displays a Toast on completion.
     */
    fun manualRefresh() {
        DebugLogger.i(TAG, "Manual refresh")
        repository.manualRefresh { success ->
            viewModelScope.launch {
                if (success) {
                    DebugLogger.d(TAG, "Refresh successful")
                    _events.emit(GalleryEvent.ShowToast(R.string.msg_gallery_refresh_success))
                } else {
                    DebugLogger.w(TAG, "Refresh failed")
                    _events.emit(GalleryEvent.ShowToast(R.string.error_gallery_refresh))
                }
            }
        }
    }

    /**
     * Background refresh (silent, no indicator).
     * Used when returning from other screens or after external changes.
     */
    fun refresh() {
        DebugLogger.d(TAG, "Background refresh")
        repository.refresh()
    }

    fun deleteItem(item: GalleryItem) {
        DebugLogger.i(TAG, "Deleting item: ${item.promptId}")
        viewModelScope.launch {
            val success = repository.deleteItem(item)
            if (success) {
                DebugLogger.d(TAG, "Delete successful")
                _events.emit(GalleryEvent.ShowToast(R.string.msg_history_item_deleted_success))
            } else {
                DebugLogger.w(TAG, "Delete failed")
                _events.emit(GalleryEvent.ShowToast(R.string.error_history_item_delete))
            }
        }
    }

    fun saveImageToGallery(context: Context, item: GalleryItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val key = MediaCacheKey(item.promptId, item.filename)
                val bitmap = MediaCache.getBitmap(key)
                    ?: MediaCache.fetchImage(key, item.subfolder, item.type)

                if (bitmap == null) {
                    _events.emit(GalleryEvent.ShowToast(R.string.error_save_image))
                    return@withContext
                }

                try {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "ComfyMobile_${System.currentTimeMillis()}.png")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ComfyMobile")
                    }

                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                    uri?.let { outputUri ->
                        resolver.openOutputStream(outputUri)?.use { outputStream ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        }
                    }

                    _events.emit(GalleryEvent.ShowToast(R.string.msg_image_saved_to_gallery))
                } catch (e: Exception) {
                    _events.emit(GalleryEvent.ShowToast(R.string.error_save_image))
                }
            }
        }
    }

    fun saveVideoToGallery(context: Context, item: GalleryItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val key = MediaCacheKey(item.promptId, item.filename)
                val videoBytes = MediaCache.getVideoBytes(key)
                    ?: MediaCache.fetchVideoBytes(key, item.subfolder, item.type)

                if (videoBytes == null) {
                    _events.emit(GalleryEvent.ShowToast(R.string.error_save_video))
                    return@withContext
                }

                try {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, "ComfyMobile_${System.currentTimeMillis()}.mp4")
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ComfyMobile")
                    }

                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)

                    uri?.let { outputUri ->
                        resolver.openOutputStream(outputUri)?.use { outputStream ->
                            outputStream.write(videoBytes)
                        }
                    }

                    _events.emit(GalleryEvent.ShowToast(R.string.msg_video_saved_to_gallery))
                } catch (e: Exception) {
                    _events.emit(GalleryEvent.ShowToast(R.string.error_save_video))
                }
            }
        }
    }

    fun fetchFullImage(item: GalleryItem, onResult: (Bitmap?) -> Unit) {
        viewModelScope.launch {
            val key = MediaCacheKey(item.promptId, item.filename)
            val bitmap = MediaCache.getBitmap(key)
                ?: MediaCache.fetchImage(key, item.subfolder, item.type)
            onResult(bitmap)
        }
    }

    fun fetchVideoUri(context: Context, item: GalleryItem, onResult: (Uri?) -> Unit) {
        viewModelScope.launch {
            val key = MediaCacheKey(item.promptId, item.filename)
            // Ensure bytes are cached
            MediaCache.getVideoBytes(key)
                ?: MediaCache.fetchVideoBytes(key, item.subfolder, item.type)

            // Get URI from cache (creates temp file from cached bytes)
            val uri = MediaCache.getVideoUri(key, context)
            onResult(uri)
        }
    }

    fun shareImage(context: Context, item: GalleryItem) {
        viewModelScope.launch {
            val key = MediaCacheKey(item.promptId, item.filename)
            val bitmap = MediaCache.getBitmap(key)
                ?: MediaCache.fetchImage(key, item.subfolder, item.type)

            if (bitmap == null) {
                _events.emit(GalleryEvent.ShowToast(R.string.error_share_image))
                return@launch
            }

            try {
                // Save to cache for sharing
                val shareFile = File(context.cacheDir, "share_image.png")
                withContext(Dispatchers.IO) {
                    shareFile.outputStream().use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    shareFile
                )

                val shareIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    type = "image/png"
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                context.startActivity(
                    android.content.Intent.createChooser(
                        shareIntent,
                        context.getString(R.string.share_image)
                    )
                )
            } catch (e: Exception) {
                _events.emit(GalleryEvent.ShowToast(R.string.error_share_image))
            }
        }
    }

    fun shareVideo(context: Context, item: GalleryItem) {
        viewModelScope.launch {
            val key = MediaCacheKey(item.promptId, item.filename)
            val videoBytes = MediaCache.getVideoBytes(key)
                ?: MediaCache.fetchVideoBytes(key, item.subfolder, item.type)

            if (videoBytes == null) {
                _events.emit(GalleryEvent.ShowToast(R.string.error_share_video))
                return@launch
            }

            try {
                val shareFile = File(context.cacheDir, "share_video.mp4")
                withContext(Dispatchers.IO) {
                    shareFile.writeBytes(videoBytes)
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    shareFile
                )

                val shareIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    type = "video/mp4"
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                context.startActivity(
                    android.content.Intent.createChooser(
                        shareIntent,
                        context.getString(R.string.share_video)
                    )
                )
            } catch (e: Exception) {
                _events.emit(GalleryEvent.ShowToast(R.string.error_share_video))
            }
        }
    }

    // Selection mode functions

    private fun getItemKey(item: GalleryItem): String = "${item.promptId}_${item.filename}"

    fun toggleSelection(item: GalleryItem) {
        val key = getItemKey(item)
        val currentSelected = _selectedItems.value.toMutableSet()

        if (currentSelected.contains(key)) {
            currentSelected.remove(key)
        } else {
            currentSelected.add(key)
        }

        _selectedItems.value = currentSelected
        _isSelectionMode.value = currentSelected.isNotEmpty()
    }

    fun isItemSelected(item: GalleryItem): Boolean {
        return _selectedItems.value.contains(getItemKey(item))
    }

    fun clearSelection() {
        _selectedItems.value = emptySet()
        _isSelectionMode.value = false
    }

    fun enterSelectionMode() {
        _isSelectionMode.value = true
    }

    fun deleteSelected() {
        val selectedItems = getSelectedItems()
        if (selectedItems.isEmpty()) return

        viewModelScope.launch {
            // Get unique prompt IDs (multiple items can share the same promptId)
            val promptIds = selectedItems.map { it.promptId }.distinct().toSet()
            val successCount = repository.deleteItems(promptIds)
            val failCount = promptIds.size - successCount

            // Show result toast
            if (failCount == 0) {
                _events.emit(GalleryEvent.ShowToast(R.string.msg_items_deleted_success))
            } else {
                _events.emit(GalleryEvent.ShowToast(R.string.msg_some_items_failed_to_delete))
            }

            // Clear selection after delete
            clearSelection()
        }
    }

    fun getSelectedItems(): List<GalleryItem> {
        val selectedKeys = _selectedItems.value
        return _uiState.value.items.filter { getItemKey(it) in selectedKeys }
    }

    fun saveSelectedToGallery(context: Context) {
        val selectedItems = getSelectedItems()
        if (selectedItems.isEmpty()) return

        viewModelScope.launch {
            var successCount = 0
            var failCount = 0

            for (item in selectedItems) {
                val success = if (item.isVideo) {
                    saveVideoToGalleryInternal(context, item)
                } else {
                    saveImageToGalleryInternal(context, item)
                }
                if (success) successCount++ else failCount++
            }

            // Show result toast
            if (failCount == 0) {
                _events.emit(GalleryEvent.ShowToast(R.string.msg_items_saved_to_gallery))
            } else {
                _events.emit(GalleryEvent.ShowToast(R.string.msg_some_items_failed_to_save))
            }

            // Clear selection after save
            clearSelection()
        }
    }

    private suspend fun saveImageToGalleryInternal(context: Context, item: GalleryItem): Boolean {
        return withContext(Dispatchers.IO) {
            val key = MediaCacheKey(item.promptId, item.filename)
            val bitmap = MediaCache.getBitmap(key)
                ?: MediaCache.fetchImage(key, item.subfolder, item.type)

            if (bitmap == null) return@withContext false

            try {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "ComfyMobile_${System.currentTimeMillis()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ComfyMobile")
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                uri?.let { outputUri ->
                    resolver.openOutputStream(outputUri)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                }
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    private suspend fun saveVideoToGalleryInternal(context: Context, item: GalleryItem): Boolean {
        return withContext(Dispatchers.IO) {
            val key = MediaCacheKey(item.promptId, item.filename)
            val videoBytes = MediaCache.getVideoBytes(key)
                ?: MediaCache.fetchVideoBytes(key, item.subfolder, item.type)

            if (videoBytes == null) return@withContext false

            try {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "ComfyMobile_${System.currentTimeMillis()}.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ComfyMobile")
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)

                uri?.let { outputUri ->
                    resolver.openOutputStream(outputUri)?.use { outputStream ->
                        outputStream.write(videoBytes)
                    }
                }
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    fun shareSelected(context: Context) {
        val selectedItems = getSelectedItems()
        if (selectedItems.isEmpty()) return

        viewModelScope.launch {
            try {
                val uris = mutableListOf<Uri>()

                for ((index, item) in selectedItems.withIndex()) {
                    val uri = if (item.isVideo) {
                        getVideoShareUri(context, item, index)
                    } else {
                        getImageShareUri(context, item, index)
                    }
                    uri?.let { uris.add(it) }
                }

                if (uris.isEmpty()) {
                    _events.emit(GalleryEvent.ShowToast(R.string.error_share_items))
                    return@launch
                }

                val shareIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND_MULTIPLE
                    putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris))
                    type = if (selectedItems.all { it.isVideo }) "video/*"
                           else if (selectedItems.none { it.isVideo }) "image/*"
                           else "*/*"
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                context.startActivity(
                    android.content.Intent.createChooser(
                        shareIntent,
                        context.getString(R.string.button_share)
                    )
                )

                // Clear selection after share
                clearSelection()
            } catch (e: Exception) {
                _events.emit(GalleryEvent.ShowToast(R.string.error_share_items))
            }
        }
    }

    private suspend fun getImageShareUri(context: Context, item: GalleryItem, index: Int): Uri? {
        return withContext(Dispatchers.IO) {
            val key = MediaCacheKey(item.promptId, item.filename)
            val bitmap = MediaCache.getBitmap(key)
                ?: MediaCache.fetchImage(key, item.subfolder, item.type)

            if (bitmap == null) return@withContext null

            try {
                val shareFile = File(context.cacheDir, "share_image_$index.png")
                shareFile.outputStream().use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }

                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    shareFile
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    private suspend fun getVideoShareUri(context: Context, item: GalleryItem, index: Int): Uri? {
        return withContext(Dispatchers.IO) {
            val key = MediaCacheKey(item.promptId, item.filename)
            val videoBytes = MediaCache.getVideoBytes(key)
                ?: MediaCache.fetchVideoBytes(key, item.subfolder, item.type)

            if (videoBytes == null) return@withContext null

            try {
                val shareFile = File(context.cacheDir, "share_video_$index.mp4")
                shareFile.writeBytes(videoBytes)

                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    shareFile
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
