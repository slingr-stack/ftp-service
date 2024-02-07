package io.slingr.service.ftp;

import io.slingr.service.ftp.beans.Processor;
import io.slingr.services.Service;
import io.slingr.services.exceptions.ErrorCode;
import io.slingr.services.exceptions.ServiceException;
import io.slingr.services.framework.annotations.*;
import io.slingr.services.services.AppLogs;
import io.slingr.services.utils.Json;
import io.slingr.services.ws.exchange.FunctionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SlingrService(name = "ftp")
public class Ftp extends Service {

    private static final String SERVICE_NAME = "ftp";
    private static final Logger logger = LoggerFactory.getLogger(Ftp.class);

    @ApplicationLogger
    private AppLogs appLogs;

    @ServiceProperty
    private String protocol;

    @ServiceProperty
    private String host;

    @ServiceProperty
    private String port;

    @ServiceProperty
    private String username;

    @ServiceProperty
    private String password;

    @ServiceProperty
    private String filePattern;

    @ServiceProperty
    private String inputFolder;

    @ServiceProperty
    private String archiveFolder;

    @ServiceProperty
    private String archiveGrouping;

    @ServiceProperty
    private String outputFolder;

    @ServiceProperty
    private Boolean recursive;

    @ServiceConfiguration
    private Json configuration;

    private Processor processor = null;

    @Override
    public void serviceStarted() {
        logger.info(String.format("Initializing service [%s]", SERVICE_NAME));
        appLogs.info(String.format("Initializing service [%s]", SERVICE_NAME));
        logger.info(String.format("Service configuration [%s]", configuration.toPrettyString()));
    }

    private void initProcessor() {
        processor = new Processor(appLogs(), events(), files(), properties().getApplicationName(), properties().isLocalDeployment(),
                protocol, host, port, username, password, filePattern, inputFolder, archiveFolder, archiveGrouping,
                recursive, outputFolder);
        processor.start();
    }

    @Override
    public void serviceStopped(String cause) {
        logger.info(String.format("Stopping FTP service: %s", cause));
        stopProcessor();
    }

    private void stopProcessor() {
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
