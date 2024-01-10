package io.slingr.service.ftp.beans;

/**
 * Available protocols.
 *
 * Created by dgaviola on 31/8/15.
 */
public enum Protocol {
    FTP("21", "ftp"), FTPS("21", "ftps"), SFTP("22", "sftp");

    private String defaultPort;
    private String code;

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
