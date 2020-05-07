package org.site.elements;

import org.site.view.VUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

public class MailMessage {

    public static final String DIRMail = VUtil.DIRCache + "mail/";
    public static final String DIRMailTemp = DIRMail + "temp/";

    public static String pathToTempFile(String mid, int fid) {
        return DIRMailTemp + mid + "-" + fid + ".tmp";
    }

    public static String pathToFile(String userFolder, String mid, int fid) {
        return DIRMail + userFolder + "/file/" + mid + "-" + fid + ".tmp";
    }

    public static class Adress {
        public String address;
        public String name;

        public Adress(String address, String name) {
            this.address = address;
            this.name = name;
        }
    }

    public class Flags {
        public boolean reply = false;
        public boolean unread = false;
    }

    public String id;
    public String subject;
    public String date;
    public String text;
    public String html;
    public HashMap<String, String> headers;
    public ArrayList<Adress> from = new ArrayList<>();
    public ArrayList<Adress> to = new ArrayList<>();
    public Flags flag = new Flags();
    public ArrayList<MailAttachment> attachments;

    public int attach(String name, long length) {
        int id = 1;
        if (attachments == null) {
            attachments = new ArrayList<>();
        } else if (attachments.size() > 0) {
            id = attachments.stream().map(a -> a.id).max(Comparator.comparingInt(a -> a)).get() + 1;
        }
        attachments.add(new MailAttachment(id, name, length));
        return id;
    }

    public MailMessage setAttachments(String userFolder, String idOld, ArrayList<MailAttachment> list) {
        if (VUtil.notEmpty(id) && list != null) {
            attachments = list;
            for (MailAttachment a : list) {
                try {
                    Files.copy(Paths.get(pathToTempFile(idOld, a.id)), Paths.get(pathToFile(userFolder, id, a.id)));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return this;
    }

    public MailMessage newId() {
        ZonedDateTime dt = VUtil.nowGMT();
        String idH = ("000000" + Math.round(Math.random() * 1000000));
        String idP = ("000000" + Math.round(Math.random() * 1000000));
        id = dt.toEpochSecond() + "-" + idH.substring(idH.length() - 6) + "-" + idP.substring(idP.length() - 6);
        date = VUtil.dateToJSON(dt);
        return this;
    }

    public MailMessage flagUnread() {
        flag.unread = true;
        return this;
    }

    public MailMessage flagReply() {
        flag.reply = true;
        return this;
    }

    public MailMessage flagRead() {
        flag.unread = false;
        return this;
    }

    public MailMessage content(String text) {
        html = text;
        return this;
    }

    public MailMessage subject(String text) {
        subject = text;
        return this;
    }

    public MailMessage addFrom(String email, String name) {
        from.add(new Adress(email, name));
        return this;
    }

    public MailMessage addTo(String email, String name) {
        to.add(new Adress(email, name));
        return this;
    }

    public void saveToInbox(String userName) {
        saveTo(userName, "inbox");
    }

    public void saveToSent(String userFolder) {
        saveTo(userFolder, "sent");
    }

    public void saveTo(String userFolder, String folder) {
        VUtil.writeJsonPretty(DIRMail + userFolder + "/" + folder + "/" + id, this);
    }

    public void save(Path path) {
        VUtil.writeJsonPretty(path, this);
    }

    public static Path path(String userFolder, String folder, String id) {
        return Paths.get(DIRMail + userFolder + "/" + folder + "/" + id + VUtil.endsJSON);
    }

    public static String pathToFolder(String userFolder, String folder) {

        return DIRMail + userFolder + "/" + folder + "/";
    }

    public static MailMessage loadFrom(String userFolder, String folder, String id) {
        return load(Paths.get(DIRMail + userFolder + "/" + folder + "/" + id));
    }

    public static MailMessage load(Path path) {
        return VUtil.readJson(path, MailMessage.class);
    }
}
