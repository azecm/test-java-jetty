package org.site.view;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.site.elements.Announce;
import org.site.elements.NodeData;
import org.site.ini.SiteProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class AppJSON {

    final JSONDoc doc = new JSONDoc();
    private final static String _typeText = "#text";
    //private final static Pattern reClassRemove = Pattern.compile("\\s*img[lr]\\s*");
    private final static List<String> disabledAttr = Arrays.asList(_typeText, "alt", "data-like");
    private NodeData node;
    private boolean inited = false;
    private ViewSite site;

    AppJSON(ViewSite site) {
        this.site = site;
        this.node = site.page.node;
        init();
    }

    private void init() {
        SiteProperty.PageType pageType = site.page.param.type;
        if (pageType == SiteProperty.PageType.outside) return;
        if (node == null || node.head == null || node.head.title == null || node.content == null) return;
        doc.title = node.head.title;
        switch (pageType) {
            case root:
                initRoot();
                break;
            case article:
                initArticle();
                break;
        }
        inited = true;
    }

    private void initArticle() {
        Document docHtml = Jsoup.parseBodyFragment(node.content);
        next(docHtml.body(), doc.content);
    }

    private void initRoot() {
        List<Integer> orderedIdnList = site.getOrderedIdnList();

        for (int i = 0; i < 20; i++) {
            int idn = orderedIdnList.get(i);
            Announce data = site.page.getAnnounce(idn);

            JSONElem elem = new JSONElem();
            doc.content.add(elem);
            elem.type = "announce";
            elem.text = data.title[1];
            elem.addAttr("src", VUtil.imageSize600(data.img.src));
            elem.addAttr("href", idn + "");
        }
    }

    void save() {
        if (!inited) return;

        String path = null;

        switch (site.page.param.type) {
            case root:
                path = VUtil.DIRFile + site.host + "/json/app/page/" + site.page.param.pageNumber + VUtil.endsJSON;
                break;
            case article:
                path = VUtil.DIRFile + site.host + "/json/app/node/" + VUtil.getFolder(node.head.idn);
                break;
        }

        if (path != null) {
            VUtil.createDirs(path);
            VUtil.writeJson(path, doc);
        }

    }

    private void next(Node elParent, JSONElem elJSONParent) {
        int max = elParent.childNodes().size();
        int i = 0;
        for (Node el : elParent.childNodes()) {
            String nodeName = el.nodeName();
            if (++i == max && nodeName.equals("br")) continue;

            String className = el.attr("class");
            if (VUtil.notEmpty(className)) {
                List<String> classList = Arrays.asList(className.split(" "));
                if (classList.contains("shop")) {
                    continue;
                } else if (classList.contains("catalog")) {
                    continue;
                }
            }

            JSONElem elJSON = new JSONElem(nodeName, el.attributes());
            elJSONParent.add(elJSON);

            if (el.childNodes().size() > 0) {
                next(el, elJSON);
            } else {
                if (nodeName.equals(_typeText)) {
                    elJSON.text = el.outerHtml();
                }
            }
        }
    }

    public static class JSONDoc {
        String title;
        JSONElem content = new JSONElem();
    }

    public static class JSONElem {
        String type;
        String text;
        List<JSONElem> children;
        List<JSONElemAttr> attrs;

        JSONElem() {
        }

        JSONElem(String _type, Attributes _attrs) {
            type = _type;
            if (_attrs.size() > 0) {
                List<JSONElemAttr> __attrs = new ArrayList<>();
                for (Attribute attr : _attrs) {
                    String key = attr.getKey();
                    String value = attr.getValue();
                    if (!disabledAttr.contains(key)) {
                        boolean flag = true;
                        if (key.equals("class")) {
                            value = value.replaceAll("\\s*img[lr]\\s*", "");
                            flag = !VUtil.isEmpty(value);
                        }
                        if (flag) {
                            __attrs.add(new JSONElemAttr(key, value));
                        }
                    }
                }
                if (__attrs.size() > 0) attrs = __attrs;
            }
        }

        JSONElem add(JSONElem el) {
            if (children == null) children = new ArrayList<>();
            children.add(el);
            return this;
        }

        JSONElem addAttr(String key, String value) {
            if (attrs == null) attrs = new ArrayList<>();
            attrs.add(new JSONElemAttr(key, value));
            return this;
        }
    }

    public static class JSONElemAttr {
        String name;
        String text;

        JSONElemAttr(String _name, String _text) {
            name = _name;
            text = _text;
        }
    }
}
