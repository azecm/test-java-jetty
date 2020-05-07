package org.site.server.site.admin;

import org.site.elements.*;
import org.site.server.Router;
import org.site.server.jsession.JSessionSite;
import org.site.server.site.ImageSite;
import org.site.view.Tracker;
import org.site.view.VUtil;
import org.site.view.ViewSite;

import javax.servlet.GenericServlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.sound.midi.Track;
import java.nio.file.Path;
import java.util.*;

import java.util.List;

public class AdminTracker extends GenericServlet {

    ArrayList<String> hosts;

    public void service(ServletRequest req, ServletResponse res) {

        JSessionSite session = new JSessionSite(req);

        if (!session.isAdmin()) {
            Router.json404(res);
            return;
        }

        if (session.host.equals("www.to.com")) {
            hosts = new ArrayList<>();
        } else {
            hosts = new ArrayList<>(Collections.singletonList(session.host));
        }

        if (session.isGet()) {
            Router.json200(getData().result, res);
        } else if (session.isPost()) {
            DataPost data = session.postJson(DataPost.class);
            Object resString = "ok-post";
            if (data.rotate != null) resString = actRotate(data.rotate);
            if (data.delete != null) resString = actDelete(data.delete);
            if (data.confirm != null) resString = actConfirm(data.confirm).result;
            Router.json200(resString, res);
        } else {
            Router.json404(res);
        }
    }

    TrackerData getData() {
        TrackerData tracker = new TrackerData();
        for (String host : hosts) {
            tracker.loadDataByHost(host);
        }
        return tracker;
    }

    static String pathTrackerAnonym(String host, String textKey) {
        return VUtil.endsJson(Tracker.folderUpdateAnonim(host) + textKey);
    }

    String pathTrackerAttach(String host, String textKey) {
        return VUtil.endsJson(Tracker.folderUpdate(host) + textKey);
    }

    TrackerAnonym loadTrackerAnonym(String host, String anonym) {
        return VUtil.readJson(pathTrackerAnonym(host, anonym), TrackerAnonym.class);
    }

    ArrayList<String> anonymSrcList(String host, String src) {
        ArrayList<String> pathList = new ArrayList<>();
        if (VUtil.notEmpty(src)) {
            pathList.add(Tracker.anonymImageFull(host, src));
            pathList.add(Tracker.anonymImagePreview(host, src));
        }
        return pathList;
    }

    String actRotate(PostRotate data) {
        ArrayList<VUtil.ImageCache> pathList = new ArrayList<>();

        int h, w;
        if (data.anonym != null) {
            TrackerAnonym dataAnonym = loadTrackerAnonym(data.host, data.anonym);
            if (dataAnonym != null) {
                for (String path : anonymSrcList(data.host, dataAnonym.src)) {
                    pathList.add(new VUtil.ImageCache(0, path));
                }

                h = dataAnonym.h;
                w = dataAnonym.w;

                dataAnonym.w = h;
                dataAnonym.h = w;

                VUtil.writeJson(pathTrackerAnonym(data.host, data.anonym), dataAnonym);
            }
        } else {
            NodeData node = VUtil.readNode(data.host, data.idn);
            if (node != null) {
                NodeAttach attach = node.attachByKey(data.idf);
                if (attach != null && attach.src != null && !VUtil.isSVG(attach.src)) {
                    pathList = VUtil.pathToImageCache(data.host, attach.src);
                    pathList.add(new VUtil.ImageCache(0, VUtil.pathToImageSrc(data.host, attach.src)));

                    h = attach.h;
                    w = attach.w;

                    attach.w = h;
                    attach.h = w;
                    VUtil.writeNode(data.host, node);
                }
            }
        }

        String result = "";
        if (pathList.size() > 0) {
            result = "ok";
            pathList.forEach(item -> {
                if (VUtil.pathExists(item.path)) {
                    new ImageSite().load(item.path).rotate(data.side.equals("right") ? 90 : -90).save();
                }
            });
        }

        return result;
    }

