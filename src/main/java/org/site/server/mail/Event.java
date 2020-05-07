package org.site.server.mail;

import org.site.server.Router;
import org.site.server.jsession.JSessionMail;
import org.site.view.VUtil;

import javax.servlet.GenericServlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Event extends GenericServlet {
    private final Pattern reURI = Pattern.compile("/event/(\\d+)/(update|delete)");

    public void service(ServletRequest req, ServletResponse res) {
        JSessionMail session = new JSessionMail(req).loadNotes();
        if (!session.isPost() || session.notes == null) {
            Router.json404(res);
            return;
        }

        Matcher m = reURI.matcher(session.uri);
        if (m.find()) {
            UserData.Note note = null;
            long idNote = VUtil.getInt(m.group(1));

            outer:
            for (UserData.Group g : session.notes.notes) {
                for (UserData.Note n : g.note) {
                    if (n.id == idNote) {
                        note = n;
                        break outer;
                    }
                }
            }
            if (note == null) {
                Router.json404(res);
                return;
            }
            switch (m.group(2)) {
                case "update": {
                    note.event = session.postJson(UserData.Event.class);
                    break;
                }
                case "delete": {
                    note.event = null;
                    break;
                }
            }
            session.saveNotes();
            Router.json200("ok", res);
        } else {
            Router.json404(res);
        }
    }
}
