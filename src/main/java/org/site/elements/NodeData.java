package org.site.elements;

import com.google.gson.annotations.JsonAdapter;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.site.server.site.ImageSite;
import org.site.view.VUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class NodeData {
    public class Order {
        @JsonAdapter(JsonIgnoreIntZero.class)
        public int next;
        @JsonAdapter(JsonIgnoreIntZero.class)
        public int first;
        @JsonAdapter(JsonIgnoreIntZero.class)
        public int prev;
        @JsonAdapter(JsonIgnoreIntZero.class)
        public int last;
    }

    @Nullable
    public String descr;
    @Nullable
    public String content;
    public Order order;
    public NodeHead head;
    public List<NodeAttach> attach;

    private transient HashMap<String, NodeAttach> attachSrc;

    public static String linkText() {
        return "новая статья";
    }

    public static String linkPath() {
        return VUtil.dateToJSON(VUtil.nowGMT()).substring(0, 23).replaceAll("[:T.]", "-");
    }

    public static void positionDelete(String host, int idn) {
        NodeTree tree = NodeTree.load(host);
        NodeTreeElem elem = tree.byIdn(idn);
        int next = elem.next;
        int prev = elem.prev;
        if (prev > 0) new NodeUpdate(host, prev).next(next).exec();
        if (next > 0) new NodeUpdate(host, next).prev(prev).exec();

        int idp = elem.idp;
        NodeTreeElem elemParent = tree.byIdn(idp);
        if (elemParent.first == idn) new NodeUpdate(host, idp).first(next).exec();
        if (elemParent.last == idn) new NodeUpdate(host, idp).last(prev).exec();
    }

    public static void positionAppendTo(String host, int idn, int idp) {
        NodeTree tree = NodeTree.load(host);
        int last = tree.byIdn(idp).last;
        new NodeUpdate(host, idn).idp(idp).prev(last).next(0).exec();
        new NodeUpdate(host, last).next(idn).exec();
        new NodeUpdate(host, idp).last(idn).exec();
    }

    public NodeData() {
        head = new NodeHead();
        head.date = new ArrayList<>();
        head.link = new ArrayList<>();
        head.labels = new ArrayList<>();
        head.keywords = new ArrayList<>();

        String date = VUtil.dateToJSON(VUtil.nowGMT());
        head.date.add(date);
        head.date.add(date);

        head.link.add("");
        head.link.add("");

        order = new Order();

        attach = new ArrayList<>();
    }

    public static boolean delete(String host, int idn) {
        NodeData node = VUtil.readNode(host, idn);
        boolean result = node.order.first == 0;

        if (result) {
            node.attach.stream().forEach(a -> node.attachRemove(host, a.idf));
            positionDelete(host, idn);
            VUtil.pathRemove(VUtil.getNodePath(host, idn));

            PostgreSQL db = new PostgreSQL(host).connect();
            db.delete("tree").where("idn", idn).exec();
            db.close();

            NodeTree tree = NodeTree.load(host);
            tree.remove(idn);
            //tree.reset();
        }

        return result;
    }

    public NodeData appendTo(String host, int idp, int idu) {
        NodeTree tree = NodeTree.load(host);

        int idn = head.idn = tree.entry().stream().map(e -> e.idn).max(Comparator.comparingInt(a -> a)).get() + 1;
        tree.put(new NodeTreeElem(idn).text("").path(""));

        String linkText = linkText();
        String linkPath = linkPath();
        ZonedDateTime date = VUtil.nowGMT();

        PostgreSQL db = new PostgreSQL(host).connect();
        db.insert("tree").cols("idn").set(idn).exec();
        db.close();

        content = descr = "<p><br></p>";
        head.title = "";
        head.date.set(1, VUtil.dateToJSON(date));
        new NodeUpdate(host, this).idu(idu).dateAdd(VUtil.dateToJSON(date)).text(linkText).path(linkPath).exec();

        positionAppendTo(host, idn, idp);

        tree.reset();

        return VUtil.readNode(host, idn);
    }

    public Document getContent() {
        return Jsoup.parse(content == null ? "" : content);
    }

    public void setContent(Document doc) {
        content = VUtil.html(doc);
    }

    public Document getDescription() {
        return Jsoup.parse(descr == null ? "" : descr);
    }

    public void setDescription(Document doc) {
        descr = VUtil.html(doc);
    }

    public boolean isFolder() {
        //return order.first != 0;
        return head.flagFolder;
    }

    @Nullable
    public NodeAttach attachByKey(int key) {
        return attachByKey(key + "");
    }

    @Nullable
    public NodeAttach attachByKey(String key) {
        if (attachSrc == null) {
            attachSrc = new HashMap<>();
            for (NodeAttach at : attach) {
                if (at.src != null) attachSrc.put(at.src, at);
                attachSrc.put(at.idf + "", at);
            }
        }
        return attachSrc.get(key);
    }

    public void attachRemove(String host, int idf) {
        NodeAttach attachElem = attachByKey(idf);
        if (attachElem != null && attachElem.src != null) {
            VUtil.pathToImageCache(host, attachElem.src).forEach(l -> VUtil.pathRemove(l.path));
            VUtil.pathRemove(VUtil.pathToImageSrc(host, attachElem.src));
        }
        attach = attach.stream().filter(a -> a.idf != idf).collect(Collectors.toList());
    }

    public NodeAttach attachNew(int idu) {
        NodeAttach attachData = new NodeAttach();
        int idf = 0;
        for (NodeAttach at : attach) {
            idf = Math.max(at.idf, idf);
        }
        attachData.idf = idf + 1;
        if (idu > 0) attachData.idu = idu;
        attachData.setDate(VUtil.nowGMT());
        attach.add(attachData);
        return attachData;
    }

    public void attachSetSrc(String host, String pathSrc, NodeAttach attach) {

        Pattern reDgt = Pattern.compile("(\\d+)");
        Matcher m;
        String folderStr, fileStr;
        String pathDir = VUtil.DIRDomain + host + "/file/";
        try {
            int folderKey = 0, fileKey = 0;
            for (Path p : Files.list(Paths.get(pathDir)).collect(Collectors.toList())) {
                folderKey = Math.max(VUtil.getInt(p.getFileName().toString()), folderKey);
            }

            if (folderKey > 0) {
                folderStr = ("000" + folderKey);
                folderStr = folderStr.substring(folderStr.length() - 4);
                for (Path p : Files.list(Paths.get(pathDir + folderStr)).collect(Collectors.toList())) {
                    m = reDgt.matcher(p.getFileName().toString());
                    if (m.find()) {
                        fileKey = Math.max(VUtil.getInt(m.group(0)), fileKey);
                    }
                }
            }

            boolean flagNewFolder = false;
            if (folderKey == 0) {
                flagNewFolder = true;
                folderKey = fileKey = 1;
            } else if (fileKey == 9999) {
                flagNewFolder = true;
                folderKey++;
                fileKey = 1;
            } else {
                fileKey++;
            }

            folderStr = "000" + folderKey;
            folderStr = folderStr.substring(folderStr.length() - 4);

            fileStr = "000" + fileKey;
            fileStr = fileStr.substring(fileStr.length() - 4) + "." + VUtil.getFileExt(pathSrc);

            folderStr = folderStr + "/";
            if (flagNewFolder) {
                VUtil.createDirs(pathDir + folderStr);
            }

            String pathFull = pathDir + folderStr + fileStr;
            attach.src = folderStr + fileStr;
            Files.move(Paths.get(pathSrc), Paths.get(pathFull));

            if (ImageSite.isBitmap(pathFull)) {
                ImageSite img = new ImageSite().load(pathFull);

                attach.w = img.image.getWidth();
                attach.h = img.image.getHeight();

                VUtil.pathToImageCache(host, attach.src).forEach(item -> {
                    img.resize(item.size).save(item.path);
                });
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
