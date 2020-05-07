package org.site.elements;

public class TagCanvas extends TagBase {
    public TagCanvas() {
        super("canvas");
    }

    public TagCanvas dataSrc(String src) {
        this.elem.attr("data-src", src);
        return this;
    }

    public TagCanvas width(int val) {
        this.elem.attr("width", val + "");
        return this;
    }

    public TagCanvas width(String val) {
        this.elem.attr("width", val);
        return this;
    }

    public TagCanvas height(int val) {
        this.elem.attr("height", val + "");
        return this;
    }

    public TagCanvas height(String val) {
        this.elem.attr("height", val);
        return this;
    }
}
