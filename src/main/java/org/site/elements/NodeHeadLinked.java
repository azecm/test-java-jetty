package org.site.elements;

import com.google.gson.annotations.JsonAdapter;

import java.util.ArrayList;

public class NodeHeadLinked {
    public ArrayList<String> links;
    @JsonAdapter(JsonIgnoreFalse.class)
    public boolean flagFull;
}
