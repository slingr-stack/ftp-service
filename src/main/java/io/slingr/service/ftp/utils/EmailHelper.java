package io.slingr.service.ftp.utils;

import com.sun.mail.imap.IMAPBodyPart;
import io.slingr.services.utils.Base64Utils;
import io.slingr.services.utils.Json;
import io.slingr.services.utils.MapsUtils;
import net.htmlparser.jericho.Source;
import org.apache.camel.StreamCache;
import org.apache.camel.converter.stream.StreamCacheConverter;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.log4j.Logger;

import javax.activation.MimetypesFileTypeMap;
import javax.mail.*;
import javax.ws.rs.core.MediaType;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility classes to work with emails.
 *
 * User: dgaviola
 * Date: 6/29/13
 */
public class EmailHelper {
    private static final Logger logger = Logger.getLogger(EmailHelper.class);

    public static final String PARAMETER_ID = "messageId";
    public static final String PARAMETER_TEXT = "textBody";
    public static final String PARAMETER_HTML = "htmlBody";
    public static final String PARAMETER_FROM = "fromEmail";

    // Regex
    private static final String spacers = "[\\s,/\\.\\-]";
    private static final String dayPattern = "(?:(?:Mon(?:day)?)|(?:Tue(?:sday)?)|(?:Wed(?:nesday)?)|(?:Thu(?:rsday)?)|(?:Fri(?:day)?)|(?:Sat(?:urday)?)|(?:Sun(?:day)?))";
    private static final String dayOfMonthPattern = String.format("[0-3]?[0-9]%s*(?:(?:th)|(?:st)|(?:nd)|(?:rd))?", spacers);
    private static final String monthPattern = "(?:(?:Jan(?:uary)?)|(?:Feb(?:uary)?)|(?:Mar(?:ch)?)|(?:Apr(?:il)?)|(?:May)|(?:Jun(?:e)?)|(?:Jul(?:y)?)|(?:Aug(?:ust)?)|(?:Sep(?:tember)?)|(?:Oct(?:ober)?)|(?:Nov(?:ember)?)|(?:Dec(?:ember)?)|(?:[0-1]?[0-9]))";
    private static final String yearPattern = "(?:[1-2]?[0-9])[0-9][0-9]";
    private static final String timePattern = "(?:[0-2])?[0-9]:[0-5][0-9](?::[0-5][0-9])?(?:(?:\\s)?[AP]M)?";
    private static final String datePattern = String.format("(?:%s%s+)?(?:(?:%s%s+%s)|(?:%s%s+%s))%s+%s", dayPattern, spacers, dayOfMonthPattern, spacers, monthPattern, monthPattern, spacers, dayOfMonthPattern, spacers, yearPattern);
    private static final String dateTimePattern = String.format("(?:%s[\\s,]*(?:(?:at)|(?:@))?\\s*%s)|(?:%s[\\s,]*(?:on)?\\s*%s)", datePattern, timePattern, timePattern, datePattern);

    // Pattern: Content-Type
    private static final Pattern CONTENT_TYPE_REMOVE_PATTERN = Pattern.compile("\\s*[;\\n\\r]+.*$", Pattern.MULTILINE);

    // Pattern: Message separator
    private static final String originalMessageSeparator = "-+\\s*(?:Original(?:\\sMessage)?)?\\s*-+(\n|\\<br\\>)";
    private static final Pattern ORIGINAL_MESSAGE_SEPARATOR_PATTERN = Pattern.compile(originalMessageSeparator);

    // Pattern: GMAIL
    private static final String gmailQuotedTextBeginning = String.format("(On\\s+%s.*wrote:(\n|\\<br\\>))", dateTimePattern);
    private static final Pattern GMAIL_SEPARATOR_PATTERN = Pattern.compile(gmailQuotedTextBeginning);

    // Pattern: OUTLOOK
    private static final String outlookSeparator = "(From:\\s+.*(\n|\\<br\\>))(Sent:\\s+.*(\n|\\<br\\>))";
    private static final Pattern OUTLOOK_SEPARATOR_PATTERN = Pattern.compile(outlookSeparator, Pattern.MULTILINE);

    // Pattern: iPhone
    private static final String datePatternIphone = "([0-9]{4})-([0-9]{2})-([0-9]{2})";
    private static final String dateTimePatternIphone = String.format("(?:%s[\\s,]*(?:(?:at)|(?:@))?\\s*%s)|(?:%s[\\s,]*(?:on)?\\s*%s)", datePatternIphone, timePattern, timePattern, datePattern);
    private static final String iphoneQuotedTextBeginning = String.format("(On\\s+%s.*wrote:(\n|\\<br\\>))", dateTimePatternIphone);
    private static final Pattern IPHONE_SEPARATOR_PATTERN = Pattern.compile(iphoneQuotedTextBeginning);

