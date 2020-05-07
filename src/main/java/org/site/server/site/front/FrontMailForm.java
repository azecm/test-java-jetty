package org.site.server.site.front;

import org.site.server.Router;
import org.site.server.SendMail;
import org.site.server.jsession.JRequest;
import org.site.view.ViewSite;

import javax.servlet.GenericServlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.util.ArrayList;

public class FrontMailForm extends GenericServlet {
    public void service(ServletRequest req, ServletResponse res) {
        Object result = null;
        JRequest request = new JRequest().init(req);
        if (request.isPost() && Router.testUser(request, 10)) {
            ArrayList<FormRow> formData = request.postJsonList(FormRow.class);
            request.getBrowser();

            String from = "no-reply@" + request.host.replaceAll("www\\.", "");
            String subject = "сообщение с сайта " + request.host;

            StringBuilder sb = new StringBuilder();
            sb.append("<p>");
            sb.append("<br>Browser: " + request.getBrowser());
            sb.append("<br>ip: " + request.getIP());
            sb.append("</p>");

            String paramContent = "";
            sb.append("<p>");
            for (FormRow row : formData) {
                switch (row.type == null ? "" : row.type) {
                    case "content": {
                        paramContent = "<p><b>" + row.label + ":</b><br>" + row.data + "</p>";
                        break;
                    }
                    default: {
                        sb.append("<br><b>" + row.label + ":</b> " + row.data);
                        break;
                    }
                }
            }
            sb.append("</p>");
            sb.append(paramContent);
            sb.append("<p>---<br>" + request.host + "</p>");

            String content = sb.toString();

            boolean flag = true;
            for (String email : ViewSite.getIni(request.host).admin.email) {
                if (!new SendMail().from(SendMail.email(from))
                        .to(SendMail.email(email))
                        .subject(subject)
                        .content(content)
                        .send()) flag = false;
            }
            result = flag ? "ok" : "";
        }
        Router.json(result, res);
    }

    public static class FormRow {
        String label;
        String data;
        String type;
    }
}
