package org.site.server.mail;

import org.site.server.Router;
import org.site.server.jsession.JSessionMail;
import org.site.view.VUtil;

import javax.servlet.GenericServlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.util.List;

public class Notes extends GenericServlet {

    public class NotesResut {
        public String signature;
        public List<UserData.Group> notes;
    }

    public void service(ServletRequest req, ServletResponse res) {
        JSessionMail session = new JSessionMail(req).loadNotes();

        if (session.notes == null) {
            Router.json404(res);
        } else {
            NotesResut resut = new NotesResut();
            resut.signature = session.notes.signature;
            resut.notes = session.notes.notes;
            Router.json200(resut, res);
        }
    }
}