    // Pattern: HTML blockquoted
    private static final String htmlBlockQuotedTextBeginning = "\\<blockquote.*id.*replyBlockquote";
    private static final Pattern HTML_BLOCKQUOTED_SEPARATOR_PATTERN = Pattern.compile(htmlBlockQuotedTextBeginning);
    public static final int ATTACHMENT_MAX_SIZE = 1024 * 1024 * 2;

    // Pattern: Email check
    private static final String emailAddressPattern = "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$";
    private static final Pattern EMAIL_ADDRESS_PATTERN = Pattern.compile(emailAddressPattern);

    public static boolean isValidEmail(String value) {
        if(StringUtils.isNotBlank(value)){
            Matcher matcher = EMAIL_ADDRESS_PATTERN.matcher(value.toUpperCase());
            if (matcher.find()) {
                return true;
            }
        }
        return false;
    }

    public static String stripOutOriginalMessage(String email) {
        if (email == null) {
            return null;
        }

        // replace new lines
        email = email.replaceAll("\\r", "");

        // try to match the original message separator with dashes
        int startIndex;
        Matcher matcher = ORIGINAL_MESSAGE_SEPARATOR_PATTERN.matcher(email);
        if (matcher.find()) {
            startIndex = matcher.start();
            email = email.substring(0, startIndex);
        }

        // try GMail separator
        matcher = GMAIL_SEPARATOR_PATTERN.matcher(email);
        if (matcher.find()) {
            startIndex = matcher.start();
            email = email.substring(0, startIndex);
        }

        // try Outlook separator
        matcher = OUTLOOK_SEPARATOR_PATTERN.matcher(email);
        if (matcher.find()) {
            startIndex = matcher.start();
            // to make this safer
            email = email.substring(0, startIndex);
        }

        // try new iPhone separator
        matcher = IPHONE_SEPARATOR_PATTERN.matcher(email);
        if (matcher.find()) {
            startIndex = matcher.start();
            email = email.substring(0, startIndex);
        }

        // try HTML blockquote separator
        matcher = HTML_BLOCKQUOTED_SEPARATOR_PATTERN.matcher(email);
        if (matcher.find()) {
            startIndex = matcher.start();
            email = email.substring(0, startIndex);
        }

        // finally trim the email
        return email.trim();
    }

    public static String parseEmail(String email) {
        if (StringUtils.isNotBlank(email)) {
            return email.trim().toLowerCase();
        }
        return email;
    }

    public static String convertToTextBody(String htmlBody) {
        if(StringUtils.isNotBlank(htmlBody)){
            Source htmlSource = new Source(StringEscapeUtils.unescapeHtml(htmlBody));
            return htmlSource.getRenderer().toString();
        } else {
            return "";
        }
    }

    public static String parseTextBody(String strippedTextBody, String originalTextBody) {
        if(StringUtils.isBlank(strippedTextBody)){
            if(StringUtils.isNotBlank(originalTextBody)){
                strippedTextBody = stripOutOriginalMessage(originalTextBody);
            }
            if(StringUtils.isBlank(strippedTextBody)){
                strippedTextBody = "";
            }
        }
        return strippedTextBody;
    }

    public static String parseHtmlBody(String strippedHtmlBody, String htmlBody) {
        if(StringUtils.isBlank(strippedHtmlBody)){
            if(StringUtils.isNotBlank(htmlBody)){
                strippedHtmlBody = stripOutOriginalMessage(htmlBody);
            } else {
                strippedHtmlBody = "";
            }
        }
        return strippedHtmlBody;
    }

    public static Json generateNotification(String messageId, String fromEmail, String htmlBody, String textBody, String originalTextBody){
        final Map<String, Object> data = new HashMap<>();

        if(StringUtils.isNotBlank(messageId)){
            data.put(PARAMETER_ID, messageId);
        }
        if(StringUtils.isNotBlank(fromEmail)){
            data.put(PARAMETER_FROM, parseEmail(fromEmail));
        }
        if(StringUtils.isBlank(originalTextBody)){
            originalTextBody = convertToTextBody(htmlBody);
        }
        data.put(PARAMETER_TEXT, parseTextBody(textBody, originalTextBody));
        data.put(PARAMETER_HTML, parseHtmlBody(null, htmlBody));

        return Json.fromMap(data);
    }

    public static String getNameFromEmailLine(String from) {
        if (from == null) {
            return "";
        }
        if (from.lastIndexOf("<") != -1 && from.lastIndexOf(">") != -1) {
            int start = from.lastIndexOf("<");
            String name = from.substring(0, start);
            return name.replaceAll("^\\s*\\\"", "").replaceAll("\\\"\\s*$", "").trim();
        } else {
            return "";
        }
    }

