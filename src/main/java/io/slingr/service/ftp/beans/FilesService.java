package io.slingr.service.ftp.beans;

import io.slingr.services.exceptions.ServiceException;
import io.slingr.services.exceptions.ErrorCode;
import io.slingr.service.ftp.utils.EmailHelper;
import io.slingr.services.services.AppLogs;
import io.slingr.services.services.Files;
import io.slingr.services.services.rest.DownloadedFile;
import io.slingr.services.utils.FilesUtils;
import io.slingr.services.utils.Json;
import org.apache.camel.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In charge of uploading files into application.
 *
 * Created by dgaviola on 31/8/15.
 */
public class FilesService {
    private static final Logger logger = LoggerFactory.getLogger(FilesService.class);

    public static final String HEADER_INTERNAL_FOLDER = "FTP_INTERNAL_FOLDER";
    public static final String HEADER_FILE_PATH = "FTP_FILE_PATH";
    public static final String HEADER_LOCAL_FILE_PATH = "LOCAL_FILE_PATH";
    public static final String HEADER_RETRIES = "UPLOAD_RETRIES";
    public static final String HEADER_NOT_EMPTY = "NOT_EMPTY_FILE";

    public static final String FILES_SERVICE_METHOD_NEW_FILE = "newFile";
    public static final String FILES_SERVICE_METHOD_DOWNLOAD_FILE = "downloadFile";
    public static final String FILES_SERVICE_METHOD_UPLOAD_FILE = "uploadFile";
    public static final String FILES_SERVICE_NO_FILES = "noFiles";

    private static final int PERIODS_BETWEEN_NOT_FILE_MESSAGES = 25;

    private final Files files;
    private final AppLogs appLogs;
    private final boolean recursive;
    private final String inputFolder;
    private final String archivedOutputFolder;
    private final String parentOutputFolder;
    private final AtomicInteger noFilesCounter = new AtomicInteger(0);
    private final AtomicLong lastSync = new AtomicLong(System.currentTimeMillis());

    public FilesService(Files files, AppLogs appLogs, boolean recursive, String inputFolder, String archivedOutputFolder, String parentOutputFolder) {
        this.files = files;
        this.appLogs = appLogs;
        this.recursive = recursive;
        this.inputFolder = inputFolder;
        this.archivedOutputFolder = archivedOutputFolder;
        this.parentOutputFolder = parentOutputFolder;
    }

    @Handler @SuppressWarnings("unused") // used on Service routes
    /**
     * @see FILES_SERVICE_METHOD_NEW_FILE
     */
    public Json newFile(@Body InputStream is, @Headers Map headers){
        lastSync.set(System.currentTimeMillis());
        if(is == null){
            headers.put(HEADER_NOT_EMPTY, false);
            return null;
        }
        noFilesCounter.set(0);

        try {
            final String originalFileName = getOriginalFileName((String) headers.get(Exchange.FILE_NAME_ONLY));
            final String contentType = EmailHelper.getContentType((String) headers.get(Exchange.FILE_CONTENT_TYPE), originalFileName);

            String path = "";
            if (recursive) {
                path = (String) headers.get(Exchange.FILE_PARENT);
                if (path.matches(String.format("^/%s/.*%s.*", archivedOutputFolder, inputFolder))) {
                    path = path.substring(path.indexOf(inputFolder) + inputFolder.length());
                }
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
            }

            logger.info(String.format("New file on FTP [%s] - content type [%s] - path [%s]", originalFileName, contentType, path));

            logger.info(String.format("Starting uploading file [%s] to app runtime", originalFileName));
            final Json file = files.upload(originalFileName, is, contentType);
            logger.info(String.format("File [%s] was uploaded", originalFileName));
            if (StringUtils.isNotBlank(path)) {
                file.set("filePath", path);
            }
            return file;
        } catch (ServiceException ee){
            appLogs.error(ee.getMessage());
            throw ee;
        } catch (Exception ex){
            final String message = String.format("Exception when process new file: %s", ex.getMessage());
            appLogs.error(message);
            throw ServiceException.permanent(ErrorCode.CLIENT, message, ex);
        }
    }

