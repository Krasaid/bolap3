package com.nexbytes.bolap3.data.db;

/** Query result: a playlist with the number of its songs that still exist in the library. */
public class PlaylistSummary {
    public long id;
    public String name;
    public int songCount;
    public String coverUri;
}
