package com.sparky.iptv.data

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * A compact copy of the library, holding only the fields the app uses, in a binary format.
 * Reading it is many times faster than re-reading the provider's raw lists on every start.
 */
object Snapshot {
    private const val VERSION = 1
    private const val MAGIC = 0x5350524B // "SPRK"

    // ---------- small helpers ----------
    private fun DataOutputStream.str(s: String?) {
        if (s == null) { writeInt(-1); return }
        val b = s.toByteArray(Charsets.UTF_8)
        writeInt(b.size)
        write(b)
    }

    private fun DataInputStream.str(): String? {
        val n = readInt()
        if (n < 0) return null
        val b = ByteArray(n)
        readFully(b)
        return String(b, Charsets.UTF_8)
    }

    private fun DataInputStream.strNN(): String = str() ?: ""

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

    private inline fun <T> read(file: File, block: DataInputStream.() -> T): T? {
        if (!file.exists() || file.length() < 8) return null
        return DataInputStream(BufferedInputStream(FileInputStream(file), 256 * 1024)).use { inp ->
            if (inp.readInt() != MAGIC || inp.readInt() != VERSION) return null
            inp.block()
        }
    }

    private fun DataOutputStream.cats(list: List<Category>) {
        writeInt(list.size)
        list.forEach { str(it.id); str(it.name) }
    }

    private fun DataInputStream.cats(): List<Category> {
        val n = readInt()
        return ArrayList<Category>(n).apply { repeat(n) { add(Category(strNN(), strNN())) } }
    }

    // ---------- live ----------
    fun writeLive(file: File, cats: List<Category>, channels: List<Channel>) = write(file) {
        cats(cats)
        writeInt(channels.size)
        for (c in channels) {
            str(c.id); writeInt(c.num); str(c.name); str(c.logo); str(c.categoryId); str(c.epgId); str(c.directUrl)
        }
    }

    fun readLive(file: File, onProgress: (Int, Int) -> Unit = { _, _ -> }): Pair<List<Category>, List<Channel>>? = read(file) {
        val cats = cats()
        val n = readInt()
        val out = ArrayList<Channel>(n)
        repeat(n) { i ->
            out.add(
                Channel(
                    id = strNN(), num = readInt(), name = strNN(), logo = str(),
                    categoryId = strNN().intern(), epgId = str(), directUrl = str(),
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

    fun readMovies(file: File, onProgress: (Int, Int) -> Unit = { _, _ -> }): Pair<List<Category>, List<Movie>>? = read(file) {
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

    fun readSeries(file: File, onProgress: (Int, Int) -> Unit = { _, _ -> }): Pair<List<Category>, List<Series>>? = read(file) {
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
