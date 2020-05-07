package org.site.server.mail;

import org.site.elements.MailAttachment;
import org.site.elements.MailMessage;
import org.site.server.Router;
import org.site.server.jsession.JSessionMail;
import org.site.view.VUtil;

import javax.servlet.GenericServlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;


public class DLFile extends GenericServlet {

    public static class PostForward {
        String messageId;
        String forwardId;
        ArrayList<MailAttachment> attachments;
    }

    public void service(ServletRequest req, ServletResponse res) {
        JSessionMail session = new JSessionMail(req);

        if (session.data == null) {
            Router.json404(res);
            return;
        }

        if (session.isGet()) {
            String pathToFile = MailMessage.pathToFolder(session.email(), "file") + session.uri.substring(6) + ".tmp";
            if (!VUtil.pathExists(pathToFile)) {
                Router.json404(pathToFile, res);
                return;
            }

            HttpServletResponse response = (HttpServletResponse) res;
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Length", String.valueOf(new File(pathToFile).length()));
            //response.setHeader("Content-Disposition", "inline; filename=" + fileName);
            //response.setHeader("Content-disposition", "attachment; filename=sample.txt");

            try {
                InputStream in = Files.newInputStream(Paths.get(pathToFile));
                OutputStream out = res.getOutputStream();
                byte[] buffer = new byte[1024];
                int numBytesRead;
                while ((numBytesRead = in.read(buffer)) > 0) {
                    out.write(buffer, 0, numBytesRead);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else if (session.isPost()) {
            if (session.uri.equals("/file/forward")) {
                PostForward data = session.postJson(PostForward.class);
                ArrayList<MailAttachment> list = new ArrayList<>();
                for (MailAttachment a : data.attachments) {
                    try {
                        Files.copy(
                                Paths.get(MailMessage.pathToFile(session.email(), data.forwardId, a.id)),
                                Paths.get(MailMessage.pathToTempFile(data.messageId, a.id))
                        );
                        list.add(new MailAttachment(a.id, a.name, a.length));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                Router.json200(list, res);
            } else {
                Router.json200(session.postFiles(session.uri.substring(6)), res);
            }
        } else {
            Router.json404(res);
        }
    }
}
