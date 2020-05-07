package org.site.elements;

public class TagMeta extends TagBase {
    public TagMeta() {
        super("meta");
    }

    public TagMeta name(String text) {
        elem.attr("name", text);
        return this;
    }

    public TagMeta content(String text) {
        elem.attr("content", text);
        return this;
    }

    public TagMeta property(String text) {
        elem.attr("property", text);
        return this;
    }

    public TagMeta charset(String text) {
        elem.attr("charset", text);
        return this;
    }

    public TagMeta itemprop(String text) {
        elem.attr("itemprop", text);
        return this;
    }

}
