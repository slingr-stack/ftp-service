package io.slingr.service.ftp.components;

import com.jcraft.jsch.ChannelSftp;
import org.apache.camel.CamelContext;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.remote.FtpUtils;
import org.apache.camel.component.file.remote.SftpComponent;
import org.apache.camel.component.file.remote.SftpConfiguration;

import java.net.URI;
import java.util.Map;

/**
 * Same functionality of Secure FTP Component but rewrite the /root/.ssh/known_hosts file
 *
 * Created by lefunes on 15/05/17.
 */
public class CustomSftpComponent extends SftpComponent {

    public CustomSftpComponent() {
        setEndpointClass(CustomSftpEndpoint.class);
    }

    public CustomSftpComponent(CamelContext context) {
        super(context);
        setEndpointClass(CustomSftpEndpoint.class);
    }

    @Override
    protected GenericFileEndpoint<ChannelSftp.LsEntry> buildFileEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // get the base uri part before the options as they can be non URI valid such as the expression using $ chars
        // and the URI constructor will regard $ as an illegal character and we dont want to enforce end users to
        // to escape the $ for the expression (file language)
        String baseUri = uri;
        if (uri.indexOf("?") != -1) {
            baseUri = uri.substring(0, uri.indexOf("?"));
        }

        // lets make sure we create a new configuration as each service can
        // customize its own version
        SftpConfiguration config = new SftpConfiguration(new URI(baseUri));

        FtpUtils.ensureRelativeFtpDirectory(this, config);

        return new CustomSftpEndpoint(uri, this, config);
    }
}
