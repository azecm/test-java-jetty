package org.site.elements;

public class MailAttachment {
    public String name;
    public long length;
    public int id;

    public MailAttachment(int id, String name, long length) {
        this.id = id;
        this.name = name;
        this.length = length;
    }
}
