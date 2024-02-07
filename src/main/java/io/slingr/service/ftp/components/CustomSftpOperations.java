package io.slingr.service.ftp.components;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Proxy;
import com.jcraft.jsch.Session;
import org.apache.camel.component.file.remote.RemoteFileConfiguration;
import org.apache.camel.component.file.remote.SftpOperations;
import org.apache.log4j.Logger;

/**
 * Same functionality of Secure FTP Operations but rewrite the /root/.ssh/known_hosts file.
 */
public class CustomSftpOperations extends SftpOperations {
    private static final Logger logger = Logger.getLogger(CustomSftpOperations.class);

    public CustomSftpOperations(Proxy proxy) {
        super(proxy);
    }

    @Override
    protected Session createSession(final RemoteFileConfiguration configuration) throws JSchException {
        final Session session = super.createSession(configuration);

        // set user information
        session.setUserInfo(new ExtendedUserInfo() {
            public String getPassphrase() {
                return null;
            }

            public String getPassword() {
                return configuration.getPassword();
            }

            public boolean promptPassword(String s) {
                return true;
            }

            public boolean promptPassphrase(String s) {
                return true;
            }

            public boolean promptYesNo(String s) {
                logger.warn("Server asks for confirmation (yes|no): " + s + ". Endpoint will answer yes.");
                // Return 'true' indicating modification of the host file is enabled.
                return true;
            }

            public void showMessage(String s) {
                logger.trace("Message received from Server: " + s);
            }

            public String[] promptKeyboardInteractive(String destination, String name, String instruction, String[] prompt, boolean[] echo) {
                // must return an empty array if the password is null
                if (configuration.getPassword() == null) {
                    return new String[0];
                } else {
                    return new String[]{configuration.getPassword()};
                }
            }
        });

        return session;
    }
}
