package io.slingr.service.ftp.beans;

import io.slingr.services.exceptions.ServiceException;
import io.slingr.services.exceptions.ErrorCode;
import io.slingr.service.ftp.components.CustomSftpComponent;
import io.slingr.services.services.AppLogs;
import io.slingr.services.services.Events;
import io.slingr.services.services.Files;
import io.slingr.services.utils.Json;
import org.apache.camel.*;
import org.apache.camel.builder.DefaultFluentProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ValueBuilder;
import org.apache.camel.component.file.remote.SftpComponent;
import org.apache.camel.main.Main;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Processor extends RouteBuilder {

    private static final Logger logger = Logger.getLogger(Processor.class);

    private static final long POLL_INTERVAL = 30 * 1000; // 30 seconds
    private static final long RECONNECTION_ATTEMPTS = 10;
    private static final long RECONNECTION_DELAY = 10000;
    private static final long LOCK_TIMEOUT = 30000;
    private static final long LOCK_CHECK_INTERVAL = 5000;
    private static final String SFTP_COMPONENT = "sftp";
    private static final String NEW_FILE_EVENT = "newFile";

    private final AppLogs appLogs;
    private final Events events;
    private final FilesService filesService;
    private final Main main = new Main();

    private final String name;
    private boolean localDeployment;
    private final Protocol protocol;
    private final String host;
    private final String port;
    private final String username;
    private final String password;
    private final String filePattern;
    private final String inputFolder;
    private final String archiveFolder;
    private final String archivedOutputFolder;
    private final boolean recursive;
    private final String parentOutputFolder;

    public Processor(AppLogs appLogs, Events events, Files files, String name, boolean localDeployment,
                     String protocol, String host, String port, String username, String password, String filePattern,
                     String inputFolder, String archiveFolder, String archiveGrouping, Boolean recursive, String outputFolder
    ) {
        this.appLogs = appLogs;
        this.events = events;
        this.name = name;
        this.localDeployment = localDeployment;

        logger.info(String.format("Configured FTP service [%s://%s@%s:%s]", protocol, username, host, port));
        Protocol pProtocol = Protocol.fromCode(protocol);
        if (pProtocol == null) {
            pProtocol = Protocol.FTP;
        }
        this.protocol = pProtocol;

        this.host = host;
        if (StringUtils.isBlank(port)) {
            this.port = this.protocol.getDefaultPort();
        } else {
            this.port = port;
        }
        if (StringUtils.isBlank(username)) {
            this.username = null;
            this.password = null;
        } else {
            this.username = username;
            this.password = password;
        }
        this.filePattern = filePattern;

        final String folder1 = normalizeFolder(inputFolder);
        final String folder2 = normalizeFolder(archiveFolder);

        String iFolder = null;
        String aFolder = null;
        if (folder1.equals(folder2)) {
            throw new IllegalArgumentException(String.format("Input and archive folders must be different [%s]", folder1));
        } else {
            iFolder = folder1;
            aFolder = "/" + folder2;
            if(!aFolder.endsWith("/")){
                aFolder += "/";
            }
        }

        this.recursive = Boolean.TRUE.equals(recursive);
        if(this.recursive){
            if(folder1.startsWith(folder2)){
                throw new IllegalArgumentException(
                        String.format("When the recursive option is enabled, the archive folder [%s] must be outside of the input folder [%s]", folder2, folder1)
                );
            }
        }

        this.archivedOutputFolder = folder2;

        // output folder
        String outputFolder1 = normalizeFolder(outputFolder);

        // add filename pattern if needed
        ArchiveGrouping ag = ArchiveGrouping.fromCode(archiveGrouping);
        if (ag == null) {
            ag = ArchiveGrouping.MONTHLY;
        }
        if(this.recursive) {
            if (ag == ArchiveGrouping.NONE) {
                aFolder += "${file:parent}/${date:now:yyyyMMddHHmmss}-${file:onlyname}";
            } else {
                aFolder += "${date:now:" + ag.getFormat() + "}/${file:parent}/${date:now:yyyyMMddHHmmss}-${file:onlyname}";
            }
        } else {
            if (ag == ArchiveGrouping.NONE) {
                aFolder += "${date:now:yyyyMMddHHmmss}-${file:onlyname}";
            } else {
                aFolder += "${date:now:" + ag.getFormat() + "}/${date:now:yyyyMMddHHmmss}-${file:onlyname}";
            }
        }
        this.inputFolder = iFolder;
        this.archiveFolder = aFolder;

        logger.info(String.format("FTP folders: input [%s], archive [%s], output [%s]", this.inputFolder, this.archivedOutputFolder, outputFolder1));

        this.parentOutputFolder = StringUtils.isNotBlank(outputFolder1) ? outputFolder1 + "/" : "";

        // we need to create a dedicated application client object to avoid blocking issues
        filesService = new FilesService(files, this.appLogs, this.recursive, this.inputFolder, archivedOutputFolder, parentOutputFolder);
    }

    public void start() {
        try {
            main.addRouteBuilder(this);
            main.run();
        } catch (Exception ex) {
            String message = String.format("Error when try to start the ftp component: %s", ex.getMessage());
            appLogs.error(message);
            throw ServiceException.permanent(ErrorCode.ARGUMENT, message, ex);
        }
    }

    public void stop() {
        try {
            main.stop();
        } catch (Exception ex) {
            String message = String.format("Error when try to stop the ftp component: %s", ex.getMessage());
            appLogs.error(message);
            throw ServiceException.permanent(ErrorCode.ARGUMENT, message, ex);
        }
    }

    @Override
    public void configure() throws Exception {
        final Json parametersToPrint = Json.map();
        parametersToPrint.set("protocol", protocol.getCode());
        parametersToPrint.set("recursive", recursive);
        parametersToPrint.set("host", host);
        parametersToPrint.set("port", port);
        parametersToPrint.set("inputFolder", inputFolder);
        parametersToPrint.set("archivedOutputFolder", archivedOutputFolder);
        parametersToPrint.set("outputFolder", parentOutputFolder);

        String uri;
        final List<String> options = new ArrayList<>();
        final List<String> uploadOptions = new ArrayList<>();

        String uploadUri = null;
        if (StringUtils.isBlank(username)) {
            uri = protocol.getCode() + "://" + host + ":" + port + "/" + inputFolder;
            uploadUri = protocol.getCode() + "://" + host + ":" + port + "/" + parentOutputFolder;
        } else {
            parametersToPrint.set("username", username);
            uri = protocol.getCode() + "://" + username + "@" + host + ":" + port + "/" + inputFolder;
            uploadUri = protocol.getCode() + "://" + username + "@" + host + ":" + port + "/" + parentOutputFolder;

            if(StringUtils.isNotBlank(password)){
                options.add("password=" + password);
                uploadOptions.add("password=" + password);
                parametersToPrint.set("password", true);
            } else {
                parametersToPrint.set("password", true);
            }
        }

        if(protocol.equals(Protocol.SFTP)){
            // change default SFTP component
            final CamelContext context = getContext();
            final SftpComponent sftpComponent;
            if(localDeployment) {
                sftpComponent = new SftpComponent();
            } else {
                sftpComponent = new CustomSftpComponent();
            }
            context.addComponent(SFTP_COMPONENT, sftpComponent);
        } else if(protocol.equals(Protocol.FTPS)){
            // change default FTPS security values
            options.add("disableSecureDataChannelDefaults=true");
            uploadOptions.add("disableSecureDataChannelDefaults=true");
            parametersToPrint.set("disableSecureDataChannelDefaults", true);

            options.add("execProt=P");
            uploadOptions.add("execProt=P");
            parametersToPrint.set("execProt", "P");

            options.add("execPbsz=0");
            uploadOptions.add("execPbsz=0");
            parametersToPrint.set("execPbsz", 0);
        }

        options.add("passiveMode=true");
        uploadOptions.add("passiveMode=true");
        parametersToPrint.set("passiveMode", true);

        options.add("disconnect=true");
        uploadOptions.add("disconnect=true");
        parametersToPrint.set("disconnect", true);

        options.add("delay=" + POLL_INTERVAL);
        parametersToPrint.set("delay", POLL_INTERVAL);

        final String localWorkDirectory = String.format("/tmp/%sFtpTmp", name);
        options.add("localWorkDirectory="+localWorkDirectory);
        uploadOptions.add("localWorkDirectory="+localWorkDirectory);
        parametersToPrint.set("localWorkDirectory", localWorkDirectory);

        options.add("binary=true");
        uploadOptions.add("binary=true");
        parametersToPrint.set("binary", true);

        // we process one file at a time to avoid issues in some cases better to be safe than fast as we have had many issues in the past
        options.add("maxMessagesPerPoll=1");
        parametersToPrint.set("maxMessagesPerPoll", 1);

        options.add("maximumReconnectAttempts=" + RECONNECTION_ATTEMPTS);
        parametersToPrint.set("maximumReconnectAttempts", RECONNECTION_ATTEMPTS);

        options.add("reconnectDelay=" + RECONNECTION_DELAY);
        parametersToPrint.set("reconnectDelay", RECONNECTION_DELAY);

        if (!StringUtils.isBlank(filePattern)) {
            options.add("antInclude=" + filePattern);
            parametersToPrint.set("antInclude", filePattern);

            options.add("antFilterCaseSensitive=false");
            parametersToPrint.set("antFilterCaseSensitive", false);
        }
        options.add("readLock=changed");
        parametersToPrint.set("readLock", "changed");

        options.add("fastExistsCheck=true");
        parametersToPrint.set("fastExistsCheck", true);

        options.add("ignoreFileNotFoundOrPermissionError=true");
        parametersToPrint.set("ignoreFileNotFoundOrPermissionError", true);

        options.add("readLockTimeout=" + LOCK_TIMEOUT);
        parametersToPrint.set("readLockTimeout", LOCK_TIMEOUT);

        options.add("readLockCheckInterval=" + LOCK_CHECK_INTERVAL);
        parametersToPrint.set("readLockCheckInterval", LOCK_CHECK_INTERVAL);

        options.add("readLockLoggingLevel=INFO");
        parametersToPrint.set("readLockLoggingLevel", "INFO");

        options.add("preMove=" + archiveFolder);
        parametersToPrint.set("preMove", archiveFolder);

        options.add("sendEmptyMessageWhenIdle=true");
        parametersToPrint.set("sendEmptyMessageWhenIdle", true);

        options.add("flatten=false");
        parametersToPrint.set("flatten", false);
        if(this.recursive){
            options.add("recursive=true");
            parametersToPrint.set("recursive", true);
        } else {
            options.add("recursive=false");
            parametersToPrint.set("recursive", false);
        }
        uri += "?" + StringUtils.join(options, "&");
        uploadUri += "${headers."+FilesService.HEADER_INTERNAL_FOLDER+"}?" + StringUtils.join(uploadOptions, "&");

        ///////////////////////////////////////////////////////////////////////////////////////////
        // Events
        ///////////////////////////////////////////////////////////////////////////////////////////

        logger.info(String.format("Starting FTP service with parameters: %s", parametersToPrint.toString()));

        from(uri)
                .routeId("ftp-new-file-event")
                .setHeader(FilesService.HEADER_NOT_EMPTY, constant(true))
                .bean(filesService, FilesService.FILES_SERVICE_METHOD_NEW_FILE)
                .choice()
                .when(simple(String.format("${header.%s}", FilesService.HEADER_NOT_EMPTY)))
                .bean(this, "sendEvent")
                .otherwise()
                .bean(filesService, FilesService.FILES_SERVICE_NO_FILES)
                .endChoice();

        ///////////////////////////////////////////////////////////////////////////////////////////
        // Functions
        ///////////////////////////////////////////////////////////////////////////////////////////

        from("direct:uploadFile")
                .routeId("ftp-upload-file-function")
                .bean(filesService, FilesService.FILES_SERVICE_METHOD_DOWNLOAD_FILE)
                .setHeader(FilesService.HEADER_RETRIES, constant(3))
                .loopDoWhile(simple(String.format("${header.%s} > 0", FilesService.HEADER_RETRIES)))
                .doTry()
                .to("seda:ftp-uploadFile?timeout="+TimeUnit.MINUTES.toMillis(10))
                .doCatch(Exception.class)
                .setBody(exceptionJsonDetails())
                .log(LoggingLevel.WARN, "Error when try to upload file [${body}]")
                .setHeader(FilesService.HEADER_RETRIES, simple(String.format("${header.%s}-1", FilesService.HEADER_RETRIES)))
                .endDoTry()
                .end();

        from("seda:ftp-uploadFile")
                .routeId("ftp-upload-file")
                .bean(filesService, FilesService.FILES_SERVICE_METHOD_UPLOAD_FILE)
                .recipientList(simple(uploadUri))
                .setHeader(FilesService.HEADER_RETRIES, constant(-1))
                .log(LoggingLevel.INFO, "File uploaded [${headers."+FilesService.HEADER_FILE_PATH+"}]")
                .setBody(constant(""));
    }

    public void sendFile(String fileId, String fileFolder) {
        final Json body = Json.map()
                .set("fileId", fileId)
                .setIfNotEmpty("folder", fileFolder);

        DefaultFluentProducerTemplate.on(getContext())
                .withBody(body)
                .to("direct:uploadFile")
                .request();
    }

    @Handler
    public void sendEvent(@Body Json body, @Headers Map<String, Object> headers){
        events.send(NEW_FILE_EVENT, body);
    }

    public static String normalizeFolder(String folder) {
        String newFolder = folder;
        if (StringUtils.isBlank(newFolder)) {
            newFolder = "";
        }
        newFolder = newFolder.trim();
        if (newFolder.startsWith("/")) {
            newFolder = newFolder.substring(1);
        }
        if (newFolder.endsWith("/")) {
            if(newFolder.length() > 1) {
                newFolder = newFolder.substring(0, newFolder.length() - 1);
            } else {
                newFolder = "";
            }
        }
        return newFolder;
    }

    /**
     * Returns a value builder that extract the exception and returns the error message in JSON.
     * Also if the exception is of type {@link ServiceException} it will return details of the
     * exception in the JSON.
     *
     * @return the value builder to extract exception information
     */
    public ValueBuilder exceptionJsonDetails() {
        Expression expression = new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Exception exception = exchange.getException();
                if (exception == null) {
                    exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                }

                ErrorCode code = ErrorCode.GENERAL;
                String message = "An exception happened in the service";
                Json additionalInformation = Json.map();

                if (exception != null) {
                    message = exception.getMessage();

                    if (exception instanceof ServiceException) {
                        code = ((ServiceException) exception).getCode();
                        if(!((ServiceException) exception).getAdditionalInfo().isEmpty()) {
                            additionalInformation = ((ServiceException) exception).getAdditionalInfo();
                        }
                    }
                }

                return ServiceException.permanent(code, message, additionalInformation).toJson(true);
            }

            @Override
            public String toString() {
                return "exchangeExceptionJsonDetails";
            }
        };
        return new ValueBuilder(expression);
    }
}