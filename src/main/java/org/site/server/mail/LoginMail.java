package org.site.server.mail;

import org.site.server.Router;
import org.site.server.jsession.DataMail;
import org.site.server.jsession.JRequest;
import org.site.server.jsession.JSessionMail;

import javax.servlet.GenericServlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.util.ArrayList;
import java.util.List;

public class LoginMail extends GenericServlet {
    public void service(ServletRequest req, ServletResponse res) {

        JRequest jreq = new JRequest().init(req);
        if (!jreq.isPost()) {
            Router.json404(res);
            return;
        }

        if (jreq.uri.endsWith("/on/test")) {
            JSessionMail session = new JSessionMail(req);
            Router.json200(session.data == null ? "no" : "ok", res);
            return;
        }

        List<String> loginData = jreq.postLogin();
        if (loginData.size() != 4) {
            Router.json404(res);
            return;
        }

        String device = loginData.get(0);
        String login = loginData.get(1);
        String pass = loginData.get(2);
        String email = loginData.get(3);

        JSessionMail session = new JSessionMail(req, email).loadNotes();
        if (session.notes == null) {
            Router.json404("notes", res);
            return;
        }

        if (!session.notes.name.equals(login) || !session.notes.pass.equals(pass)) {
            Router.json404("equals(login)", res);
            return;
        }

        session.data = new DataMail();
        session.data.ip = jreq.getIP();
        session.data.browser = jreq.getBrowser();
        session.data.device = device;
        session.data.email = email.split("@");
        session.genKeys(session.data);

        session.save();

        ArrayList<String> result = new ArrayList<>();
        result.add(session.key);
        result.add(session.pin);
        Router.json200(result, res);
    }
}
