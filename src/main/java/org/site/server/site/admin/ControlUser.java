package org.site.server.site.admin;

import org.site.elements.NodeTree;
import org.site.elements.NodeTreeElem;
import org.site.elements.PostgreSQL;
import org.site.elements.SiteUserData;
import org.site.ini.SiteProperty;
import org.site.server.Router;
import org.site.server.jsession.JSessionSite;
import org.site.view.VUtil;
import org.site.view.ViewSite;

import javax.servlet.GenericServlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.util.ArrayList;

public class ControlUser extends GenericServlet {
    JSessionSite session;
    SiteUserData user;

    public void service(ServletRequest req, ServletResponse res) {

        Object result = null;
        session = new JSessionSite(req);

        if (session.enabled() && session.isPost()) {
            DataLoad data = session.postJson(DataLoad.class);
            if (data.uri != null) {
                if (data.user != null) {
                    user = session.findUserByName(data.user);
                }
                if (user != null) {
                    switch (data.uri) {
                        case "user":
                            result = getUserData();
                            break;
                        case "user-save":
                            result = postUserData(data);
                            break;
                        case "list":
                            result = getUserList(data);
                            break;
                    }
                }
            }
        }

        if (result == null) {
            Router.json404(res);
        } else {
            Router.json200(result, res);
        }
    }

    String postUserData(DataLoad data) {
        if (session.user.idu == user.idu) {
            //VUtil.println(session.user.password, data.param);
            session.user.password = data.param;
            session.user.webpage = data.webpage;
            session.user.description = data.descr;
            VUtil.writeUser(session.host, session.user);
        }
        return "ok";
    }

    DataUser getUserData() {
        DataUser result = new DataUser();

        PostgreSQL db = new PostgreSQL(session.host).connect();
        ArrayList<SQLCount> res = db.select(SQLCount.class).fromTree()
                .and("idu", user.idu)
                .and("flagFolder", false)
                .and("flagBlock", false)
                .and("flagValid", true)
                .exec();
        db.close();

        if (res.size() == 1) result.all = res.get(0).COUNT;

        result.page = session.user.webpage == null ? "" : session.user.webpage;
        result.descr = session.user.description == null ? "" : session.user.description;
        result.add = session.user.dateAdd.substring(0, 10);
        result.last = session.user.dateLast.substring(0, 10);

        if (session.user.idu == user.idu) {
            result.data = new ArrayList<>();
            result.data.add(session.user.name);
            result.data.add(session.user.email.replace("@", "&&"));
            result.data.add(session.user.password);
        }

        return result;
    }

    DataList getUserList(DataLoad data) {
        int perPage = 30;
        int pageNum = data.pageNum >= 0 ? data.pageNum : 0;

        NodeTree tree = NodeTree.load(session.host);

        DataList result = new DataList();
        ArrayList<DataListElem> list = result.list;

        PostgreSQL db = new PostgreSQL(session.host).connect();

        PostgreSQL.Select<SQLList> select1 = db.select(SQLList.class).fromTree().and("idu", user.idu).andMore("idp", 0).and("flagFolder", false);
        if (session.user.idu != user.idu) {
            select1.and("flagBlock", false).and("flagValid", true);
        }
        ArrayList<SQLList> res1 = select1.limit(perPage).offset(pageNum * perPage).order("dateAdd", false).exec();

        PostgreSQL.Select<SQLCount> select0 = db.select(SQLCount.class).fromTree().and("idu", user.idu).andMore("idp", 0).and("flagFolder", false);
        if (session.user.idu != user.idu) {
            select0.and("flagBlock", false).and("flagValid", true);
        }
        ArrayList<SQLCount> res0 = select0.exec();
        db.close();

        result.all = (int) Math.ceil(res0.get(0).COUNT / (double) perPage);
        result.num = data.pageNum;
        if (result.num == 0 && session.user.idu == user.idu) {
            result.folders = listFolderAdd();
        }

        //VUtil.println("ControlUser::getUserList res1", res1.size());
        for (SQLList line : res1) {
            if (line != null) {
                list.add(new DataListElem(session.user.idu == user.idu, line, tree));
            } else {
                VUtil.println("ControlUser::getUserList line==null", res1);
            }
        }

        return result;
    }

    ArrayList<DataFolderElem> listFolderAdd() {
        NodeTree tree = NodeTree.load(session.host);
        SiteProperty ini = ViewSite.getIni(session.host);

        ArrayList<DataFolderElem> folders = new ArrayList<>();
        int idp = ini.blogStreamIdn;
        boolean flagInner = ini.blogStreamLevel == 2;// || ini.flagWithJoint;

        int idNext = tree.byIdn(idp).first;
        int idNext2;
        NodeTreeElem elem, elem2;
        while (idNext > 0) {
            elem = tree.byIdn(idNext);
            if (elem.first > 0 && idNext != ini.idnLabel) {
                folders.add(new DataFolderElem(idNext, elem.text));
                if (flagInner && elem.first > 0) {
                    idNext2 = elem.first;
                    while (idNext2 > 0) {
                        elem2 = tree.byIdn(idNext2);
                        if (elem2.first > 0) {
                            folders.add(new DataFolderElem(idNext2, "\u2014 " + elem2.text));
                        }
                        idNext2 = elem2.next;
                    }
                }
            }
            idNext = elem.next;
        }
        return folders;
    }

    // ============

    public static class DataFolderElem {
        int value;
        String text;

        DataFolderElem(int value, String text) {
            this.value = value;
            this.text = text;
        }
    }

    public static class DataListElem {
        int idn;
        String parent;
        String url;
        String text;
        int commentAll;
        int commentLast;

        DataListElem(boolean flag, SQLList data, NodeTree tree) {
            text = data.text;

            NodeTreeElem elem = tree.byIdn(data.idn);
            if (elem != null) {
                NodeTreeElem elem2 = tree.byIdn(elem.idp);
                if (elem2 != null) {
                    parent = elem2.text;
                } else {
                    VUtil.println("ControllUser::DataListElem::2", VUtil.jsonString(data));
                }
            } else {
                VUtil.println("ControllUser::DataListElem::1", VUtil.jsonString(data));
            }
            //parent = tree.byIdn(tree.byIdn(data.idn).idp).text;
            boolean flagActive = data.flagValid && !data.flagBlock;
            if (flagActive) this.url = tree.getUrl(data.idn);
            if (flag) idn = data.idn;

            this.commentAll = data.commentAll;
            this.commentLast = data.commentLast;
        }
    }

    public static class DataList {
        int all;
        int num;
        ArrayList<DataFolderElem> folders;
        ArrayList<DataListElem> list = new ArrayList<>();
    }

    public static class DataUser {
        int all;
        String add;
        String last;
        String page;
        String descr;
        ArrayList<String> data;
    }

    public static class SQLCount {
        public int COUNT;
    }

    public static class SQLList {
        public int idn;
        public String path;
        public String text;
        public int commentAll;
        public int commentLast;
        public boolean flagValid;
        public boolean flagBlock;
    }

    public static class DataLoad {
        String user;
        String uri;

        String webpage;
        String descr;
        String param;

        int pageNum;
    }
}
