package io.slingr.service.ftp.utils;

import io.slingr.services.utils.Json;
import io.slingr.services.utils.converters.ContentTypeFormat;
import io.slingr.services.utils.converters.JsonSource;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.converter.stream.StreamCacheConverter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Multipart;
import javax.mail.internet.MimeMultipart;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>Converts the inputs to Json.
 *
 * <p>Created by lefunes on 23/04/15.
 */
@Converter
public final class ToJsonConverter {
    private static final Logger logger = LoggerFactory.getLogger(ToJsonConverter.class);

    private static final Json stringReplaces = Json.map();

    public static void registerReplace(String pattern, String replace){
        stringReplaces.set(pattern, replace);
    }

    @Converter(allowNull = true)
    @SuppressWarnings("unchecked")
    public static Json fromMultipart(MimeMultipart message, Exchange exchange) {
        try{
            return baseFromObject(message, exchange);
        } catch (Exception ex){
            ex.printStackTrace();
            return fromString("{ \"error\":\""+ex.toString()+"\"}");
        }
    }

    @Converter(allowNull = true)
    @SuppressWarnings("unchecked")
    public static Json fromObject(Object message, Exchange exchange) {
        try{
            return baseFromObject(message, exchange);
        } catch (Exception ex){
            ex.printStackTrace();
            return fromString("{ \"error\":\""+ex.toString()+"\"}");
        }
    }

    @SuppressWarnings("unchecked")
    public static Json baseFromObject(Object message, Exchange exchange) throws Exception {
        if (message instanceof JsonSource) {
            return fromJson((Json) message);
        } else if (message instanceof Map) {
            return fromMap((Map<String, Object>) message);
        } else if (message instanceof List) {
            return fromList((List) message);
        } else if (message instanceof StreamCache) {
            final String s = new String(StreamCacheConverter.convertToByteArray((StreamCache) message, exchange));
            return fromString(s);
        } else if (message instanceof InputStream) {
            return fromInputStream((InputStream) message, exchange);
        } else if (message instanceof Multipart) {
            return fromMultipart((Multipart) message);
        } else if (message != null) {
            return fromString(message.toString());
        }
        return Json.map();
    }

    @Converter(allowNull = true)
    public static Json fromJson(Json message) {
        if(message == null){
            return Json.map();
        }
        return message.toJson();
    }

    @Converter(allowNull = true)
    public static Json fromJson(JsonSource message) {
        if(message == null){
            return Json.map();
        }
        return message.toJson();
    }

    @Converter(allowNull = true)
    public static Json fromMap(Map<String, ?> message) {
        if(message == null){
            return Json.map();
        }
        return Json.fromMap(message);
    }

    @Converter(allowNull = true)
    @SuppressWarnings("unchecked")
    public static Json fromList(List message) {
        Json json = Json.list();
        if(message != null && !message.isEmpty()) {
            json = Json.fromList(message);
        }
        return json;
    }

    private static Json fromString(String message) {
        return Json.parse(message, false);
    }

    @Converter(allowNull = true)
    public static Json fromInputStream(InputStream is, Exchange exchange) {
        try {
            final String value = IOUtils.toString(is, StandardCharsets.UTF_8);
            return convertString(value, exchange);
        } catch (IOException e) {
            logger.warn("Error parsing JSON from input stream", e);
            return null;
        }
    }

    @Converter(allowNull = true)
    public static Json fromMultipart(Multipart multipart) {
        return EmailHelper.convertMultipart(multipart);
    }

    public static Json convertString(String value, Exchange exchange) {
        String contentType = null;
        if(exchange != null){
            final Object contentTypeObject = exchange.getIn().getHeaders().get(Exchange.CONTENT_TYPE);
            if (contentTypeObject instanceof String) {
                contentType = (String) contentTypeObject;
            }
        }
        return convertString(value, contentType);
    }

    public static Json convertString(String value) {
        return convertString(value, (String) null);
    }

    public static Json convertString(String value, String contentType) {
        return convertString(value, contentType, true);
    }

    public static Json convertString(String value, String contentType, boolean showErrors) {
        if(!stringReplaces.isEmpty()){
            for (String pattern : stringReplaces.keys()) {
                value = value.replaceAll(pattern, stringReplaces.string(pattern));
            }
        }

        Json response = null;

        if (StringUtils.isNotBlank(contentType)) {
            if (ContentTypeFormat.isJsonContentType(contentType)) {
                // try to convert JSON string
                try {
                    response = Json.parse(value);
                } catch (Exception e) {
                    if(showErrors) {
                        logger.warn(String.format("Error parsing JSON from input stream [%s]", value));
                    }
                    return null;
                }
            } else if (ContentTypeFormat.isUrlEncodedFormContentType(contentType)) {
                // try to convert the URL encoded form
                try {
                    response = convertFormToJson(value);
                } catch (Exception e) {
                    if(showErrors) {
                        logger.warn(String.format("Error parsing JSON from URL encoded form [%s]", value));
                    }
                    return null;
                }
            } else if (ContentTypeFormat.isXmlContentType(contentType)) {
                // try to convert the XMl document
                try {
                    response = XmlHelper.xmlToJson(value);
                } catch (Exception e) {
                    if(showErrors) {
                        logger.warn(String.format("Error parsing JSON from XML document [%s]", value));
                    }
                    return null;
                }
            } else if (ContentTypeFormat.isHtmlContentType(contentType) || ContentTypeFormat.isPlainTextContentType(contentType)) {
                response = Json.map();
                response.set("body", value);
            }
        }

        if(response == null){
            // generic case, try to convert the string to JSON
            try {
                response = Json.parse(value);
            } catch (Exception ex) {
                try {
                    response = XmlHelper.xmlToJson(value);
                } catch (Exception e2) {
                    try{
                        response = convertFormToJson(value);
                    } catch (Exception ex3) {
                        if(showErrors) {
                            logger.warn(String.format("Error parsing JSON from input stream [%s]", value));
                        }
                    }
                }
            }
        }

        return response;
    }

    @SuppressWarnings("unchecked")
    private static Json convertFormToJson(String value) {
        if(StringUtils.isBlank(value)){
            return Json.map();
        }
        final String normalizedString = value.replaceAll("&(?![#a-zA-Z0-9]+;)", "|||");
        final String[] parts = normalizedString.split("\\|\\|\\|");
        Json response = Json.map();

        for (String part : parts) {
            String[] p = part.split("=", 2);
            if(p.length > 1){
                if(p[0].contains("[")){
                    String[] k = p[0].split("[\\[\\]]+");
                    Map<String, Object> map = response.toMap();
                    Map<String, Object> actual = map;
                    for (int i = 0; i < k.length-1 ; i++) {
                        if(!actual.containsKey(k[i])) {
                            actual.put(k[i], new HashMap());
                        }
                        actual = (Map<String, Object>) actual.get(k[i]);
                    }
                    actual.put(k[k.length-1], parse(p[1]));
                    response = Json.fromMap(map);
                } else {
                    response.set(p[0], parse(p[1]));
                }
            }
        }
        return response;
    }

    private static Object parse(Object o){
        try {
            final String decodedString = URLDecoder.decode(o.toString(), "UTF-8");
            try {
                return Json.fromObject(decodedString);
            } catch (Exception ex) {
                return decodedString;
            }
        } catch (Exception ex) {
            return o;
        }
    }
}
