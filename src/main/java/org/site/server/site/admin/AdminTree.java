package org.site.server.site.admin;

import org.site.elements.NodeData;
import org.site.elements.NodeTree;
import org.site.elements.NodeTreeElem;
import org.site.elements.NodeUpdate;
import org.site.server.Router;
import org.site.server.jsession.JSessionSite;
import org.site.view.VUtil;

import javax.servlet.GenericServlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class AdminTree extends GenericServlet {
    JSessionSite session;

    public void service(ServletRequest req, ServletResponse res) {
        session = new JSessionSite(req);

        if (!session.isAdmin()) {
            Router.json404(res);
            return;
        }

        if (session.isGet()) {
            Router.json200(this.getData(req), res);
        } else if (session.isPost()) {
            DataPost data = session.postJson(DataPost.class);
            Object result = null;
            switch (data.route) {
                case "add":
                    result = postAdd(data);
                    break;
                case "edit":
                    result = postEdit(data);
                    break;
                case "delete":
                    result = postDelete(data);
                    break;
                case "param-get":
                    result = postToolStart(data);
                    break;
                case "param-post":
                    result = postToolEnd(data);
                    break;
            }

            if (result == null) Router.json404(res);
            else Router.json200(result, res);
        } else {
            Router.json404(res);
        }
    }

    DataTree getData(ServletRequest req) {
        int perPage = 100;

        DataTree result = new DataTree();
        HashMap<String, String> param = Router.queryMap(req);
        int idp = VUtil.getInt(param.getOrDefault("idp", "0"));
        int page = VUtil.getInt(param.getOrDefault("page", "1"));

        NodeTree tree = session.loadTree();
        NodeTreeElem elem = tree.byIdn(idp);
        if (elem != null && elem.isFolder()) {
            int count = 0;
            int c1 = perPage * (page - 1), c2 = perPage * page + 1;
            for (Integer idn : tree.children(idp)) {
                if (c1 < ++count && count < c2) {
                    elem = tree.byIdn(idn);
                    result.items.add(new DataTreeItem(idn, elem.text, elem.path, elem.isFolder()));
                }
            }

            result.page = page;
            result.pages = (int) Math.ceil(count / (double) perPage);

            int idnT = idp;
            while (idnT > 0) {
                elem = tree.byIdn(idnT);
                result.parents.add(0, new DataTreeParent(idnT, elem.path));
                idnT = elem.idp;
            }
            result.parents.add(0, new DataTreeParent(0, session.host));

        }

        return result;
    }

    DataTreeItem postAdd(DataPost data) {
        NodeData node = new NodeData().appendTo(session.host, data.idp, session.user.idu);
        DataTreeItem result = new DataTreeItem(node.head.idn, node.head.link.get(0), node.head.link.get(1), false);
        return result;
    }

    String postEdit(DataPost data) {
        new NodeUpdate(session.host, data.idn).text(data.label).path(data.path).exec();
        return "ok";
    }

    String postDelete(DataPost data) {
        return NodeData.delete(data.host, data.idn) ? "ok" : "";
    }

    int getOrder(NodeTree tree, int idn) {
        int order = 0;
        NodeTreeElem elem = tree.byIdn(tree.byIdn(tree.byIdn(idn).idp).first);
        while (elem.next > 0) {
            if (elem.idn == idn) break;
            order++;
            elem = tree.byIdn(elem.next);
        }
        return order;
    }

    DataToolGet postToolStart(DataPost data) {
        DataToolGet res = new DataToolGet();

        NodeTree tree = session.loadTree();
        NodeData node = VUtil.readNode(session.host, data.idn);

        res.flagFolder = node.head.flagFolder;
        res.flagValid = node.head.flagValid;
        res.flagBlock = node.head.flagBlock;
        res.idu = node.head.idu;
        res.idp = node.head.idp;
        res.idn = node.head.idn;
        res.label = node.head.link.get(0);
        res.path = node.head.link.get(1);
        res.date = node.head.date.get(0);
        res.order = getOrder(tree, data.idn);
        res.orderMax = tree.children(node.head.idp).size() - 1;

        flatSiteTree(tree, res.tree, 0, 0);

        return res;
    }

    String postToolEnd(DataPost data) {

        NodeTree tree = session.loadTree();
        String host = session.host;

        NodeUpdate upd = new NodeUpdate(host, data.idn);

        NodeData node = upd.node;
        if (node.head.flagFolder != data.flagFolder) {
            upd.flagFolder(data.flagFolder);
        }
        if (node.head.flagBlock != data.flagBlock) {
            upd.flagBlock(data.flagBlock);
        }
        if (node.head.flagValid != data.flagValid) {
            upd.flagValid(data.flagValid);
        }
        if (node.head.idu != data.idu) {
            upd.idu(data.idu);
        }
        if (!node.head.date.get(0).equals(data.date)) {
            upd.dateAdd(data.date);
        }
        upd.exec();

        if (node.head.idp != data.idp) {
            NodeData.positionDelete(host, data.idn);
            NodeData.positionAppendTo(host, data.idn, data.idp);
            //NodeData.positionAppendTo(host, VUtil.readNode(host, data.idn));
        } else {
            int order = getOrder(tree, data.idn);
            List<Integer> list = tree.children(data.idp);
            if (order != data.order && data.order >= 0 && data.order < list.size()) {

                if (data.order == 0) {
                    int next = tree.byIdn(data.idp).first;
                    NodeData.positionDelete(host, data.idn);
                    new NodeUpdate(host, data.idn).idp(data.idp).next(next).prev(0).exec();
                    new NodeUpdate(host, next).prev(data.idn).exec();
                    new NodeUpdate(host, data.idp).first(data.idn).exec();
                } else if (data.order == list.size() - 1) {
                    int prev = tree.byIdn(data.idp).last;
                    NodeData.positionDelete(host, data.idn);
                    new NodeUpdate(host, data.idn).idp(data.idp).prev(prev).next(0).exec();
                    new NodeUpdate(host, prev).next(data.idn).exec();
                    new NodeUpdate(host, data.idp).last(data.idn).exec();
                } else {
                    list = list.stream().filter(v -> v != data.idn).collect(Collectors.toList());
                    int next = list.get(data.order);
                    int prev = tree.byIdn(next).prev;

                    NodeData.positionDelete(host, data.idn);
                    new NodeUpdate(host, data.idn).next(next).prev(prev).exec();
                    new NodeUpdate(host, next).prev(data.idn).exec();
                    new NodeUpdate(host, prev).next(data.idn).exec();
                }
            }
        }

        return "ok";
    }

    void flatSiteTree(NodeTree tree, ArrayList<DataToolTree> tooltree, int idp, int level) {

        NodeTreeElem elem = tree.byIdn(idp);
        String levelStr = String.join("", Collections.nCopies(level, "-"));
        DataToolTree d = new DataToolTree();
        d.idp = idp;
        d.text = levelStr + (idp == 0 ? session.host : elem.path);
        tooltree.add(d);

        if (elem.first > 0) {
            elem = tree.byIdn(elem.first);
            while (elem.next > 0) {
                if (elem.isFolder()) {
                    flatSiteTree(tree, tooltree, elem.idn, level + 1);
                }
                elem = tree.byIdn(elem.next);
            }
        }
    }

    // ============

    public class DataTreeItem {
        int idn;
        String label;
        String path;
        boolean isFolder;

        DataTreeItem(int idn, String label, String path, boolean isFolder) {
            this.idn = idn;
            this.label = label;
            this.path = path;
            this.isFolder = isFolder;
        }
    }

    public class DataTreeParent {
        int idp;
        String path;

        DataTreeParent(int idp, String path) {
            this.idp = idp;
            this.path = path;
        }
    }

    public class DataTree {
        int page;
        int pages;
        ArrayList<DataTreeParent> parents = new ArrayList<>();
        ArrayList<DataTreeItem> items = new ArrayList<>();
    }

    // ============

    public class DataAdd {
        int idn;
        String label;
        String path;
    }

    public class DataToolTree {
        int idp;
        String text;
    }

    public class DataToolGet {
        boolean flagFolder;
        boolean flagValid;
        boolean flagBlock;
        int idu;
        int order;
        int orderMax;
        int idp;
        int idn;
        String label;
        String path;
        String date;
        ArrayList<DataToolTree> tree = new ArrayList<>();
    }


    public class DataPost {
        int idn;
        int idp;
        int idu;
        int order;
        String label;
        String path;
        String date;
        String route;
        String host;

        boolean flagFolder;
        boolean flagBlock;
        boolean flagValid;
    }

}
