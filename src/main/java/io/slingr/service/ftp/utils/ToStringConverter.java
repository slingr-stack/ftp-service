package io.slingr.service.ftp.utils;

import io.slingr.services.utils.Json;
import io.slingr.services.utils.converters.JsonSource;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.converter.stream.StreamCacheConverter;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Multipart;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * <p>Converts the inputs to String.
 *
 * <p>Created by lefunes on 23/04/15.
 */
@Converter
public final class ToStringConverter {
    private static final Logger logger = LoggerFactory.getLogger(ToStringConverter.class);

    @Converter(allowNull = true)
    @SuppressWarnings("unused") // used by Camel converters schema
    public static String fromJson(JsonSource message) {
        return message.toString();
    }

    @Converter(allowNull = true)
    @SuppressWarnings("unused") // used by Camel converters schema
    public static String fromJson(Json message) {
        return message.toString();
    }

    @Converter(allowNull = true)
    @SuppressWarnings({"unused", "unchecked"}) // used by Camel converters schema
    public static String fromMap(Map message) {
        return Json.fromMap(message).toString();
    }

    @Converter(allowNull = true)
    @SuppressWarnings({"unused", "unchecked"}) // used by Camel converters schema
    public static String fromList(List message) {
        return Json.fromList(message).toString();
    }

    public static String fromObject(Object message) {
        return fromObject(message, null);
    }

    public static String fromObject(Object message, Exchange exchange) {
        if(message == null){
            return "";
        }
        if (message instanceof JsonSource) {
            return fromJson((JsonSource) message);
        } else if (message instanceof Map) {
            return fromMap((Map<String, Object>) message);
        } else if (message instanceof List) {
            return fromList((List) message);
        } else if (message instanceof StreamCache) {
            try{
                return new String(StreamCacheConverter.convertToByteArray((StreamCache) message, exchange));
            } catch (IOException e) {
                logger.warn("Error parsing JSON from stream cache", e);
                return null;
            }
        } else if (message instanceof InputStream) {
            try {
                return IOUtils.toString((InputStream) message, StandardCharsets.UTF_8);
            } catch (IOException e) {
                logger.warn("Error parsing JSON from input stream", e);
                return null;
            }
        } else if (message instanceof Multipart) {
            return EmailHelper.convertMultipart((Multipart) message).toString();
        } else {
            return message.toString();
        }
    }
}
