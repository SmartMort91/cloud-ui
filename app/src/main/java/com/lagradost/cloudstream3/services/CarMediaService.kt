package com.lagradost.cloudstream3.services

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.isEpisodeBased
import com.lagradost.cloudstream3.ui.player.FixedNextRenderersFactory
import com.lagradost.cloudstream3.utils.DOWNLOAD_EPISODE_CACHE
import com.lagradost.cloudstream3.utils.DOWNLOAD_HEADER_CACHE
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager.getDownloadFileInfo
import java.util.concurrent.Executors

/**
 * Android Auto (and other car / Bluetooth media browsers) support.
 *
 * Android Auto only allows audio style media apps, it never shows video. So this plays the sound of
 * the episodes and movies the user has already downloaded, with the video track turned off.
 * Streaming from providers is not included, as that needs the plugins loaded and links resolved
 * before anything can play.
 *
 * The browse tree is:
 *  - Continue watching: downloaded titles that are partly watched (only listed when there are any,
 *    Android Auto opens on the first tab and an empty first tab looks like a broken app)
 *  - Downloads: every finished download, series are folders of episodes
 *  - With no finished downloads there is a single hint line instead of two empty tabs
 *
 * The position is stored with [DataStoreHelper.setViewPos], the same place the normal player uses.
 */
@OptIn(UnstableApi::class)
class CarMediaService : MediaLibraryService() {
    private var mediaSession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    /** Where to jump to once the item that just started is ready, to resume where it was left */
    private var pendingSeekMs = 0L
    private var lastDurationMs = 0L

