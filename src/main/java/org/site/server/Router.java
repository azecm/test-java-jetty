package org.site.server;


import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.site.server.jsession.JRequest;
import org.site.server.mail.*;
import org.site.server.site.LoginSite;
import org.site.server.site.admin.*;
import org.site.server.site.front.*;
import org.site.view.VUtil;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.StdErrLog;

//import org.apache.logging.log4j;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;


public class Router {

    public final static String folderInbox = "inbox";
    public final static String folderTrash = "trash";
    public final static String folderSent = "sent";
    public final static String folderReady = "ready";
    public final static String folderNote = "note";

    public static HashMap<String, HashSet<String>> folderInboxPrev = new HashMap<>();
    public static HashMap<String, ZonedDateTime> userHash = new LinkedHashMap<>();

    public static boolean testUserWithParam(JRequest request, int deltaSec, String param) {
        boolean flag = false;
        if (request.getReferer().length() > 0) {
            flag = true;
            final String key = request.getIP() + "::" + request.getBrowser() + (VUtil.notEmpty(param) ? "::" + param : "");
            if (userHash.containsKey(key)) {
                ZonedDateTime date = userHash.get(key);
                flag = false;
                if (date.until(VUtil.nowGMT(), ChronoUnit.SECONDS) > deltaSec) {
                    flag = true;
                }
            }

            userHash.put(key, VUtil.nowGMT());
            if (userHash.size() > 300) {
                VUtil.println("Router::testUserWithParam > 300");
            }
            while (userHash.size() > 300) {
                Set<String> set = userHash.keySet();
                try {
                    Iterator<String> iter = set.iterator();
                    if (iter.hasNext()) {
                        String k = iter.next();
                        if (k != null) {
                            userHash.remove(k);
                        }
                    }
                } catch (Exception e) {
                    VUtil.println("Router::testUserWithParam::ERROR", e.getMessage());
                    break;
                }
            }
        }

        return flag;

    }

    public static boolean testUser(JRequest request, int deltaSec) {
        return testUserWithParam(request, deltaSec, "");
    }

    public static void json(@Nullable Object result, @NotNull ServletResponse res) {
        if (result == null) {
            json404(res);
        } else {
            json200(result, res);
        }
    }

    public static void json404(ServletResponse res) {
        sendJson(new ResultJson(404), res);
    }

    public static void json404(String errorText, ServletResponse res) {
        sendJson(new ResultJson(404).error(errorText), res);
    }

    public static void json200(Object result, ServletResponse res) {
        sendJson(new ResultJson(200).resut(result), res);
    }

