package org.site.server.site.admin;

import org.site.elements.StatData;
import org.site.log.LogHostStat;
import org.site.server.Router;
import org.site.server.jsession.JSessionSite;
import org.site.view.VUtil;

import javax.servlet.GenericServlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.util.HashMap;
import java.util.regex.Pattern;

public class AdminStat extends GenericServlet {
    private final static Pattern reDate = Pattern.compile("^\\d\\d\\d\\d-\\d\\d-\\d\\d$");

    public void service(ServletRequest req, ServletResponse res) {

        StatData result = null;
        JSessionSite session = new JSessionSite(req);

        StatData data = null;

        if (session.isAdmin() && session.isPost()) {
            StatDate datareq = session.postJson(StatDate.class);
            if (datareq != null && datareq.date != null && reDate.matcher(datareq.date).find()) {
                data = VUtil.readJson(VUtil.DIRStat + datareq.date.replaceAll("-", "/"), StatData.class);
            }
        }

        if (data != null && data.hosts != null) {
            result = new StatData();

            if (session.uri.endsWith("/stat")) {
                LogHostStat dataHost = data.hosts.get(session.host);
                if (dataHost == null) {
                    Router.json404(res);
                    return;
                }
                result.date = data.date;
                result.hosts = new HashMap<>();
                result.hosts.put(session.host, dataHost);
            } else {
                result = data;
            }
        }

        Router.json(result, res);
    }

    public class StatDate {
        String date;
    }
}
