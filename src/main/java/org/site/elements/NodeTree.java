package org.site.elements;

import org.jetbrains.annotations.Nullable;
import org.site.view.VUtil;

import java.util.*;

public class NodeTree {

    static HashMap<String, NodeTree> treeByHost = new HashMap<>();

    private Map<Integer, NodeTreeElem> tree;
    private Map<String, NodeTreeElem> treeByUrl = new HashMap<>();
    private String host;
    private HashMap<Integer, String> urls = new HashMap<>();

    public static NodeTree load(String host) {
        NodeTree tree = treeByHost.get(host);
        if (tree == null) {
            tree = new NodeTree(host);
            treeByHost.put(host, tree);
        }
        return tree;
    }

    public NodeTree(String host) {
        this.host = host;
        init();
    }

    public void put(NodeTreeElem elem) {
        tree.put(elem.idn, elem);
        afterInit();
    }

    public void remove(int ind) {
        tree.remove(ind);
        afterInit();
    }

    public int size() {
        return tree.size();
    }

    public List<Integer> children(int idp) {
        List<Integer> list = new ArrayList<>();
        NodeTreeElem elem = byIdn(byIdn(idp).first);
        while (elem.next > 0) {
            list.add(elem.idn);
            elem = byIdn(elem.next);
        }
        list.add(elem.idn);
        return list;
    }

    public String getHost() {
        return host;
    }

    public Collection<NodeTreeElem> entry() {
        return tree.values();
    }

    private void init() {
        PostgreSQL sql = new PostgreSQL(host);
        sql.connect();
        tree = sql.getTree();
        sql.close();
        afterInit();
    }

    private void afterInit() {
        treeByUrl = new HashMap<>();
        urls = new HashMap<>();
        for (NodeTreeElem elem : tree.values()) {
            String path = elem.path;
            if (elem.idp == 0) {
                path = elem.path.isEmpty() ? "/" : elem.path;
                treeByUrl.put(path, elem);
            } else if (elem.path.isEmpty()) {
                continue;
            }
            treeByUrl.put(elem.idp + ":" + path, elem);
        }
    }

    public void reset() {
        afterInit();
    }

    public NodeTreeElem byIdn(int idn) {
        return tree.get(idn);
    }

    @Nullable
    public NodeTreeElem byUrl(String url) {
        //if (url.equals("/")) url = "";
        NodeTreeElem res = treeByUrl.get(url);
        if (res == null) {
            String[] list = url.split("/");
            int idp = 0;
            NodeTreeElem elem = null;
            for (int i = 1; i < list.length; i++) {
                elem = treeByUrl.get(idp + ":" + VUtil.decodeURI(list[i]));
                if (elem == null) break;
                else idp = elem.idn;
            }
            if (elem != null) {
                res = elem;
                treeByUrl.put(url, elem);
            }
        }
        return res;
    }

    public String getUrl(int idn) {
        String url = urls.get(idn);
        if (url == null) {
            ArrayList<String> list = new ArrayList<>();
            NodeTreeElem elem = byIdn(idn);
            if (elem == null) {
                VUtil.error("NodeTree::getUrl idn==null", idn);
            } else {
                if (elem.idn == 0) {
                    url = "/";
                } else {
                    if (elem.isFolder()) list.add(0, "");
                    list.add(0, VUtil.encodeURI(elem.path));
                    while (elem.idp > 0) {
                        elem = byIdn(elem.idp);
                        list.add(0, VUtil.encodeURI(elem.path));
                    }
                    list.add(0, "");
                    url = String.join("/", list);
                }
                urls.put(idn, url);
            }
        }
        return url;
    }

}
