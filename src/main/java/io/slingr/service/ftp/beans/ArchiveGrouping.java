package io.slingr.service.ftp.beans;

/**
 * Ways to group archive of files.
 *
 * Created by dgaviola on 1/9/15.
 */
public enum ArchiveGrouping {
    DAILY("daily", "yyyy-MM-dd"),
    WEEKLY("weekly", "yyyy-MM-W"),
    MONTHLY("monthly", "yyyy-MM"),
    NONE("none", null);

    private String code;
    private String format;

    ArchiveGrouping(String code, String format) {
        this.code = code;
        this.format = format;
    }

    public String getCode() {
        return code;
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
