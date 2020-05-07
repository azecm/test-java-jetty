package org.site.server.mail;

import org.site.elements.MailMessage;
import org.site.server.Router;
import org.site.server.jsession.JSessionMail;
import org.site.view.VUtil;

import javax.servlet.GenericServlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class UpdateMessage extends GenericServlet {

    public class MessageData {
        String from;
        String to;
        String method;
        String id;
    }

    public void service(ServletRequest req, ServletResponse res) {
        JSessionMail session = new JSessionMail(req);
        if (!session.isPost() || session.data == null) {
            Router.json404(res);
            return;
        }

        boolean flag = false;
        MessageData data = session.postJson(MessageData.class);

        switch (data.method) {
            case "flag-read":
            case "flag-unread": {
                Path pathFrom = MailMessage.path(session.email(), "inbox", data.id);
                MailMessage message = MailMessage.load(pathFrom);
                message.flag.unread = data.method.equals("flag-unread");
                message.save(pathFrom);
                flag = true;
                break;
            }
            case "move": {
                Path pathFrom = MailMessage.path(session.email(), data.from, data.id);
                Path pathTo = MailMessage.path(session.email(), data.to, data.id);

                if (data.to.equals(Router.folderNote)) {
                    session.loadNotes();
                    if (session.notes != null && session.notes.notes.size() > 0) {
                        MailMessage message = MailMessage.load(pathFrom);
                        long id = session.nextElementId();
                        session.notes.notes.get(0).note.add(
                                new UserData.Note(id, message.from.get(0).name, message.from.get(0).address)
                        );
                        NoteData.MailUserNode node = new NoteData.MailUserNode();
                        node.content = message.html;
                        if (VUtil.isEmpty(node.content))
                            node.content = "<p>" + message.text.replaceAll("\\n", "<br>") + "</p>";
                        VUtil.writeJson(session.pathNode() + "/" + id, node);
                        session.saveNotes();
                        flag = true;
                    }
                } else {
                    try {
                        Files.move(pathFrom, pathTo);
                        flag = true;
                    } catch (IOException e) {
                        VUtil.println("UpdateMessage::service::move", pathFrom.toString(), pathTo.toString());
                        e.printStackTrace();
                    }
                }

                break;
            }
        }

        if (flag) {
            Router.json200("ok", res);
        } else {
            Router.json404(res);
        }
    }
}
