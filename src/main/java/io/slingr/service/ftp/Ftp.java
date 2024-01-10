package io.slingr.service.ftp;

import io.slingr.service.ftp.beans.Processor;
import io.slingr.services.Service;
import io.slingr.services.exceptions.ErrorCode;
import io.slingr.services.exceptions.ServiceException;
import io.slingr.services.framework.annotations.ServiceFunction;
import io.slingr.services.framework.annotations.ServiceProperty;
import io.slingr.services.framework.annotations.SlingrService;
import io.slingr.services.utils.Json;
import io.slingr.services.ws.exchange.FunctionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SlingrService(name = "ftp")
public class Ftp extends Service {

    private static final Logger logger = LoggerFactory.getLogger(Ftp.class);

    @ServiceProperty(name = "protocol")
    private String protocol;

    @ServiceProperty(name = "host")
    private String host;

    @ServiceProperty(name = "port")
    private String port;

    @ServiceProperty(name = "username")
    private String username;

    @ServiceProperty(name = "password")
    private String password;

    @ServiceProperty(name = "filePattern")
    private String filePattern;

    @ServiceProperty(name = "inputFolder")
    private String inputFolder;

    @ServiceProperty(name = "archiveFolder")
    private String archiveFolder;

    @ServiceProperty(name = "archiveGrouping")
    private String archiveGrouping;

    @ServiceProperty(name = "outputFolder")
    private String outputFolder;

    @ServiceProperty(name = "recursive")
    private Boolean recursive;

    private Processor processor = null;

    @Override
    public void serviceStarted() {
        logger.info("Starting FTP service");
        processor = new Processor(appLogs(), events(), files(), properties().getApplicationName(), properties().isLocalDeployment(),
                protocol, host, port, username, password, filePattern, inputFolder, archiveFolder, archiveGrouping,
                recursive, outputFolder);
        processor.start();
    }

    @Override
    public void serviceStopped(String cause) {
        logger.info(String.format("Stopping FTP service: %s", cause));

        if(processor != null){
            processor.stop();
        }
    }

    @ServiceFunction(name = "uploadFile")
    public void uploadFile(FunctionRequest request){
        try {
            final Json body = request.getJsonParams();
            processor.sendFile(body.string("fileId"), body.string("folder"));
        } catch (ServiceException ex){
            throw ex;
        } catch (Exception ex){
            throw ServiceException.permanent(ErrorCode.GENERAL, String.format("An exception happened in the service: %s", ex.getMessage()), ex);
        }
    }
}
