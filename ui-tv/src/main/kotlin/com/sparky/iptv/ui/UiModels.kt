package com.sparky.iptv.ui

import com.sparky.iptv.data.Channel
import com.sparky.iptv.data.Library
import com.sparky.iptv.data.Movie
import com.sparky.iptv.data.Names
import com.sparky.iptv.data.Series

// Screens, requests, list rows and constants shared by the UI (moved out of AppViewModel.kt in 2.8).

sealed interface Screen {
    data object Setup : Screen
    data object Loading : Screen
    data object Home : Screen
    data object About : Screen
    data object Diagnostics : Screen
    data object SubtitleStyle : Screen
    data object FavFolders : Screen
    data class BackupRestore(val restore: Boolean) : Screen
    data class Regions(val firstRun: Boolean) : Screen
    data class MovieDetail(val movie: Movie) : Screen
    data class SeriesDetail(val series: Series) : Screen
    data class Player(val request: PlayRequest) : Screen
}

enum class Section { HOME, LIVE, GUIDE, FAVORITES, RECENT, MOVIES, SERIES, SEARCH, SETTINGS }

sealed interface PlayRequest {
    data class Live(val channels: List<Channel>, val index: Int) : PlayRequest
    data class Vod(val items: List<VodItem>, val index: Int, val resumeMs: Long) : PlayRequest
}

data class VodItem(
    val key: String, val title: String, val subtitle: String, val url: String,
    val seriesId: String? = null, val episodeId: String? = null,
    /** Catch-up recordings don't keep resume points. */
    val saveProgress: Boolean = true,
)

const val SLEEP_WARNING = "Sleeping in 1 minute · press any key to keep watching"

const val GROUP_FAV = "__fav"
const val GROUP_ALL = "__all"
const val GROUP_RECENT = "__recent"
const val GROUP_ACTION_ALL_REGIONS = "__toggle_regions"

enum class GroupKind { GROUP, HEADER, ACTION }

/**
 * One row in a group list: a channel group, a region heading, or an action.
 * Headings carry their [region] and whether they're [open]; favorites folders are [indent]ed under Favorites.
 */
data class GroupItem(
    val id: String, val name: String, val count: Int, val kind: GroupKind = GroupKind.GROUP,
    val region: String? = null, val open: Boolean = true, val indent: Boolean = false,
)

/** Favorites folders appear as groups with this prefix plus the folder id. */
const val GROUP_FOLDER = "fav:"

/** Lists that can be reordered: all favorite channels / movies / series, one folder, or the folders themselves. */
const val LIST_LIVE = "all:live"
const val LIST_MOVIE = "all:movie"
const val LIST_SERIES = "all:series"
const val LIST_FOLDERS = "folders"
fun folderList(id: String) = "folder:$id"

/** A favorite being moved: [working] is the list as shown while it moves. Keys move by [upDown] / [leftRight] places. */
data class MoveSession(val listId: String, val key: String, val working: List<String>, val upDown: Int, val leftRight: Int)

/** The hold-OK menu for a favorite. [moveList] is null where the list can't be reordered. */
data class FavMenuRequest(val key: String, val label: String, val moveList: String?, val upDown: Int, val leftRight: Int)

data class RegionInfo(val code: String, val label: String, val channels: Int)

/** Lookups derived from the library: which region each category belongs to, cleaned names, counts. */
class LiveIndex(lib: Library) {
    val catRegion: Map<String, String> = lib.liveCats.associate { it.id to (Names.region(it.name) ?: Names.OTHER) }
    val catName: Map<String, String> = lib.liveCats.associate { it.id to Names.strip(it.name) }
    val vodRegion: Map<String, String> = lib.vodCats.associate { it.id to (Names.region(it.name) ?: Names.OTHER) }
    val seriesRegion: Map<String, String> = lib.seriesCats.associate { it.id to (Names.region(it.name) ?: Names.OTHER) }
    val countByCat: Map<String, Int> = lib.channels.asSequence().filter { !it.divider }.groupingBy { it.categoryId }.eachCount()
    val regions: List<RegionInfo> = run {
        val counts = HashMap<String, Int>()
        for ((cat, n) in countByCat) {
            val r = catRegion[cat] ?: Names.OTHER
            counts[r] = (counts[r] ?: 0) + n
        }
        counts.map { (code, n) -> RegionInfo(code, Names.regionLabel(code), n) }.sortedByDescending { it.channels }
    }

    fun regionOfChannel(ch: Channel): String = catRegion[ch.categoryId] ?: Names.OTHER

    /** Channels (dividers included, provider order) per category, built once on first use (2.8). */
    val byCat: Map<String, List<Channel>> by lazy { lib.channels.groupBy { it.categoryId } }

    companion object {
        val EMPTY = LiveIndex(Library.EMPTY)
    }
}
const val CAT_RECENT = "__recent"
const val CAT_FAV = "__fav"
const val CAT_ALL = "__all"

