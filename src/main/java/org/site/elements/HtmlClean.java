package org.site.elements;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.site.view.VUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlClean {
    public static final String ATTR_Direct = "data-direct";

    final static Pattern reEnabledTag = Pattern.compile("^(p|h\\d|ol|ul|li|blockquote|a|img|hr|br|dfn|strong|em|b|i|q|u|sup|sub|del)$", Pattern.CASE_INSENSITIVE);
    final static Pattern reZeroTag = Pattern.compile("^(img|hr|br)$", Pattern.CASE_INSENSITIVE);
    final static Pattern reRemovedTag = Pattern.compile("^(script|style|svg)", Pattern.CASE_INSENSITIVE);
    final static Pattern reProtocol = Pattern.compile("(https?|ftp)://", Pattern.CASE_INSENSITIVE);
    final static Pattern reSrc = Pattern.compile("(/file/\\d{4}/(?:(150|250|600)/)?\\d{4}\\.(jpg|png|svg))");

    final static Pattern reInt = Pattern.compile("^\\d+$");
    final static Pattern reGoto = Pattern.compile("^/goto/(s/)?");

    String[] classAlign = new String[]{"left", "right", "center", "justify", "clear-both"};
    String[] classText = new String[]{"notice", "attention"};
    String[] classParagraph = new String[]{"notice", "attention", "like", "command"};
    String[] classBlockquote = new String[]{"col3r", "col3l", "col3"};
    String[] classImg = new String[]{"imgl", "imgr"};

    boolean linkAllowed;

    NodeTree tree;

    public String directLinkProhibited(NodeTree tree, String html) {
        this.tree = tree;
        linkAllowed = false;
        return begin(html);
    }

    public String directLinkAllowed(NodeTree tree, String html) {
        this.tree = tree;
        linkAllowed = true;
        return begin(html);
    }

    void removeAllAttr(Element elem) {
        for (Attribute attr : elem.attributes()) {
            elem.attributes().remove(attr.getKey());
        }
    }

    String urlGoto(URL url, boolean flagSec) {
        StringBuilder list = new StringBuilder();
        list.append("/goto/");
        if (flagSec) list.append("s/");
        list.append(url.getHost());
        if (url.getPort() > 0 && url.getPort() != 80) {
            list.append(":" + url.getPort());
        }
        list.append(url.getPath());
        if (url.getQuery() != null) list.append("?" + url.getQuery());
        return list.toString();
    }

    boolean testLink(Element elem) {
        boolean flag = false;
        String href = elem.attributes().get("href");
        if (href != null) {
            String newHref = null;
            if ((reInt.matcher(href).find() && tree.byIdn(VUtil.getInt(href)) != null) || reGoto.matcher(href).find()) {
                flag = true;
            } else if (reProtocol.matcher(href).find()) {
                URL url = null;
                try {
                    url = new URL(href);
                } catch (MalformedURLException e) {
                }
                if (url != null) {
                    if (url.getHost().equals(tree.getHost())) {
                        NodeTreeElem treeElem = tree.byUrl(url.getPath());
                        if (treeElem != null) newHref = treeElem.idn + "";
                    } else if (url.getProtocol().equals("http")) {
                        newHref = urlGoto(url, false);
                    } else if (url.getProtocol().equals("https")) {
                        newHref = urlGoto(url, true);
                    }
                }

            } else if (href.startsWith("//")) {
                if (linkAllowed) {
                    flag = true;
                } else {
                    newHref = "/goto/" + href.substring(2);
                }
            } else if (href.startsWith("/")) {
                NodeTreeElem treeElem = tree.byUrl(href);
                if (treeElem != null) newHref = treeElem.idn + "";
            }

            if (newHref != null && newHref.length() > 0) {
                elem.attr("href", newHref);
                flag = true;
            }
        }
        return flag;
    }

    boolean testImage(Element elem) {
        boolean flag = false;
        String src = elem.attributes().get("src");
        Matcher m = reSrc.matcher(src);
        if (m.find()) {
            flag = true;
            if (!src.startsWith("/file/")) {
                elem.attr("src", m.group(1));
            }
        }
        return flag;
    }

    String begin(String html) {
        ArrayList<Element> listRemove = new ArrayList<>();
        ArrayList<Element> listDrop = new ArrayList<>();

        html = html
                .replaceAll("&nbsp;", " ")
                .replaceAll("\\u00A0", " ")
                .replaceAll("&#xa0;", " ")
                .replaceAll("\\s+", " ");

        Document doc = Jsoup.parse(html);
        for (Element elem : doc.body().getAllElements()) {
            String tagName = elem.tagName();
            if (tagName.equals("body")) continue;

            if (reEnabledTag.matcher(tagName).find()) {
                if (!reZeroTag.matcher(tagName).find() && elem.childNodeSize() == 0) {
                    listRemove.add(elem);
                    continue;
                }

                ArrayList<String> attrs = new ArrayList<>();
                ArrayList<String> classList = new ArrayList<>();
                switch (tagName) {
                    case "h1":
                    case "h2":
                    case "h3":
                    case "h4":
                    case "h5":
                    case "h6": {
                        attrs.add("class");
                        classList.addAll(Arrays.asList(classAlign));
                        break;
                    }
                    case "p": {
                        attrs.add("class");
                        attrs.add("data-type");
                        attrs.add("data-param");
                        classList.addAll(Arrays.asList(classAlign));
                        classList.addAll(Arrays.asList(classParagraph));
                        break;
                    }
                    case "strong":
                    case "em": {
                        attrs.add("class");
                        classList.addAll(Arrays.asList(classText));
                        break;
                    }
                    case "blockquote": {
                        attrs.add("class");
                        classList.addAll(Arrays.asList(classBlockquote));
                        break;
                    }
                    case "img": {
                        if (testImage(elem)) {
                            classList.addAll(Arrays.asList(classImg));
                            attrs.add("class");
                            attrs.add("src");
                            attrs.add("alt");
                            attrs.add("width");
                            attrs.add("height");
                            attrs.add("data-type");
                            attrs.add("data-param");
                        } else {
                            listDrop.add(elem);
                        }
                        break;
                    }
                    case "a": {
                        if (testLink(elem)) {
                            attrs.add("href");
                            String target = elem.attributes().get("target");
                            if (target != null && target.equals("_blank")) {
                                attrs.add("target");
                            }
                            if (linkAllowed) {
                                attrs.add(ATTR_Direct);
                            }
                        } else {
                            listDrop.add(elem);
                        }
                        break;
                    }
                }
                if (attrs.size() > 0) {
                    for (Attribute attr : elem.attributes()) {
                        if (attrs.contains(attr.getKey())) {
                            switch (attr.getKey()) {
                                case "class": {
                                    for (String name : elem.classNames()) {
                                        if (!classList.contains(name)) {
                                            elem.removeClass(name);
                                        }
                                    }
                                    if (elem.classNames().size() == 0) {
                                        elem.attributes().remove(attr.getKey());
                                    }
                                    break;
                                }
                            }
                        } else {
                            elem.attributes().remove(attr.getKey());
                        }
                    }

                } else {
                    removeAllAttr(elem);
                }
            } else if (reRemovedTag.matcher(tagName).find()) {
                listRemove.add(elem);
            } else {
                listDrop.add(elem);
            }
        }

        for (Element elem : listRemove) elem.remove();

        for (Element elem : listDrop) {
            VUtil.tagDrop(elem);
        }
        html = VUtil.html(doc);

        html = reProtocol.matcher(html).replaceAll("");

        return html;
    }
}
