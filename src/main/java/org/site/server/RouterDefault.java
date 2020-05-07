package org.site.server;

import javax.servlet.GenericServlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

public class RouterDefault extends GenericServlet {
    public void service(ServletRequest req, ServletResponse res) {
        Router.json404(res);
    }
}
