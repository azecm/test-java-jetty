package org.site.elements;

import com.google.gson.reflect.TypeToken;
import org.site.view.VUtil;
import org.site.view.ViewSite;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// price(name=Baby-Born, max=10, keys=[Chiqui])

public class PriceKeywords {
    class Counter {
        int yes = 0;
        int no = 0;
    }

    String host;
    int idnNode = 0;
    private int counter = 0;
    private static Pattern reNotWord = Pattern.compile("\\W");
    private static Pattern reHostKey = Pattern.compile("(?:(\\w+)\\.)?([\\w\\-]+)\\.(\\w+)");


    public boolean useOnlyT = false;

    HashMap<String, Integer> wordcount;
    ArrayList<PriceElem> priceList = new ArrayList<>();
    HashMap<Integer, ArrayList<PriceElem>> srcList = new HashMap<>();
    HashMap<String, ArrayList<PriceElem>> goodsByHost = new HashMap<>();
    //final public static String dirPath = VUtil.DIRPriceSrc + "keywords/";
    String dirImg = VUtil.DIRPrice + "file/view";

    public PriceKeywords(String _host) {
        host = _host;
    }

    public static String getDir(String _host) {
        return VUtil.DIRPriceSrc + _host + "/";
    }

    public PriceKeywords init() {
        if (VUtil.pathExists(getDir(host))) {
            try {
                Files.list(Paths.get(getDir(host))).forEach(p -> {
                    int idn = VUtil.getInt(p.getFileName().toString().replace(".json", ""));
                    if (idn > 0) srcList.put(idn, null);
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return this;
    }

    public Set<Integer> idns() {
        return srcList.keySet();
    }

    public void test() {
        Counter c = new Counter();
        Set<Integer> idns = idns();
        HashMap<Integer, Integer> labelsFree = new HashMap<>();
        HashMap<Integer, Integer> labelsAll = new HashMap<>();
        VUtil.readNodes(host, node -> {
            if (node.head.flagFolder || node.head.labels == null) return;
            boolean flag = false;
            for (int idn : node.head.labels) {
                if (idns.contains(idn)) flag = true;
                labelsAll.compute(idn, (k, v) -> v == null ? 1 : ++v);
            }
            if (flag) {
                c.yes++;
            } else {
                c.no++;
                for (int idn : node.head.labels) {
                    labelsFree.compute(idn, (k, v) -> v == null ? 1 : ++v);
                }
            }
        });

        VUtil.println(Math.round(((float) c.yes) / ((float) (c.yes + c.no)) * 100), c.yes, c.no, c.yes + c.no);

        ArrayList<Map.Entry<Integer, Integer>> labelsList = new ArrayList<>(labelsFree.entrySet());
        labelsList.sort(Comparator.comparingInt(Map.Entry::getValue));
        //HashMap<String, Integer> keyword = new HashMap<>();
        HashMap<String, Integer> keyword1 = VUtil.readJson(VUtil.DIRFile + host + "/json/keywords.json", TypeToken.getParameterized(HashMap.class, String.class, Integer.class).getType());
        HashMap<Integer, String> keyword2 = new HashMap<>();
        for (Map.Entry<String, Integer> row : keyword1.entrySet()) keyword2.put(row.getValue(), row.getKey());

        String text = "";
        for (Map.Entry<Integer, Integer> row : labelsList) {
            text += keyword2.get(row.getKey()) + "\t" + row.getValue() + "\t" + labelsAll.get(row.getKey()) + "\n";
        }

        VUtil.writeFile("/usr/local/www/app.log/labels.cvs", text);

    }

    private void setGoodsByHost() {
        goodsByHost = new HashMap<>();
        for (PriceElem item : priceList) {
            ArrayList<PriceElem> l = new ArrayList<>();
            String linkHost = item.link.substring(0, item.link.indexOf(" "));
            if (goodsByHost.containsKey(linkHost)) {
                l = goodsByHost.get(linkHost);
            } else {
                goodsByHost.put(linkHost, l);
            }
            l.add(item);
        }
    }

    public void createGoodsList(List<Integer> idns, int _idnNode) {
        idnNode = _idnNode;
        setGoods(idns);
        setGoodsByHost();
    }


    public TagBase insertGoodsList() {
        if (goodsByHost.size() == 0) return null;

        boolean flagWhithShop = true;
        ArrayList<PriceElem> dataOut = new ArrayList<>();
        if (goodsByHost.keySet().size() > 1) {
            for (String hostName : goodsByHost.keySet()) {
                ArrayList<PriceElem> rows = goodsByHost.get(hostName);
                PriceElem elemPrice = rows.get((int) Math.floor(Math.random() * rows.size()));
                dataOut.add(elemPrice);
            }
        } else {
            flagWhithShop = false;
            for (int i = 0; i < 3; i++) {
                if (priceList.size() == 0) break;
                dataOut.add(priceList.remove((int) Math.floor(priceList.size() * Math.random())));
            }
        }

        TagBase ul = tag("ul");
        for (PriceElem elemPrice : dataOut) {
            if (elemPrice.link.startsWith("la")) continue;
            TagBase link = tag("a").addClass("external")
                    .attr("id", "hh" + (++counter))
                    .attr("data-link", elemPrice.link)
                    .text(elemPrice.name);
            if (flagWhithShop) {
                String hostName = elemPrice.link.split(" ")[0];
                link.attr("data-shop", hostName);
            }

            ul.append(tag("li").append(link));
        }
        return tag("div").addClass("content-list").append(ul);
    }

    public ArrayList<PriceElem> insertGoodsNew(String host, int idn, List<String> labels, List<String> tags, boolean isBook) {
        ArrayList<PriceElem> dataOut = new ArrayList<>();


        List<String> pictures = new ArrayList<>();
        for (ViewSite.ElasticPriceItem item : ViewSite.getPriceList(host, idn, labels, tags, isBook)) {
            if (item.picture.endsWith("substitute-m.gif")) continue;

            PriceElem pelem = new PriceElem();
            pelem.link = getLink(item.url);
            pelem.price = (int) item.price;
            pelem.name = item.label;
            pelem.src = getImageSrc(item.picture);
            dataOut.add(pelem);
            pictures.add(item.picture);
        }

        testImage(pictures);

        return dataOut;
    }

    public ArrayList<PriceElem> insertGoods(String title) {
        ArrayList<PriceElem> dataOut = new ArrayList<>();
        if (priceList.size() > 0) {

            List<Pattern> list = Arrays.stream(title.split("\\s"))
                    .map(e -> e.replaceAll("[^a-zA-Z0-9а-яА-ЯёЁ]", ""))
                    .filter(e -> !e.isEmpty())
                    .map(e -> Pattern.compile(e, Pattern.CASE_INSENSITIVE))
                    .collect(Collectors.toList());

            for (PriceElem elemp : priceList) {
                elemp.weight = 0;
                for (Pattern p : list) {
                    if (p.matcher(elemp.name).find()) elemp.weight += 1;
                }
            }

            priceList.sort(Comparator.comparingInt(value -> -value.weight));

            if (priceList.get(0).weight > list.size() / 3) {
                for (int i = 0; i < 10; i++) {
                    if (priceList.size() == 0) break;
                    dataOut.add(priceList.remove(0));
                }
            } else {
                for (int i = 0; i < 10; i++) {
                    if (priceList.size() == 0) break;
                    dataOut.add(priceList.remove((int) Math.floor(priceList.size() * Math.random())));
                }
            }

        }

        return dataOut;
    }

    private TagBase tag(String name) {
        return new TagBase(name);
    }

    private void fillGoods(int idn, HashMap<String, PriceElem> list) {
        ArrayList<PriceElem> results = srcList.get(idn);
        if (results == null) {
            ArrayList<PriceElem> dataFile = loadFile(host, idn);
            results = new ArrayList<>();
            for (PriceElem elem : dataFile) {
                elem.src = getImageSrc(elem);
                elem.link = getLink(elem);
                if (elem.src == null || elem.link == null) continue;
                results.add(elem);
            }
            srcList.put(idn, results);
        }
        for (PriceElem elem : results) {
            list.put(elem.link, elem);
        }
    }

    private void setGoods(List<Integer> idns) {
        HashMap<String, PriceElem> list = new HashMap<>();
        Set<Integer> idnsLinked = idns();
        if (idns.size() > 0) {
            if (idnsLinked.contains(idns.get(0))) {
                fillGoods(idns.get(0), list);
            } else {
                if (wordcount == null) {
                    wordcount = ViewSite.loadWordCount(host);
                }
                //int valCnt = 0;
                int valMin = 0;
                int valIdn = 0;
                int valCur = 0;
                for (Integer idn : idns) {
                    if (idnsLinked.contains(idn)) {
                        //valCnt++;
                        valCur = wordcount.get(idn + "");
                        if (valMin == 0 || valCur < valMin) {
                            valMin = valCur;
                            valIdn = idn;
                        }
                    }
                }
                if (valIdn > 0) {
                    fillGoods(valIdn, list);
                }
            }
        }

        if (useOnlyT) {
            priceList = new ArrayList<>();
            for (PriceElem row : new ArrayList<>(list.values())) {
                if (row.link.startsWith("to")) {
                    priceList.add(row);
                }
            }
        } else {
            priceList = new ArrayList<>(list.values());
        }

    }

    public static ArrayList<PriceElem> loadFile(String _host, int idn) {
        return VUtil.readJson(getDir(_host) + idn, new TypeToken<ArrayList<PriceElem>>() {
        }.getType());
    }

    public static String getLink(PriceElem elem) {
        return getLink(elem.link);
    }

    public static String getLink(String elemLink) {
        String link = null;
        URL url = null;
        try {
            url = new URL(elemLink);
        } catch (MalformedURLException e) {
        }
        if (url != null) {
            Matcher m = reHostKey.matcher(url.getHost());
            if (m.find()) {
                link = m.group(2) + url.getFile();
                //VUtil.println("link", url.getHost(), link, m.group(1), m.group(2),m.group(3));
            } else {
                VUtil.println("ОШИБКА PriceKeywords::getLink", url.getHost(), elemLink);
            }

        }
        if (link != null) {
            link = link.replaceAll("/", " ");
        }
        return link;
    }

    public static String getImageSrc(PriceElem elem) {
        return getImageSrc(elem.src);
    }

    public static String getImageSrc(String srcOrig) {
        String src = null;
        URL url = null;
        try {
            url = new URL(srcOrig);
        } catch (MalformedURLException e) {
        }
        if (url != null) {
            String pathEnd = url.getPath();
            Matcher m = reHostKey.matcher(url.getHost());
            if (m.find()) {
                String key = m.group(1).equals("my") ? "my" : m.group(2);
                src = "/" + key + pathEnd;
                //VUtil.println("src", src);
            } else {
                VUtil.println("ОШИБКА PriceKeywords::getImageSrc::hostName", url.getHost(), srcOrig);
            }

        }
        return src;
    }

    public void testImage(List<String> list) {
        int counter = 0;
        for (String line : list) {
            counter++;
            if (line.isEmpty()) continue;
            String src = getImageSrc(line);
            if (src == null) {
                VUtil.println("ERROR image(1)", line);
                continue;
            }

            String imgFullPath = dirImg + "-2" + src;
            if (VUtil.pathExists(imgFullPath)) continue;

            byte[] imgData = VUtil.getUrlRaw(line);

            if (imgData != null) {
                VUtil.createDirs(imgFullPath);
                VUtil.writeFile(Paths.get(imgFullPath), imgData);
            } else {
                VUtil.println("ERROR image(2)", line, src);
            }

        }

    }

    public void updateImages() {
        String pathTo = "/usr/local/www/app.log/_images_.txt";
        testImage(Arrays.asList(VUtil.readFile(pathTo).split("\n")));
    }

    public PriceKeywords updateImage() {
        for (Integer idn : srcList.keySet()) {
            ArrayList<PriceElem> dataFile = loadFile(host, idn);
            for (PriceElem elem : dataFile) {
                String src = getImageSrc(elem);
                if (src == null) continue;
                String imgFullPath = dirImg + src;

                if (VUtil.pathExists(imgFullPath)) continue;

                byte[] imgData = VUtil.getUrlRaw(elem.src);
                if (imgData != null) {
                    VUtil.createDirs(imgFullPath);
                    VUtil.writeFile(Paths.get(imgFullPath), imgData);
                }
            }
        }
        return this;
    }
}
