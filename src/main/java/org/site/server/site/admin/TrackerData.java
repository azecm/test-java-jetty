package org.site.server.site.admin;

import org.site.elements.*;
import org.site.server.site.ImageSite;
import org.site.view.Tracker;
import org.site.view.VUtil;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TrackerData {

    HashMap<String, String> userDict;
    DataGet result = new DataGet();


    String userName(String host, int idu) {
        if (userDict == null) userDict = new HashMap<>();
        String userKey = host + '-' + idu;
        String name = userDict.get(userKey);
        if (name == null) {
            if (idu > 0) {
                SiteUserData u = VUtil.readUser(host, idu);
                userDict.put(userKey, u == null ? "---" : u.name);
            } else {
                userDict.put(userKey, "Гость");
            }
            name = userDict.get(userKey);
        }
        return name;
    }


    int[] getAttachParam(String text) {
        text = text.endsWith(".json") ? text.substring(0, text.length() - 5) : text;
        String[] line = text.split("-");
        int idn = Integer.parseInt(line[0], 10);
        int idf = Integer.parseInt(line[1], 10);
        return new int[]{idn, idf};
    }


    void loadDataByHost(String host) {
        NodeTree tree = NodeTree.load(host);

        ArrayList<Path> listNode = new ArrayList<>();
        ArrayList<Path> listAttach = new ArrayList<>();
        ArrayList<Path> listAnonym = new ArrayList<>();

        Path pathLine;
        int i;
        List<Path> list;

        list = VUtil.dirlistLastModified(Tracker.folderUpdate(host));
        i = list.size();
        while (i-- > 0) {
            pathLine = list.get(i);
            if (VUtil.isJson(pathLine)) {
                if (pathLine.getFileName().toString().contains("-")) {
                    listAttach.add(pathLine);
                } else {
                    listNode.add(pathLine);
                }
            }
        }

        list = VUtil.dirlistLastModified(Tracker.folderUpdateAnonim(host));
        i = list.size();
        while (i-- > 0) {
            pathLine = list.get(i);
            if (VUtil.isJson(pathLine)) {
                listAnonym.add(pathLine);
            }
        }


        for (Path pathData : listNode) {
            NodeHead data = VUtil.readJson(pathData, NodeHead.class);
            DataNode res = new DataNode();
            res.idn = data.idn;
            res.host = host;

            NodeTreeElem elem1 = tree.byIdn(data.idn);
            if (elem1 == null) {
                VUtil.error("TrackerData::getData::1", host, data.idn, pathData.toAbsolutePath());
                continue;
            }

            NodeTreeElem elem2 = tree.byIdn(elem1.idp);
            if (elem2 == null) {
                VUtil.error("TrackerData::getData::2", host, data.idn, elem1.idp);
                continue;
            }

            res.user = userName(host, data.idu);
            res.folder = elem2.text;
            res.text = elem1.text;
            res.date = data.date.get(1);
            result.node.add(res);
        }

        for (Path pathData : listAttach) {

            int[] param = getAttachParam(pathData.getFileName().toString());

            NodeAttach data = VUtil.readJson(pathData, NodeAttach.class);
            DataAttach res = new DataAttach();
            res.flagAnonymous = false;
            res.host = host;
            res.idn = param[0];
            res.idf = param[1];
            res.user = userName(host, data.idu);
            res.folder = tree.byIdn(tree.byIdn(res.idn).idp).text;
            res.text = tree.byIdn(res.idn).text;
            res.path = tree.getUrl(res.idn);
            res.flagComment = VUtil.isTrue(data.flagComment);
            res.flagCatalog = VUtil.isTrue(data.flagCatalog);
            res.flagNode = VUtil.isTrue(data.flagNode);
            res.date = data.date;
            res.content = data.content;
            res.src = data.src;
            result.attach.add(res);
        }

        for (Path pathData : listAnonym) {
            TrackerAnonym data = VUtil.readJson(pathData, TrackerAnonym.class);

            NodeTreeElem nodeTreeElem1 = tree.byIdn(data.idn);
            if (nodeTreeElem1 == null) {
                VUtil.error("TrackerData::getData::3[removed]", host, data.idn, pathData.toString());
                VUtil.pathRemove(pathData.toString());
                continue;
            }
            NodeTreeElem nodeTreeElem2 = tree.byIdn(nodeTreeElem1.idp);
            if (nodeTreeElem2 == null) {
                VUtil.error("TrackerData::getData::4", host, data.idn, nodeTreeElem1.idp);
                continue;
            }

            DataAttach res = new DataAttach();
            res.flagAnonymous = true;
            res.host = host;
            res.idn = data.idn;
            res.anonym = pathData.getFileName().toString().split("\\.")[0];
            res.folder = nodeTreeElem2.text;
            res.text = nodeTreeElem1.text;
            res.path = tree.getUrl(data.idn);
            res.date = data.date;
            res.ip = data.ip;
            res.browser = data.browser;
            res.content = data.content;
            res.user = data.name;
            res.src = data.src;
            //res.key = data.key;
            result.attach.add(res);
        }
    }

    public class DataNode {
        int idn;
        String host;
        String user;
        String folder;
        String text;
        String date;
    }

    public class DataAttach {
        int idn;
        int idf;
        String host;
        String user;
        String folder;
        String text;
        String path;
        boolean flagComment;
        boolean flagCatalog;
        boolean flagNode;
        String date;
        String content;
        String src;

        boolean flagAnonymous;
        String anonym;
        String ip;
        String browser;
    }


    public class DataGet {
        ArrayList<DataNode> node = new ArrayList<>();
        ArrayList<DataAttach> attach = new ArrayList<>();
        //ArrayList<DataAnonym> anonym = new ArrayList<>();
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
