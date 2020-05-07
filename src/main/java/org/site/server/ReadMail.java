package org.site.server;


import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.site.elements.MailMessage;
import org.site.view.VUtil;

import javax.mail.*;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;
import java.io.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReadMail {
    final String DIRMailSource = "/var/mail/virtual/";
    final String DIRMailTarget = "/usr/local/www/cache/mail/";

    final List<String> emailList = Arrays.asList("m@x.r", "d@t.r");

    //
    final Pattern reTrusted = Pattern.compile("(talantiuspeh\\.ru|sochisirius\\.ru|amigeschool\\.ru|zoom\\.us)");


    final Pattern reBadZone = Pattern.compile("\\.(eu|au|us|ml|tk|de|nl|g.|bid|fr|xyz|icu)$");

    final static HashSet<String> badContent = new HashSet<String>() {{
        add("kuturie.ru");
        add("www.true-cleaning.ru");
        add("np-cnti@mail.ru");
        add("Yoga Tip");
        add("Rotorazer");
    }};


    MailMessage messageData;

    final Pattern reId = Pattern.compile("^\\w+\\.\\w+", Pattern.CASE_INSENSITIVE);

    String userName;
    //int counter;

    String getFolderNew(String name) {
        String[] list = name.split("@");
        return DIRMailSource + list[1] + "/" + list[0] + "/new/";
    }

    public void readAll() {
        emailList.forEach(this::readNewMail);
    }

    void readNewMail(String name) {
        //counter = 0;
        userName = name;
        try {
            Files.list(Paths.get(getFolderNew(name)))
                    .filter(Files::isRegularFile).forEach(this::readNewMailNext);
        } catch (IOException e) {
            VUtil.error("Mail::readNewMail", e.getMessage());
        }
    }

    void readNewMailNext(Path path) {
        readEmailFile(path);
        try {
            Files.move(path, Paths.get(DIRMailTarget + "_orig/" + userName + "/" + path.getFileName().toString()));
        } catch (IOException e) {
            VUtil.error("Mail::readEmailFile::move", e.getMessage());
        }
    }

    public void readSingle() {
        String emailFile = "155628121";
        userName = "d@t.r";
        readEmailFile(Paths.get(DIRMailTarget + "_orig/" + userName + "/" + emailFile));
    }

    void readEmailFile(Path path) {
        InputStream is;
        try {
            is = Files.newInputStream(path);
        } catch (IOException e) {
            System.err.println("readEmailFile - " + e.getMessage());
            return;
        }

        MimeMessage message = null;
        try {
            Session s = Session.getInstance(new Properties());
            message = new MimeMessage(s, is);
        } catch (MessagingException e) {
            VUtil.error("Mail::readEmailFile", e.getMessage());
        }

        Matcher m = reId.matcher(path.getFileName().toString());
        if (message != null) {
            if (m.find()) {
                if (readMessage(message, m.group(0))) {
                    if (messageData.text != null) {
                        if (messageData.html == null) messageData.html = messageData.text;
                        messageData.text = null;
                    }

                    String flagBad = null;
                    for (String c : badContent) {
                        if (messageData.html != null && messageData.html.contains(c)) {
                            flagBad = c;
                            break;
                        }
                    }

                    if (flagBad == null) {
                        messageData.saveToInbox(userName);
                    } else {
                        VUtil.error("Mail::readMessage::badContent", flagBad, userName, messageData.id, messageData.subject);
                    }

                } else {
                    VUtil.println("readEmailFile", "not readMessage", m.group(0));
                }

            } else {
                VUtil.println("readEmailFile", "not find");
            }
        } else {
            VUtil.println("readEmailFile", "==null");
        }

    }

    boolean readMessage(MimeMessage message, String idMessage) {
        messageData = new MailMessage().flagUnread();
        messageData.id = idMessage;

        try {
            Date sentDate = message.getSentDate();
            String[] hTo = message.getHeader("To");
            String[] hFrom = message.getHeader("From");

            if (hTo == null || hFrom == null
                    || hTo.length == 0
                    || hFrom.length == 0
                    || sentDate == null
            ) {
                VUtil.error("Mail::readMessage::badMessage", userName, messageData.id);
                return false;
            }

            InternetAddress from = null, to = null, returnPath = null;
            try {
                from = new InternetAddress(message.getHeader("From")[0]);
                returnPath = new InternetAddress(message.getHeader("Return-path")[0]);

                if (message.getHeader("To")[0].contains(",") && message.getHeader("To")[0].contains(userName)) {
                    to = new InternetAddress(userName);
                } else {
                    to = new InternetAddress(message.getHeader("To")[0]);
                }

            } catch (AddressException e) {
                VUtil.error("---");
                e.printStackTrace();
                VUtil.error("bad address", message.getHeader("From")[0], message.getHeader("To")[0]);
                VUtil.error("---");
                return false;
            }

            messageData.subject = message.getSubject();

            String emailFrom = from.getAddress();
            String emailReturn = returnPath.getAddress();
            String emailTo = to.getAddress();


            //final boolean flagTrusted = reTrusted.matcher(emailFrom).find();


            if (reTrusted.matcher(emailFrom).find()) {
                VUtil.println("TRUSTED FROM", emailFrom);
                VUtil.error("TRUSTED FROM", emailFrom);
            } else {
                //if(!reTrusted.matcher(emailFrom).find() && !emailReturn.equals(emailFrom)){
                //    VUtil.error("Mail::readMessage::badEmailFrom", emailFrom+"!="+returnPath, userName, messageData.id, messageData.subject);
                //    return false;
                //}
                //if(!flagTrusted) {
                if (reBadZone.matcher(emailFrom).find()) {
                    VUtil.error("Mail::readMessage::badZone", emailFrom, userName, messageData.id, messageData.subject);
                    return false;
                }
                if (!userName.equals(emailTo)) {
                    VUtil.error("Mail::readMessage::badEmailTo", userName + "!=" + emailTo, userName, messageData.id, messageData.subject);
                    return false;
                }
                //}

                //if(reBadZone.matcher(emailFrom).find()){
                //    VUtil.error("Mail::readMessage::badEmail", emailFrom, userName, messageData.id);
                //    return false;
            }

            messageData.addFrom(emailFrom, from.getPersonal());
            messageData.addTo(to.getAddress(), to.getPersonal());

            //messageData.id = idMessage;//message.getMessageID();


            final TimeZone tz = TimeZone.getTimeZone("UTC");
            String ISO_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSX";
            SimpleDateFormat formatter = new SimpleDateFormat(ISO_FORMAT);
            formatter.setTimeZone(tz);
            messageData.date = formatter.format(sentDate);

            message.getAllHeaderLines();
            messageData.headers = new HashMap<>();
            for (Enumeration<Header> e = message.getAllHeaders(); e.hasMoreElements(); ) {
                Header h = e.nextElement();
                messageData.headers.put(h.getName(), h.getValue());
            }


        } catch (MessagingException e) {
            VUtil.error("Mail::readMessage", e.getMessage());
            return false;
        }

        return handleMessage(message);
    }

    boolean handleMessage(MimeMessage message) {
        Object content;
        boolean isHtml;
        try {
            content = message.getContent();
            isHtml = message.isMimeType("text/html");
        } catch (MessagingException | IOException e) {
            VUtil.error("Mail::handleMessage", e.getMessage(), messageData.id);
            return false;
        }

        if (content instanceof String) {
            handleString((String) content, isHtml);
        } else if (content instanceof Multipart) {
            Multipart mp = (Multipart) content;
            handleMultipart(mp);
        } else {
            VUtil.error("Mail::handleMessage", content.getClass());
        }
        return true;
    }

    void dropTags(Document doc, String tag) {
        ArrayList<Element> els = new ArrayList<>();
        els.addAll(doc.getElementsByTag(tag));
        for (Element e : els) e.remove();
    }

    void handleString(String data, boolean isHtml) {

        if (isHtml) {
            Document doc = Jsoup.parse(data);

            dropTags(doc, "img");
            dropTags(doc, "style");
            dropTags(doc, "script");

            //String bgColors="bgcolors";
            //for (Element e : doc.getElementsByTag("a")){
            //    e.removeAttr(bgColors);
            //}

            for (Element e : doc.getElementsByTag("a")) {
                if (e.text().trim().isEmpty()) {
                    e.appendElement("b").text("[link]");
                }
            }

            //doc.getElementsByTag("img").forEach(Element::remove);
            //doc.getElementsByTag("style").forEach(Element::remove);
            //doc.getElementsByTag("script").forEach(Element::remove);
            doc.getAllElements().forEach(e -> e.removeAttr("style"));
            doc.getAllElements().forEach(e -> e.removeAttr("bgcolor"));

            if (messageData.html == null) messageData.html = "";
            messageData.html += VUtil.htmlSetting(doc).body().html();

        } else {
            if (messageData.text == null) messageData.text = "";
            messageData.text += "<p>" + data.replaceAll("\\n", "<br>").replaceAll("\\r", "") + "</p>";
        }
    }

    void handleAttach(String fileName, int size, InputStream content) {
        // https://www.programcreek.com/java-api-examples/?class=javax.mail.Part&method=getDisposition

        if (fileName == null) {
            VUtil.error("handleAttach fileName==null");
            return;
        }

        try {
            fileName = MimeUtility.decodeText(fileName);
        } catch (UnsupportedEncodingException e) {
            VUtil.error("Mail::handleAttach", e);
        }

        int id = messageData.attach(fileName, size);

        Path attachFileName = Paths.get(DIRMailTarget + userName + "/file/" + messageData.id + "-" + id + ".tmp");

        try {
            Files.copy(content, attachFileName, StandardCopyOption.REPLACE_EXISTING);
            content.close();
        } catch (IOException e) {
            VUtil.error("Mail::handleAttach", e);
        }

        VUtil.fileOwner(attachFileName);

        //if(messageData.attachment==null) messageData.attachment = new ArrayList<>();
        //messageData.attachment.add(new MessageAttach().name(fileName).size(size));

    }

    void handleMultipart(Multipart mp) {
        int count = 0;
        try {
            count = mp.getCount();
        } catch (MessagingException e) {
            VUtil.error("Mail::handleMultipart::count", e.getMessage());
        }
        for (int i = 0; i < count; i++) {
            Object content = null;
            boolean isHtml = false;
            boolean isTextContent = false;
            String disposition = null, fileName = null, contentType = null;
            int size = 0;
            BodyPart bp = null;
            try {
                bp = mp.getBodyPart(i);
                content = bp.getContent();
                isHtml = bp.isMimeType("text/html");
                isTextContent = bp.isMimeType("text/*");
                size = bp.getSize();
                disposition = bp.getDisposition();
                contentType = bp.getContentType();
                //bp.getHeader("X-Attachment-Id")
                fileName = bp.getFileName();
            } catch (MessagingException | IOException e) {
                VUtil.error("Mail::handleMultipart::2", e.getMessage());
            }


            if (content == null) continue;

            if (content instanceof String) {
                if (disposition != null && disposition.equalsIgnoreCase(Part.ATTACHMENT)) {
                    InputStream is;
                    try {
                        is = bp.getInputStream();
                    } catch (MessagingException | IOException/**/ e) {
                        VUtil.error("Mail::handleMultipart::3", e.getMessage());
                        continue;
                    }
                    //handleAttach(fileName, size, new ByteArrayInputStream(((String) content).getBytes(StandardCharsets.UTF_8)));
                    handleAttach(fileName, size, is);
                } else {
                    if (isTextContent) {
                        handleString((String) content, isHtml);
                    } else {
                        VUtil.error("handleMultipart::notText::notAttach", messageData.id);
                    }
                }
            } else if (content instanceof InputStream) {
                //
                //if (disposition != null && disposition.equalsIgnoreCase(Part.ATTACHMENT)) {
                if (disposition != null && fileName != null) {
                    handleAttach(fileName, size, (InputStream) content);
                }
            } else if (content instanceof Multipart) {
                Multipart mp2 = (Multipart) content;
                handleMultipart(mp2);
            } else {
                VUtil.error("Mail::handleMultipart: " + content.getClass(), messageData.id);
            }
        }
    }
}
