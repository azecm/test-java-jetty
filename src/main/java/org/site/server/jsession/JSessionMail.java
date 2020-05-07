package org.site.server.jsession;

import org.apache.commons.fileupload.FileItemStream;
import org.site.elements.MailAttachment;
import org.site.elements.MailMessage;
import org.site.server.mail.UserData;
import org.site.view.VUtil;

import javax.servlet.ServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class JSessionMail extends JSession {

    public UserData notes;
    public DataMail data = null;
    public String userFolder;

    public JSessionMail(ServletRequest req) {
        initSession(req);
        data = loadSession(DataMail.class);
        updateUri();
    }

    public JSessionMail(ServletRequest req, String userFolder) {
        initSession(req);
        this.userFolder = userFolder;
        updateUri();
    }

    public void save() {
        if (data != null && key != null) {
            VUtil.writeJson(path(), data);
        }
    }

    public String email() {
        if (userFolder == null) {
            userFolder = String.join("@", data.email);
        }
        return userFolder;
    }

    public String pathUser() {
        return VUtil.DIRDomain + host + "/user/" + email();
    }

    public String pathNode() {
        return VUtil.DIRDomain + host + "/node/" + email();
    }

    public JSessionMail loadNotes() {
        if (data != null || userFolder != null) {
            notes = VUtil.readJson(pathUser(), UserData.class);
        }
        return this;
    }

    public JSessionMail saveNotes() {
        if (notes != null) VUtil.writeJson(pathUser(), notes);
        return this;
    }

    public long nextElementId() {
        long id = 0;
        for (UserData.Group g : notes.notes) {
            for (UserData.Note n : g.note) id = n.id > id ? n.id : id;
        }
        return id + 1;
    }


    public ArrayList<MailAttachment> postFiles(String mid) {

        ArrayList<MailAttachment> attachList = new ArrayList<>();

        postForm((FileItemStream item) -> {
            int id = 1;
            while (VUtil.pathExists(MailMessage.pathToTempFile(mid, id))) id++;
            Path path = Paths.get(MailMessage.pathToTempFile(mid, id));
            attachList.add(new MailAttachment(id, item.getName(), 0));
            return path;
        });

        for (MailAttachment item : attachList) {
            try {
                Path path = Paths.get(MailMessage.pathToTempFile(mid, item.id));
                item.length = Files.size(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return attachList;
    }
}
