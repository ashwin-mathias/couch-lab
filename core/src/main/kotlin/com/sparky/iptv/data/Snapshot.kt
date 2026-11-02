package com.sparky.iptv.data

import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * A compact copy of the library, holding only the fields the app uses, in a binary format.
 * Reading it is many times faster than re-reading the provider's raw lists on every start.
 */
object Snapshot {
    // Version 2 adds catch-up days to channels; version 1 files are still readable.
    private const val VERSION = 2
    private const val MAGIC = 0x5350524B // "SPRK"

    // ---------- small helpers ----------
    private fun DataOutputStream.str(s: String?) {
        if (s == null) { writeInt(-1); return }
        val b = s.toByteArray(Charsets.UTF_8)
        writeInt(b.size)
        write(b)
    }

    /**
     * A plain buffered reader for the snapshot files (2.9). DataInputStream over BufferedInputStream
     * goes through a synchronized read() for every byte of every number; this decodes straight from
     * one 256 KB buffer, and makes strings without an extra copy. Same file format.
     */
    private class FastIn(private val inp: java.io.InputStream) : java.io.Closeable {
        private val buf = ByteArray(256 * 1024)
        private var pos = 0
        private var lim = 0

        /** Makes at least [n] bytes available at [pos] (n ≤ buffer size). */
        private fun need(n: Int) {
            if (lim - pos >= n) return
            System.arraycopy(buf, pos, buf, 0, lim - pos)
            lim -= pos
            pos = 0
            while (lim < n) {
                val r = inp.read(buf, lim, buf.size - lim)
                if (r < 0) throw java.io.EOFException()
                lim += r
            }
        }

        fun readInt(): Int {
            need(4)
            val b = buf; val p = pos
            pos += 4
            return (b[p].toInt() and 0xFF shl 24) or (b[p + 1].toInt() and 0xFF shl 16) or
                (b[p + 2].toInt() and 0xFF shl 8) or (b[p + 3].toInt() and 0xFF)
        }

        fun readLong(): Long = (readInt().toLong() shl 32) or (readInt().toLong() and 0xFFFFFFFFL)

        fun str(): String? {
            val n = readInt()
            if (n < 0) return null
            if (n <= buf.size) {
                need(n)
                val s = String(buf, pos, n, Charsets.UTF_8)
                pos += n
                return s
            }
            // Longer than the buffer (never expected): read it in pieces.
            val out = ByteArray(n)
            var done = 0
            while (done < n) {
                need(1)
                val k = minOf(lim - pos, n - done)
                System.arraycopy(buf, pos, out, done, k)
                pos += k
                done += k
            }
            return String(out, Charsets.UTF_8)
        }

        fun strNN(): String = str() ?: ""

        override fun close() = inp.close()
    }

    private inline fun write(file: File, block: DataOutputStream.() -> Unit) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        DataOutputStream(BufferedOutputStream(FileOutputStream(tmp), 256 * 1024)).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.block()
        }
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    private inline fun <T> read(file: File, block: FastIn.(Int) -> T): T? {
        if (!file.exists() || file.length() < 8) return null
        return FastIn(FileInputStream(file)).use { inp ->
            if (inp.readInt() != MAGIC) return null
            val v = inp.readInt()
            if (v < 1 || v > VERSION) return null
            inp.block(v)
        }
    }

    private fun DataOutputStream.cats(list: List<Category>) {
        writeInt(list.size)
        list.forEach { str(it.id); str(it.name) }
    }

    private fun FastIn.cats(): List<Category> {
        val n = readInt()
        return ArrayList<Category>(n).apply { repeat(n) { add(Category(strNN(), strNN())) } }
    }

    // ---------- live ----------
    fun writeLive(file: File, cats: List<Category>, channels: List<Channel>) = write(file) {
        cats(cats)
        writeInt(channels.size)
        for (c in channels) {
            str(c.id); writeInt(c.num); str(c.name); str(c.logo); str(c.categoryId); str(c.epgId); str(c.directUrl)
            writeInt(c.archiveDays)
        }
    }

    fun readLive(file: File, onProgress: (Int, Int) -> Unit = { _, _ -> }): Pair<List<Category>, List<Channel>>? = read(file) { v ->
        val cats = cats()
        val n = readInt()
        val out = ArrayList<Channel>(n)
        repeat(n) { i ->
            val id = strNN(); val num = readInt(); val name = strNN(); val logo = str()
            val cat = strNN().intern(); val epg = str(); val direct = str()
            val archive = if (v >= 2) readInt() else 0
            out.add(
                Channel(
                    id = id, num = num, name = name, logo = logo,
                    categoryId = cat, epgId = epg, directUrl = direct, archiveDays = archive,
                )
            )
            if (i % 20_000 == 0) onProgress(i, n)
        }
        cats to out
    }

    // ---------- movies ----------
    fun writeMovies(file: File, cats: List<Category>, movies: List<Movie>) = write(file) {
        cats(cats)
        writeInt(movies.size)
        for (m in movies) {
            str(m.id); str(m.name); str(m.poster); str(m.categoryId); str(m.rating); writeLong(m.added); str(m.ext)
        }
    }

    fun readMovies(file: File, onProgress: (Int, Int) -> Unit = { _, _ -> }): Pair<List<Category>, List<Movie>>? = read(file) { _ ->
        val cats = cats()
        val n = readInt()
        val out = ArrayList<Movie>(n)
        repeat(n) { i ->
            out.add(
                Movie(
                    id = strNN(), name = strNN(), poster = str(), categoryId = strNN().intern(),
                    rating = str()?.intern(), added = readLong(), ext = strNN().intern(),
                )
            )
            if (i % 20_000 == 0) onProgress(i, n)
        }
        cats to out
    }

    // ---------- series ----------
    fun writeSeries(file: File, cats: List<Category>, series: List<Series>) = write(file) {
        cats(cats)
        writeInt(series.size)
        for (s in series) {
            str(s.id); str(s.name); str(s.cover); str(s.categoryId); str(s.plot); str(s.year)
            str(s.rating); str(s.genre); writeLong(s.lastModified)
        }
    }

    fun readSeries(file: File, onProgress: (Int, Int) -> Unit = { _, _ -> }): Pair<List<Category>, List<Series>>? = read(file) { _ ->
        val cats = cats()
        val n = readInt()
        val out = ArrayList<Series>(n)
        repeat(n) { i ->
            out.add(
                Series(
                    id = strNN(), name = strNN(), cover = str(), categoryId = strNN().intern(), plot = str(),
                    year = str(), rating = str()?.intern(), genre = str()?.intern(), lastModified = readLong(),
                )
            )
            if (i % 20_000 == 0) onProgress(i, n)
        }
        cats to out
    }
}
