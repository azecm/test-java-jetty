package org.site.command;

import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.site.elements.NodeAttach;
import org.site.elements.NodeData;
import org.site.server.site.ImageSite;
import org.site.view.VUtil;
import org.site.view.ViewSite;

import javax.swing.text.html.parser.Entity;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScanSite {

    final Pattern reTagName = Pattern.compile("^[a-z1-6]+$");
    final Pattern reTagSingle = Pattern.compile("^(img|hr|br)$");
    final Pattern reTagEnabled = Pattern.compile("^(br|img|hr|blockquote|p|h[\\d]|ol|ul|li|strong|em|b|i|u|del|dfn|q|sub|sup|a)$");
    final Pattern reTagDropped = Pattern.compile("^(address|cite|font|ins|small|span|time)$");
    final Pattern reTagDrop = Pattern.compile("</?(address|cite|font|ins|small|span|time)[^>]*>", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    HashSet<String> classSet = new HashSet<>();
    // </span>


    String host;
    NodeData node;
    int idn;
    int idf;

    boolean updated;

    int counterAll = 0;
    int counterSmall = 0;
    int counterLike = 0;

    HashMap<String, HashSet<String>> tags = new HashMap<>();

    public void startHost() {
        this.start("---");
        finish();
    }

    public void startAll() {
        ViewSite.hosts().forEach(this::start);
        finish();
    }

    void finish() {
        //ArrayList<String> sortedList = new ArrayList<>(tags.);
        //Collections.sort(sortedList);
        //sortedList.forEach(VUtil::println);
        for (String tagName : tags.keySet()) {
            HashSet<String> attrs = tags.get(tagName);
            VUtil.println(tagName, attrs);
        }

        VUtil.println(classSet);

    }

    void start(String _host) {
        VUtil.println(_host);
        host = _host;
        VUtil.readNodes(_host, this::readNode);
    }

    void readNode(NodeData _node) {

        idn = _node.head.idn;
        node = _node;


    }

    void dropTags(String html, String type, NodeAttach attach) {
        if (html == null) return;
        if (reTagDrop.matcher(html).find()) {

            //VUtil.println("+++++++");
            //VUtil.println(html);
            //VUtil.println("");
            //VUtil.println(reTagDrop.matcher(html).replaceAll(""));

            updated = true;
            html = reTagDrop.matcher(html).replaceAll("");
        }

        if (updated) {
            switch (type) {
                case "content":
                    node.content = html;
                    break;
                case "description":
                    node.descr = html;
                    break;
                case "attach":
                    attach.content = html;
                    break;
            }
        }
    }

    void scanTags(Document doc, String type, NodeAttach attach) {

        //   match-three__record-ico     //

        // match-three__record-ico
        for (Element el : new ArrayList<>(doc.body().select("img"))) {
            String src = el.attr("src");
            if (src == null) {
                VUtil.println("++");
                continue;
            }
            if (src.contains("_")) {
                VUtil.println(host, idn, attach.idf, attach.content);
            } else {
                el.addClass("match-three__record-ico");
                updated = true;
            }
        }


        for (Element el : new ArrayList<>(doc.body().select("*"))) {
            String name = el.nodeName();
            if (name.equals("body")) continue;

            HashSet<String> attrs = tags.get(name);
            if (attrs == null) {
                attrs = new HashSet<>();
                tags.put(name, attrs);
            }

            for (Attribute attr : el.attributes().asList()) {
                attrs.add(attr.getKey());
            }

            if (el.attributes().hasKey("style")) {
                el.attributes().remove("style");
                updated = true;
            }
            if (el.attributes().hasKey("srcset")) {
                el.attributes().remove("srcset");
                updated = true;
            }

            //classSet.addAll(el.classNames());

            // MsoNormal
            //String className = "match-three__result-ico";
            //if(el.classNames().contains(className)){
            //    updated = true;
            //    el.removeClass(className);
            //}


        }


        if (updated) {
            switch (type) {
                case "content":
                    node.setContent(doc);
                    break;
                case "description":
                    node.setDescription(doc);
                    break;
                case "attach":
                    attach.setContent(doc);
                    break;
            }
        }
    }
}
