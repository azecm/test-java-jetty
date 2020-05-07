package org.site.server.site.front;

import org.site.elements.NodeSearch;
import org.site.server.Router;
import org.site.server.jsession.JRequest;
import org.site.view.VUtil;

import javax.servlet.GenericServlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.util.ArrayList;

public class FrontSearch extends GenericServlet {
    public void service(ServletRequest req, ServletResponse res) {
        Object result = null;
        JRequest request = new JRequest().init(req);
        if (request.isPost() && Router.testUser(request, 10)) {
            PostSearch data = request.postJson(PostSearch.class);
            if (data != null && data.search != null) {
                VUtil.Timer t = new VUtil.Timer();
                result = new NodeSearch(request.host).find(data.search.replaceAll("\\+", " "));
                t.endLog("FrontSearch");
            }
        }
        Router.json(result, res);
    }

    public static class PostSearch {
        public String search;
    }
}
