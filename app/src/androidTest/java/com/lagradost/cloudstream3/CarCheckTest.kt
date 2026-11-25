package com.lagradost.cloudstream3

import android.content.ComponentName
import android.content.Context
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.services.CarMediaService
import com.lagradost.cloudstream3.utils.DOWNLOAD_EPISODE_CACHE
import com.lagradost.cloudstream3.utils.DOWNLOAD_HEADER_CACHE
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Not a real test. Seeds a few fake downloads, then browses CarMediaService the way Android Auto
 * does (legacy MediaBrowser API, no activity started) and logs what a car would see.
 */
@RunWith(AndroidJUnit4::class)
class CarCheckTest {
    private val tag = "CARCHECK"
    private val main = Handler(Looper.getMainLooper())

    private fun log(msg: String) {
        Log.i(tag, msg)
    }

    private fun wav(file: File, seconds: Int) {
        val rate = 8000
        val data = ByteArray(rate * 2 * seconds)
        val bb = ByteBuffer.allocate(44 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()).putInt(36 + data.size).put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate)
            .putInt(rate * 2).putShort(2).putShort(16)
        bb.put("data".toByteArray()).putInt(data.size).put(data)
        file.writeBytes(bb.array())
    }

    private fun seed(ctx: Context) {
        val base = File(ctx.getExternalFilesDir(null), "carcheck").apply { mkdirs() }
        val relDir = File(base, "rel").apply { mkdirs() }
        val now = System.currentTimeMillis()

        data class Item(val header: Int, val ep: Int, val name: String, val type: TvType, val epNo: Int, val season: Int?)

        val items = listOf(
            Item(1001, 2001, "Dexter", TvType.TvSeries, 1, 1),
            Item(1001, 2002, "Dexter", TvType.TvSeries, 2, 1),
            Item(1003, 2003, "The Odyssey", TvType.Movie, 0, null),
        )
        for (it in items) {
            ctx.setKey(
                DOWNLOAD_HEADER_CACHE, it.header.toString(),
                DownloadObjects.DownloadHeaderCached("test", "u", it.type, it.name, null, now, it.header)
            )
            ctx.setKey(
                DOWNLOAD_EPISODE_CACHE, it.ep.toString(),
                DownloadObjects.DownloadEpisodeCached(
                    if (it.epNo > 0) "Episode ${it.epNo}" else null, null, it.epNo, it.season, it.header, null, null, now, it.ep
                )
            )
            val file = File(relDir, "ep${it.ep}.wav")
            wav(file, 20)
            ctx.setKey(
                VideoDownloadManager.KEY_DOWNLOAD_INFO, it.ep.toString(),
                DownloadObjects.DownloadedFileInfo(file.length(), "rel", file.name, null, base.absolutePath)
            )
        }
        // Episode 2002 is half watched, so it should be in "Continue watching"
        DataStoreHelper.setViewPos(2002, 120_000L, 600_000L)
        log("seeded ${items.size} downloads in ${base.absolutePath}, account=${DataStoreHelper.currentAccount}")
    }

    private fun describe(i: MediaBrowser.MediaItem): String {
        val d = i.description
        return "id=${d.mediaId} title='${d.title}' sub='${d.subtitle}' browsable=${i.isBrowsable} playable=${i.isPlayable} icon=${d.iconUri}"
    }

    @Test
    fun browseLikeAndroidAuto() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            seed(ctx)

            val connected = CountDownLatch(1)
            var failed: String? = null
            lateinit var browser: MediaBrowser
            main.post {
                browser = MediaBrowser(
                    ctx,
                    ComponentName(ctx, CarMediaService::class.java),
                    object : MediaBrowser.ConnectionCallback() {
                        override fun onConnected() = connected.countDown()
                        override fun onConnectionFailed() {
                            failed = "onConnectionFailed"; connected.countDown()
                        }

                        override fun onConnectionSuspended() {
                            failed = "onConnectionSuspended"; connected.countDown()
                        }
                    },
                    null
                )
                browser.connect()
            }
            if (!connected.await(30, TimeUnit.SECONDS)) {
                log("RESULT connect timed out"); return
            }
            if (failed != null) {
                log("RESULT connect $failed"); return
            }
            log("connected root=${browser.root} extras=${browser.extras}")

            fun children(parent: String): List<MediaBrowser.MediaItem>? {
                val latch = CountDownLatch(1)
                var out: List<MediaBrowser.MediaItem>? = null
                var err = false
                main.post {
                    browser.subscribe(parent, object : MediaBrowser.SubscriptionCallback() {
                        override fun onChildrenLoaded(p: String, c: MutableList<MediaBrowser.MediaItem>) {
                            out = c.toList(); latch.countDown()
                        }

                        override fun onError(p: String) {
                            err = true; latch.countDown()
                        }
                    })
                }
                if (!latch.await(20, TimeUnit.SECONDS)) {
                    log("children($parent) timed out"); return null
                }
                if (err) log("children($parent) onError")
                return out
            }

            val tabs = children(browser.root)
            log("ROOT has ${tabs?.size} children")
            var firstPlayable: String? = null
            tabs?.forEach { tab ->
                log("  TAB ${describe(tab)}")
                val kids = children(tab.mediaId!!)
                log("    -> ${kids?.size} items")
                kids?.forEach { k ->
                    log("    ITEM ${describe(k)}")
                    if (k.isBrowsable) {
                        val sub = children(k.mediaId!!)
                        sub?.forEach { s ->
                            log("      SUB ${describe(s)}")
                            if (s.isPlayable && firstPlayable == null) firstPlayable = s.mediaId
                        }
                    } else if (k.isPlayable && firstPlayable == null) firstPlayable = k.mediaId
                }
            }

            val id = firstPlayable
            if (id == null) {
                log("RESULT nothing playable found"); return
            }
            val states = mutableListOf<String>()
            val controllerReady = CountDownLatch(1)
            lateinit var controller: MediaController
            main.post {
                controller = MediaController(ctx, browser.sessionToken)
                controller.registerCallback(object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        states += "${state?.state}@${state?.position}"
                    }
                })
                controller.transportControls.playFromMediaId(id, null)
                controllerReady.countDown()
            }
            controllerReady.await(10, TimeUnit.SECONDS)
            Thread.sleep(5000)
            val st = controller.playbackState
            log("PLAY $id state=${st?.state} position=${st?.position} error='${st?.errorMessage}' history=$states")
            log("RESULT done")
        } catch (t: Throwable) {
            log("RESULT crashed: ${Log.getStackTraceString(t)}")
        }
    }
}
