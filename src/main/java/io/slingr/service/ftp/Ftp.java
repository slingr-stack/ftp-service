package io.slingr.service.ftp;

import io.slingr.service.ftp.beans.Processor;
import io.slingr.services.Service;
import io.slingr.services.exceptions.ErrorCode;
import io.slingr.services.exceptions.ServiceException;
import io.slingr.services.framework.annotations.ApplicationLogger;
import io.slingr.services.framework.annotations.ServiceConfiguration;
import io.slingr.services.framework.annotations.ServiceFunction;
import io.slingr.services.framework.annotations.SlingrService;
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

    @ServiceConfiguration
    private Json configuration;

    private Processor processor = null;

    @Override
    public void serviceStarted() {
        logger.info("Initializing service [{}]", SERVICE_NAME);
        appLogs.info(String.format("Initializing service [%s]", SERVICE_NAME));
        initProcessor();
    }

    private void initProcessor() {
        logger.info("Service configuration [{}]", configuration.toPrettyString());
        if(processor == null) {
            processor = new Processor(appLogs(), events(), files(), properties().getApplicationName(), properties().isLocalDeployment(),
                    configuration.string("protocol"), configuration.string("host"), configuration.string("port"),
                    configuration.string("username"), configuration.string("password"),
                    configuration.string("filePattern") != null ? configuration.string("filePattern") : "",
                    configuration.string("inputFolder") != null ? configuration.string("inputFolder") : "",
                    configuration.string("archiveFolder"), configuration.string("archiveGrouping"),
                    configuration.string("recursive").equals("enabled"),
                    configuration.string("outputFolder") != null ? configuration.string("outputFolder") : "");
            processor.start();
        } else {
            stopProcessor();
            initProcessor();
        }
    }

    @Override
    public void serviceStopped(String cause) {
        logger.info(String.format("Stopping FTP service: %s", cause));
        stopProcessor();
    }

    private void stopProcessor() {
        if(processor != null) {
            processor.stop();
            processor = null;
        }
    }

    @ServiceFunction(name = "uploadFile")
    public void uploadFile(FunctionRequest request) {
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
