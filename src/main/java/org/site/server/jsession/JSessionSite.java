package org.site.server.jsession;

import org.site.elements.NodeTree;
import org.site.elements.SiteUserData;
import org.site.view.VUtil;

import javax.servlet.ServletRequest;
import java.util.HashMap;

public class JSessionSite extends JSession {

    public final static int userAdminStatus = 5;
    public final static int userTrustedStatus = 3;

    static HashMap<String, HashMap<Integer, SiteUserData>> usersByHost = new HashMap<>();


    public DataSite data = null;
    public SiteUserData user = null;

    public JSessionSite(ServletRequest req) {
        initSession(req);
        data = loadSession(DataSite.class);
        updateUri();
        if (data != null) {
            HashMap<Integer, SiteUserData> users = loadUsers();
            user = users.get(data.idu);
        }
    }

    public boolean isAdmin() {
        return enabled() && user.status == JSessionSite.userAdminStatus;
    }

    public boolean enabled() {
        return data != null && user != null;
    }

    public boolean accessAllowed(int idu) {
        return enabled() && (user.idu == idu || user.status == JSessionSite.userAdminStatus);
    }


    public void save() {
        if (data != null && key != null) {
            VUtil.writeJson(path(), data);
        }
    }

    HashMap<Integer, SiteUserData> loadUsers() {
        HashMap<Integer, SiteUserData> users = usersByHost.get(host);
        if (users == null) {
            final HashMap<Integer, SiteUserData> usersLoad = new HashMap<>();
            VUtil.readUsers(host, (user) -> {
                if (user != null) usersLoad.put(user.idu, user);
            });
            users = usersLoad;
            usersByHost.put(host, users);
        }
        return users;
    }

    public NodeTree loadTree() {
        return NodeTree.load(host);
    }

    public SiteUserData findUserByName(String userName) {
        HashMap<Integer, SiteUserData> users = loadUsers();
        SiteUserData user = null;
        if (userName != null) {
            for (SiteUserData u : users.values()) {
                if (u.name.equals(userName)) {
                    user = u;
                    break;
                }
            }
        }

        return user;
    }
}