    String actDelete(ArrayList<PostDelete> list) {

        String result = "ok";

        for (PostDelete row : list) {
            String line = row.id;
            String host = row.host;
            if (line.contains("-")) {
                int[] param = getAttachParam(line);
                NodeData node = VUtil.readNode(host, param[0]);
                if (node != null) {
                    node.attachRemove(host, param[1]);
                    VUtil.writeNode(host, node);
                }
                VUtil.pathRemove(pathTrackerAttach(host, line));
            } else {
                TrackerAnonym dataAnonym = loadTrackerAnonym(host, line);
                if (dataAnonym != null) {
                    for (String p : anonymSrcList(host, dataAnonym.src)) {
                        VUtil.pathRemove(p);
                    }
                    VUtil.pathRemove(pathTrackerAnonym(host, line));
                } else {
                    VUtil.println("++ERROR::adminTracker::actDelete::null", line);
                }
            }
        }
        return result;
    }

    @SuppressWarnings("Duplicates")
    TrackerData actConfirm(ArrayList<PostConfirm> dataList) {

        HashSet<String> list = new HashSet<>();

        for (PostConfirm data : dataList) {
            String host = data.host;
            if (data.id.contains("-")) {

                int[] param = getAttachParam(data.id);
                NodeData node = VUtil.readNode(host, param[0]);
                if (node != null) {
                    NodeAttach attach = node.attachByKey(param[1]);
                    if (attach != null) {
                        if (data.content != null || data.like > 0 || data.catalog) {
                            if (data.content != null) attach.content = data.content;
                            if (data.like > 0) attach.like += data.like;
                            if (data.catalog) attach.flagCatalog = true;

                            VUtil.writeNode(host, node);
                            list.add(host + "::" + param[0]);
                        }
                        if (node.head.flagValid) {
                            Tracker.addConfirmed(host, node.head.idn, attach);
                        }
                    }
                }
                VUtil.pathRemove(pathTrackerAttach(host, data.id));
            } else {
                TrackerAnonym dataAnonym = loadTrackerAnonym(host, data.id);
                if (dataAnonym == null) continue;

                NodeData node = VUtil.readNode(host, dataAnonym.idn);
                if (node != null) {
                    NodeAttach attach = node.attachNew(0);

                    attach.content = dataAnonym.content;
                    attach.anonym = dataAnonym.name;
                    attach.flagComment = true;

                    if (dataAnonym.src != null) {
                        node.attachSetSrc(host, Tracker.anonymImageFull(host, dataAnonym.src), attach);
                        //VUtil.pathRemove(Tracker.anonymImagePreview(host, dataAnonym.src));
                    }

                    if (data.content != null) attach.content = data.content;
                    if (data.like > 0) attach.like += data.like;
                    if (data.catalog) attach.flagCatalog = true;

                    VUtil.writeNode(host, node);
                    list.add(host + "::" + dataAnonym.idn);

                    for (String p : anonymSrcList(host, dataAnonym.src)) VUtil.pathRemove(p);
                    //VUtil.pathRemove(pathTrackerAnonym(host, data.id));

                    if (node.head.flagValid) {
                        Tracker.addConfirmed(host, node.head.idn, attach);
                    }
                }

                if (dataAnonym.src != null) {
                    VUtil.pathRemove(Tracker.anonymImagePreview(host, dataAnonym.src));
                }
                VUtil.pathRemove(pathTrackerAnonym(host, data.id));
            }
        }

        if (list.size() > 0) {
            for (String hostIdn : list) {
                String host = hostIdn.substring(0, hostIdn.indexOf("::"));
                Integer idn = VUtil.getInt(hostIdn.substring(hostIdn.indexOf("::") + 2));
                ViewSite.updateHostIdn(host, idn);
            }
        }

        return getData();
    }

    int[] getAttachParam(String text) {
        text = text.endsWith(".json") ? text.substring(0, text.length() - 5) : text;
        String[] line = text.split("-");
        int idn = Integer.parseInt(line[0], 10);
        int idf = Integer.parseInt(line[1], 10);
        return new int[]{idn, idf};
    }

    public class PostDelete {
        String host;
        String id;
    }

    public class PostRotate {
        String side;
        String anonym;
        String host;
        int idn;
        int idf;
    }

    public class PostConfirm {
        String id;
        String host;
        String content;
        boolean catalog;
        int like;
    }

    public class DataPost {
        PostRotate rotate;
        ArrayList<PostDelete> delete;
        ArrayList<PostConfirm> confirm;
    }

    public static class TrackerAnonym {
        public int idn;
        public String date;
        public String content;
        public String ip;
        public String browser;

        public String name;

        public String src;
        public int w;
        public int h;

    }
}
