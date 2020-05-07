package org.site.elements;

import org.jsoup.nodes.Element;
import org.site.view.VUtil;
import org.site.view.ViewSite;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Announce {
    public class Img {
        public String src;
        public int width;
        public int height;
    }

    public class Labels {
        public String path;
        public List<String[]> data;
    }

    public String[] section;
    public String[] title;
    public String body;
    public String add;
    public String last;
    public Img img;
    public String user;
    public int cat;
    public int com;
    public int views;
    public int comLast;
    public List<Integer> labelList;
    public Labels labels;


    public Announce fromNode(ViewSite site, int idn) {

        // количество просморов
        views = VUtil.getNodeViews(site.host, idn);

        NodeData node = VUtil.readNode(site.host, idn);

        // считаем количество элементов каталога и комментарии
        ZonedDateTime n = VUtil.nowGMT();
        for (NodeAttach a : node.attach) {
            if (VUtil.isTrue(a.flagCatalog)) cat++;
            if (VUtil.isTrue(a.flagComment)) {
                com++;
                long d = VUtil.dateFromJSON(a.date).until(n, ChronoUnit.HOURS);
                if (d < 30) {
                    comLast++;
                }
            }
        }

        // основная картинка
        Element elImg = node.getContent().getElementsByTag("img").first();
        if (elImg == null) {
            elImg = node.getDescription().getElementsByTag("img").first();
        }
        if (elImg != null) {
            int w = VUtil.getInt(elImg.attr("width"));
            int h = VUtil.getInt(elImg.attr("height"));
            if (w > 0 && h > 0) {
                img = new Img();
                img.src = VUtil.imageKey(elImg.attr("src"));
                img.width = w;
                img.height = h;
            }
        }

        labelList = new ArrayList<>();
        labelList.addAll(node.head.labels);

        labels = new Labels();
        if (site.ini.idnLabel > 0) {
            labels.path = "/" + site.tree.byIdn(site.ini.idnLabel).path + "/";
            labels.data = labelList.stream().map(i -> new String[]{site.tree.byIdn(i).path, site.tree.byIdn(i).text}).collect(Collectors.toList());
        }

        add = node.head.date.get(0);
        last = node.head.date.get(1);
        body = node.descr;

        user = site.getUser(node.head.idu);

        int sectionIdn = node.head.idp;
        while (!site.folderEnabled(sectionIdn) && sectionIdn > 0) {
            sectionIdn = site.tree.byIdn(sectionIdn).idp;
        }

        //title = new String[]{site.getUrlByIdn(idn), site.tree.byIdn(idn).text};
        title = new String[]{site.getUrlByIdn(idn), node.head.title};
        section = new String[]{site.getUrlByIdn(sectionIdn), site.tree.byIdn(sectionIdn).text};

        return this;
    }

}