    public static String getEmailFromEmailLine(String from) {
        if (from == null) {
            return "";
        }
        if (from.lastIndexOf("<") != -1 && from.lastIndexOf(">") != -1) {
            int start = from.lastIndexOf("<");
            int end = from.lastIndexOf(">");
            String email = from.substring(start+1, end);
            return email.trim();
        } else {
            return from.trim();
        }
    }

    public static long getDate(Object date) {
        if (date instanceof String) {
            final FastDateFormat sdf = FastDateFormat.getInstance("EEE MMM dd HH:mm:ss z yyyy");
            try {
                Date dateObj = sdf.parse((String) date);
                return dateObj.getTime();
            } catch (Exception e) {
                return new Date().getTime();
            }
        } else if (date instanceof Date) {
            return ((Date) date).getTime();
        } else {
            return new Date().getTime();
        }
    }

    public static String convertToHtml(String body) {
        return StringEscapeUtils.escapeHtml(body);
    }

    public static String convertToText(String body) {
        final Source htmlBody = new Source(StringEscapeUtils.unescapeHtml(body));
        return htmlBody.getRenderer().toString();
    }

    public static String getContentType(String contentType){
        if(StringUtils.isBlank(contentType)){
            return "text/plain";
        }
        contentType = CONTENT_TYPE_REMOVE_PATTERN.matcher(contentType).replaceAll("");
        return contentType.trim().toLowerCase();
    }

    public static String getContentType(String contentType, String fileName){
        if(StringUtils.isBlank(contentType)){
            contentType = URLConnection.guessContentTypeFromName(fileName);
            if(StringUtils.isBlank(contentType)) {
                contentType = MimetypesFileTypeMap.getDefaultFileTypeMap().getContentType(fileName);
            }
        }
        return contentType;
    }

    public static Json convertMultipart(Multipart multipart) {
        final Json response = Json.map();
        if(multipart != null) {
            String contentType = multipart.getContentType();
            response.setIfNotEmpty("originalContentType", contentType);
            contentType = getContentType(contentType);
            response.setIfNotEmpty("contentType", contentType);

            try {
                final List<Json> parts = new ArrayList<>();

                final int partsSize = multipart.getCount();
                for (int i = 0; i < partsSize; i++) {
                    final Json part = convertPart(multipart.getBodyPart(i), true, ATTACHMENT_MAX_SIZE);
                    if(!part.isEmpty()) {
                        parts.add(part);

                        if(!part.bool("attachment", false)) {
                            if (response.isEmpty("text")) {
                                response.setIfNotEmpty("text", part.string("text"));
                            }
                            if (response.isEmpty("html")) {
                                response.setIfNotEmpty("html", part.string("html"));
                            }
                        }
                    }
                }

                response.setIfNotEmpty("parts", parts);
            } catch (MessagingException e) {
                logger.warn("Error checking part of email", e);
            }

            try {
                final Json parent = convertPart(multipart.getParent(), false, 0);
                response.merge(parent);
            } catch (MessagingException e) {
                logger.warn("Error checking parent of email", e);
            }
        }
        return response;
    }

