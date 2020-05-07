package org.site.elements;

public class NodeTreeElem {
    public int idn;
    public int idp;
    public int idu;

    public int prev;
    public int next;
    public int first;
    public int last;

    public String text = "";
    public String path = "";

    //static final String IDN = "idn";

    public NodeTreeElem(int idn) {
        this.idn = idn;
    }

    public NodeTreeElem text(String text) {
        this.text = text;
        return this;
    }

    public NodeTreeElem path(String path) {
        this.path = path;
        return this;
    }

    public NodeTreeElem idp(int idp) {
        this.idp = idp;
        return this;
    }

    public NodeTreeElem idu(int idu) {
        this.idu = idu;
        return this;
    }

    public NodeTreeElem prev(int prev) {
        this.prev = prev;
        return this;
    }

    public NodeTreeElem next(int next) {
        this.next = next;
        return this;
    }

    public NodeTreeElem first(int first) {
        this.first = first;
        return this;
    }

    public NodeTreeElem last(int last) {
        this.last = last;
        return this;
    }

    public boolean isFolder() {
        return first != 0;
    }
}
