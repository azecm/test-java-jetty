package org.site.server;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.site.elements.MailAttachment;
import org.site.elements.MailMessage;
import org.site.view.VUtil;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.*;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Properties;

public class SendMail {
    InternetAddress from;
    InternetAddress to;
    String subject;
    String content;

    String attachId;
    ArrayList<MailAttachment> attachList;

    public static InternetAddress email(String address, String person) {
        InternetAddress email = null;
        try {
            email = new InternetAddress(address, person);
        } catch (UnsupportedEncodingException e) {
            VUtil.dateLogPrint();
            e.printStackTrace();
        }
        return email;
    }

    public static InternetAddress email(String text) {
        InternetAddress email = null;
        try {
            email = new InternetAddress(text);
        } catch (MessagingException e) {
            e.printStackTrace();
        }
        return email;
    }

    public SendMail from(InternetAddress address) {
        from = address;
        return this;
    }

    public SendMail to(InternetAddress address) {
        to = address;
        return this;
    }

    public SendMail subject(String text) {
        subject = text;
        return this;
    }

    public SendMail content(String html) {

        Document doc = Jsoup.parse("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\"><html><head></head><body>" + html + "</body></html>");

        doc.head().appendChild(
                new Element("meta")
                        .attr("http-equiv", "Content-Type")
                        .attr("content", "text/html; charset=UTF-8")
        );

        content = VUtil.htmlSetting(doc).html();
        return this;
    }

    public SendMail attach(String id, ArrayList<MailAttachment> list) {
        if (VUtil.notEmpty(id) && list != null) {
            attachId = id;
            attachList = new ArrayList<>();
            attachList.addAll(list);
        }
        return this;
    }


    public boolean send() {


        Properties properties = System.getProperties();
        properties.setProperty("mail.smtp.host", "localhost");
        Session mailSession = Session.getDefaultInstance(properties);

        boolean flag = false;
        try {

            MimeMessage message = new MimeMessage(mailSession);

            message.addHeader("Content-type", "text/HTML; charset=UTF-8");
            //message.addHeader("format", "flowed");
            message.addHeader("Content-Transfer-Encoding", "8bit");

            message.setFrom(from);
            message.addRecipient(Message.RecipientType.TO, to);
            message.setSubject(subject, "UTF-8");

            if (attachId == null) {
                message.setContent(content, "text/html; charset=UTF-8");
            } else {

                Multipart multipart = new MimeMultipart();

                // ====

                BodyPart messageBodyPart = new MimeBodyPart();
                messageBodyPart.setContent(content, "text/html; charset=UTF-8");
                //messageBodyPart.setText("This is message body");
                multipart.addBodyPart(messageBodyPart);

                // ====

                for (MailAttachment a : attachList) {
                    messageBodyPart = new MimeBodyPart();
                    FileDataSource source = new FileDataSource(MailMessage.pathToTempFile(attachId, a.id));
                    messageBodyPart.setDataHandler(new DataHandler(source));
                    messageBodyPart.setFileName(a.name);
                    multipart.addBodyPart(messageBodyPart);
                }

                // ====

                message.setContent(multipart);
            }

            Transport.send(message);
            flag = true;
        } catch (MessagingException e) {
            e.printStackTrace();
        }

        return flag;
    }
}
