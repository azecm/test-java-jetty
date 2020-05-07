package org.site.server.mail;

import org.site.server.Router;
import org.site.server.jsession.JSessionMail;
import org.site.view.VUtil;

import javax.servlet.GenericServlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

public class NoteData extends GenericServlet {
    public static class MailUserNode {
        public String title = "";
        public String content = "";
        public String template = "";
    }

    public void service(ServletRequest req, ServletResponse res) {
        JSessionMail session = new JSessionMail(req);
        if (session.data == null) {
            Router.json404(res);
            return;
        }

        String pathToData = session.pathNode() + "/" + session.uri.split("/")[2];
        if (session.isGet()) {
            MailUserNode nodeData = VUtil.readJson(pathToData, MailUserNode.class);
            if (nodeData == null) {
                nodeData = new MailUserNode();
            }
            Router.json200(nodeData, res);
        } else if (session.isPost()) {

            //For text content, use HttpServletRequest.getReader()
            //For binary content, use HttpServletRequest.getInputStream()

            MailUserNode data = session.postJson(MailUserNode.class);
            if (data == null) {
                Router.json404(res);
            } else {
                VUtil.writeJson(pathToData, data);
                Router.json200("ok", res);
            }
        }
    }
}
