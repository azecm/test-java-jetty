package org.site.view;

import com.google.gson.reflect.TypeToken;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.*;
import org.elasticsearch.client.transport.NoNodeAvailableException;
import org.elasticsearch.common.settings.Settings;
//import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.site.elements.*;
import org.site.elements.SiteUserData;
import org.site.ini.*;
import redis.clients.jedis.Jedis;
import twitter4j.*;
import twitter4j.conf.ConfigurationBuilder;

import java.net.InetAddress;

import java.io.*;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class ViewSite {

    public final static int commentsPerPage = 40;

    final String nameContent = "content-";
    final static Pattern reBook = Pattern.compile("(^|\\s)(к|кн)", Pattern.CASE_INSENSITIVE);
    final Pattern rePageUrl = Pattern.compile("^/page/(\\d+)$");
    final Pattern reNotIndex = Pattern.compile("^(/goto/|/file/|https?:)");
    final Pattern reCatalogExtList = Pattern.compile("\\.(jpg|png|mp3|svg)$");
    final Pattern reImgExt = Pattern.compile("\\.(jpg|png)$");
    final Pattern reLinkLocal = Pattern.compile("^/\\w?");
    final Pattern reLinkExternal = Pattern.compile("^//\\w");
    final Pattern reLinkGoto = Pattern.compile("^/goto/(s/)?([^/]+)(.*)");
    final Pattern reLinkKey = Pattern.compile("([0-9a-zA-Z\\-]+)\\.[0-9a-zA-Z\\-]+$");
    public final static Pattern reShopGame = Pattern.compile("game-([a-z\\-]+)");
    public final static Pattern reShopSearch = Pattern.compile("(/search/([^/]+)/)");
    public final static Pattern reShopLine = Pattern.compile("^([a-z]{2}[^\\s]+)\\s?(.+)?$");
    final Pattern reCSS = Pattern.compile("([^{}]+)\\{([^}]+)\\}");
    final Pattern reCSS4Var = Pattern.compile("var\\(([a-z0-9\\-]+)\\)");
    public final static Pattern reAmp = Pattern.compile("&amp;", Pattern.CASE_INSENSITIVE);
    //final Pattern reImageType = Pattern.compile("slideshow|catalog|audio|video|quiz");
    final Pattern reCheckAuto = Pattern.compile("\"checkAuto\":true");
    final Pattern reParag = Pattern.compile("^(p|li|blockquote|h\\d|td|ul)$");
    //final static int deltaSeconds = 3600*3;
    public String host;
    public NodeTree tree;
    Type type;
    public final static Affiliate affiliate = new Affiliate();
    public SiteProperty ini;
    //String newsSrc;
    //String newsUrl;
    String srcTemplate;
    String srcStyle;
    ViewPage page;
    Element srcSidebar;
    List<Node> srcBannerBottom;
    String versionCss;
    String versionJs;
    String protocol;
    public PriceKeywords priceKeywords;
    //HashMap<Integer, String> urls = new HashMap<>();
    HashMap<Integer, String> users = new HashMap<>();
    HashMap<String, String> cssKeyList = new HashMap<>();
    HashMap<String, String> cssDataList = new HashMap<>();
    HashMap<Integer, Announce> announceList = new HashMap<>();
    HashMap<Integer, NodeHead> nodeHeadList = new HashMap<>();
    public HashMap<String, String> sapeData;
    public HashMap<String, String> cssMapCommon = new HashMap<>();
    public HashMap<String, String> cssMapHost = new HashMap<>();
    public HashMap<String, String> css4Vars = new HashMap<>();
    HashMap<Integer, Element> keywordsLink;
    int linkUniID = 0;
    HashMap<Integer, HashSet<String>> keywords = new HashMap<>();
    //HashMap<Integer, String> keywordsPrice = new HashMap<>();
    //ArrayList<MenuLine> menuMain;
    String menuMain;
    String menuSection;
    //ArrayList<MenuLine> menuSection;
    private ArrayList<Integer> idnsOrdered;
    List<String> urlsList = new ArrayList<>();
    //final Pattern reParag = Pattern.compile("^(p|li|blockquote|h\\d)$");
    String[] defaultPages = new String[]{"/search/", "/200", "/404", "/410", "/50x"};

    //static RestClient clientElastic = null;
    static Client clientElastic = null;


    public ViewSite(String host) {
        init(host);
    }

    // ===============

    public static void updateHostUrl(String host, String url) {
        VUtil.Timer t = VUtil.timer();
        new ViewSite(host).url(url).finished();
        t.end();
    }

    public static ViewSite updateHostIdn(String host, int idn) {
        return updateHostIdn(host, idn, false);
    }

    public static ViewSite updateHostIdnTwitter(String host, int idn) {
        return updateHostIdn(host, idn, true);
    }

    static void initElastic() {

        if (clientElastic == null) {

            try {
                clientElastic = new PreBuiltTransportClient(Settings.EMPTY)
                        .addTransportAddress(new TransportAddress(InetAddress.getByName("localhost"), 9300));
            } catch (UnknownHostException e) {
            }

        }

    }

    static void closeElastic() {

    }


    public static ViewSite updateHostIdn(String host, int idn, boolean flagValidated) {

        ViewSite site = new ViewSite(host).idn(idn).finished();

        if (flagValidated) {
            VUtil.println("ViewSite::updateHostIdn - postTwitter");
            site.postTwitter();
        }

        return site;
    }

    public static void updateHost(String host) {
        VUtil.Timer t = VUtil.timer();

        //initElastic(host);
        new ViewSite(host).all().finished();
        //closeElastic();

        t.end();
    }

    public static void updateAllHostCron(boolean flagMini) {
        for (String host : hosts()) {
            if (flagMini) {
                updateHostMini(host);
            } else {
                new ViewSite(host).all().finished();
            }
        }
    }

    public static void updateAllIndex() {
        for (String host : hosts()) {
            VUtil.println(host);
            NodeSearch.indexSite(host);
        }
    }

    public static void scanAllHost() {
        for (String host : hosts()) {
            VUtil.println(host);

            VUtil.readNodes(host, (node) -> {
                boolean updated = false;
                for (NodeAttach attach : node.attach) {
                    if (attach.date.contains("[")) {
                        VUtil.println(node.head.idn, attach.date);

                        if (attach.date.endsWith("[]")) {
                            //updated = true;
                            //attach.date = attach.date.substring(0, attach.date.length()-2).replaceAll("2620", "26T20");
                            //VUtil.println(attach.date);
                        }
                    }
                }
                if (updated) {
                    VUtil.writeNode(host, node);
                }
            });
        }
    }

    public static void updateAllHost(boolean flagMini) {
        VUtil.Timer t = VUtil.timer();

        for (String host : hosts()) {
            VUtil.println(host);
            if (flagMini) {
                updateHostMini(host);
            } else {
                new ViewSite(host).all().finished();
            }
        }

        t.end();
    }

    public static void updateAuto() {
        for (Map.Entry<String, SiteProperty> ent : ViewSite.hostsIni().entrySet()) {
            if (ent.getValue().flagAutoUpdate) {
                String hostName = ent.getKey();
                String res = VUtil.exec("find", VUtil.DIRDomain + hostName + "/node", "-type", "f", "-cmin", "-15");
                if (!res.isEmpty()) {
                    VUtil.println("flagAutoUpdate", res);
                    NodeSearch.indexSite(hostName);
                    updateHost(hostName);
                }
            }
        }
    }

    // ====================

    public static SiteProperty getIni(String host) {
        SiteProperty res = null;
        try {
            res = (SiteProperty) Class.forName("org.site.ini." + VUtil.getHostKey(host)).newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }

        if (res == null) {
            System.err.println("ini for host " + host + " not found");
        }
        return res;
    }

    public static List<String> hosts() {

        List<String> res = new ArrayList<>();
        try {
            res = Files.list(Paths.get(VUtil.DIRDomain))
                    .filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith("www."))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }

        return res;
    }

    public static HashMap<String, SiteProperty> hostsIni() {
        HashMap<String, SiteProperty> res = new HashMap<>();
        for (String host : ViewSite.hosts()) {
            SiteProperty ini = ViewSite.getIni(host);
            if (ini == null) continue;
            res.put(host, ini);
        }
        return res;
    }

    public static void updateHostMini(String host) {
        String path = Tracker.folderMark(host);

        List<Path> dirList = new ArrayList<>();
        try {
            dirList = Files.list(Paths.get(path)).filter(Files::isRegularFile).collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }

        //File[] dirList = new File(path).listFiles(File::isFile);
        if (dirList.size() == 0) return;

        ViewSite site = new ViewSite(host);

        // обновляем статьи
        NodeSearch nodeSearch = null;
        if (getIni(host).flag2019) nodeSearch = new NodeSearch(host);
        for (Path f : dirList) {
            VUtil.delete(f);
            String fileName = f.getFileName().toString();
            int idn = VUtil.getInt(fileName.substring(0, fileName.length() - 4));
            if (idn == 0) continue;
            site.idn(idn);
            if (nodeSearch != null) nodeSearch.updateDB(idn);
        }
        if (nodeSearch != null) nodeSearch.finish();

        Document doc;
        String text;
        String href;

        ArrayList<String> linkList = new ArrayList<>();
        ArrayList<String> linkToUpdate = new ArrayList<>();
        ArrayList<String> linkToUpdate2 = new ArrayList<>();
        if (site.ini.tracker > 0) linkToUpdate2.add("/tracker/");

        for (int i = 1; i < 11; i++) {
            path = i == 1 ? "/" : "/page/" + i;
            text = VUtil.readCacheFile(host, path);
            if (text == null) continue;
            linkToUpdate.add(path);

            doc = Jsoup.parse(text);
            for (Element a : doc.getElementsByTag("a")) {
                href = a.attr("href");

                if (!site.reNotIndex.matcher(href).find() && href.startsWith("/") && !href.contains("#")) {
                    linkList.add(href);
                }
            }
        }

        for (String l : linkToUpdate) {
            site.url(l);
            text = VUtil.readCacheFile(host, path);
            if (text == null) continue;
            doc = Jsoup.parse(text);
            for (Element a : doc.getElementsByTag("a")) {
                href = a.attr("href");
                if (!site.reNotIndex.matcher(href).find() && href.startsWith("/") && !href.contains("#")) {
                    if (!linkList.contains(href)) {
                        linkToUpdate2.add(href);
                    }
                }
            }
        }

        for (String l : linkToUpdate2) {
            site.url(l);
        }

        site.finished();
    }

    // ===============

    public ViewSite finished() {
        //closeElastic();
        return this;
    }

    public ViewSite idn(int idn) {
        type = Type.page;


        new ViewPage(this).byIdn(idn);
        createAnnonce(idn);
        return this;
    }

    // ===============

    public ViewSite url(String url) {
        type = Type.page;
        new ViewPage(this).byUrl(url);
        return this;
    }

    public ViewSite all() {

        type = Type.full;

        beginFullUpdate();

        urlsList.add("/");
        if (ini.tracker > 0) urlsList.add("/tracker/");
        Collections.addAll(urlsList, defaultPages);

        int i = 0;

        while (urlsList.size() < 100_000) {
            if (urlsList.size() == i) break;
            String nextUrl = urlsList.get(i++);
            ViewPage page = new ViewPage(this).byUrl(nextUrl);
            for (Element a : page.doc.getElementsByTag("a")) {
                String href = a.attr("href");
                if (href.isEmpty() || reNotIndex.matcher(href).find() || urlsList.contains(href)) continue;
                urlsList.add(href);
            }
        }

        VUtil.println("Всего страниц: " + urlsList.size());

        finishFullUpdate();

        return this;

    }

    void init(String host) {
        this.host = host;
        tree = NodeTree.load(host);

        //TypeToken.getParameterized(HashMap.class, String.class, Integer.class).getType()
        HashMap<String, ArrayList<Object>> kk = VUtil.readJson(VUtil.DIRFile + host + "/json/keywordSite.json", new TypeToken<HashMap<String, ArrayList<Object>>>() {
        }.getType());
        for (HashMap.Entry<String, ArrayList<Object>> entry : kk.entrySet()) {
            HashSet<String> list = new HashSet<>();
            for (Object v : entry.getValue()) {
                if (v.getClass() == String.class) {
                    list.add((String) v);
                } else if (v.getClass() == Double.class) {
                    int i = (int) (double) v;
                    list.add(i + "");
                }
            }

            keywords.put(Integer.parseInt(entry.getKey()), list);
        }

        ini = getIni(host);

        if (ini == null) return;

        //if (ini.flagElastic) {
        initElastic();
        //}

        protocol = ini.flagSSL ? "https://" : "http://";


        if (ini.flagPriceByKeywords) {
            priceKeywords = new PriceKeywords(host).init();
        }

        initTemplate();

        ini.onInit(this);

    }

    void fillCssMap(HashMap<String, String> map, String pathDir) {
        if (VUtil.pathExists(pathDir)) {
            try {
                for (Path path : Files.list(Paths.get(pathDir))
                        .filter(Files::isRegularFile)
                        .collect(Collectors.toList())) {
                    String pathName = path.getFileName().toString();
                    map.put(pathName.substring(0, pathName.length() - 4), VUtil.readFile(path));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    void initTemplate() {
        String pathToCss;
        String pathToTempl;
        if (ini.flag2019) {
            pathToTempl = VUtil.DIRTemplateNew + "html/" + host + ".html";
            pathToCss = VUtil.DIRTemplateNew + "css/" + host + "/layout.css";
        } else {
            pathToTempl = VUtil.DIRTemplate + host + "/template.html";
            pathToCss = VUtil.DIRTemplate + host + "/style.css";
        }

        srcTemplate = VUtil.readFile(pathToTempl);
        srcStyle = VUtil.readFile(pathToCss);

        versionCss = VUtil.lastModified(pathToCss);
        versionJs = VUtil.lastModified(VUtil.DIRPublic + "js/" + ini.jsFile).replaceAll(":", "").replace("T", "-").replace("Z", "");

        if (srcTemplate == null || srcStyle == null || versionCss == null) {
            System.err.println("template for host " + host + " not found");
            return;
        }

        // srcSidebar =========
        NodeData node = VUtil.readNode(tree, "@sidebar");
        if (node != null) {
            srcSidebar = node.getContent().body();
        }
        node = VUtil.readNode(tree, "@banner");
        if (node != null) {
            if (srcSidebar == null) {
                srcSidebar = node.getContent().body();
            } else {
                List<Node> prev = srcSidebar.childNodesCopy();
                srcSidebar = node.getContent().body();
                for (Node n : prev) {
                    srcSidebar.appendChild(n);
                }
            }
        }


        if (srcSidebar != null) {
            Pattern reCtrl = Pattern.compile("ctrl\\(([^)]+)\\)");
            for (Element e : srcSidebar.getElementsByTag("a")) elemLink(e, 0);
            for (Element e : srcSidebar.getElementsByTag("img")) elemCanvasFromImg(e);
            for (Element p : srcSidebar.getElementsByTag("p")) {
                String text = p.text();
                if (text == null) continue;
                Matcher m = reCtrl.matcher(text.trim());
                if (m.find()) {
                    p.replaceWith(new TagBase("div").id("fwCounter").elem);
                }
            }
        }
        // ====================

        node = VUtil.readNode(tree, "@banner-bottom");
        if (node != null) {
            // www.o8ode.ru
            srcBannerBottom = node.getContent().body().childNodesCopy();
        }

        node = VUtil.readNode(tree, "@keywords-link");
        if (node != null) {
            Pattern reIdn = Pattern.compile("(\\[([\\d,\\s]+)\\])");
            keywordsLink = new HashMap<>();
            for (Element p : node.getContent().body().getElementsByTag("p")) {
                Matcher m = reIdn.matcher(p.text());
                if (m.find()) {

                    for (String idnStr : m.group(2).split(",")) {
                        idnStr = idnStr.trim();
                        if (VUtil.reDgt.matcher(idnStr).find()) {
                            Node n1 = p.childNodes().get(0);
                            Node n2 = new TextNode(n1.outerHtml().replaceAll("\\[[^]]+\\]\\s*", ""));
                            n1.replaceWith(n2);
                            keywordsLink.put(Integer.parseInt(idnStr, 10), p);
                        }
                    }
                }
            }
        }

        if (ini.flag2019) {
            fillCssMap(cssMapCommon, VUtil.DIRPublic + "template-new/css/common/element/");
            fillCssMap(cssMapHost, VUtil.DIRPublic + "template-new/css/" + host + "/element/");

            cssColors(css4Vars, "common/variable.css");
            cssColors(css4Vars, host + "/variable.css");
        }
    }

    void cssColors(HashMap<String, String> css4Vars, String pathEnd) {
        String pathToColors = VUtil.DIRPublic + "template-new/css/" + pathEnd;
        if (VUtil.pathExists(pathToColors)) {
            Matcher m = reCSS.matcher(VUtil.readFile(pathToColors));
            while (m.find()) {
                String selectors = m.group(1).trim();
                if (selectors.equals(":root")) {
                    String[] style;
                    for (String styleRule : m.group(2).split(";")) {
                        style = styleRule.split(":");
                        css4Vars.put(style[0].trim(), style[1].trim());
                    }
                }
            }
        }
    }

    // ===============

    void beginFullUpdate() {
        VUtil.println("beginFullUpdate-1");
        updateKeywords(host, ini);
        VUtil.println("beginFullUpdate-2");
        updateAnnonce();
        VUtil.println("beginFullUpdate-3");
        if (host.equals(Activity.host)) {
            new Activity().genList();
        }
        VUtil.println("beginFullUpdate-4");
    }

    public boolean newsNotificationTested = false;

    public boolean newsNotificationTest(String host) {
        String text = VUtil.readFile(VUtil.DIRFile + host + "/json/site-news.json");
        return text != null && !text.equals("{}");
    }

    public static void newsNotificationUpdate(NodeTree tree, String host, NodeData node) {
        if (node == null) return;
        NodeAttach newsAttach = node.attach.get(0);
        String src = newsAttach.src;
        int idn = VUtil.getInt(newsAttach.getContent().text().trim());
        HashMap<String, String> objNews = new HashMap<>();
        if (src != null && idn > 0) {
            //HashMap<String, String> objNews = new HashMap<>();
            objNews.put("src", src);
            objNews.put("href", tree.getUrl(idn));
        }
        VUtil.writeJson(VUtil.DIRFile + host + "/json/site-news.json", objNews);
    }

    void finishFullUpdate() {


        if (ini.flagNewsNotification) {
            NodeTreeElem elem = tree.byUrl("@image");
            if (elem != null) {
                newsNotificationUpdate(tree, host, VUtil.readNode(host, elem.idn));
            }
        }

        HashSet<String> defaultPagesSet = new HashSet<>();
        Collections.addAll(defaultPagesSet, defaultPages);
        ArrayList<String> sitemap = new ArrayList<>();
        sitemap.add("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sitemap.add("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
        for (String url : urlsList) {
            if (defaultPagesSet.contains(url)) continue;
            sitemap.add("<url><loc>" + protocol + host + url + "</loc></url>");
        }
        sitemap.add("</urlset>");
        VUtil.writeFile(VUtil.DIRDomain + host + "/sitemap.xml", String.join("\n", sitemap));

        // tracker
        if (ini.tracker > 0) {

            String path = Tracker.folderTracker(host);
            List<Path> fs = VUtil.dirlistLastModified(path);
            if (fs != null && fs.size() > ini.tracker) {
                for (Path f : fs.subList(ini.tracker, fs.size())) {
                    if (VUtil.isJson(f)) {
                        VUtil.delete(f);
                    }
                }
            }
        }

        updateUrlList();
        updateNotConfirmed();
        updateMainFolder();
        updateNodeRating();
        if (host.equals(Activity.host)) updateTopTen();

        Process cmdProc;
        ArrayList<String> cmdList = new ArrayList<>();

        if (host.equals(Activity.host)) {
            cmdList.add("find " + Tracker.folderTracker(host) + "activity -type f -ctime +5w -delete");
        }


        cmdList.add("find " + VUtil.DIRHtml + host + " -type f -cmin +1440 -delete");


        cmdList.add("find " + VUtil.DIRHtml + host + " -type d -empty -delete");


        cmdList.add("find " + VUtil.DIRFile + host + "/json -type f -cmin +1440 -delete");


        cmdList.add("find " + VUtil.DIRTemp + " -type f -cmin +10 -delete");
        cmdList.add("find " + VUtil.DIRFile + host + "/file/wait -type f -cmin +10 -delete");
        //cmdList.add("find " + VUtil.DIRFile + host + "/file/anonym/150 -type f -ctime +1w -delete");


        String styleCSS = VUtil.DIRFile + host + "/css/style.css";
        if (VUtil.pathExists(styleCSS)) cmdList.add("touch " + styleCSS);
        cmdList.add("find " + VUtil.DIRFile + host + "/css -type f -cmin +1440 -delete");

        for (String cmd : cmdList) {
            VUtil.exec(cmd);

        }
    }

    public static void updateKeywords(String host) {
        updateKeywords(host, ViewSite.getIni(host));
    }

    public static void updateKeywords(String host, SiteProperty ini) {

        HashMap<String, List<Object>> keywordSite = new HashMap<>();
        HashMap<String, Integer> counter = new HashMap<>();
        HashMap<String, Integer> keyword = new HashMap<>();
        HashSet<String> keywordExt = new HashSet<>();

        VUtil.readNodes(host, (n) -> {
            NodeHead h = n.head;
            //VUtil.println(h.idn);
            if (h != null) {
                if (h.idp == ini.idnLabel) {
                    keyword.put(h.link.get(0), h.idn);
                } else if (h.flagValid && !h.flagFolder && !h.flagBlock && h.keywords != null && h.labels != null) {

                    if (h.idp > 0) {
                        List<Object> kw = new ArrayList<>();
                        kw.addAll(h.keywords);
                        kw.addAll(h.labels);
                        keywordSite.put(h.idn + "", kw);
                    }
                    if (h.idn > 0) {
                        for (String word : h.keywords) {
                            Integer val = counter.get(word);
                            counter.put(word, val == null ? 1 : ++val);
                            keywordExt.add(word);
                        }
                        for (Integer label : h.labels) {
                            Integer val = counter.get(label.toString());
                            counter.put(label.toString(), val == null ? 1 : ++val);
                        }
                    }
                }
            }
        });

        if (ini.flagPriceByKeywords) {
            VUtil.writeJson(VUtil.DIRFile + host + "/json/keywords-linked.json", new PriceKeywords(host).init().idns());
        }

        VUtil.writeJson(VUtil.DIRFile + host + "/json/wordcount.json", counter);
        VUtil.writeJson(VUtil.DIRFile + host + "/json/keywords.json", keyword);
        VUtil.writeJson(VUtil.DIRFile + host + "/json/keywords-ext.json", keywordExt);
        VUtil.writeJson(VUtil.DIRFile + host + "/json/keywordSite.json", keywordSite);
    }

    // =============

    String getDirAnnonce() {
        return VUtil.DIRFile + host + "/json/announce/";
    }

    public Announce createAnnonce(Integer idn) {
        Announce ann = new Announce().fromNode(this, idn);
        writeAnnounce(idn, ann);
        announceList.put(idn, ann);
        return ann;
    }

    void updateAnnonce() {

        NodeData node;
        NodeHead h;

        PostgreSQL db = new PostgreSQL(host);
        db.connect();

        for (Integer idn : getOrderedIdnList()) {
            node = VUtil.readNode(host, idn);
            if (node == null) continue;
            h = node.head;
            if (h == null || h.flagBlock || h.flagFolder || !h.flagValid || h.idp == 0) continue;

            Announce ann = createAnnonce(idn);

            db.update("tree")
                    .set("commentAll", ann.com)
                    .set("commentLast", ann.comLast)
                    .where("idn", idn)
                    .exec();
        }

        db.close();

    }

    Announce readAnnounce(int idn) {
        String path = VUtil.DIRFile + host + "/json/announce/" + VUtil.getFolder(idn);
        return VUtil.readJson(path, Announce.class);
    }

    void writeAnnounce(int idn, Announce ann) {
        String path = VUtil.DIRFile + host + "/json/announce/" + VUtil.getFolder(idn);
        VUtil.writeJson(path, ann);
    }

    Announce getAnnounce(int idn) {
        Announce ann;
        if (announceList.containsKey(idn)) {
            ann = announceList.get(idn);
        } else {
            ann = readAnnounce(idn);
            announceList.put(idn, ann);
        }
        return ann;
    }

    // =============

    static public ArrayList<ElasticPriceItem> getPriceList(String host, int idn, List<String> labels, List<String> tagsPre, boolean isBook) {

        if (!isBook) {
            isBook = reBook.matcher(String.join(" ", tagsPre)).find();
        }

        if (!isBook) {
            isBook = reBook.matcher(String.join(" ", labels)).find();
        }

        List<String> tags = new ArrayList<>();

        for (String tag : tagsPre) {

            tags.add(tag);

        }

        if (labels.isEmpty()) {
            labels = tags;
        }


        ArrayList<ElasticPriceItem> list = new ArrayList<>();

        if (clientElastic != null) {
            SearchHits hitsMax = null;

            double maxScore = 0;

            if (labels.size() > 0) {
                for (String searchText : labels) {

                    SearchRequest searchRequest = new SearchRequest();
                    searchRequest.indices(isBook ? "price-book" : "price-stuff");
                    SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
                    sourceBuilder.query(
                            QueryBuilders.boolQuery()

                                    .should(QueryBuilders.matchQuery("label", searchText))
                                    .should(QueryBuilders.matchQuery("tags", tags))
                                    //.should(QueryBuilders.termQuery("label", text))
                                    //.should(QueryBuilders.matchPhraseQuery("label", text))
                                    .must(QueryBuilders.matchQuery("available", true))
                    );
                    searchRequest.source(sourceBuilder);
                    //

                    SearchHits hits;
                    try {
                        hits = clientElastic.search(searchRequest).actionGet().getHits();
                    } catch (NoNodeAvailableException e) {
                        e.printStackTrace();
                        continue;
                    }
                    //SearchHits hits = clientElastic.search(searchRequest).actionGet().getHits();

                    //VUtil.println(hits.getMaxScore());
                    if (hits.getMaxScore() > maxScore) {
                        hitsMax = hits;
                        maxScore = hits.getMaxScore();
                    }

                    if (hits.getMaxScore() > 3) {
                        break;
                    }
                }
            } else {


                VUtil.println("ViewSite::getPriceList", host, idn, labels, tagsPre, hitsMax.getMaxScore());
            }

            if (hitsMax == null) {
                VUtil.println("ViewSite::getPriceList", host, idn, labels, tagsPre);
            } else {

                for (SearchHit hit : hitsMax.getHits()) {
                    String hitJson = hit.getSourceAsString();
                    ElasticPriceItem item = VUtil.getClassFromJson(hitJson, ElasticPriceItem.class);

                    if (item.picture == null) continue;

                    if (item.picture.contains("stat")) {

                        item.picture = item.picture.replaceAll("/\\d/", "/2/");

                    }


                    list.add(item);
                    //VUtil.println(item.label, item.price, item.picture, hit.getScore());
                }
            }

        }

        return list;

    }

    void updateUrlList() {

        String urlKey = VUtil.redisKeyUrl(host), urlKeyNew = urlKey + ":new";
        HashMap<String, String> mapUrlIdn = new HashMap<>();
        for (NodeTreeElem ne : tree.entry()) mapUrlIdn.put(tree.getUrl(ne.idn), ne.idn + "");

        Jedis jedis = RAction.redis();
        jedis.hmset(urlKeyNew, mapUrlIdn);
        jedis.disconnect();

    }

    public void updateNodeRating() {
        VUtil.readNodes(host, (node) -> {
            if (node != null && node.head.rating != null) {
                VUtil.Rating data = new VUtil.Rating(node.head.rating);
                if (data.value < 3.1) {
                    node.head.rating.set(4, node.head.rating.get(4) + 10);
                    VUtil.writeNode(host, node);
                }
            }
        });
    }

    void updateMainFolder() {

        ArrayList<ArrayList<Object>> folderAdd = new ArrayList<>();
        int idp = ini.blogStreamIdn;
        boolean flagInner = ini.blogStreamLevel == 2;

        int idNext = tree.byIdn(idp).first, idNext2;
        while (idNext > 0) {
            NodeTreeElem ne = tree.byIdn(idNext);
            if (ne.first > 0 && idNext != ini.idnLabel) {
                ArrayList<Object> line = new ArrayList<>();
                line.add(idNext);
                line.add(ne.text);
                folderAdd.add(line);
                if (flagInner) {
                    idNext2 = ne.first;
                    while (idNext2 > 0) {
                        NodeTreeElem ne2 = tree.byIdn(idNext2);
                        if (ne2.first > 0) {
                            line = new ArrayList<>();
                            line.add(idNext2);
                            // —
                            line.add("\u2014 " + ne2.text);
                            folderAdd.add(line);
                        }
                        idNext2 = ne2.next;
                    }
                }
            }
            idNext = ne.next;
        }
        VUtil.writeJson(VUtil.getMainFolderPath(host), folderAdd);

    }

    void updateNotConfirmed() {
        // поднимаем наверх не подтвержденные
        PostgreSQL db = new PostgreSQL(host).connect();
        ArrayList<SQLIdn> select1 = db.select(SQLIdn.class)
                .fromTree()
                .and("flagFolder", false)
                .and("flagValid", false)
                .andMore("idn", 0)
                .order("idn", true)
                .exec();
        db.close();

        if (select1 == null) return;

        db = new PostgreSQL(host).connect();
        ZonedDateTime d = VUtil.nowGMT();
        for (SQLIdn row : select1) {
            d = d.plusSeconds(1);
            NodeData n = VUtil.readNode(host, row.idn);
            if (n == null) continue;
            n.head.date.set(0, VUtil.dateToJSON(d));
            n.head.date.set(1, VUtil.dateToJSON(d));
            VUtil.writeNode(host, n);
            db.update("tree").set("dateAdd", VUtil.dateToDB(d)).where("idn", row.idn).exec();
        }
        db.close();

    }

    void updateTopTen() {
        class TTCounter {
            int idn;
            int count;

            TTCounter idn(int val) {
                idn = val;
                return this;
            }

            TTCounter count(int val) {
                count = val;
                return this;
            }
        }

        class TTResult {
            List<TTCounter> viewed = new ArrayList<>();
            List<TTCounter> talked = new ArrayList<>();
            List<TTCounter> played = new ArrayList<>();
            List<TTCounter> liked = new ArrayList<>();
        }

        ZonedDateTime now = VUtil.nowGMT();
        ZonedDateTime prev = now.minusMonths(1);
        final int y1 = now.getYear(), y2 = prev.getYear();
        final String m1 = VUtil.last("0" + now.getMonthValue(), 2);
        final String m2 = VUtil.last("0" + prev.getMonthValue(), 2);

        int deltaDays = 15;
        TTResult resust = new TTResult();

        VUtil.readNodes(host, (node) -> {
            if (node != null && node.head != null && node.head.idp > 0 && node.head.link != null && node.head.link.get(1) != null && !node.head.flagFolder && !node.head.flagBlock && node.head.flagValid) {
                VUtil.NodeStatistic ns = VUtil.readStatNode(host, node.head.idn);

                int view = VUtil.getStatMonth(ns, y1, m1) + VUtil.getStatMonth(ns, y2, m2);
                if (view > 5) resust.viewed.add(new TTCounter().idn(node.head.idn).count(view));

                if (node.attach != null && node.attach.size() > 0) {
                    int commentCounter = 0, game = 0, like = 0;
                    for (NodeAttach na : node.attach) {
                        if (VUtil.isTrue(na.flagComment)) {
                            if (VUtil.dateFromJSON(na.date).until(now, ChronoUnit.DAYS) < deltaDays) {
                                if (na.gameResult != null) {
                                    game++;
                                } else {
                                    commentCounter++;
                                    if (na.like > 0) {
                                        like += na.like;
                                    }
                                }
                            }
                        }
                    }
                    if (commentCounter > 0) {
                        resust.talked.add(new TTCounter().idn(node.head.idn).count(commentCounter));
                    }
                    if (game > 0) {
                        resust.played.add(new TTCounter().idn(node.head.idn).count(game));
                    }
                    if (like > 0) {
                        resust.liked.add(new TTCounter().idn(node.head.idn).count(like));
                    }
                }
            }
        });

        resust.viewed.sort(Comparator.comparingInt(a -> -a.count));
        resust.talked.sort(Comparator.comparingInt(a -> -a.count));
        resust.played.sort(Comparator.comparingInt(a -> -a.count));
        resust.liked.sort(Comparator.comparingInt(a -> -a.count));

        if (resust.viewed.size() > 10) resust.viewed = resust.viewed.subList(0, 10);
        if (resust.talked.size() > 10) resust.talked = resust.talked.subList(0, 10);
        if (resust.played.size() > 10) resust.played = resust.played.subList(0, 10);
        if (resust.liked.size() > 10) resust.liked = resust.liked.subList(0, 10);


        TTTopTen topTen = new TTTopTen();
        topTen.viewed = resust.viewed.stream().map(l ->
                new TTNodeList().link(tree.getUrl(l.idn)).text(tree.byIdn(l.idn).text)
        ).collect(Collectors.toList());

        topTen.talked = resust.talked.stream().map(l ->
                new TTNodeList().link(tree.getUrl(l.idn)).text(tree.byIdn(l.idn).text)
        ).collect(Collectors.toList());

        topTen.played = resust.played.stream().map(l ->
                new TTNodeList().link(tree.getUrl(l.idn)).text(tree.byIdn(l.idn).text)
        ).collect(Collectors.toList());

        topTen.liked = resust.liked.stream().map(l ->
                new TTNodeList().link(tree.getUrl(l.idn)).text(tree.byIdn(l.idn).text)
        ).collect(Collectors.toList());

        VUtil.writeJson(VUtil.DIRFile + host + "/json/top-ten.json", topTen);

    }

    // =============

    NodeHead getNodeHead(int idn) {
        NodeHead res = nodeHeadList.get(idn);
        if (res == null) {
            NodeData node = VUtil.readNode(host, idn);
            if (node != null) {
                nodeHeadList.put(idn, node.head);
                res = node.head;
            }
        }
        return res;
    }

    ArrayList<Integer> getOrderedIdnList() {

        if (idnsOrdered == null) {

            PostgreSQL db = new PostgreSQL(host);
            db.connect();
            ArrayList<SQLIdn> rowsSelect = db.select(SQLIdn.class)
                    .fromTree()
                    .and("flagFolder", false)
                    .and("flagBlock", false)
                    .and("flagValid", true)
                    .andMore("idp", 0)
                    .order("dateAdd", false)
                    .exec();
            db.close();

            idnsOrdered = new ArrayList<>();
            if (rowsSelect != null) {
                for (SQLIdn line : rowsSelect) {
                    if (line.idp == ini.idnLabel) continue;
                    idnsOrdered.add(line.idn);
                }
            }
        }
        return idnsOrdered;
    }

    public static HashMap<String, Integer> loadWordCount(String host) {
        //java.lang.reflect.Type listType = new TypeToken<HashMap<String, Integer>>() {
        //}.getType();
        return VUtil.readJson(VUtil.DIRFile + host + "/json/wordcount.json", TypeToken.getParameterized(HashMap.class, String.class, Integer.class).getType());
    }

    void loaSData() {
        if (sapeData == null) {
            sapeData = new HashMap<>();
            if (ini.flagS) {
                String splitter = "\\|\\|SAPE\\|\\|";
                String text = VUtil.readFile(VUtil.DIRMemory + host + "/adv_sape.txt");
                if (text != null) {
                    for (String line : text.split("\n")) {
                        String[] data = line.split(splitter);
                        if (data.length == 2) {
                            sapeData.put(data[0].trim(), data[1].trim());
                        }
                    }
                }
            }
        }
    }

    public String getUrlByIdn(int idn) {
        return tree.getUrl(idn);
    }

    public String getUser(int idu) {
        String name = users.get(idu);
        if (name == null) {
            SiteUserData user = VUtil.readUser(host, idu);
            name = user == null ? "Гость" : user.name;
            users.put(idu, name);
        }
        return name;
    }

    String getUser(int idu, String anonym) {
        return getUser(idu) + (anonym == null ? "" : " (" + anonym + ")");
    }

    Element elemLinkExternal(Element elem, String link) {
        elem.removeAttr("href");
        elem.addClass("external");
        elem.attr("id", "h" + (++linkUniID));
        elem.attr("data-link", link.replaceAll("/", " "));
        return elem;
    }

    Element elemCanvasFromImg(Element elem) {

        if (elem.hasClass("done")) return elem;

        String className = elem.attr("class");

        boolean flagExt = elem.hasClass("slideshow") || elem.hasClass("as-is") || elem.attr("data-type") != null;

        String src = elem.attr("src");
        if (src.isEmpty()) {
            VUtil.error("ViewPage::elemCanvasFromImg", elem + "");
            return elem;
        }
        if (VUtil.isSVG(src)) return elem;

        String tagName = elem.parent().tagName();
        // span - command price-list
        if (tagName.equals("a") || tagName.equals("span") || flagExt) {
            elem.attr("data-src", src);
            elem.removeAttr("src");
            elem.removeAttr("alt");
            elem.removeAttr("srcset");
            elem.tagName("canvas");
        } else {

            TagBase canvas = new TagCanvas().dataSrc(src);
            if (!elem.attr("width").isEmpty())
                canvas.attr("width", elem.attr("width"));
            if (!elem.attr("height").isEmpty())
                canvas.attr("height", elem.attr("height"));


            new TagBase("a").addClass("thumbnail " + className)
                    .attr("aria-label", "Image")
                    .attr("href", src.replaceAll("/(150|250)/", "/600/"))
                    .append(canvas);
        }
        return elem;
    }

    void elemLink(Element elem, int idn) {
        if (!elem.attr("data-link").isEmpty()) return;

        String href = elem.attr("href");
        if (href == null) return;
        //elem.removeAttr("href");

        if (VUtil.reDgt.matcher(href).find()) {
            elem.attr("href", getUrlByIdn(Integer.parseInt(href)));
        } else {
            Matcher mgoto = reLinkGoto.matcher(href);
            if (mgoto.find()) {
                boolean flagSecure = mgoto.group(1) != null;
                String hostName = mgoto.group(2);
                String hostPath = mgoto.group(3);
                if (elem.attributes().hasKey(HtmlClean.ATTR_Direct)) {
                    elem.removeAttr(HtmlClean.ATTR_Direct);
                    elem.attr("href", (flagSecure ? "https:" : "http:") + "//" + hostName + hostPath);
                } else {
                    Matcher mkey = reLinkKey.matcher(hostName);
                    if (mkey.find()) {
                        String hostKey = mkey.group(1);
                        String dataLink = href.substring(6);
                        Affiliate.Link alink = affiliate.links.get(hostKey);
                        if (alink == null) {

                        } else {
                            dataLink = hostKey + hostPath;
                            if (elem.attr("is-shop").isEmpty()) {
                                if (elem.childNodes().size() > 0 && elem.childNodes().get(0).nodeName().equals("#text")) {
                                    elem.attr("data-shop", hostKey);
                                }
                            } else {
                                elem.removeAttr("is-shop");
                            }
                        }

                        elemLinkExternal(elem, dataLink);

                    } else {
                        //(VUtil.IFunc) () -> page.node.head.idn,
                        VUtil.error("ViewSite::elemLink", host, hostName, hostPath, idn, href);
                        //elem.removeAttr("href");
                    }
                }
            } else if (reLinkExternal.matcher(href).find()) {
                // todo старый вариант ATTR_Direct
                if (href.startsWith("//s/")) {
                    elem.attr("href", "https://" + href.substring(4));
                } else {
                    elem.attr("href", "http:" + href);
                }
            } else if (!reLinkLocal.matcher(href).find()) {
                elem.removeAttr("href");
                VUtil.error("ViewPage::elemLink OTHER", host, idn, href);
            }
        }
    }

    public static String getLinkHost(String text) {
        String linkHost = text.replace("www.", "");
        int pos = linkHost.indexOf(".");
        if (pos == -1) pos = linkHost.indexOf("/");
        return pos > -1 ? linkHost.substring(0, pos) : linkHost;
    }

    public boolean folderEnabled(int idn) {
        boolean res = false;
        int streamIdn = ini.blogStreamIdn;

        if (idn == ini.idnLabel) {
            res = true;
        } else if (streamIdn != idn) {
            if (tree.byIdn(idn).idp == streamIdn) {
                res = true;
            } else {
                if (ini.blogStreamLevel == 2) {
                    if (tree.byIdn(tree.byIdn(idn).idp).idp == streamIdn) {
                        res = true;
                    }
                }
            }
        }
        return res;
    }

    Element getParag(Element elem) {
        Element res = null;
        Element parent = elem.parent();
        while (parent != null) {
            if (reParag.matcher(parent.tagName()).find()) {
                res = parent;
                break;
            }
            parent = parent.parent();
        }
        return res;
    }

    public void postTwitter() {

        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.setDebugEnabled(true)
                .setOAuthConsumerKey("")
                .setOAuthConsumerSecret("")
                .setOAuthAccessToken("")
                .setOAuthAccessTokenSecret("");
        TwitterFactory tf = new TwitterFactory(cb.build());
        Twitter twitter = tf.getInstance();

        Element header = page.doc.getElementsByTag("h1").first();

        if (header == null || page.param.og == null || page.param.og.imgSrc == null) {

            return;
        }

        String imgPath = VUtil.DIRFile + host + page.param.og.imgSrc;
        if (!VUtil.pathExists(imgPath)) {
            imgPath = VUtil.DIRDomain + host + page.param.og.imgSrc;
            if (!VUtil.pathExists(imgPath)) {
                return;
            }
        }

        String statusMessage = "";
        for (int i = 0; i < 3; i++) {
            if (i < page.param.keywords.size()) {
                statusMessage += " #" + VUtil.camelcase(page.param.keywords.get(i));
            }
        }

        statusMessage += " " + header.text() + "\n" + protocol + host + page.param.path;
        statusMessage = statusMessage.trim();


        VUtil.println("ViewSite::postTwitterData", imgPath, statusMessage);

        try {
            StatusUpdate ad = new StatusUpdate(statusMessage);
            File file = new File(imgPath);
            InputStream inputStream = new FileInputStream(file);
            ad.setMedia("myMedia", inputStream);
            Status status = twitter.updateStatus(ad);


            VUtil.println("ViewSite::postTwitterStatus", status.getId());


        } catch (TwitterException e) {
            VUtil.error("ViewSite::postTwitterException", e);
            //e.printStackTrace();
        } catch (FileNotFoundException e) {
            VUtil.error("ViewSite::postTwitter::fileNotFound", e);
            //e.printStackTrace();
        }

    }


    // =========

    enum Type {
        full, page
    }


    public static class SQLIdn {
        public int idn;
        public int idp;
        //public int idu;
        //public int dateAdd;
    }

    // =========

    static public class MenuLine {
        public String text;
        public String path;
        public ArrayList<MenuLine> sub;

        public MenuLine(String path, String text) {
            this.path = path;
            this.text = text;
        }

        public MenuLine add(MenuLine line) {
            if (sub == null) sub = new ArrayList<>();
            sub.add(line);
            return this;
        }
    }

    public static class CatalogDict {
        NodeAttach jpg;
        NodeAttach mp3;
        NodeAttach mp4;
        NodeAttach webm;

        public CatalogDict set(String ext, NodeAttach text) {
            switch (ext) {
                case "jpg":
                    jpg = text;
                    break;
                case "mp3":
                    mp3 = text;
                    break;
                case "mp4":
                    mp4 = text;
                    break;
                case "webm":
                    webm = text;
                    break;
            }
            return this;
        }
    }

    public static class Size {
        public int width;
        public int height;

        public Size(int size, int width, int height) {
            double k = Math.max(width / (double) size, height / (double) size);
            this.width = (int) Math.round(width / k);
            this.height = (int) Math.round(height / k);
        }
    }

    public class DataAnonym {
        public String src;
        public int w;
        public int h;
        public int idn;
        public int idu;
        public int idf;
        public String content;
        public String name;
        public String date;
        public String ip;
        public String anonym;

    }

    // ===========
    public class SitePriceElem {
        String type;
        String group;
        String name;
        String image;
        String url;
        float price;
    }

    public class SitePrice {

    }

    // ===========
    public class TTTopTen {
        List<TTNodeList> viewed;
        List<TTNodeList> talked;
        List<TTNodeList> played;
        List<TTNodeList> liked;
    }

    public class TTNodeList {
        String link;
        String text;

        TTNodeList link(String val) {
            link = val;
            return this;
        }

        TTNodeList text(String val) {
            text = val;
            return this;
        }
    }

    public class ElasticPriceItem {
        public String type; //'book' | 'stuff'
        public String label;
        public ArrayList<String> tags;
        public boolean available;
        public String url;
        public double price;
        public String picture;
        public String host;

        int priceInt() {
            return (int) price;
        }
    }
}
