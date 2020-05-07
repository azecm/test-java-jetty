package org.site.server;

import org.site.view.VUtil;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeUtility;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;


public class SendMailCli {
    private final static String pathCli = "/usr/local/bin/smtp-cli";

    InternetAddress from;
    InternetAddress to;
    String subject;
    String content;

    String attach;

    public SendMailCli from(InternetAddress address) {
        from = address;
        return this;
    }

    public SendMailCli to(InternetAddress address) {
        to = address;
        return this;
    }

    public SendMailCli subject(String text) {
        subject = getSubject(text);
        return this;
    }

    public SendMailCli content(String html) {
        StringBuilder str = new StringBuilder();
        str.append("<!-- cli --><!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">");
        str.append("<html>");
        str.append("<head>");
        str.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">");
        str.append("</head>");
        str.append("<body>");
        str.append(html);
        str.append("</body>");
        str.append("</html>");
        content = str.toString();
        return this;
    }

    public SendMailCli attach() {
        //cmd.push('--attach "' + sym + '"');
        return this;
    }


    private String getSubject(String text) {
        String res = null;
        try {
            res = MimeUtility.fold(9, MimeUtility.encodeText(text, "UTF-8", null));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return res;
    }

    public boolean send() {
        boolean res = false;

        if (from != null && to != null) {
            ArrayList<String> cmd = new ArrayList<>();
            cmd.add(pathCli);
            //Content-transfer-encoding: 8bit
            //--hello-host=<string>
            //--print-only
            //cmd.add("--missing-modules-ok");
            cmd.add("--missing-modules-ok");

            cmd.add("--server=localhost");
            cmd.add("--remove-header=X-Mailer");
            cmd.add("--charset=utf-8");
            cmd.add("--text-encoding=8bit");
            cmd.add("--from");
            cmd.add(from.toString());
            cmd.add("--to");
            cmd.add(to.toString());
            //cmd.add("--add-header="From2: ' + from + '"');
            //cmd.add("--add-header="To2: ' + to + '"');
            if (subject != null) {
                cmd.add("--subject");
                cmd.add(subject);
            }
            //cmd.add("--body-plain");
            //cmd.add("ТЕКСТ");
            cmd.add("--body-html");
            cmd.add(content);

            if (attach != null) {
                cmd.add("--attach");
                cmd.add("/path/to/file");
            }

            String resText = VUtil.exec(cmd).trim();
            if (VUtil.notEmpty(resText)) {
                VUtil.error("SendMail::send", resText);
            } else {
                res = true;
            }
        }

        return res;
    }

}
