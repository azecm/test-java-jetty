package org.site.elements;

import com.google.gson.annotations.JsonAdapter;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.site.view.VUtil;

import java.time.ZonedDateTime;
import java.util.List;

public class NodeAttach {
    public int idf;
    public String date;

    @JsonAdapter(JsonIgnoreIntZero.class)
    public int idu = 0;
    @JsonAdapter(JsonIgnoreIntZero.class)
    public int like = 0;
    //@JsonAdapter(JsonIgnoreIntZero.class)
    //public int price = 0;

    @Nullable
    public String anonym = null;
    @Nullable
    public String content = null;
    @Nullable
    public List<Integer> group = null;
    @Nullable
    public String src = null;
    @JsonAdapter(JsonIgnoreIntZero.class)
    public int w = 0;
    @JsonAdapter(JsonIgnoreIntZero.class)
    public int h = 0;
    @Nullable
    public String quiz = null;

    //@JsonAdapter(JsonIgnoreFalse.class)
    //public Boolean flagMark;
    @JsonAdapter(JsonIgnoreFalse.class)
    public Boolean flagNode;
    @JsonAdapter(JsonIgnoreFalse.class)
    public Boolean flagComment;
    @JsonAdapter(JsonIgnoreFalse.class)
    public Boolean flagCatalog;
    //@JsonAdapter(JsonIgnoreFalse.class)
    //public Boolean flagMark;

    public Object gameResult;

    public ZonedDateTime getDate() {
        return NUtil.dateFromString(date);
    }

    public void setDate(ZonedDateTime d) {
        date = d.toString().replaceAll("\\[GMT\\]", "");
    }

    public Document getContent() {
        return Jsoup.parse(content == null ? "" : VUtil.htmlClean(content));
    }

    public void setContent(Document doc) {
        content = VUtil.html(doc);
    }
}
