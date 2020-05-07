package org.site.view;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.reflect.TypeToken;
import org.jsoup.nodes.Element;
import org.site.elements.*;
import org.site.ini.SiteProperty;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PCommand {
    final Pattern reHost = Pattern.compile("(m|my|la|t)\\.ru(/.*)", Pattern.CASE_INSENSITIVE);
    ViewPage.PageParam pageParam;
    Element elem;
    NodeTree tree;
    NodeData node;
    ViewSite site;
    SiteProperty ini;
    String host;

    //int linkCounter = 0;

    final Pattern reDgt = Pattern.compile("^\\d+$");
    final Pattern reBool = Pattern.compile("^(true|false)$", Pattern.CASE_INSENSITIVE);

    // NodeTree tree, SiteProperty ini,
    PCommand(ViewSite site, NodeData node, ViewPage.PageParam pageParam, Element elem) {
        this.pageParam = pageParam;
        this.elem = elem;
        this.site = site;
        this.tree = site.tree;
        this.node = node;
        this.ini = site.ini;
        this.host = tree.getHost();

        String keyType = elem.attr("data-type");

        if (!keyType.isEmpty()) {
            switch (keyType) {
                case "price":
                    typePrice();
                    break;
                case "video":
                    video();
                    break;
                case "shop":
                    shop();
                    break;
            }
            return;
        }

        String text = elem.text().trim();
        Matcher m = Pattern.compile("^([a-z\\-]+)").matcher(text);
        if (!m.find()) return;
        switch (m.group(1)) {
            case "price-list":
                priceList();
                elem.remove();
                break;
            case "sitemap":
                sitemap();
                break;
            case "mailform":
                mailForm();
                break;
            case "add":
                addContentFn();
                break;
            case "funny":
                funny();
                break;
            default:
                elem.remove();
                break;
        }
    }

    static public <T> T getParam(String dataSrc, Class<T> classRef) {

        dataSrc = VUtil.textClean(dataSrc);

        Pattern p1 = Pattern.compile("([\\wа-яё\\-!(^][\\wа-яё\\s\\-|().?!^+*\\\\$\"<>]*[\\wа-яё)]*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

        String data = "{" + dataSrc.replaceAll("=", ":") + "}";
        data = p1.matcher(data).replaceAll("\"$1\"");
        //data = data.replaceAll(/([\wа-яё\-\!\(\^][\wа-яё\s\-\|\(\)\.\?\!\+\*\\\^\$\"\+\^<>]*[\wа-яё\)]*)/ig, ""$1"");

        data = data.replaceAll("\"(-?\\d+\\.?(\\d+)?|true|false)\"", "$1");
        data = data.replaceAll("<", "[").replaceAll(">", "]");

        return VUtil.jsonData(data, classRef);
    }

    TagBase tag(String name) {
        return new TagBase(name);
    }

    TagBase span() {
        return new TagBase("span");
    }

    public static PriceType priceParam(Element elem) {
        return VUtil.jsonData(elem.attr("data-param"), PriceType.class);
    }

    void shop() {
        ShopType data = VUtil.jsonData(elem.attr("data-param"), ShopType.class);
        //VUtil.println(elem.attr("data-param"));
        if (data != null) {
            Affiliate.Link alink;
            TagBase ul = tag("ul");
            TagBase a;

            for (ShopTypeItem item : data.list) {
                if (item.link.contains("oz")) continue;
                String text = item.text;
                String link = item.link;
                if (item.search) {
                    String hostKey = item.link.substring(0, item.link.indexOf("."));
                    alink = ViewSite.affiliate.links.get(hostKey);
                    //VUtil.println(alink, hostKey, item.link, data.phrase);
                    link = item.link + alink.search(data.phrase);
                    //a = tag("a").addClass("external").id("p" + (++linkCounter)).attr("data-link", link);
                } else {
                    String hostKey = ViewSite.getLinkHost(item.link);
                    alink = ViewSite.affiliate.links.get(hostKey);
                    //alink.getLink()
                    //a = tag("a").attr("href", "/goto/" + link);
                }
                if (text == null || text.isEmpty()) text = alink.label();

                ul.append(
                        tag("li").append(
                                tag("a").attr("href", "/goto/" + link).attr("is-shop", "true").text(text)
                        )
                );
            }

            elem.before(tag("div").addClass("shop").append(ul).elem);
            pageParam.css.add("shop");
        }
        elem.remove();
    }

    void typePrice() {

        final String priceClass = "view-price";
        //final int maxCount = 10;

        PriceType param = priceParam(elem);

        List<String> tags = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        tags.add(tree.byIdn(param.idn).text);
        if (param.keys != null && param.keys.size() > 0) {
            labels.add(param.keys.get(0));
            labels.addAll(param.keys);
        }


        List<PriceElem> dataOut = site.priceKeywords.insertGoodsNew(site.host, site.page.node.head.idn, labels, tags, node.head.flagBook);


        if (dataOut.size() > 0) {
            elem.before(tag("div")
                    .addClass(priceClass)
                    .addClass(VUtil.notEmpty(param.orient) ? priceClass + "-" + param.orient : null)
                    .addClass("unselect")
                    .append(
                            tag("script").attr("type", "html/template").attr("data-source", "2").text(VUtil.jsonString(dataOut))
                    ).elem);
        }
        pageParam.css.add("price");
        //}

        elem.remove();
    }


    void priceList() {

        class Table {
            String title;
            List<PriceElemReader> line = new ArrayList<>();

            Table(String title) {
                this.title = title;
            }
        }

        int idLink = 0;
        String tagText = elem.text().trim();
        Matcher m = Pattern.compile("price-list\\((.*)\\)").matcher(tagText);
        if (!m.find()) return;

        ParamPriceList param = getParam(m.group(1), ParamPriceList.class);
        if (param.keys == null) {
            param.keys = new ArrayList<>();
            param.other = 1;
        }


        Pattern reNot = param.not != null ? Pattern.compile(param.not, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE) : null;

        List<PriceElemReader> priceData;

        priceData = VUtil.readJson(PriceKeywords.getDir(host) + param.idn, TypeToken.getParameterized(ArrayList.class, PriceElemReader.class).getType());
        for (PriceElemReader row : priceData) {
            row.src = PriceKeywords.getImageSrc(row);
        }


        if (priceData == null) {
            VUtil.error("PCommand::priceList - not found price " + param.name);
            return;
        }


        List<Table> tables = new ArrayList<>();

        for (Object keys : param.keys) {
            String key, title;
            if (keys.getClass() == String.class) {
                title = key = (String) keys;
            } else {
                @SuppressWarnings("unchecked")
                ArrayList<String> keysList = (ArrayList<String>) keys;

                if (keysList.size() == 1) {
                    title = key = keysList.get(0);
                } else {
                    title = keysList.get(0);
                    key = keysList.get(1);
                }
            }

            Table tableLine = new Table(title);
            Pattern reSearch = Pattern.compile(key, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            for (PriceElemReader line : priceData) {

                if (reNot != null && reNot.matcher(line.name).find() || param.low > 0 && line.price < param.low || param.high > 0 && line.price > param.high) {
                    line.used = true;
                    continue;
                }
                if (reSearch.matcher(line.name).find()) {
                    tableLine.line.add(line);
                    line.used = true;
                }
            }
            if (tableLine.line.size() > 0) {
                tableLine.line.sort(Comparator.comparingInt(a -> a.price));
                tables.add(tableLine);
            }
        }

        tables.sort(Comparator.comparing(a -> a.title));

        if (param.other > 0) {
            Table tableLine = new Table("Разное");
            for (PriceElemReader line : priceData) {
                if (!line.used) {
                    if (reNot != null && reNot.matcher(line.name).find() || param.low > 0 && line.price < param.low || param.high > 0 && line.price > param.high) {
                    } else {
                        tableLine.line.add(line);
                    }
                }
            }
            if (tableLine.line.size() > 0) {
                tableLine.line.sort(Comparator.comparingInt(a -> a.price));
                tables.add(tableLine);
            }
        }


        TagBase div = tag("div").addClass("price-list unselect");
        for (Table table : tables) {
            if (tables.size() > 1) {
                tag("h2").append(table.title).appendTo(div);
            }
            TagBase ul = tag("ul").addClass("price-list__items").appendTo(div);
            for (PriceElemReader line : table.line) {
                Matcher m2 = reHost.matcher(line.link);
                if (!m2.find()) continue;
                String link = (m2.group(1) + m2.group(2)).replaceAll("/", " ");

                tag("li").addClass("item").addClass("price-list__item").append(
                        tag("a").id("pl-" + (++idLink)).data("link", link).append(
                                span().addClass("image").append(new TagCanvas().dataSrc("/file/view" + line.src).width(150).height(150)),
                                span().addClass("name").data("text", m2.group(1)),
                                span().addClass("price").text(line.price),
                                span().addClass("text").text(line.name)
                        )
                ).appendTo(ul);
            }
        }

        //price-list__item
        //VUtil.println(div.elem.getElementsByClass("price-list__item").size());


        pageParam.css.add("price-list");
        pageParam.js.add("priceList");
        elem.before(div.elem);


    }

    void sitemapNext(int key, Element parent, boolean flagRoot) {

        int idNext = tree.byIdn(key).first;
        while (idNext > 0) {
            NodeData _node = VUtil.readNode(host, idNext);
            if (_node != null) {
                NodeHead idHd = _node.head;
                if (idHd.flagValid && !idHd.flagBlock) {
                    if (idHd.idn == ini.blogStreamIdn) {
                        sitemapNext(idHd.idn, parent, false);
                    } else {
                        if (flagRoot || idHd.flagFolder) {
                            tag("li").appendTo(parent).append(
                                    tag("a").attr("href", idNext).text(tree.byIdn(idNext).text)
                            );
                            if (idHd.flagFolder) {
                                sitemapNext(idHd.idn, tag("ul").appendTo(parent).elem, false);
                            }
                        }
                    }
                }
            }
            idNext = tree.byIdn(idNext).next;
        }
    }

    void sitemap() {
        Element ul = new Element("ul");
        elem.replaceWith(ul);
        elem = ul;
        sitemapNext(0, elem, true);
    }

    void mailForm() {
        String tagText = elem.text().trim();
        Pattern reExtra = Pattern.compile("mailform\\((.*)\\)");
        TagBase div = tag("div");
        elem.replaceWith(div.elem);
        // todo удалить .id("mailform")
        div.id("mailform").addClass("mailform");
        pageParam.css.add("mailform");
        Matcher m = reExtra.matcher(tagText);
        if (m.find()) {
            pageParam.js.add("mailform~" + VUtil.encodeURIComponent(m.group(1)));
        } else {
            pageParam.js.add("mailform");
        }
    }

    void video() {

        //int width = 853, height = 480;
        String srcKey = elem.attr("data-param");

        if (!srcKey.isEmpty()) {
            // www.youtube-nocookie.com
            String src = "https://www.youtube-nocookie.com/embed/" + srcKey + "?rel=0&amp;controls=1&amp;showinfo=1";
            pageParam.og.videoSrc = src;
            elem.replaceWith(
                    tag("div").addClass("center").addClass("youtube").attr("data-src", srcKey).elem

            );
        } else {
            elem.remove();
        }
    }

    void addContentFn() {

        Element div = null;

        Matcher m = Pattern.compile("add\\((.*)\\)").matcher(elem.text().trim());
        if (m.find()) {
            div = new Element("div").append(
                    "<h2>Журналы в продаже</h2>"
            );
            switch (m.group(1)) {
                case "subscribe-pm":
                case "subscribe-class": {
                    break;
                }

                default:
                    div = null;
                    break;
            }
        }
        if (div == null) elem.remove();
        else elem.replaceWith(div);
    }


    Object setVal(String val) {
        if (reDgt.matcher(val).find()) {
            return Integer.parseInt(val);
        } else if (reBool.matcher(val).find()) {
            return val.toLowerCase().equals("true");
        } else {
            return val;
        }
    }

    void funny() {

        Matcher m = Pattern.compile("funny\\((.*)\\)").matcher(elem.text().trim());
        if (m.find()) {
            String srcData = m.group(1);

            List<String> dataLine = Arrays.stream(srcData.split(",")).collect(Collectors.toList());
            String title = dataLine.get(0);


            dataLine = dataLine.subList(1, dataLine.size());
            HashMap<String, Object> jsData = new HashMap<>();

            if (dataLine.size() > 0) {
                ArrayList<Object> list = new ArrayList<>();
                Pattern reNamed = Pattern.compile("([^=]+)=(.*)");

                for (String line : dataLine) {
                    Matcher m2 = reNamed.matcher(line);
                    if (m2.find()) {
                        jsData.put(VUtil.camelcase(m2.group(1).trim()), setVal(m2.group(2).trim().replaceAll("\\+\\s+\\+", "++")));
                    } else {
                        list.add(setVal(line));
                    }
                }
                if (list.size() > 0) jsData.put("param", list);
            }

            List<String> srcList = new ArrayList<>();
            HashMap<String, Object> srcDict = new HashMap<>();
            for (NodeAttach elemFile : node.attach) {
                if (VUtil.isFalse(elemFile.flagComment) && VUtil.isFalse(elemFile.flagCatalog) && VUtil.isFalse(elemFile.flagNode) && VUtil.notEmpty(elemFile.src)) {
                    String name2 = VUtil.textOnly(elemFile.content);
                    if (VUtil.notEmpty(name2)) {
                        name2 = VUtil.camelcase(name2);
                        if (srcDict.get(name2) == null) {
                            srcDict.put(name2, elemFile.src);
                        } else {
                            Object prevStr = srcDict.get(name2);
                            if (prevStr.getClass() == String.class) {
                                ArrayList<Object> l = new ArrayList<>();
                                l.add(prevStr);
                                srcDict.put(name2, l);
                            }
                            @SuppressWarnings("unchecked")
                            ArrayList<Object> curList = (ArrayList<Object>) srcDict.get(name2);
                            curList.add(elemFile.src);
                        }
                    } else {
                        srcList.add(elemFile.src);
                    }
                }
            }
            if (srcList.size() > 0) jsData.put("srcList", srcList);
            if (srcDict.size() > 0) jsData.put("srcDict", srcDict);

            TagBase div = tag("div");
            if (ini.flag2019) {
                div.addClass("funny").data("name", title);
                if (jsData.size() > 0) div.data("data", VUtil.jsonString(jsData));
            } else {
                if (jsData.size() > 0) pageParam.jsData.put("funny", VUtil.jsonString(jsData));
                pageParam.js.add("funny~" + title);
                pageParam.css.add("funny/" + title);
                //pageParam.css.add("funny/common");
                div.id("funny");
            }
            elem.replaceWith(div.elem);

        } else {
            elem.remove();
        }
    }

    public static class ShopTypeItem {
        @JsonAdapter(JsonIgnoreFalse.class)
        public boolean search;
        public String link;
        public String text;
    }

    public static class ShopType {
        public String phrase;
        public ArrayList<ShopTypeItem> list;
    }

    public static class PriceType {
        public int idn;
        public String orient; // 'h' | 'v',
        public String order;  // '' | 'desc' | 'asc',
        @JsonAdapter(JsonIgnoreIntZero.class)
        public int min;       // минимальная цена
        public ArrayList<String> keys;
    }

    // =========================

    public class ParamPrice {
        public String name;
        public String orient;// 'h' | 'v'
        public String sort;// 'low' | 'high'
        public int max;
        public int price;
        public List<String> keys;
    }

    public class ParamPriceList {
        public String name;
        public List<Object> keys;
        public String not;
        public int low;
        public int high;
        public int other;
        public int idn;
    }
}
