package org.site.server.mail;

import org.site.elements.MailAttachment;
import org.site.elements.MailMessage;
import org.site.server.Router;
import org.site.server.SendMail;
import org.site.server.jsession.JSessionMail;
import org.site.view.VUtil;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.servlet.GenericServlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

public class SendMessage extends GenericServlet {


    public static class PostData {
        public String to;
        public String subject;
        public String content;
        public String id;
        public String forward;
        public ArrayList<MailAttachment> attach;
    }

    public void service(ServletRequest req, ServletResponse res) {

        JSessionMail session = new JSessionMail(req).loadNotes();
        if (!session.isPost() || session.data == null || session.notes == null) {
            Router.json404(res);
            return;
        }

        PostData data = session.postJson(PostData.class);

        InternetAddress from;
        if (VUtil.notEmpty(data.forward)) {
            from = SendMail.email(data.forward);
            from = SendMail.email(from.getAddress(), from.getPersonal());
        } else {
            from = SendMail.email(session.notes.email, session.notes.name);
        }

        InternetAddress to = SendMail.email(data.to);
        to = SendMail.email(to.getAddress(), to.getPersonal());

        boolean flag = new SendMail().from(from)
                .to(to)
                .subject(data.subject)
                .content(data.content)
                .attach(data.id, data.attach)
                .send();

        if (flag) {
            new MailMessage()
                    .newId()
                    .setAttachments(session.email(), data.id, data.attach)
                    .addFrom(session.notes.email, session.notes.name)
                    .addTo(to.getAddress(), to.getPersonal())
                    .content(data.content)
                    .subject(data.subject)
                    .saveToSent(session.notes.email);
            Router.json200("ok", res);
        } else {
            Router.json404(res);
        }
    }
}
