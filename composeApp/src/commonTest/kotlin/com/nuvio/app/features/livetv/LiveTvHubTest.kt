package com.nuvio.app.features.livetv

import kotlin.test.Test
import kotlin.test.assertEquals

class LiveTvHubTest {
    private fun channel(name: String, group: String? = null, url: String = name, playlist: String? = null) =
        LiveTvChannel(id = url, name = name, streamUrl = url, group = group, playlistName = playlist)

    @Test
    fun sortsChannelsIntoThemes() {
        assertEquals(LiveTvTheme.News, LiveTvTheme.of(channel("France 24", group = "News")))
        assertEquals(LiveTvTheme.Sports, LiveTvTheme.of(channel("beIN Sports 1")))
        assertEquals(LiveTvTheme.Kids, LiveTvTheme.of(channel("Gulli")))
        assertEquals(LiveTvTheme.General, LiveTvTheme.of(channel("TF1", group = "Undefined")))
    }

    @Test
    fun mergesTheSameChannelFromSeveralPlaylists() {
        val entries = listOf(
            channel("Arte HD (1080p)", url = "a", playlist = "France"),
            channel("ARTE", url = "b", playlist = "Français"),
            channel("TV5Monde", url = "c", playlist = "France"),
        ).mergedByChannelName()

        assertEquals(2, entries.size)
        assertEquals(listOf("b"), entries.first { it.channel.streamUrl == "a" }.alternates.map { it.streamUrl })
    }

    @Test
    fun listsSourceFoldersOnlyForSeveralPlaylists() {
        val folders = buildLiveTvFolders(
            listOf(channel("A", group = "News", url = "1", playlist = "X"), channel("B", group = "News", url = "2", playlist = "Y")),
        )
        assertEquals(listOf("theme:news", "source:X", "source:Y"), folders.map { it.key })
    }
}
