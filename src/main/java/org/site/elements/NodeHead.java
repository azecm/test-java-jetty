package org.site.elements;

import java.util.List;

import com.google.gson.annotations.JsonAdapter;
import org.jetbrains.annotations.Nullable;

public class NodeHead {
    public int idn;
    public int idp;
    public int idu;
    public String title;
    @Nullable
    public String searchPhrase;

    public List<String> link;
    public List<String> date;

    @JsonAdapter(JsonIgnoreFalse.class)
    public boolean flagValid;
    @JsonAdapter(JsonIgnoreFalse.class)
    public boolean flagFolder;
    @JsonAdapter(JsonIgnoreFalse.class)
    public boolean flagBlock;
    @JsonAdapter(JsonIgnoreFalse.class)
    public boolean flagOpenLink;
    @JsonAdapter(JsonIgnoreFalse.class)
    public boolean flagBook;

    @Nullable
    public List<Integer> rating;
    @Nullable
    public List<String> keywords;
    @Nullable
    public List<Integer> labels;
    @Nullable
    public NodeHeadNotice notice;
    @Nullable
    public NodeHeadLinked linked;

}
