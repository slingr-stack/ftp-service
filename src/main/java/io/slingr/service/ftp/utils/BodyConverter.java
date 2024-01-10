package io.slingr.service.ftp.utils;

import io.slingr.services.utils.converters.XmlToJsonParser;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.converter.stream.StreamCacheConverter;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import javax.mail.Multipart;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class BodyConverter {
    public static Object parseHttpBody(Object body, Exchange exchange, Logger logger){
        if(body != null) {
            if (body instanceof StreamCache) {
                try {
                    body = new String(StreamCacheConverter.convertToByteArray((StreamCache) body, exchange));
                } catch (IOException e) {
                    logger.warn("Error parsing JSON from stream cache", e);
                    body = null;
                }
            } else if (body instanceof InputStream) {
                try {
                    body = IOUtils.toString((InputStream) body, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    logger.warn("Error parsing JSON from input stream", e);
                    body = null;
                }
            } else if (body instanceof Multipart) {
                body = EmailHelper.convertMultipart((Multipart) body);
            }
            if(body instanceof String) {
                final Object ct = exchange.getIn().getHeader(Exchange.CONTENT_TYPE);
                if (ct != null){
                    if(ct.toString().toLowerCase().contains("json")) {
                        try {
                            // try to convert JSON documents
                            body = ToJsonConverter.baseFromObject(body, exchange);
                        } catch (Exception ex) {
                            logger.info(String.format("Body can not be converted to JSON [%s]: %s", body, ex.getMessage()));
                        }
                    } else if(ct.toString().toLowerCase().contains("xml")) {
                        try {
                            // try to convert XML documents
                            body = XmlToJsonParser.parse(body.toString().trim());
                        } catch (Exception ex) {
                            logger.info(String.format("Body can not be converted to XML [%s]: %s", body, ex.getMessage()));
                        }
                    }
                }
            }
        }
        return body;
    }
}
