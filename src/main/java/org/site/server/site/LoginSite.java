package org.site.server.site;

import org.site.elements.SiteUserData;
import org.site.server.Router;
import org.site.server.jsession.*;
import org.site.view.VUtil;

import javax.servlet.GenericServlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class LoginSite extends GenericServlet {
    public void service(ServletRequest req, ServletResponse res) {
        JRequest jreq = new JRequest().init(req);
        if (!jreq.isPost()) {
            Router.json404(res);
            return;
        }

        List<String> loginData = jreq.postLogin();
        if (loginData.size() != 3) {
            Router.json404(res);
            return;
        }

        String device = loginData.get(0);
        String login = loginData.get(1);
        String pass = loginData.get(2);

        JSessionSite session = new JSessionSite(req);
        SiteUserData user = session.findUserByName(login);

        if (user == null || !user.password.equals(pass)) {
            Router.json404(res);
            return;
        }

        session.data = new DataSite();
        session.data.ip = jreq.getIP();
        session.data.browser = jreq.getBrowser();
        session.data.device = device;
        session.data.idu = user.idu;
        session.genKeys(session.data);

        ArrayList<String> menu = new ArrayList<>();
        menu.add("главная:home:/");
        menu.add(user.name);
        if (user.status == JSessionSite.userAdminStatus) {
            menu.add("новые:verify:/admin/verify");
            menu.add("структура:struct:/admin/tree#0");
            menu.add("темы:theme:/admin/keywords");
            menu.add("статистика:stat:/admin/statistic");
        }
        session.save();

        ArrayList<Object> result = new ArrayList<>();
        result.add(session.key);
        result.add(session.pin);
        result.add(menu);
        Router.json200(result, res);

    }
}
