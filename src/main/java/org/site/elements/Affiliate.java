package org.site.elements;

import org.jetbrains.annotations.Nullable;
import org.site.view.VUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;

public class Affiliate {
    static HashMap<String, String> alpha1251Encode = new HashMap<>();

    public class Link {
        String host;
        String _code;
        HashMap<String, String> codes = new HashMap<>();
        ArrayList<String> labels = new ArrayList<>();
        String _search;
        boolean aaa = false;
        boolean bbb = false;
        boolean flagSecure = false;

        Link(String host, boolean flagSecure) {
            this.host = host;
            this.flagSecure = flagSecure;
        }

        public Link add(String label) {
            labels.add(label);
            return this;
        }

        public Link code(String code) {
            _code = code;
            return this;
        }

        public Link code(String host, String code) {
            codes.put(host, code);
            return this;
        }

        public Link isAaa() {
            aaa = true;
            return this;
        }

        public Link isBbbb() {
            bbb = true;
            return this;
        }

        public Link setSearch(String text) {
            _search = text;
            return this;
        }


        public String label() {
            return labels.get((int) Math.floor(Math.random() * labels.size()));
        }

        @Nullable
        public String search(String text) {
            if (_search == null) {
                return null;
            } else {
                return _search.replace("++++", VUtil.encodeURIComponent(text));
            }
        }

        public String getProtocol() {
            return flagSecure ? "https://" : "http://";
        }

        public String getLink(String path) {

            String result = "";
            String urlName = getProtocol() + host + path;

            URL url = null;
            try {
                url = new URL(urlName);
            } catch (MalformedURLException e) {
                VUtil.error("Affiliate::getLink", e.getLocalizedMessage(), urlName);
            }
            if (url != null) {
                if (bbb) {
                } else if (aaa) {
                } else {
                    if (_code.startsWith("?")) {
                        result = urlName + (urlName.contains("?") ? ("&" + _code.substring(1)) : _code);
                    } else if (_code.startsWith("/")) {
                        result = urlName + (urlName.endsWith("/") ? _code.substring(1) : _code);
                    }
                }
            }

            return result;
        }

    }

    public class Label {
        String[][] list;
        String[] text;
        String[] link;

        public Label setList(String[][] line) {
            list = line;
            return this;
        }

        public Label setText(String[] line) {
            text = line;
            return this;
        }

        public Label setLink(String[] line) {
            link = line;
            return this;
        }

        String random(String[] l) {
            return l[(int) Math.floor(Math.random() * l.length)];
        }

        String[] listRandom() {
            return list[(int) Math.floor(Math.random() * list.length)];
        }

        public String getLink() {
            return list == null ? random(link) : listRandom()[0];
        }

        public String getText() {
            return list == null ? random(text) : listRandom()[0];
        }
    }

    public HashMap<String, Link> links = new HashMap<>();
    public HashMap<String, Label> labels = new HashMap<>();

    public Pattern reHosts;

    public Affiliate() {

        reHosts = Pattern.compile("(?:\\W|^)(" + String.join("|", links.keySet()) + ")(?:\\W|$)");

    }
}
