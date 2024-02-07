package io.slingr.service.ftp.components;

import com.jcraft.jsch.ChannelSftp;
import org.apache.camel.component.file.remote.*;

/**
 * Same functionality of Secure FTP Endpoint but rewrite the /root/.ssh/known_hosts file.
 */
public class CustomSftpEndpoint extends SftpEndpoint {

    public CustomSftpEndpoint(String uri, SftpComponent component, SftpConfiguration configuration) {
        super(uri, component, configuration);
    }

    @Override
    public RemoteFileOperations<ChannelSftp.LsEntry> createRemoteFileOperations() {
        SftpOperations operations = new CustomSftpOperations(proxy);
        operations.setEndpoint(this);
        return operations;
    }
}
