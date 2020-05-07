package org.site.server.mail;

import org.site.server.Router;
import org.site.server.jsession.JSessionMail;
import org.site.view.VUtil;

import javax.servlet.GenericServlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Note extends GenericServlet {

    Pattern reURI = Pattern.compile("/note/(group|element)");

    public class PostElement {
        String method;
        long nid;
        long gid;
        int position;
        String email;
        String name;
    }

    public class PostGroup {
        String method;
        long id;
        long before;
        String name;
    }


    public void service(ServletRequest req, ServletResponse res) {
        JSessionMail session = new JSessionMail(req).loadNotes();
        if (!session.isPost() || session.notes == null) {
            Router.json404(res);
            return;
        }

        boolean flag = false;
        Matcher m = reURI.matcher(session.uri);
        if (m.find()) {
            switch (m.group(1)) {
                case "group": {
                    flag = group(session, session.postJson(PostGroup.class));
                    break;
                }
                case "element": {
                    flag = element(session, session.postJson(PostElement.class));
                    break;
                }
            }
        }

        if (flag) {
            session.saveNotes();
            Router.json200("ok", res);
        } else {
            Router.json404(res);
        }
    }

    void removeData(JSessionMail session, long id) {
        String pathToData = session.pathNode() + "/" + id + VUtil.endsJSON;
        try {
            Files.deleteIfExists(Paths.get(pathToData));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    boolean element(JSessionMail session, PostElement data) {
        boolean flag = false;
        if (data != null) {
            switch (data.method) {
                case "update": {
                    UserData.Note note = null;
                    UserData.Group gr = null;
                    int position = -1;
                    for (UserData.Group g : session.notes.notes) {
                        int p = -1;
                        for (UserData.Note n : g.note) {
                            p++;
                            if (n.id == data.nid) {
                                note = n;
                                gr = g;
                                position = p;
                            }
                        }
                    }
                    if (note != null) {
                        note.email = data.email;
                        note.name = data.name;
                        if (data.gid > 0 && data.gid != gr.id) {
                            gr.note.remove(position);
                            getGroup(session, data.gid).note.add(note);
                        } else if (data.position != position) {
                            gr.note.remove(position);
                            if (data.position < gr.note.size()) gr.note.add(data.position, note);
                            else gr.note.add(note);
                        }
                        flag = true;
                    }
                    break;
                }
                case "delete": {
                    for (UserData.Group g : session.notes.notes) {
                        g.note = g.note.stream().filter(n -> n.id != data.nid).collect(Collectors.toList());
                    }
                    removeData(session, data.nid);
                    flag = true;
                    break;
                }
                case "add": {
                    long id = session.nextElementId();
                    if (id == data.nid) {
                        UserData.Note n = new UserData.Note(data.nid, data.name, data.email);
                        getGroup(session, data.gid).note.add(n);
                        flag = true;
                    }
                    break;
                }
            }
        }
        return flag;
    }

    UserData.Group getGroup(JSessionMail session, long id) {
        UserData.Group res = null;
        for (UserData.Group g : session.notes.notes) {
            if (g.id == id) {
                res = g;
                break;
            }
        }
        return res;
    }

    int groupInd(JSessionMail session, long id) {
        int ind = -1, pos = -1;
        for (UserData.Group g : session.notes.notes) {
            pos++;
            if (g.id == id) {
                ind = pos;
                break;
            }
        }
        return ind;
    }

    void groupPosit(JSessionMail session, PostGroup data, UserData.Group group) {
        if (data.before > -1) {
            session.notes.notes = session.notes.notes.stream().filter(g -> g.id != data.id).collect(Collectors.toList());
            if (data.before > 0) {
                session.notes.notes.add(groupInd(session, data.before), group);
            } else {
                session.notes.notes.add(group);
            }
        }
    }

    boolean group(JSessionMail session, PostGroup data) {
        boolean flag = false;
        if (data != null) {
            switch (data.method) {
                case "update": {
                    UserData.Group group = getGroup(session, data.id);
                    if (group != null) {
                        flag = true;
                        group.name = data.name;
                        groupPosit(session, data, group);
                    }
                    break;
                }
                case "delete": {
                    if (getGroup(session, data.id).note.size() == 0) {
                        session.notes.notes = session.notes.notes.stream().filter(g -> g.id != data.id).collect(Collectors.toList());
                        flag = true;
                    }
                    break;
                }
                case "add": {
                    long id = session.notes.notes.stream().max(Comparator.comparingLong(g -> g.id)).get().id + 1;
                    if (id == data.id) {
                        UserData.Group g = new UserData.Group();
                        g.id = data.id;
                        g.name = data.name;
                        g.note = new ArrayList<>();
                        groupPosit(session, data, g);
                        flag = true;
                    }
                    break;
                }
            }
        }
        return flag;
    }
}
