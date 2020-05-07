package org.site.command;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.site.elements.Affiliate;
import org.site.view.VUtil;
import org.site.elements.NodeData;
import org.site.view.PCommand;
import org.site.view.ViewSite;

import javax.swing.text.View;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdatePrice {
    private String host = "";


    HashSet<String> hostMap = new HashSet<>();


    public UpdatePrice() {
        init();
    }

    void init() {
        hostMap.add("---");


        VUtil.println(host);

    }

    void readNodeShopTag(NodeData node) {
        if (node.head.flagFolder || node.head.labels == null || node.content == null) return;
        boolean flag = false;
        Document doc = node.getContent();
        for (Element elem : doc.getElementsByTag("p")) {
            if (!elem.hasClass("shop")) continue;
            //counter2++;

            //VUtil.println("+", node.head.idn, elem.outerHtml());

            //boolean flagClearline = false;
            List<String> list = new ArrayList<>();
            for (Node c : elem.childNodes()) {
                if (c.nodeName().equals("#text")) {
                    String t = c.outerHtml().trim();
                    if (!t.isEmpty()) {
                        list.add(t);
                    } else {
                        //flagClearline = true;
                    }
                }
            }

            if (list.size() == 0) {
                elem.remove();
                flag = true;
                continue;
            }

            PCommand.ShopType data = new PCommand.ShopType();
            data.list = new ArrayList<>();

            Matcher m;
            String linkPost = null;
            if (list.get(0).startsWith("/")) {
                m = ViewSite.reShopSearch.matcher(list.get(0));
                if (m.find()) {
                    linkPost = m.group(1);
                    data.phrase = m.group(2).replaceAll("\\+", " ");
                }
                list = list.subList(1, list.size());

                if (linkPost == null) {
                    VUtil.println(node.head.idn, "linkPost==null");
                }
            }


            for (String line : list) {

                String link = null;
                String linkText = null;

                m = ViewSite.reShopLine.matcher(line);
                if (m.find()) {
                    link = m.group(1);
                    linkText = m.group(2);
                } else {
                    Affiliate.Label alabel = ViewSite.affiliate.labels.get(line);
                    if (alabel == null) continue;
                    link = alabel.getLink();
                    linkText = alabel.getText();
                }

                if (link == null) {
                    //flagClearline = true;
                    continue;
                }


                if (link.contains("/search/")) {
                    m = ViewSite.reShopSearch.matcher(link);
                    if (m.find()) {
                        String phrase = m.group(2).replaceAll("\\+", " ");
                        if (data.phrase == null) {
                            data.phrase = phrase;
                        }
                        if (!data.phrase.equals(phrase)) {
                            //VUtil.println(node.head.idn, "data.phrase", data.phrase, phrase);
                        }
                    }
                    link = link.substring(0, link.indexOf("/"));
                }

                boolean flagHost = false;
                for (String hostName : hostMap) {
                    if (link.contains(hostName)) {
                        flagHost = true;
                        link = link.substring(link.indexOf(hostName));
                    }
                }

                if (!flagHost) {
                    VUtil.println(node.head.idn, "bad host", link, line);
                    continue;
                }

                PCommand.ShopTypeItem item = new PCommand.ShopTypeItem();
                item.link = link;
                if (linkText != null) item.text = linkText;
                if (!link.contains("/")) item.search = true;
                data.list.add(item);
            }

            if (data.phrase != null) {
                boolean flagO = false;
                boolean flagM = false;
                for (PCommand.ShopTypeItem item : data.list) {
                    if (item.link.startsWith("--")) flagO = true;
                    if (item.link.startsWith("--")) flagM = true;
                }
                //
                if (data.list.size() == 0 || (flagM && !flagO)) {
                    PCommand.ShopTypeItem item = new PCommand.ShopTypeItem();
                    item.link = "oz";
                    item.search = true;
                    data.list.add(item);
                    //counter++;
                }

            }

            flag = true;

            if (data.list.size() > 0) {
                //VUtil.println(node.head.idn);
                //VUtil.println(elem.outerHtml());
                elem.removeAttr("class");
                elem.html("<br>").addClass("command")
                        .attr("data-type", "shop")
                        .attr("data-param", VUtil.jsonString(data));
                //VUtil.println(elem.outerHtml());
            } else {
                VUtil.println(node.head.idn, "REMOVED");
                elem.remove();
            }
        }

        if (flag) {
            node.setContent(doc);
            //VUtil.writeNode(host, node);
        }
    }

    void readNodeVideoTag(NodeData node) {
        if (node.head.flagFolder || node.head.labels == null || node.content == null) return;
        String pref = "www.youtube.com/embed/";
        boolean flag = false;
        Document doc = node.getContent();
        for (Element e : doc.getElementsByTag("p")) {
            if (e.hasClass("command") && e.attr("data-type").equals("price")) {
                //String text = e.text().trim();
                //VUtil.println(e.outerHtml());

                //e.html("<br>");
                VUtil.println(e.html(), node.head.idn);
                //flag = true;
            }
        }

        if (flag) {
            node.setContent(doc);
            //VUtil.writeNode(host, node);
        }
    }

    void readNodePriceTag(NodeData node) {
        if (node.head.flagFolder || node.head.labels == null || node.content == null) return;

        boolean flag = false;
        Document doc = node.getContent();

        for (Element e : doc.getElementsByTag("p")) {
            if (e.hasClass("command")) {

                if (e.attr("data-type").equals("price")) {
                    PCommand.PriceType param = PCommand.priceParam(e);
                    if (param.keys != null) {
                        //VUtil.println(node.head.idn);
                        ArrayList<String> keys = new ArrayList<>();
                        for (String word : param.keys) {
                            //String word = param.keys.get(i);
                            if (word.contains("+")) {
                                word = word.replaceAll("\\+", " ");
                                //param.keys.(i, word.replaceAll("\\+", " "));
                                //VUtil.println(node.head.idn, word);
                                flag = true;
                            }
                            keys.add(word);
                        }
                        if (flag) {
                            param.keys = keys;
                            VUtil.println(node.head.idn);
                            VUtil.println(e.outerHtml());
                            e.attr("data-param", VUtil.jsonString(param));
                            VUtil.println(e.outerHtml());
                        }
                    }
                }


            }
        }

        if (flag) {
            node.setContent(doc);
            VUtil.writeNode(host, node);
        }


    }
}