    public static void sendRedirect(String url, boolean flagFast, ServletResponse res) {
        HttpServletResponse response = (HttpServletResponse) res;

        if (url == null || url.isEmpty()) {
            response.setContentType(MimeTypes.Type.TEXT_PLAIN.asString());
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        } else {
            if (flagFast) {
                response.setContentType(MimeTypes.Type.TEXT_PLAIN.asString());
                response.setHeader("Location", url);
                response.setStatus(HttpServletResponse.SC_SEE_OTHER);
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("<!DOCTYPE html>");
                sb.append("<html>");
                sb.append("<head>");
                sb.append("<meta charset=\"utf-8\" />");
                sb.append("<title>Переход по внешней ссылке</title>");
                sb.append("<meta name=\"robots\" content=\"noindex, nofollow, noarchive\" />");
                sb.append("<meta name=\"googlebot\" content=\"noindex\" />");
                sb.append("</head>");
                sb.append("<body>");

                sb.append("<!--noindex--><p>");
                sb.append("Вы переходите по внешней ссылке: ");
                sb.append("<a href=\"" + url + "\" rel=\"nofollow\">" + url + "</a>");
                sb.append("</p><!--/noindex-->");

                sb.append("</body>");
                sb.append("</html>");

                response.setHeader("Refresh", "15;" + url);

                response.setContentType(MimeTypes.Type.TEXT_HTML_UTF_8.asString());
                try {
                    response.getWriter().append(sb);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                response.setStatus(HttpServletResponse.SC_OK);
            }
        }
    }

    public static void sendJson(ResultJson resultJson, ServletResponse res) {

        String resultText = VUtil.jsonString(resultJson);

        HttpServletResponse response = (HttpServletResponse) res;
        response.setContentType(MimeTypes.Type.APPLICATION_JSON_UTF_8.asString());
        try {
            response.getWriter().append(resultText);
        } catch (IOException e) {
            e.printStackTrace();
        }
        response.setStatus(HttpServletResponse.SC_OK);
    }

    public static HashMap<String, String> queryMap(ServletRequest req) {
        HttpServletRequest request = (HttpServletRequest) req;
        HashMap<String, String> map = new HashMap<>();
        String queryString = request.getQueryString();
        if (queryString != null) {
            String[] params = queryString.split("&");
            for (String param : params) {
                String[] paramList = param.split("=");
                if (paramList.length == 2) {
                    map.put(paramList[0], paramList[1]);
                }
            }
        }
        return map;
    }

    public void start() {

        Log.setLog(new StdErrLog());

        ServletContextHandler contextMail = new ServletContextHandler(ServletContextHandler.NO_SECURITY | ServletContextHandler.NO_SESSIONS);

        contextMail.setContextPath("/a/m");
        contextMail.addServlet(LoginMail.class, "/o/*");
        contextMail.addServlet(NoteData.class, "/note-d/*");
        contextMail.addServlet(Note.class, "/not/*");
        contextMail.addServlet(Event.class, "/even/*");
        contextMail.addServlet(Notes.class, "/not/*");
        contextMail.addServlet(ReadFolder.class, "/fold/*");
        contextMail.addServlet(DLFile.class, "/file/*");
        contextMail.addServlet(UpdateMessage.class, "/message/*");
        contextMail.addServlet(SendMessage.class, "/send/*");


        ServletContextHandler contextSite = new ServletContextHandler(ServletContextHandler.NO_SECURITY | ServletContextHandler.NO_SESSIONS);
        contextSite.setContextPath("/api/v2");
        contextSite.addServlet(LoginSite.class, "/ok/");
        contextSite.addServlet(AdminStat.class, "/sta");
        contextSite.addServlet(AdminStat.class, "/lo");
        contextSite.addServlet(AdminKeywords.class, "/keyword");
        contextSite.addServlet(AdminTracker.class, "/verify");
        contextSite.addServlet(AdminTree.class, "/tree");
        contextSite.addServlet(ControlUser.class, "/user");
        contextSite.addServlet(ControlEdit.class, "/ed");
        contextSite.addServlet(ControlEdit.class, "/edit/file");
        contextSite.addServlet(ControlEdit.class, "/edit/upd");

        contextSite.addServlet(FrontComment.class, "/comment");
        contextSite.addServlet(FrontSearch.class, "/search");
        contextSite.addServlet(FrontMailForm.class, "/mail");
        contextSite.addServlet(FrontArticleRating.class, "/rat");
        contextSite.addServlet(FrontActivity.class, "/act");


        Server _server = new Server(8089);
        ServerConnector connector = _server.getBean(ServerConnector.class);
        HttpConfiguration config = connector.getBean(HttpConnectionFactory.class).getHttpConfiguration();
        config.setSendDateHeader(true);
        config.setSendServerVersion(false);


        HandlerCollection handlers = new HandlerCollection();

        _server.setHandler(handlers);


        _server.addBean(new RouterErrorHandler());

        try {
            _server.start();
            _server.join();
        } catch (Exception e) {
            e.printStackTrace();
            VUtil.println("Error::405", e.getMessage(), e.toString());
        }
    }

    public static class ResultJson {
        int code;
        Object result;
        String error;

        public ResultJson(int statusCode) {
            code = statusCode;
        }

        public ResultJson resut(Object obj) {
            result = obj;
            return this;
        }

        public ResultJson error(String text) {
            error = text;
            return this;
        }
    }
}
