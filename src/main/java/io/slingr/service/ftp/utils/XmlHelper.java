package io.slingr.service.ftp.utils;

import io.slingr.services.exceptions.ServiceException;
import io.slingr.services.utils.Json;
import io.slingr.services.utils.converters.XmlToJsonParser;

/**
 * <p>Helper that permits interacting with XML documents.
 */
public class XmlHelper {

    /**
     * Converts the XML document to an equivalent Json object using the
     * <a href="https://developer.mozilla.org/en-US/docs/JXON">the JXON principles.</a>
     *
     * @param xml XML document to convert
     * @return equivalent Json
     * @throws ServiceException if any error happens
     */
    public static Json xmlToJson(String xml) throws ServiceException {
        return XmlToJsonParser.parse(xml);
    }

}