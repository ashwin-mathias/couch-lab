package com.sparky.iptv.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Checks of the shared code that run on every build (3.0). */
class CoreTest {
    private fun tmp(name: String) = File.createTempFile("sparky", name).apply { deleteOnExit() }

    @Test
    fun snapshotRoundTrip() {
        val f = tmp(".bin")
        val cats = listOf(Category("1", "US| NEWS"), Category("2", "IN| SPORTS"))
        val chans = (1..5000).map {
            Channel(id = "$it", num = it, name = "Chännel $it ᴴᴰ", logo = if (it % 3 == 0) null else "http://x/$it.png",
                categoryId = "${it % 2 + 1}", epgId = if (it % 5 == 0) null else "epg$it", archiveDays = it % 8)
        }
        Snapshot.writeLive(f, cats, chans)
        val (c2, ch2) = Snapshot.readLive(f)!!
        assertEquals(cats, c2)
        assertEquals(chans, ch2)
    }

    @Test
    fun parsesProviderList() {
        val f = tmp(".json")
        f.writeText("""[{"stream_id":7,"num":"3","name":"US| CNN","stream_icon":"","category_id":"5","epg_channel_id":null,"tv_archive":1,"tv_archive_duration":"7"},
            {"stream_id":"9","num":1,"name":"##### NEWS #####","category_id":"5"}, 42]""")
        val p = Playlist("xtream", "Home", "http://h:80", "u", "p")
        val list = StreamParsers.live(f, XtreamClient(Http.base, p), "ts")
        assertEquals(listOf("9", "7"), list.map { it.id })   // sorted by number
        assertEquals(7, list[1].archiveDays)
        assertTrue(list[0].divider)
    }

    @Test
    fun base64() {
        val b = ByteArray(300) { it.toByte() }
        assertTrue(b.contentEquals(B64.decode(B64.encode(b))))
        assertNull(B64.decode("not base64!"))
    }

    @Test
    fun loginIsStoredThroughTheSecretStore() {
        SecretBox.store = object : SecretStore {
            override fun encrypt(plain: String) = "v1:" + B64.encode(plain.reversed().toByteArray())
            override fun decrypt(stored: String) = String(B64.decode(stored.removePrefix("v1:"))!!).reversed()
        }
        val store = MemoryStore()
        val p = Playlist("xtream", "Home", "http://h:80", "user", "secret")
        Prefs(store).playlist = p
        assertTrue(store.getString("playlist_enc", null)!!.startsWith("v1:"))
        assertNull(store.getString("playlist", null))
        assertEquals(p, Prefs(store).playlist)
    }

    @Test
    fun liveFormatChoice() {
        val prefs = Prefs(MemoryStore())
        prefs.liveFormat = "m3u8"
        assertEquals("m3u8", prefs.effectiveLiveFormat)
        prefs.liveFormatChoice = "ts"
        assertEquals("ts", prefs.effectiveLiveFormat)
    }
}
