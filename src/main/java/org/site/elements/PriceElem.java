package org.site.elements;

import com.google.gson.reflect.TypeToken;
import org.site.view.VUtil;

import java.util.ArrayList;
import java.util.HashMap;

public class PriceElem {
    public String name;
    public int price;
    public String link;
    public String src;

    public int weight = 0;

    public PriceElem name(String text) {
        name = text.trim();
        return this;
    }

    public PriceElem price(int val) {
        price = val;
        return this;
    }

    public PriceElem link(String text) {
        link = text.trim();
        return this;
    }

    public PriceElem src(String text) {
        src = text.trim();
        return this;
    }

    public static ArrayList<PriceElem> load(String path) {
        return VUtil.readJson(path, TypeToken.getParameterized(ArrayList.class, PriceElem.class).getType());
    }
}
