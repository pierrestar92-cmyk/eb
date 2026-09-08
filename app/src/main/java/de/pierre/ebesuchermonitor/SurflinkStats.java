package de.pierre.ebesuchermonitor;

public final class SurflinkStats {
    public final long id;
    public final String fullName;
    public final String url;
    public final String lastActivity;
    public final long lastActivityMillis;
    public double todayBtp;
    public double last10MinutesBtp;

    public SurflinkStats(long id, String fullName, String url, String lastActivity, long lastActivityMillis) {
        this.id = id;
        this.fullName = fullName;
        this.url = url;
        this.lastActivity = lastActivity;
        this.lastActivityMillis = lastActivityMillis;
    }
}
