package org.site.view;

import org.site.elements.NodeAttach;
import org.site.elements.NodeData;
import org.site.server.site.admin.AdminTracker;

public class Tracker {

    public static String folderTracker(String host) {
        return VUtil.DIRMemory + host + "/tracker/";
    }

    public static String folderMark(String host) {
        return folderTracker(host) + "mark/";
    }

    public static String folderUpdate(String host) {
        return folderTracker(host) + "update/";
    }

    public static String folderUpdateAnonim(String host) {
        return folderTracker(host) + "anonym/";
    }

    static String folderImageAnonym(String host) {
        return VUtil.DIRFile + host + "/file/anonym/";
    }

    public static String anonymImageFull(String host, String src) {
        return folderImageAnonym(host) + src;
    }

    public static String anonymImagePreview(String host, String src) {
        return folderImageAnonym(host) + "150/" + src;
    }

    public static String pathMark(String host, int idn) {
        return folderMark(host) + idn + ".txt";
    }

    public static void addMark(String host, int idn) {
        String path = pathMark(host, idn);
        if (!VUtil.pathExists(path)) VUtil.writeFile(path, "");
    }

    public static void remove(String host, NodeData node) {
        VUtil.pathRemove(VUtil.endsJson(folderUpdate(host) + node.head.idn));
    }

    public static void addNews(String host, NodeData node) {
        VUtil.writeJson(folderUpdate(host) + node.head.idn, node.head);
    }

    public static void addNews(String host, int idn, NodeAttach attach) {
        VUtil.writeJson(folderUpdate(host) + idn + "-" + attach.idf, attach);
    }

    public static void addNews(String host, AdminTracker.TrackerAnonym anonymData) {
        VUtil.writeJson(folderUpdateAnonim(host) + VUtil.nowGMT().toEpochSecond() + ((int) Math.round(Math.random() * 100000)), anonymData);
    }

    public static void addConfirmed(String host, int idn, NodeAttach attach) {
        if (ViewSite.getIni(host).tracker > 0) {
            addMark(host, 0);
            VUtil.writeJson(folderTracker(host) + idn + "-" + attach.idf, attach);
        }
    }
}