    @SuppressWarnings("unchecked")
    public static Json convertPart(Part part, boolean expandContent, long attachmentMaxSize) throws MessagingException {
        final Json json = Json.map();
        if(part != null) {
            boolean attachment = false;
            boolean base64 = false;
            String stringContent = null;
            Json objectContent = null;

            String contentType = part.getContentType();
            final String contentDisposition = part.getDisposition();
            final int size = part.getSize();

            if(expandContent && part instanceof BodyPart) {
                if (size > attachmentMaxSize) {
                    logger.warn(String.format("Attachment size [%s] is too big", size));
                } else {
                    try {
                        final Object contentObject = part.getContent();
                        if (contentObject != null) {

                            if (StringUtils.isNotBlank(contentDisposition) && (contentDisposition.equalsIgnoreCase("ATTACHMENT") || contentDisposition.equalsIgnoreCase("INLINE"))) {
                                attachment = true;

                                if (contentObject instanceof StreamCache) {
                                    final String s = new String(StreamCacheConverter.convertToByteArray((StreamCache) contentObject, null));
                                    stringContent = ToJsonConverter.fromObject(s, null).toString();
                                } else if (contentObject instanceof InputStream) {
                                    stringContent = Base64Utils.encode((InputStream) contentObject);
                                    base64 = true;
                                } else {
                                    stringContent = contentObject.toString();
                                }
                            } else {
                                try {
                                    objectContent = ToJsonConverter.baseFromObject(contentObject, null);
                                } catch (Exception e) {
                                    stringContent = contentObject.toString();
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Exception when try to parse the part content of the email", e);
                    }
                }
            }

            if(size >= 0) {
                json.set("size", size);
            }

            final Json headers = Json.map();

            final Enumeration<Header> hds = part.getAllHeaders();
            while (hds.hasMoreElements()) {
                final Header hd = hds.nextElement();
                final String name = hd.getName().trim();
                if(StringUtils.isNotBlank(name)) {
                    final String value = StringUtils.isNotBlank(hd.getValue()) ? hd.getValue().trim() : "";
                    headers.setIfNotEmpty(MapsUtils.cleanDotKey(name), value);

                    switch (name.toLowerCase()) {
                        case "from":
                            final List<Json> from = processReceiversLine(value);
                            if(from != null && !from.isEmpty()) {
                                json.setIfNotEmpty("fromName", from.get(0).string("name"));
                                json.setIfNotEmpty("fromEmail", from.get(0).string("email"));
                            }
                            break;
                        case "to":
                            final List<Json> to = processReceiversLine(value);
                            if(to != null && !to.isEmpty()) {
                                json.setIfNotEmpty("to", to);
                                json.setIfNotEmpty("toName", to.get(0).string("name"));
                                json.setIfNotEmpty("toEmail", to.get(0).string("email"));
                            }
                            break;
                        case "cc":
                            final List<Json> cc = processReceiversLine(value);
                            if(cc != null && !cc.isEmpty()) {
                                json.setIfNotEmpty("cc", cc);
                            }
                            break;
                        case "bcc":
                            final List<Json> bcc = processReceiversLine(value);
                            if(bcc != null && !bcc.isEmpty()) {
                                json.setIfNotEmpty("bcc", bcc);
                            }
                            break;
                        case "date":
                            json.setIfNotEmpty("date", getDate(value));
                            break;
                        case "subject":
                            json.setIfNotEmpty("subject", value);
                            break;
                        case "content-type":
                            if(StringUtils.isBlank(contentType)) {
                                json.setIfNotEmpty("subject", value);
                            }
                            break;
                    }
                }
            }
            json.setIfNotEmpty("headers", headers);

            if(part instanceof IMAPBodyPart) {
                json.setIfNotEmpty("contentId", ((IMAPBodyPart) part).getContentID());
                json.setIfNotEmpty("contentMD5", ((IMAPBodyPart) part).getContentMD5());
                json.setIfNotEmpty("encoding", ((IMAPBodyPart) part).getEncoding());
            }
            json.setIfNotEmpty("description", part.getDescription());
            json.setIfNotEmpty("contentDisposition", contentDisposition);
            json.setIfNotEmpty("fileName", part.getFileName());

            json.setIfNotEmpty("originalContentType", contentType);
            contentType = getContentType(contentType);
            json.setIfNotEmpty("contentType", contentType);

            json.setIfNotEmpty("attachment", attachment);
            json.setIfNotEmpty("base64", base64);

            if(StringUtils.isNotBlank(contentType)) {
                if(StringUtils.isNotBlank(stringContent)){
                    if (contentType.equalsIgnoreCase("text/plain")) {
                        json.setIfNotEmpty("content", stringContent);
                        json.setIfNotEmpty("text", stringContent);
                    } else if (contentType.equalsIgnoreCase("text/html")) {
                        json.setIfNotEmpty("content", stringContent);
                        json.setIfNotEmpty("html", stringContent);
                    } else {
                        json.setIfNotEmpty("content", stringContent);
                    }
                } else if (objectContent != null) {
                    json.setIfNotEmpty("content", objectContent);

                    if(json.isEmpty("text")) {
                        json.setIfNotEmpty("text", objectContent.string("text"));
                    }
                    if(json.isEmpty("html")) {
                        json.setIfNotEmpty("html", objectContent.string("html"));
                    }
                }
            }
        }
        return json;
    }

    public static List<Json> processReceiversLine(String value){
        final List<Json> receiver = new ArrayList<>();
        if(StringUtils.isNotBlank(value)){
            final String[] emails = value.split(",");
            for (String email : emails) {
                final String e = getEmailFromEmailLine(email);
                if(StringUtils.isNotBlank(e)) {
                    receiver.add(Json.map()
                            .set("email", e)
                            .setIfNotEmpty("name", getNameFromEmailLine(email))
                    );
                }
            }
        }
        return receiver;
    }

    public static MediaType getMediaType(String contentType){
        return getMediaType(contentType, null);
    }

    public static MediaType getMediaType(String contentType, String filename){
        String normalizedContentType = getContentType(contentType, filename);

        MediaType mediaType = null;
        if(StringUtils.isNotBlank(normalizedContentType)) {
            try {
                mediaType = MediaType.valueOf(normalizedContentType);
            } catch (Exception ex){
                logger.warn(String.format("Error when try to convert the content type [%s]. Exception [%s]", contentType, ex.getMessage()));
            }
        }
        return mediaType;
    }
}