    @Handler @SuppressWarnings("unused") // used on Service routes
    /**
     * @see FILES_SERVICE_METHOD_DOWNLOAD_FILE
     */
    public void downloadFile(@Body Json body, @Headers Map<String, String> headers) {
        String fileId = body.string("fileId");
        if(StringUtils.isBlank(fileId)){
            throw ServiceException.permanent(ErrorCode.ARGUMENT, "Empty file id");
        } else {
            fileId = fileId.trim();
        }

        String fileName = fileId;
        try {
            final Json metadata = files.metadata(fileId);
            if (metadata != null && StringUtils.isNotBlank(metadata.string("fileName"))) {
                String fn = metadata.string("fileName");
                logger.info(String.format("File name from app [%s]", fn));
                fileName = fn;
            }
        } catch (Exception ex){
            logger.info(String.format("Error when try to recover the file name from app [%s]", ex.getMessage()), ex);
        }

        headers.put(Exchange.FILE_NAME, fileName);
        headers.put(HEADER_FILE_PATH, parentOutputFolder+fileName);

        String folder = Processor.normalizeFolder(body.string("folder"));
        headers.put(HEADER_INTERNAL_FOLDER, "");
        if(StringUtils.isNotBlank(folder)){
            headers.put(HEADER_INTERNAL_FOLDER, folder);
            headers.put(HEADER_FILE_PATH, parentOutputFolder+folder+"/"+fileName);
        }
        logger.info(String.format("File to upload [%s]", headers.getOrDefault(HEADER_FILE_PATH, "<empty>")));

        // download the file from the service
        final DownloadedFile dwnFile = files.download(fileId);
        if(dwnFile == null || dwnFile.getFile() == null){
            final ServiceException re = ServiceException.permanent(ErrorCode.CLIENT, String.format("It is not possible to download the file [%s] from application to service. The file will not be uploaded to ftp.", fileName));
            logger.warn(re.getMessage());
            throw re;
        }
        final File tmp = FilesUtils.copyInputStreamToTemporaryFile(fileName, dwnFile.getFile());
        if(tmp == null || !tmp.exists()){
            final ServiceException re = ServiceException.permanent(ErrorCode.CLIENT, String.format("It is not possible to copy the file [%s] from application to service. The file will not be uploaded to ftp.", fileName));
            logger.warn(re.getMessage());
            throw re;
        }
        final long fileLength = tmp.length();
        if(fileLength < 1){
            final ServiceException re = ServiceException.permanent(ErrorCode.CLIENT, String.format("The copy of the file [%s] on service is empty. The file will not be uploaded to ftp.", fileName));
            logger.warn(re.getMessage());
            throw re;
        }
        headers.put(HEADER_LOCAL_FILE_PATH, tmp.getAbsolutePath());
    }


    @Handler @SuppressWarnings("unused") // used on Service routes
    /**
     * @see FILES_SERVICE_METHOD_UPLOAD_FILE
     */
    public void uploadFile(Exchange exchange, @Header(HEADER_LOCAL_FILE_PATH) String localFilePath) {
        if(StringUtils.isBlank(localFilePath)){
            final ServiceException re = ServiceException.permanent(ErrorCode.CLIENT, String.format("The copy of the file [%s] on service is invalid. The file will not be uploaded to ftp.", localFilePath));
            logger.warn(re.getMessage());
            throw re;
        }
        exchange.getIn().setBody(new File(localFilePath));
    }

    private String getOriginalFileName(String fileName) {
        int index = fileName.indexOf("-");
        if (index == -1) {
            return fileName;
        } else {
            return fileName.substring(index+1);
        }
    }

    @Handler @SuppressWarnings("unused") // used on Service routes
    /**
     * @see FILES_SERVICE_NO_FILES
     */
    public void noFiles(){
        int val = noFilesCounter.getAndIncrement();
        if(val > PERIODS_BETWEEN_NOT_FILE_MESSAGES || val < 0){
            val = 0;
            noFilesCounter.set(1);
        }
        if(val == 0){
            // show message
            logger.info("There is not files to process");
        }
    }

    public long lastSyncPeriod(){
        return System.currentTimeMillis() - lastSync.get();
    }
}
