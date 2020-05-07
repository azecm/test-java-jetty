package org.site.elements;

import org.site.view.RAction;
import org.site.view.VUtil;
import redis.clients.jedis.Jedis;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NodeUpdate {

    private NodeTreeElem treeElem;
    public NodeData node;
    private String host;
    private HashMap<String, String> listRedis = new HashMap<>();
    private PostgreSQL db;
    private PostgreSQL.Update dbUpdate;
    private boolean flagPath = false;
    private boolean updated = false;


    public NodeUpdate(String host, int idn) {
        init(host, VUtil.readNode(host, idn));
    }

    public NodeUpdate(String host, NodeData n) {
        init(host, n);
    }

    private void init(String host, NodeData n) {
        this.host = host;
        node = n;
        //db = new PostgreSQL(host).viewSql().connect();
        db = new PostgreSQL(host).connect();
        dbUpdate = db.update("tree");
        treeElem = NodeTree.load(host).byIdn(n.head.idn);
    }

    public NodeUpdate idp(int v) {
        updated = true;
        node.head.idp = v;
        dbUpdate.set("idp", v);
        listRedis.put("idp", v + "");
        treeElem.idp = v;
        return this;
    }

    public NodeUpdate idu(int v) {
        updated = true;
        node.head.idu = v;
        dbUpdate.set("idu", v);
        treeElem.idu = v;
        return this;
    }

    public NodeUpdate flagFolder(boolean flag) {
        updated = true;
        node.head.flagFolder = flag;
        dbUpdate.set("flagFolder", flag);
        return this;
    }

    public NodeUpdate flagBlock(boolean flag) {
        updated = true;
        node.head.flagBlock = flag;
        dbUpdate.set("flagBlock", flag);
        return this;
    }

    public NodeUpdate flagValid(boolean flag) {
        updated = true;
        node.head.flagValid = flag;
        dbUpdate.set("flagValid", flag);
        return this;
    }

    public NodeUpdate dateAdd(String dateStr) {
        updated = true;
        node.head.date.set(0, dateStr);
        dbUpdate.set("dateAdd", VUtil.dateToDB(VUtil.dateFromJSON(dateStr)));
        return this;
    }

    public NodeUpdate text(String t) {
        updated = true;
        node.head.link.set(0, t);
        dbUpdate.set("text", t);
        listRedis.put("text", t);
        treeElem.text = t;
        return this;
    }

    public NodeUpdate path(String t) {
        updated = true;

        long next = 1;
        String testPath = t;

        Matcher m = Pattern.compile("(.*)-(\\d+)$").matcher(t);
        if (m.find()) {
            t = m.group(1);
            next = Long.parseLong(m.group(2));
        }
        NodeTree tree = NodeTree.load(host);
        boolean flagCunterUpdated = true;
        while (flagCunterUpdated) {
            flagCunterUpdated = false;
            for (int idn : tree.children(treeElem.idp)) {
                if (treeElem.idn != idn && tree.byIdn(idn).path.equals(testPath)) {
                    testPath = t + "-" + (++next);
                    flagCunterUpdated = true;
                    break;
                }
            }
        }
        t = testPath;

        node.head.link.set(1, t);
        dbUpdate.set("path", t);
        listRedis.put("path", t);
        treeElem.path = t;
        flagPath = true;
        return this;
    }

    public NodeUpdate last(int v) {
        updated = true;
        node.order.last = v;
        dbUpdate.set("last", v);
        treeElem.last = v;
        return this;
    }

    public NodeUpdate first(int v) {
        updated = true;
        node.order.first = v;
        dbUpdate.set("first", v);
        listRedis.put("first", v + "");
        treeElem.first = v;
        return this;
    }

    public NodeUpdate next(int v) {
        updated = true;
        node.order.next = v;
        dbUpdate.set("next", v);
        listRedis.put("next", v + "");
        treeElem.next = v;
        return this;
    }

    public NodeUpdate prev(int v) {
        updated = true;
        node.order.prev = v;
        dbUpdate.set("prev", v);
        treeElem.prev = v;
        return this;
    }

    public NodeUpdate exec() {
        if (updated) {
            VUtil.writeNode(host, node);

            dbUpdate.where("idn", node.head.idn).exec();
            db.close();

            if (listRedis.size() > 0) {
                // todo нужен ли Redis для tree
                Jedis jedis = RAction.redis();
                jedis.hmset(RAction.keyTree(host, node.head.idn), listRedis);
                jedis.disconnect();
            }

            if (flagPath) {
                NodeTree.load(host).reset();
            }
        } else {
            db.close();
        }
        return this;
    }
}
