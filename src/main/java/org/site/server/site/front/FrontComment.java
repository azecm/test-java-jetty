package org.site.server.site.front;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.site.elements.*;
import org.site.server.Router;
import org.site.server.jsession.JSessionSite;
import org.site.server.site.ImageSite;
import org.site.server.site.admin.AdminTracker;
import org.site.view.RAction;
import org.site.view.Tracker;
import org.site.view.VUtil;
import org.site.view.ViewSite;
import redis.clients.jedis.Jedis;

import javax.servlet.GenericServlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FrontComment extends GenericServlet {
    JSessionSite session;

    HashMap<String, String> idns;
    NodeTree tree;

    public void service(ServletRequest req, ServletResponse res) {
        Object result = null;
        session = new JSessionSite(req);

        boolean flagDisabled = session.getIP().equals("185.232.170.190");
        if (flagDisabled) {
            result = "ok";
            VUtil.println("comment disabled", session.getIP());
        }

        if (session.isPost() && !flagDisabled) {

            if (session.isMultipart()) {
                if (Router.testUser(session, 3)) {
                    result = comment();
                } else {
                    VUtil.println("FrontComment", "Router.testUser::false");
                }
            } else {
                String referer = session.getReferer();
                if (!VUtil.isEmpty(referer)) {
                    DataLoad data = session.postJson(DataLoad.class);
                    tree = NodeTree.load(session.host);
                    NodeTreeElem elem = tree.byUrl(referer);
                    if (elem != null && data != null) {
                        data.idn = elem.idn;
                        data.idu = session.enabled() ? session.data.idu : 0;
                        switch (data.route) {
                            case "like": {
                                //if (Router.testUserWithParam(session, 1, "like:idf:" + data.idf)) {
                                result = like(data);
                                //}
                                break;
                            }
                            case "page": {
                                if (Router.testUser(session, 1)) {
                                    try {
                                        result = page(data);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }

        Router.json(result, res);
    }

    ArrayList<ResultComment> page(DataLoad data) {
        ArrayList<ResultComment> res = new ArrayList<>();
        NodeData node = VUtil.readNode(session.host, data.idn);

        int ind = 0, perPage = ViewSite.commentsPerPage, indStart = (data.num - 1) * perPage, indEnd = data.num * perPage;

        HashMap<Integer, String> users = new HashMap<>();
        for (NodeAttach attach : node.attach) {
            if (VUtil.isFalse(attach.flagComment)) continue;
            if (ind >= indStart && ind < indEnd) {
                if (attach.idu > 0 && users.get(attach.idu) == null) {
                    users.put(attach.idu, VUtil.readUser(session.host, attach.idu).name);
                }
                res.add(new ResultComment(attach, users.get(attach.idu)).updateLink(attach, tree));
            }
            ind++;
        }

        return res;
    }

    String like(DataLoad data) {
        String result = "no";
        String voteKey = (data.idu > 0 ? (data.idn + ":" + data.idf + ":" + data.idu) : (data.idn + ":" + data.idf + ":" + session.getIP() + ":" + session.getBrowser()));

        Jedis jedis = RAction.redis();
        long res = jedis.sadd(RAction.keyCommentDayVoting(session.host), voteKey);
        jedis.disconnect();

        if (res > 0) {
            NodeData node = VUtil.readNode(session.host, data.idn);
            if (node != null && node.attach != null) {
                NodeAttach attach = node.attachByKey(data.idf);
                if (attach != null && VUtil.isTrue(attach.flagComment)) {
                    attach.like++;
                    VUtil.writeNode(session.host, node);
                    Tracker.addMark(session.host, data.idn);
                    result = "yes";
                }
            }
        }

        return result;
    }
    //

    String getContent(String text, boolean isUser) {
        idns = new HashMap<>();

        String textTest = VUtil.textClean(VUtil.textOnly(text, false), 1);

        if (isUser) {
            if (textTest.length() < 3) text = "";
        } else {
            if (textTest.length() < 5) text = "";
        }
        if (text.length() == 0) return "";

        ArrayList<String> list = new ArrayList<>();

        NodeTree tree = NodeTree.load(session.host);

        Pattern reSpace = Pattern.compile("\\s+");
        Pattern reLink = Pattern.compile("https?://[a-z0-9/%#.?\\-_=&]+[a-z0-9_/]", Pattern.CASE_INSENSITIVE);
        for (String line : text.split("\n")) {
            line = VUtil.textClean(VUtil.textOnly(line.trim(), true));
            if (line.length() == 0) continue;
            line = reSpace.matcher(line).replaceAll(" ");

            StringBuffer sb = new StringBuffer(line.length());
            Matcher m = reLink.matcher(line);
            while (m.find()) {
                String text1 = m.group(0);

                String href = null, linkText = null;
                URL url;
                try {
                    url = new URL(text1);
                    if (url.getHost().equals(session.host)) {

                        NodeTreeElem elem = tree.byUrl(url.getPath());
                        if (elem != null) {
                            linkText = "(" + elem.text + ")";
                            href = elem.idn + "";
                            idns.put(elem.idn + "", url.getPath());
                        }
                    } else {

                        href = url.getHost();
                        if (url.getPath() != null) href += url.getPath();
                        if (url.getQuery() != null) href += "?" + url.getQuery();
                        if (url.getRef() != null) href += "#" + url.getRef();
                        linkText = href;
                        href = (url.getProtocol().equals("https") ? "/goto/s/" : "/goto/") + href;
                    }

                } catch (MalformedURLException e) {
                    //m.appendReplacement(sb, "-");
                    continue;
                }

                if (href == null) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(text1));
                } else {
                    m.appendReplacement(sb, "<a href=\"" + href + "\">" + linkText + "</a>");
                }
            }

            if (sb.length() > 0) {
                m.appendTail(sb);
                list.add(sb.toString());
            } else list.add(line);

        }


        text = "";
        if (list.size() > 0) {
            text = "<p>" + String.join("<br>", list) + "</p>";


            text = text.replaceAll(":-*\\)+", "\uD83D\uDE42").replaceAll("\\){3,}", "\uD83D\uDE42");


            text = text.replaceAll(":-*\\(+", "☹️").replaceAll("\\({3,}", "☹️");

            text = text.replaceAll(";-*\\)+", "\uD83D\uDE09");


            text = text.replaceAll(":-*D+", "\uD83D\uDE2E");

        }

        return text;
    }

    ResultComment comment() {

        ResultComment resultComment = new ResultComment();

        AdminTracker.TrackerAnonym dataAnonym = new AdminTracker.TrackerAnonym();
        if (!session.enabled()) {
            dataAnonym.browser = session.getBrowser();
            dataAnonym.ip = session.getIP();
            dataAnonym.date = VUtil.dateToJSON(VUtil.nowGMT());
        }

        FileKeys keys = new FileKeys(session.host);
        HashMap<String, String> result = session.postForm(session.enabled() ? keys::postAttach : keys::postTempFile);
        int idn = dataAnonym.idn = VUtil.getInt(result.get("idn"));
        if (idn == 0) return null;
        int idu = session.enabled() ? session.data.idu : 0;
        NodeData node = VUtil.readNode(session.host, idn);
        NodeAttach attach = node.attachNew(idu);
        attach.flagComment = true;
        if (result.containsKey("comment")) {
            resultComment.content = dataAnonym.content = attach.content = getContent(result.get("comment"), true);
            for (Map.Entry<String, String> ent : idns.entrySet()) {
                resultComment.content = resultComment.content.replaceAll(ent.getKey(), ent.getValue());
            }
        }

        if (!session.enabled() && result.containsKey("anonym")) {
            dataAnonym.name = VUtil.textOnly(result.get("anonym"), true);
            if (dataAnonym.name.length() > 20) dataAnonym.name = dataAnonym.name.substring(0, 20);
            resultComment.anonym = dataAnonym.name;
        }

        if (keys.postFiles.size() > 0) {
            if (session.enabled()) {
                ImageSite.setAttach(session.host, attach, keys.postFiles.get(0));
                if (attach.src != null) {
                    //resultComment.src = "/file/" + attach.src.replaceAll("/", "/150/");
                    resultComment.src = attach.src;
                }
                //resultComment.user = session.user.name;
            } else {
                if (result.containsKey("anonym")) {
                    //resultComment.name = "Гость";
                    dataAnonym.name = VUtil.textOnly(VUtil.textClean(result.get("anonym")));
                    if (dataAnonym.name.length() > 0) {
                        if (dataAnonym.name.length() > 50) dataAnonym.name = dataAnonym.name.substring(0, 50);
                        resultComment.anonym += dataAnonym.name;
                    }
                }

                String pathNameOrign = keys.postFiles.get(0);
                String pathName = pathNameOrign.replace(".jpeg", ".jpg");
                if (ImageSite.isImage(pathName)) {
                    if (ImageSite.isBitmap(pathName)) {
                        @SuppressWarnings("Duplicates")
                        ImageSite image = new ImageSite().load(pathNameOrign);
                        ImageSite imageMax = image.resize(1800);
                        dataAnonym.w = imageMax.image.getWidth();
                        dataAnonym.h = imageMax.image.getHeight();

                        dataAnonym.src = VUtil.getFileName(pathName).toLowerCase();
                        imageMax.save(Tracker.anonymImageFull(session.host, dataAnonym.src));
                        image.resize(150).save(Tracker.anonymImagePreview(session.host, dataAnonym.src));
                    } else {
                        dataAnonym.src = VUtil.getFileName(pathName).toLowerCase();
                        try {
                            Files.copy(Paths.get(pathNameOrign), Paths.get(Tracker.anonymImageFull(session.host, dataAnonym.src)));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                //resultComment.src = "/file/anonym/150/" + dataAnonym.src;
                resultComment.src = "anonym/" + dataAnonym.src;
                VUtil.pathRemove(pathNameOrign);
            }
        }

        if (session.enabled()) {
            if (attach.content.length() > 0 || attach.src != null) {
                VUtil.writeNode(session.host, node);
            }
            SiteUserData user = VUtil.readUser(session.host, idu);
            if (user != null) resultComment.user = user.name;
            if (session.isAdmin()) {
                ViewSite.updateHostIdn(session.host, idn);
                Tracker.addConfirmed(session.host, node.head.idn, attach);
            } else {
                Tracker.addNews(session.host, node.head.idn, attach);
                Tracker.addMark(session.host, idn);
            }

        } else {
            Tracker.addNews(session.host, dataAnonym);
        }

        resultComment.date = VUtil.dateToJSON(VUtil.nowGMT());

        return resultComment;
    }

    // =====

    public static class ResultComment {
        int idf;
        int like;
        String date;
        String content;
        String anonym;
        String user;
        String src;
        Integer w;
        Integer h;

        ResultComment() {
        }

        ResultComment(NodeAttach attach, String userName) {
            idf = attach.idf;
            like = attach.like;
            date = attach.date;
            content = attach.content;
            anonym = attach.anonym;
            user = userName;

            if (attach.src != null) {
                src = attach.src;
                w = attach.w;
                h = attach.h;
            }
        }

        ResultComment updateLink(NodeAttach attach, NodeTree tree) {
            if (content != null && content.contains("href")) {
                Document doc = attach.getContent();
                for (Element l : doc.getElementsByTag("a").stream().collect(Collectors.toList())) {
                    String href = l.attr("href");
                    int idn = VUtil.getInt(href);
                    if (idn > 0) {
                        String path = tree.getUrl(idn);
                        if (path == null) {
                            VUtil.println("FrontComment::badUrl", tree.getHost(), attach.idf);
                        } else {
                            l.attr("href", path);
                        }
                    }
                }
                content = VUtil.html(doc);
            }
            return this;
        }
    }

    public static class DataLoad {
        String route;
        int num;
        int idf;
        int idn;
        int idu;
    }


}
