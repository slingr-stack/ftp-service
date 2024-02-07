package io.slingr.service.ftp.beans;

/**
 * Ways to group archive of files.
 */
public enum ArchiveGrouping {
    DAILY("daily", "yyyy-MM-dd"),
    WEEKLY("weekly", "yyyy-MM-W"),
    MONTHLY("monthly", "yyyy-MM"),
    NONE("none", null);

    private final String code;
    private final String format;

    ArchiveGrouping(String code, String format) {
        this.code = code;
        this.format = format;
    }

    public String getFormat() {
        return format;
    }

    public static ArchiveGrouping fromCode(String code) {
        for (ArchiveGrouping value : values()) {
            if (value.code.endsWith(code)) {
                return value;
            }
        }
        return null;
    }
}