    private val progressTicker = object : Runnable {
        override fun run() {
            saveProgress()
            handler.postDelayed(this, PROGRESS_SAVE_INTERVAL_MS)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId?.toEpisodeId()
            val saved = DataStoreHelper.getViewPos(id)
            pendingSeekMs = if (saved != null && isResumable(saved)) saved.position else 0L
            lastDurationMs = 0L
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    lastDurationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                    if (pendingSeekMs > 0L) {
                        val target = pendingSeekMs
                        pendingSeekMs = 0L
                        player.seekTo(target)
                    }
                }

                Player.STATE_ENDED -> saveProgress()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            handler.removeCallbacks(progressTicker)
            if (isPlaying) {
                handler.postDelayed(progressTicker, PROGRESS_SAVE_INTERVAL_MS)
            } else {
                saveProgress()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            // The item that was playing is being replaced or has finished, keep where it got to
            if (reason == Player.DISCONTINUITY_REASON_REMOVE ||
                reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION ||
                reason == Player.DISCONTINUITY_REASON_SKIP
            ) {
                val id = oldPosition.mediaItem?.mediaId?.toEpisodeId() ?: return
                if (lastDurationMs > 0L) {
                    DataStoreHelper.setViewPos(id, oldPosition.positionMs, lastDurationMs)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val renderers = FixedNextRenderersFactory(this).apply {
            setEnableDecoderFallback(true)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        }

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Nothing is shown on a car screen, so there is no point decoding video or subtitles
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        player.addListener(playerListener)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            // The phone player creates its own sessions, make sure the ids never clash
            .setId(SESSION_ID)
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    override fun onDestroy() {
        handler.removeCallbacks(progressTicker)
        saveProgress()
        player.removeListener(playerListener)
        mediaSession?.release()
        mediaSession = null
        player.release()
        executor.shutdown()
        super.onDestroy()
    }

    private fun saveProgress() {
        val id = player.currentMediaItem?.mediaId?.toEpisodeId() ?: return
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return
        // Do not overwrite the saved spot with 0 while an item is still loading
        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_BUFFERING) return
        DataStoreHelper.setViewPos(id, player.currentPosition, duration)
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            // The service has to be exported for Android Auto to find it, but it lists what the
            // user has downloaded, so only let the car/assistant/system and ourselves connect
            val allowed = controller.isTrusted ||
                    controller.packageName == packageName ||
                    controller.packageName in ALLOWED_CONTROLLERS ||
                    ALLOWED_PREFIXES.any { controller.packageName.startsWith(it) }
            Log.i(TAG, "Media browser ${controller.packageName} trusted=${controller.isTrusted} allowed=$allowed")
            if (!allowed) {
                Log.w(TAG, "Rejected media browser from ${controller.packageName}")
                return MediaSession.ConnectionResult.reject()
            }
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(
                LibraryResult.ofItem(folderItem(ROOT_ID, getString(R.string.app_name)), params)
            )
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return background<LibraryResult<MediaItem>> {
                val item = when {
                    mediaId == ROOT_ID -> folderItem(ROOT_ID, getString(R.string.app_name))
                    mediaId == TAB_CONTINUE -> folderItem(
                        TAB_CONTINUE,
                        getString(R.string.car_tab_continue)
                    )

                    mediaId == TAB_DOWNLOADS -> folderItem(
                        TAB_DOWNLOADS,
                        getString(R.string.car_tab_downloads)
                    )

                    mediaId == INFO_EMPTY -> infoItem()

                    mediaId.startsWith(TITLE_PREFIX) -> {
                        val parentId = mediaId.removePrefix(TITLE_PREFIX).toIntOrNull()
                        val header = parentId?.let { loadHeader(it) }
                        if (header == null) null else folderItem(mediaId, header.name)
                    }

                    else -> mediaId.toEpisodeId()?.let { loadEntry(it)?.toMediaItem() }
                }
                if (item == null) {
                    LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                } else {
                    LibraryResult.ofItem(item, null)
                }
            }
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return background<LibraryResult<ImmutableList<MediaItem>>> {
                val all = children(parentId)
                // Long maths, a controller may ask for an enormous page size
                val from = (page.toLong() * pageSize.toLong()).coerceIn(0L, all.size.toLong())
                val to = (from + pageSize.toLong()).coerceIn(from, all.size.toLong())
                val slice = all.subList(from.toInt(), to.toInt())
                LibraryResult.ofItemList(ImmutableList.copyOf(slice), params)
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            // Controllers only know the id, fill in the file to play
            return background<MutableList<MediaItem>> {
                mediaItems.mapNotNull { item ->
                    item.mediaId.toEpisodeId()?.let { loadEntry(it)?.toMediaItem() }
                }.toMutableList()
            }
        }
    }

    // region Data

    private class Entry(
        val episode: DownloadObjects.DownloadEpisodeCached,
        val header: DownloadObjects.DownloadHeaderCached?,
        val uri: Uri,
    )

    private fun loadHeader(parentId: Int): DownloadObjects.DownloadHeaderCached? {
        return getKey<DownloadObjects.DownloadHeaderCached>(
            DOWNLOAD_HEADER_CACHE,
            parentId.toString()
        )
    }

    /** One finished download, null if it is missing or still downloading */
    private fun loadEntry(episodeId: Int): Entry? {
        val episode = getKey<DownloadObjects.DownloadEpisodeCached>(
            DOWNLOAD_EPISODE_CACHE,
            episodeId.toString()
        ) ?: return null
        return toFinishedEntry(episode, loadHeader(episode.parentId))
    }

    private fun toFinishedEntry(
        episode: DownloadObjects.DownloadEpisodeCached,
        header: DownloadObjects.DownloadHeaderCached?
    ): Entry? {
        val file = getDownloadFileInfo(this, episode.id) ?: return null
        // Same rule as the download button uses for "done", with some padding
        if (file.fileLength <= 1024L || file.fileLength + 1024L < file.totalBytes) return null
        return Entry(episode, header, file.path)
    }

    private fun loadDownloads(): List<Entry> {
        val headers = getKeys(DOWNLOAD_HEADER_CACHE)
            .mapNotNull { getKey<DownloadObjects.DownloadHeaderCached>(it) }
            .associateBy { it.id }

        return getKeys(DOWNLOAD_EPISODE_CACHE)
            .mapNotNull { getKey<DownloadObjects.DownloadEpisodeCached>(it) }
            .distinctBy { it.id }
            .mapNotNull { toFinishedEntry(it, headers[it.parentId]) }
    }

    private fun children(parentId: String): List<MediaItem> {
        return when {
            parentId == ROOT_ID -> rootItems()
            parentId == TAB_CONTINUE -> continueItems(loadDownloads())
            parentId == TAB_DOWNLOADS -> downloadItems(loadDownloads())
            parentId.startsWith(TITLE_PREFIX) -> {
                val id = parentId.removePrefix(TITLE_PREFIX).toIntOrNull() ?: return emptyList()
                loadDownloads()
                    .filter { it.episode.parentId == id }
                    .sortedWith(
                        compareBy<Entry>({ it.episode.season ?: 0 }, { it.episode.episode })
                    )
                    .take(MAX_ITEMS)
                    .map { it.toMediaItem() }
            }

            else -> emptyList()
        }
    }

    private fun rootItems(): List<MediaItem> {
        val downloads = loadDownloads()
        if (downloads.isEmpty()) return listOf(infoItem())

        val tabs = ArrayList<MediaItem>(2)
        // Resume first when there is something to resume, that is what people open the car for
        if (continueItems(downloads).isNotEmpty()) {
            tabs += folderItem(TAB_CONTINUE, getString(R.string.car_tab_continue))
        }
        tabs += folderItem(TAB_DOWNLOADS, getString(R.string.car_tab_downloads))
        return tabs
    }

    private fun continueItems(downloads: List<Entry>): List<MediaItem> {
        // Same "last watched" times the home screen uses, so the order matches the phone
        val lastWatched = DataStoreHelper.getAllResumeStateIds()
            ?.mapNotNull { id -> DataStoreHelper.getLastWatched(id)?.let { id to it.updateTime } }
            ?.toMap()
            ?: emptyMap()

        return downloads
            .mapNotNull { entry ->
                val pos = DataStoreHelper.getViewPos(entry.episode.id)
                if (pos != null && isResumable(pos)) entry to pos else null
            }
            .sortedByDescending { (entry, _) -> lastWatched[entry.episode.parentId] ?: 0L }
            .take(MAX_ITEMS)
            .map { (entry, pos) ->
                val minutesLeft = ((pos.duration - pos.position) / 60_000L).coerceAtLeast(1L)
                entry.toMediaItem(subtitle = "$minutesLeft min left")
            }
    }

    private fun downloadItems(downloads: List<Entry>): List<MediaItem> {
        return downloads
            .groupBy { it.episode.parentId }
            .values
            .sortedBy { (it.first().header?.name ?: it.first().episode.name ?: "").lowercase() }
            .take(MAX_ITEMS)
            .map { group ->
                val first = group.first()
                val header = first.header
                if (header != null && header.type.isEpisodeBased()) {
                    folderItem(
                        TITLE_PREFIX + first.episode.parentId,
                        header.name,
                        artwork = header.poster
                    )
                } else {
                    first.toMediaItem()
                }
            }
    }

    // endregion

    // region Items

    private fun folderItem(mediaId: String, title: String, artwork: String? = null): MediaItem {
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtworkUri(artwork.toArtworkUri())
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()
    }

    /** Shown instead of the tabs when nothing is downloaded, so the car screen is never just blank */
    private fun infoItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(INFO_EMPTY)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(getString(R.string.car_no_downloads))
                    .setSubtitle(getString(R.string.car_no_downloads_hint))
                    .setIsBrowsable(false)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    private fun Entry.toMediaItem(subtitle: String? = null): MediaItem {
        val ep = episode
        val show = header?.name
        val number = if (ep.episode > 0) {
            (ep.season?.let { "S$it:" } ?: "") + "E${ep.episode}"
        } else null
        val title = when {
            header != null && !header.type.isEpisodeBased() -> header.name
            else -> listOfNotNull(number, ep.name).joinToString(" · ")
                .ifBlank { show ?: "?" }
        }
        val duration = DataStoreHelper.getViewPos(ep.id)?.duration?.takeIf { it > 0L }

        return MediaItem.Builder()
            .setMediaId(EPISODE_PREFIX + ep.id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(show)
                    .setSubtitle(subtitle)
                    .setArtworkUri((ep.poster ?: header?.poster).toArtworkUri())
                    .setDurationMs(duration)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }

    // endregion

    private fun <T> background(block: () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        executor.execute {
            try {
                future.set(block())
            } catch (t: Throwable) {
                future.setException(t)
            }
        }
        return future
    }

    private fun String?.toArtworkUri(): Uri? {
        if (this.isNullOrBlank()) return null
        return runCatching { Uri.parse(this) }.getOrNull()
            ?.takeIf { it.scheme == "http" || it.scheme == "https" || it.scheme == "content" }
    }

    private fun String.toEpisodeId(): Int? {
        return if (startsWith(EPISODE_PREFIX)) removePrefix(EPISODE_PREFIX).toIntOrNull() else null
    }

    private fun isResumable(pos: DataStoreHelper.PosDur): Boolean {
        return pos.duration > 0L &&
                pos.position > MIN_RESUME_MS &&
                pos.position < pos.duration * 95L / 100L
    }

    companion object {
        private const val TAG = "CarMediaService"
        private const val SESSION_ID = "cs3_car"

        private const val ROOT_ID = "root"
        private const val TAB_CONTINUE = "tab_continue"
        private const val TAB_DOWNLOADS = "tab_downloads"
        private const val INFO_EMPTY = "info_empty"
        private const val TITLE_PREFIX = "title:"
        private const val EPISODE_PREFIX = "dl:"

        // Android Auto cuts long lists anyway, and it keeps the lists quick to build
        private const val MAX_ITEMS = 100
        private const val MIN_RESUME_MS = 10_000L
        private const val PROGRESS_SAVE_INTERVAL_MS = 5_000L

        /** Android Auto (also used by the Desktop Head Unit), Google Assistant, Bluetooth car stereos */
        private val ALLOWED_CONTROLLERS = setOf(
            "com.google.android.projection.gearhead",
            "com.google.android.googlequicksearchbox",
            "com.google.android.carassistant",
            "com.android.bluetooth",
            // Android Auto runs its media UI inside Google Play services on some versions
            "com.google.android.gms",
        )

        /** Other Android Auto / Google car components, so a renamed process still gets in */
        private val ALLOWED_PREFIXES = listOf(
            "com.google.android.projection.",
            "com.google.android.apps.automotive.",
        )
    }
}
