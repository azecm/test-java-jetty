package org.site.elements;

import org.site.view.VUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class NodeTreeDB {
    public static String tableName = "tree";
    static Cols col = new Cols();
    String host;
    PostgreSQL db;
    VUtil.Timer t;
    FileOutputStream fos;

    //new NodeTreeDB(host).create().insert().toCVS().finish();
    //new NodeTreeDB(host).genCSV().fromCVS().finish();

    public NodeTreeDB(String host) {
        t = new VUtil.Timer();
        this.host = host;
        db = new PostgreSQL(host).connect();
    }

    public NodeTreeDB create() {
        db.dropTable(tableName);
        db.createTable(tableName)
                .col(col.idn).integer().primaryKey().notNull()
                .col(col.idp).integer().notNull().defaultVal(0)
                .col(col.idu).integer().notNull().defaultVal(0)
                .col(col.dateAdd).integer().notNull().defaultVal(0)
                .col(col.next).integer().notNull().defaultVal(0)
                .col(col.prev).integer().notNull().defaultVal(0)
                .col(col.first).integer().notNull().defaultVal(0)
                .col(col.last).integer().notNull().defaultVal(0)
                .col(col.text).varchar(100).notNull().defaultVal("")
                .col(col.path).varchar(70).notNull().defaultVal("")
                .col(col.flagFolder).bool().notNull().defaultVal(false)
                .col(col.flagValid).bool().notNull().defaultVal(false)
                .col(col.flagBlock).bool().notNull().defaultVal(false)
                .col(col.commentAll).integer().notNull().defaultVal(0)
                .col(col.commentLast).integer().notNull().defaultVal(0)
                .exec();
        return this;
    }

    String tempFileName() {
        return VUtil.DIRLog + tableName + "-" + host + ".csv";
    }

    public NodeTreeDB fromCVS() {
        create();
        db.copyfromCSV(tableName, tempFileName());
        return this;
    }

    public NodeTreeDB toCVS() {
        db.copyToCSV(tableName, tempFileName());
        return this;
    }

    @SuppressWarnings("Duplicates")
    public NodeTreeDB genCSV() {
        try {
            VUtil.pathRemove(tempFileName());
            fos = new FileOutputStream(new File(tempFileName()), true);
            fos.write((String.join(";", Arrays.asList(
                    col.idn, col.idp, col.idu, col.dateAdd,
                    col.next, col.prev, col.first, col.last,
                    col.text, col.path,
                    col.flagFolder, col.flagValid, col.flagBlock,
                    col.commentAll, col.commentLast)) + "\n").getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }

        VUtil.readNodes(host, (node) -> {
            NodeHead h = node.head;

            int idn = h.idn, idp = 0, idu = 0, dateAdd = 0;
            int next = 0, prev = 0, first = 0, last = 0;
            String text = "", path = "";
            boolean flagFolder = false, flagValid = false, flagBlock = false;
            int commentAll = 0, commentLast = 0;

            if (idn > 0) {
                idp = h.idp;
                idu = h.idu;
                flagFolder = h.flagFolder;
                flagValid = h.flagValid;
                flagBlock = h.flagBlock;
                if (h.date != null) dateAdd = VUtil.dateToDB(VUtil.dateFromJSON(h.date.get(0)));
                if (h.link != null) {
                    text = h.link.get(0);
                    path = h.link.get(1);
                }
            }

            if (text.contains("\"") || text.contains(";")) {
                text = "\"" + text.replaceAll("\"", "\"\"") + "\"";
            }
            if (text.length() == 0) text = "\"\"";
            if (path.length() == 0) path = "\"\"";

            NodeData.Order order = node.order;
            if (order != null) {
                next = order.next;
                prev = order.prev;
                first = order.first;
                last = order.last;
            }

            commentAll = commentAll(node);

            StringBuilder sb = new StringBuilder();
            sb.append(idn).append(";").append(idp).append(";").append(idu).append(";").append(dateAdd).append(";");
            sb.append(next).append(";").append(prev).append(";").append(first).append(";").append(last).append(";");
            sb.append(text).append(";").append(path).append(";");
            sb.append(flagFolder).append(";").append(flagValid).append(";").append(flagBlock).append(";");
            sb.append(commentAll).append(";").append(commentLast).append("\n");

            try {
                fos.write(sb.toString().getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }

        });

        return this;
    }

    @SuppressWarnings("Duplicates")
    public NodeTreeDB insert() {

        VUtil.readNodes(host, (node) -> {
            NodeHead h = node.head;

            int idn = h.idn, idp = 0, idu = 0, dateAdd = 0;
            int next = 0, prev = 0, first = 0, last = 0;
            String text = "", path = "";
            boolean flagFolder = false, flagValid = false, flagBlock = false;
            int commentAll = 0, commentLast = 0;

            if (idn > 0) {
                idp = h.idp;
                idu = h.idu;
                flagFolder = h.flagFolder;
                flagValid = h.flagValid;
                flagBlock = h.flagBlock;
                if (h.date != null) dateAdd = VUtil.dateToDB(VUtil.dateFromJSON(h.date.get(0)));
                if (h.link != null) {
                    text = h.link.get(0);
                    path = h.link.get(1);
                }
            }

            NodeData.Order order = node.order;
            if (order != null) {
                next = order.next;
                prev = order.prev;
                first = order.first;
                last = order.last;
            }

            commentAll = commentAll(node);

            db.insert(tableName)
                    .cols(
                            col.idn, col.idp, col.idu, col.dateAdd,
                            col.next, col.prev, col.first, col.last,
                            col.text, col.path,
                            col.flagFolder, col.flagValid, col.flagBlock,
                            col.commentAll, col.commentLast
                    )
                    .values()
                    .set(idn).set(idp).set(idu).set(dateAdd)
                    .set(next).set(prev).set(first).set(last)
                    .set(text).set(path)
                    .set(flagFolder).set(flagValid).set(flagBlock)
                    .set(commentAll).set(commentLast)
                    .exec();

        });

        return this;
    }

    int commentAll(NodeData node) {
        int commentAll = 0;
        if (node.attach != null) {
            for (NodeAttach attach : node.attach) {
                if (VUtil.isTrue(attach.flagComment)) commentAll++;
            }
        }
        return commentAll;
    }


    @SuppressWarnings("Duplicates")
    public void finish() {
        db.close();
        try {
            if (fos != null) {
                fos.close();
                VUtil.pathRemove(tempFileName());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        t.end();
    }

    public static class Cols {
        String idn = "idn";
        String idp = "idp";
        String idu = "idu";
        String dateAdd = "dateAdd";
        String next = "next";
        String prev = "prev";
        String first = "first";
        String last = "last";
        String text = "text";
        String path = "path";
        String flagFolder = "flagFolder";
        String flagValid = "flagValid";
        String flagBlock = "flagBlock";
        String commentAll = "commentAll";
        String commentLast = "commentLast";
    }

}
