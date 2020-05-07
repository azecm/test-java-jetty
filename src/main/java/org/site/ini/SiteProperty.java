package org.site.ini;

import org.jsoup.nodes.Element;
import org.site.view.ViewPage;
import org.site.view.ViewSite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

public class SiteProperty {
    public enum Block {
        labels, breadcrumb, publisher, readalso, rating, contents
    }

    public enum PageType {
        root, folder, label, article, outside
    }

    public class Logo {
        public String src;
        public int width;
        public int height;

        Logo(String src, int width, int height) {
            this.src = src;
            this.width = width;
            this.height = height;
        }
    }

    public class Admin {
        public ArrayList<String> email = new ArrayList<>();
        public ArrayList<String> menu = new ArrayList<>();

        public Admin addEmail(String email) {
            this.email.add(email);
            return this;
        }

        public Admin addMenu(String menu) {
            this.menu.add(menu);
            return this;
        }
    }


    public String jsFile = "app.js";
    public boolean flag2020 = true;
    public boolean flag2019 = true;


    public int idnLabel = -1;
    public int blogStreamIdn = 0;
    public int blogStreamLevel = 0;
    public int blogPerPage = 0;
    public int maxReadAlso = 10;
    public int tracker = 0;


    public boolean flagPriceByKeywords = true;
    public boolean flagBodyURL = false;
    public boolean flagAutoUpdate = false;
    public boolean flagSite2016 = false;
    public boolean sapeUpdate = false;
    public boolean flagS = false;
    public boolean flagSSL = false;
    public boolean flagApp = false;

    public boolean flagNewsNotification = false;

    public boolean contentBody = false;
    public boolean contentCatalog = false;
    public boolean contentComment = false;

    public ArrayList<String> js = new ArrayList<>();

    public Logo logo;
    public Admin admin = new Admin().addEmail("d@t.r");

    public void onInit(ViewSite site) {
    }

    public void onFinish(ViewPage page) {
    }

    public void menuMain(ArrayList<ViewSite.MenuLine> menu) {
    }

    public void menuSection(ArrayList<ViewSite.MenuLine> menu) {
    }

    public ArrayList<Block> contentHeader(PageType type, boolean isRootParent) {
        return new ArrayList<>();
    }

    public ArrayList<Block> contentFooter(PageType type, boolean isRootParent) {
        return new ArrayList<>();
    }


    public void sidebar(Element elem) {
    }

    // ==========

    public void sidebarSape(Element elSidebar) {
        new Element("div").attr("id", "sapeR").appendTo(elSidebar);
    }


    public ArrayList<Block> contentHeaderDef(PageType type, boolean notRootParent) {
        ArrayList<Block> res = new ArrayList<>();
        boolean flag = notRootParent && (type == PageType.label || type == PageType.article);
        if (flag) {
            res.add(Block.publisher);
        }
        if (type != PageType.root) {
            res.add(Block.breadcrumb);
        }
        if (flag) {
            res.add(Block.labels);
        }
        return res;
    }

    public ArrayList<Block> contentFooterDef(PageType type, boolean notRootParent) {
        ArrayList<Block> res = new ArrayList<>();
        if (notRootParent && type == PageType.article) {
            res.add(Block.readalso);
        }
        return res;
    }

}
