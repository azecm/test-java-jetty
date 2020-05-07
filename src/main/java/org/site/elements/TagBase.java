package org.site.elements;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.site.view.VUtil;

import java.util.List;
import java.util.stream.Collectors;

public class TagBase {
    public Element elem;

    public TagBase(String name) {
        elem = new Element(name);
    }

    public TagBase(Element elem) {
        this.elem = elem;
    }

    public TagBase text(String text) {
        elem.text(text);
        return this;
    }

    public TagBase text(int text) {
        elem.text(text + "");
        return this;
    }

    public TagBase text(double text) {
        elem.text(text + "");
        return this;
    }

    public TagBase id(String id) {
        elem.attr("id", id);
        return this;
    }

    public TagBase attr(String key, String text) {
        if (key != null && text != null) elem.attr(key, text);
        return this;
    }

    public TagBase attr(String key, int text) {
        elem.attr(key, text + "");
        return this;
    }

    public TagBase data(String key, String text) {
        elem.attr("data-" + key, text);
        return this;
    }

    public TagBase data(String key, int text) {
        elem.attr("data-" + key, text + "");
        return this;
    }

    public TagBase addClass(String text) {
        if (text != null) elem.addClass(text);
        return this;
    }

    public TagBase itemprop(String text) {
        elem.attr("itemprop", text);
        return this;
    }

    public TagBase itemscope(String text) {
        elem.attr("itemscope", text);
        return this;
    }

    public TagBase itemtype(String text) {
        elem.attr("itemtype", text);
        return this;
    }

    public TagBase appendTo(Element parent) {
        elem.appendTo(parent);
        return this;
    }

    public TagBase appendTo(TagBase parent) {
        if (parent != null) elem.appendTo(parent.elem);
        else VUtil.error("ViewPage::Tag::appendTo - parent null");
        return this;
    }

    public TagBase append(List<Node> list) {
        for (Node n : list) elem.appendChild(n);
        return this;
    }

    public TagBase append(String html) {
        if (html != null) elem.append(html);
        return this;
    }

    public TagBase append(TagBase... tags) {
        for (TagBase t : tags) {
            if (t != null) elem.appendChild(t.elem);
        }
        return this;
    }

    public TagBase append(org.jsoup.nodes.Node... tags) {
        for (org.jsoup.nodes.Node t : tags) if (t != null) elem.appendChild(t);
        return this;
    }

}
