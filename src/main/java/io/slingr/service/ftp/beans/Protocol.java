package io.slingr.service.ftp.beans;

/**
 * Available protocols.
 */
public enum Protocol {
    FTP("21", "ftp"), FTPS("21", "ftps"), SFTP("22", "sftp");

    private final String defaultPort;
    private final String code;

    Protocol(String defaultPort, String code) {
        this.defaultPort = defaultPort;
        this.code = code;
    }

    public String getDefaultPort() {
        return defaultPort;
    }

    public String getCode() {
        return code;
    }

    static public Protocol fromCode(String code) {
        for (Protocol proto : values()) {
            if (proto.getCode().equals(code)) {
                return proto;
            }
        }
        return FTP;
    }
}
