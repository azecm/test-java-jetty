package org.site.elements;

import org.site.ini.SiteProperty;
import org.site.view.VUtil;
import org.site.view.ViewSite;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class NodeSearch {
    static String tableSearch = "search";

    static Pattern reDgt = Pattern.compile("^\\d+$");
    static Pattern reChar1 = Pattern.compile("ё", Pattern.UNICODE_CASE);
    static Pattern reChar2 = Pattern.compile("[^а-яa-z0-9]", Pattern.UNICODE_CASE);
    static Pattern reChar3 = Pattern.compile("(\\D)\\1+", Pattern.UNICODE_CASE);

    static Pattern reEnd10 = Pattern.compile("[аеийоуыъьэюя]+$", Pattern.UNICODE_CASE);
    static Pattern reEnd11 = Pattern.compile("[аеиоуыъьэюя]+$", Pattern.UNICODE_CASE);
    static Pattern reEnd20 = Pattern.compile("[вхмтсгхцчщшкн]+$", Pattern.UNICODE_CASE);
    static Pattern reEnd21 = Pattern.compile("[вхмтсх]+$", Pattern.UNICODE_CASE);

    static Pattern reHeader = Pattern.compile("<(?:h(\\d))[^>]*>((?:.(?!\\/h\\d))*)");

    static String _col_key = "key";
    static String _col_idn = "idn";
    static String _col_weight = "weight";
    static String _col_word = "word";

    String host;
    NodeTree tree;
    PostgreSQL db;
    SiteProperty ini;
    File file;
    FileOutputStream fos;

    int counter = 0;
    int counterItem = 0;
    //int counterKey = 0;

    public static void indexSite(String host) {
        VUtil.Timer t0 = VUtil.timer();
        NodeSearch nodeSearch = new NodeSearch(host);
        nodeSearch.createDBTable();
        int size = nodeSearch.tree.size();
        VUtil.readNodes(host, (node) -> {
            nodeSearch.counter++;
            if (node.head == null || node.head.idn == 0 || node.head.idp == nodeSearch.ini.idnLabel) return;
            if (nodeSearch.counter % 100 == 0) {
                VUtil.println(nodeSearch.counter, Math.round(((double) nodeSearch.counter) / size * 1000) / 10.0, node.head.idn);
                VUtil.println(Math.round(((double) t0.ms()) / nodeSearch.counterItem * 10) / 10.0, "ms/node");
            }
            nodeSearch.appendToFile(node);
            nodeSearch.counterItem++;
        });
        nodeSearch.fromCVS();
        nodeSearch.finish();
        t0.end();
        VUtil.println(Math.round(((double) t0.ms()) / nodeSearch.counterItem * 10) / 10.0, "ms/node");
    }

    public NodeSearch(String host) {
        this.host = host;
        tree = NodeTree.load(host);
        ini = ViewSite.getIni(host);
        db = new PostgreSQL(host).connect();
    }

    void createDBTable() {
        db.dropTable(tableSearch);
        db.createTable(tableSearch)
                .col(_col_key).bigserial().notNull()//.primaryKey()
                .col(_col_idn).integer().notNull().defaultVal(0)
                .col(_col_weight).integer().notNull().defaultVal(0)
                .col(_col_word).varchar(15).notNull().defaultVal("")
                .exec();
    }

    String tempFileName() {
        return VUtil.DIRLog + tableSearch + "-" + host + ".csv";
    }

    public void toCVS() {
        db.copyToCSV(tableSearch, tempFileName());
    }

    public void fromCVS() {
        createDBTable();
        db.copyfromCSV(tableSearch, tempFileName(), _col_idn, _col_weight, _col_word);
    }

    public ArrayList<FrontSearchRes> find(String searchText) {

        HashMap<String, Integer> nodeMap = new HashMap<>();
        indexText(nodeMap, searchText, 0);

        //VUtil.Timer t = VUtil.timer();

        ArrayList<SearchRes> res = new ArrayList<>();
        //HashMap<Integer, Integer> resDist = new HashMap<>();
        //VUtil.println("NodeSearch::pref", host, searchText, String.join(" ", nodeMap.keySet()));
        if (nodeMap.size() > 0) {
            res = db.select(SearchRes.class).from(tableSearch).sum(_col_weight).groupBy(_col_idn).andIn(_col_word, nodeMap.keySet()).order(_col_weight, false).limit(100).exec();
        }
        VUtil.println("NodeSearch::find", host, res.size(), searchText, String.join(" ", nodeMap.keySet()));


        ArrayList<FrontSearchRes> nodes = new ArrayList<>();
        for (SearchRes row : res) {
            NodeTreeElem elem = tree.byIdn(row.idn);
            if (elem == null) {
                VUtil.println("NodeSearch::find", host, row.idn, "not found idn");
            } else {
                nodes.add(new FrontSearchRes(tree.getUrl(row.idn), elem.text));
            }
        }
        finish();

        return nodes;
    }

    void appendToFile(NodeData node) {
        HashMap<String, Integer> nodeMap = getMap(node);

        if (nodeMap.size() == 0) return;
        if (file == null) {
            VUtil.pathRemove(tempFileName());
            file = new File(tempFileName());
        }
        if (fos == null) {
            try {
                fos = new FileOutputStream(file, true);
                //fos.write((String.join(";", Arrays.asList(_col_key, _col_idn, _col_weight, _col_word)) + "\n").getBytes());
                fos.write((String.join(";", Arrays.asList(_col_idn, _col_weight, _col_word)) + "\n").getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (fos != null) {
            String idn = node.head.idn + "";
            try {
                for (Map.Entry<String, Integer> ent : nodeMap.entrySet()) {
                    int weight = ent.getKey().length() * 2 + ent.getValue();
                    //fos.write((String.join(";", Arrays.asList((++counterKey) + "", idn, weight + "", ent.getKey())) + "\n").getBytes());
                    fos.write((String.join(";", Arrays.asList(idn, weight + "", ent.getKey())) + "\n").getBytes());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public NodeSearch updateDB(int idn) {
        NodeData node = VUtil.readNode(host, idn);
        if (node != null) updateDB(node);
        return this;
    }

    public void updateDB(NodeData node) {
        HashMap<String, Integer> nodeMap = getMap(node);
        if (nodeMap.size() == 0) return;

        int idn = node.head.idn;

        //VUtil.error(VUtil.dateLog(), "NodeSearch::updateDB", host, idn);

        db.begin();

        db.delete(tableSearch).where(_col_idn, idn).exec();
        PostgreSQL.Insert insert = db.insert(tableSearch).cols(_col_idn, _col_weight, _col_word);
        for (Map.Entry<String, Integer> ent : nodeMap.entrySet()) {
            int weight = ent.getKey().length() * 2 + ent.getValue();
            insert.values().set(idn).set(weight).set(ent.getKey());
        }
        insert.exec();

        db.commit();
    }

    HashMap<String, Integer> getMap(NodeData node) {
        HashMap<String, Integer> nodeMap = new HashMap<>();

        NodeHead h = node.head;

        if (h == null || h.flagFolder || h.flagBlock || !h.flagValid || h.idp == ini.idnLabel) {
            if (h != null && h.idn > 0) {
                db.delete(tableSearch).where(_col_idn, h.idn).exec();
            }
            return nodeMap;
        }

        indexText(nodeMap, node.content, 10);
        for (NodeAttach attach : node.attach) {
            indexText(nodeMap, attach.content, VUtil.isTrue(attach.flagComment) ? 1 : 5);
        }

        if (node.content != null) {
            Matcher m = reHeader.matcher(node.content);
            while (m.find()) {
                indexText(nodeMap, m.group(2), 100 - VUtil.getInt(m.group(1)) * 10);
            }
        }

        if (node.head.keywords != null) {
            for (String words : node.head.keywords) {
                indexText(nodeMap, words, 30);
            }
        }

        if (node.head.labels != null) {
            for (Integer key : node.head.labels) {
                indexText(nodeMap, tree.byIdn(key).text, 60);
            }
        }

        // за каждое слово в результате +1000
        Integer val;
        for (String word : nodeMap.keySet()) {
            val = nodeMap.get(word);
            nodeMap.put(word, val + 1000);
        }

        return nodeMap;
    }

    @SuppressWarnings("Duplicates")
    public void finish() {
        db.close();
        try {
            if (fos != null) {
                fos.close();
                VUtil.pathRemove(tempFileName());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void indexText(HashMap<String, Integer> nodeMap, String text, int weight) {
        if (text == null) return;
        text = VUtil.textOnly(text);
        Integer val;
        for (String word : text.split("\\s")) {
            //VUtil.println("-",word);
            word = word(word);
            //VUtil.println("+",word);
            if (word.length() == 0) continue;
            val = nodeMap.get(word);
            nodeMap.put(word, val == null ? weight : (val + weight));
        }
    }

    public static String word(String text) {
        text = text.trim();
        text = reChar1.matcher(text.toLowerCase()).replaceAll("е");
        text = reChar2.matcher(text).replaceAll("");
        text = reChar3.matcher(text).replaceAll("$1");

        int i = 3;
        String wordSave;
        while (i > 0) {
            i--;
            wordSave = text;
            text = text.length() > 4 ? reEnd10.matcher(text).replaceFirst("") : reEnd11.matcher(text).replaceFirst("");
            if (text.length() < 3) {
                text = wordSave;
                break;
            }
            if (text.length() > 6) {
                text = text.length() > 7 ? reEnd20.matcher(text).replaceFirst("") : reEnd21.matcher(text).replaceFirst("");
            } else {
                break;
            }
            if (text.equals(wordSave)) {
                break;
            }
        }
        if (text.length() < 2) text = "";
        else if (text.length() > 15) {
            text = "";
        }

        return text;
    }

    public static class FrontSearchRes {
        public String path;
        public String text;

        public FrontSearchRes(String path, String text) {
            this.path = path;
            this.text = text;
        }
    }

    static class SearchRes {
        public int weight;
        public int idn;
    }

}
