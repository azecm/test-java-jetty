package org.site.server.site.admin;

import org.apache.commons.fileupload.FileItemStream;
import org.site.elements.*;
import org.site.ini.SiteProperty;
import org.site.server.Router;
import org.site.server.jsession.JSessionSite;
import org.site.server.site.ImageSite;
import org.site.view.Tracker;
import org.site.view.VUtil;
import org.site.view.ViewSite;

import javax.servlet.GenericServlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class ControlEdit extends GenericServlet {
    JSessionSite session;
    NodeTree tree;

    public void service(ServletRequest req, ServletResponse res) {

        VUtil.Timer t = VUtil.timer();
        DataNode data = null;

        Object result = null;
        session = new JSessionSite(req);

        if (session.enabled() && session.isPost()) {
            if (session.uri.equals("/edit/files")) {
                result = saveFile();
            } else if (session.uri.equals("/edit/update")) {
                result = updateFile();
            } else {
                tree = NodeTree.load(session.host);
                data = session.postJson(DataNode.class);
                if (data.idn > 0) {
                    NodeTreeElem elem = tree.byIdn(data.idn);
                    if (elem != null) {
                        NodeData node = VUtil.readNode(session.host, data.idn);
                        if (node != null) {
                            if (data.route.equals("new")) {
                                if (session.user.status >= JSessionSite.userTrustedStatus) {
                                    result = getNewNode(tree, node);
                                }
                            } else if (session.accessAllowed(elem.idu)) {
                                switch (data.route) {
                                    case "load": {
                                        result = getNode(tree, node);
                                        break;
                                    }
                                    case "save": {
                                        result = saveNode(node, data);
                                        break;
                                    }
                                    case "delete": {
                                        result = deleteAttach(node, data);
                                        break;
                                    }
                                    case "order": {
                                        result = orderAttach(node, data);
                                        break;
                                    }
                                    case "rotate": {
                                        result = rotateAttach(node, data);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } else if (data.idn == 0 && data.idp > 0 && data.route != null && data.route.equals("save")) {
                    int idp = data.idp;
                    NodeData node = new NodeData().appendTo(session.host, idp, session.user.idu);
                    result = saveNode(node, data) + "-" + node.head.idn;
                } else {
                    VUtil.println("ControlEdit::FAIL::data::", VUtil.jsonString(data));
                }
            }
        }

        if (result == null) {
            Router.json404(res);
        } else {
            Router.json200(result, res);
        }

    }

    void updateHtml(int idn, boolean flagValidated) {
        ViewSite.updateHostIdn(session.host, idn, flagValidated);
    }

    String rotateAttach(NodeData node, DataNode data) {
        NodeAttach attach = getAttach(node, data);
        if (attach == null) return "";
        for (VUtil.ImageCache item : VUtil.pathToImages(session.host, attach.src)) {
            ImageSite img = new ImageSite().load(item.path);
            img.rotate(data.flagRight.equals(true) ? 90 : -90).save();
        }

        int w = attach.w;
        attach.w = attach.h;
        attach.h = w;
        VUtil.writeNode(session.host, node);

        return "ok";
    }

    NodeAttach getAttach(NodeData node, DataNode data) {
        int idf = data.idf;
        return idf > 0 ? node.attachByKey(idf) : null;
    }

    String deleteAttach(NodeData node, DataNode data) {
        NodeAttach attach = getAttach(node, data);
        if (attach == null) return "";
        node.attachRemove(session.host, attach.idf);
        for (NodeAttach item : node.attach) {
            if (item.group == null) continue;
            int pos = item.group.indexOf(attach.idf);
            if (pos == -1) continue;
            item.group.remove(pos);
        }
        VUtil.writeNode(session.host, node);
        updateHtml(data.idn, false);
        return "ok";
    }

    String orderAttach(NodeData node, DataNode data) {
        NodeAttach attach = getAttach(node, data);
        if (attach == null) return "";

        node.attach = node.attach.stream().filter(item -> item.idf != attach.idf).collect(Collectors.toList());
        if (data.order > node.attach.size()) {
            node.attach.add(attach);
        } else {
            node.attach.add(data.order - 1, attach);
        }

        VUtil.writeNode(session.host, node);

        return "ok";
    }

    String updateFile() {
        String pathTemp = VUtil.DIRTemp + VUtil.nowGMT().toEpochSecond();
        HashMap<String, String> result = session.postForm((FileItemStream item) -> Paths.get(pathTemp));
        int idn = VUtil.getInt(result.get("idn"));
        int idf = VUtil.getInt(result.get("idf"));
        NodeData node = VUtil.readNode(session.host, idn);
        if (node == null) return "";
        NodeAttach attach = node.attachByKey(idf);
        if (attach == null) return "";

        String pathToOrig = VUtil.pathToImageSrc(session.host, attach.src);
        try {
            Files.move(Paths.get(pathTemp), Paths.get(pathToOrig), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }

        ImageSite image = new ImageSite().load(pathToOrig);
        ImageSite imageResized = image.resize(1800).save();
        attach.w = imageResized.image.getWidth();
        attach.h = imageResized.image.getHeight();

        for (VUtil.ImageCache item : VUtil.pathToImageCache(session.host, attach.src)) {
            image.resize(item.size).save(item.path);
        }

        VUtil.writeNode(session.host, node);

        return "ok";
    }

    boolean isUser() {
        return session.user.status != JSessionSite.userAdminStatus;
    }

    ArrayList<NodeAttach> saveFile() {

        FileKeys keys = new FileKeys(session.host);
        HashMap<String, String> result = session.postForm(keys::postAttach);

        int idn = VUtil.getInt(result.get("idn"));
        boolean flagNode = result.get("flagNode") != null && result.get("flagNode").equals("true");
        boolean flagCatalog = result.get("flagCatalog") != null && result.get("flagCatalog").equals("true");
        boolean flagComment = result.get("flagComment") != null && result.get("flagComment").equals("true");

        ArrayList<NodeAttach> resultAttach = new ArrayList<>();
        NodeData node = VUtil.readNode(session.host, idn);
        if (node == null) return resultAttach;

        for (String pathName : keys.postFiles) {
            NodeAttach attach = node.attachNew(session.data.idu);
            if (flagNode) attach.flagNode = true;
            if (flagCatalog) attach.flagCatalog = true;
            if (flagComment) attach.flagComment = true;

            ImageSite.setAttach(session.host, attach, pathName);

            resultAttach.add(attach);
            if (isUser()) Tracker.addNews(session.host, node.head.idn, attach);
        }

        VUtil.writeNode(session.host, node);

        return resultAttach;
    }

    DataNode getNewNode(NodeTree tree, NodeData node) {
        DataNode result = new DataNode();

        if (node.head.flagFolder) {
            result.idn = 0;
            result.idp = node.head.idn;
            result.content = "";
            result.title = NodeData.linkText();
            result.linkText = NodeData.linkText();
            result.linkPath = NodeData.linkPath();
            result.folder = tree.getUrl(node.head.idn);
            result.attach = new ArrayList<>();
            if (session.user.status == JSessionSite.userAdminStatus) {
                result.descr = "";
                result.flagValid = false;
                result.flagBlock = false;
                result.labels = new ArrayList<>();
                result.keywords = new ArrayList<>();
            }
        }

        return result;
    }

    DataNode getNode(NodeTree tree, NodeData node) {
        DataNode result = new DataNode();

        result.idn = node.head.idn;
        result.content = node.content == null ? "" : node.content;
        result.title = node.head.title == null ? "" : node.head.title;
        result.folder = tree.getUrl(node.head.idp);
        result.linkPath = node.head.link.get(1);
        result.attach = node.attach;

        if (session.user.status == JSessionSite.userAdminStatus) {
            result.searchPhrase = node.head.searchPhrase == null ? "" : node.head.searchPhrase;
            result.descr = node.descr == null ? "" : node.descr;
            result.linkText = node.head.link.get(0);
            result.flagValid = node.head.flagValid;
            result.flagBlock = node.head.flagBlock;
            result.flagBook = node.head.flagBook;
            result.notice = node.head.notice;
            result.linked = node.head.linked;
            result.labels = node.head.labels;
            result.keywords = node.head.keywords;
        }

        return result;
    }

    String saveNode(NodeData node, DataNode data) {

        SiteProperty ini = ViewSite.getIni(session.host);

        boolean flagActivity = false;
        boolean flagValidated = false;

        NodeUpdate update = new NodeUpdate(session.host, node);

        if (data.linkText != null) {
            update.text(data.linkText);
        }
        if (data.linkPath != null) {
            update.path(data.linkPath);
        }
        if (data.flagBlock != null) {
            update.flagBlock(data.flagBlock);
        }
        if (data.flagValid != null) {
            VUtil.println(VUtil.dateLog(), "ControlEdit::saveNode::flagValid", data.flagValid, node.head.flagValid, node.head.idn);
            if (data.flagValid) {
                ZonedDateTime date = VUtil.nowGMT();
                node.head.date.set(1, VUtil.dateToJSON(date));
                update.dateAdd(VUtil.dateToJSON(date));

                flagActivity = node.content.contains("<div class=\"funny\"") || node.content.contains("<ul class=\"quiz");
                if (!node.head.flagValid) {
                    flagValidated = true;
                }
            }
            update.flagValid(data.flagValid);
        }

        // ====

        node.head.date.set(1, VUtil.dateToJSON(VUtil.nowGMT()));

        if (data.title != null) {
            node.head.title = VUtil.textClean(data.title);
        }
        if (data.flagBook != null) {
            node.head.flagBook = data.flagBook;
        }
        if (data.searchPhrase != null) {
            node.head.searchPhrase = VUtil.textClean(data.searchPhrase);
            if (node.head.searchPhrase.isEmpty()) {
                node.head.searchPhrase = null;
            }
        }
        if (data.labels != null) {
            node.head.labels = data.labels;
        }
        if (data.keywords != null) {
            node.head.keywords = data.keywords;
        }
        if (data.linked != null) {
            if (data.linked.links == null) {
                node.head.linked = null;
            } else {
                node.head.linked = data.linked;
            }
        }
        if (data.content != null) {
            if (session.user.status == JSessionSite.userAdminStatus) {
                node.content = new HtmlClean().directLinkAllowed(tree, data.content);
            } else {
                node.content = new HtmlClean().directLinkProhibited(tree, data.content);
            }
        }
        if (data.descr != null) {
            node.descr = new HtmlClean().directLinkProhibited(tree, data.descr);
        }
        if (data.notice != null) {
            if (data.notice.date != null || data.notice.email != null || data.notice.message != null) {
                node.head.notice = data.notice;
            } else {
                node.head.notice = null;
            }
        }
        if (data.attach != null) {
            for (NodeAttach attachNew : data.attach) {
                NodeAttach attach = node.attachByKey(attachNew.idf);
                if (attach == null) continue;
                if (attachNew.flagNode != null) attach.flagNode = attachNew.flagNode;
                if (attachNew.flagCatalog != null) attach.flagCatalog = attachNew.flagCatalog;
                if (attachNew.flagComment != null) attach.flagComment = attachNew.flagComment;
                if (attachNew.like > 0) attach.like = attachNew.like;
                if (attachNew.group != null) {
                    if (attachNew.group.size() > 0) {
                        attach.group = attachNew.group;
                    } else {
                        attach.group = null;
                    }
                }
                if (attachNew.quiz != null) {
                    if (attachNew.quiz.length() > 0) {
                        attach.quiz = attachNew.quiz;
                    } else {
                        attach.quiz = null;
                    }
                }
                if (attachNew.content != null) {
                    attach.content = new HtmlClean().directLinkProhibited(tree, attachNew.content);
                }

                if (isUser()) Tracker.addNews(session.host, node.head.idn, attach);
            }

            if (ini.flagNewsNotification && node.head.link != null && node.head.link.get(1).equals("@image")) {
                ViewSite.newsNotificationUpdate(tree, session.host, node);
            }
        }

        if (isUser()) Tracker.addNews(session.host, node);
        else Tracker.remove(session.host, node);

        update.exec();
        VUtil.writeNode(session.host, node);

        if (session.isAdmin()) {
            if (ini.flag2019) {
                new NodeSearch(session.host).updateDB(node.head.idn).finish();
            }
        }

        if (node.head.flagValid) {
            Tracker.addMark(session.host, node.head.idn);
        }

        if (flagActivity) new Activity().genList();

        updateHtml(node.head.idn, flagValidated);

        return "ok";
    }


    // ==========

    public static class DataNode {
        String route;

        int idn;

        Integer idf;
        Integer order;
        Integer idp;
        Boolean flagRight;

        String title;
        String searchPhrase;
        String descr;
        String content;
        String linkText;
        String linkPath;
        String folder;

        Boolean flagValid;
        Boolean flagBlock;
        Boolean flagBook;

        List<Integer> labels;
        List<String> keywords;
        List<NodeAttach> attach;

        NodeHeadNotice notice;
        NodeHeadLinked linked;
    }
}


