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

    @ServiceConfiguration
    private Json configuration;

    private Processor processor = null;

    @Override
    public void serviceStarted() {
        logger.info(String.format("Initializing service [%s]", SERVICE_NAME));
        appLogs.info(String.format("Initializing service [%s]", SERVICE_NAME));
        //initProcessor();
    }

    private void initProcessor() {
        logger.info(String.format("Service configuration [%s]", configuration.toPrettyString()));
        processor = new Processor(appLogs(), events(), files(), properties().getApplicationName(), properties().isLocalDeployment(),
                configuration.string("protocol"), configuration.string("host"), configuration.string("port"),
                configuration.string("username"), configuration.string("password"), configuration.string("filePattern"),
                configuration.string("inputFolder"), configuration.string("archiveFolder"), configuration.string("archiveGrouping"),
                configuration.bool("recursive"), configuration.string("outputFolder"));
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
            if (body.contains("config")) {
                configuration= body.json("config");
                this.initProcessor();
            } else {
                throw ServiceException.permanent(ErrorCode.ARGUMENT, "Empty configuration");
            }
            processor.sendFile(body.string("fileId"), body.string("folder"));
        } catch (ServiceException ex){
            throw ex;
        } catch (Exception ex){
            throw ServiceException.permanent(ErrorCode.GENERAL, String.format("An exception happened in the service: %s", ex.getMessage()), ex);
        }
        finally {
            this.stopProcessor();
        }
    }
}
