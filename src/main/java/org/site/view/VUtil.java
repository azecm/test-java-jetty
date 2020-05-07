package org.site.view;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.site.elements.NodeData;
import org.site.elements.NodeTree;
import org.site.elements.NodeTreeElem;
import org.site.elements.SiteUserData;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class VUtil {

    public static final String PATH_PREF = "";//-nj-


    public static final int[] sizes = new int[]{150, 250, 600};

    public static final String endsJSON = ".json";
    static final String endsSVG = ".svg";

    private static final String[] months = {
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"};

    public static final String DIRRoot = "/usr/local/www/";
    public static final String DIRCache = DIRRoot + "cache/";
    public static final String DIRPrice = DIRCache + "price/";
    public static final String DIRTemp = DIRCache + "temp/";
    public static final String DIRMemory = DIRCache + "memory/";
    public static final String DIRHtml = DIRCache + "html/";
    public static final String DIRFile = DIRCache + "file/";
    public static final String DIRStat = DIRRoot + "stat/";
    public static final String DIRDomain = DIRRoot + "data/domain/";
    public static final String DIRStatistic = DIRRoot + "data/statistic/";
    public static final String DIRPriceSrc = DIRRoot + "data/price/";
    public static final String DIRPublic = DIRRoot + "public/";
    public static final String DIRTemplate = DIRRoot + "public/template/";
    public static final String DIRTemplateNew = DIRRoot + "public/template-new/";
    public static final String DIRLog = DIRRoot + "app.log/";

    @SuppressWarnings("unused")
    public static final String DIRSession = DIRCache + "session/";
    @SuppressWarnings("unused")
    public static final String DIRApp = DIRRoot + "app.java/";
    @SuppressWarnings("unused")
    public static final String DIROldApp = DIRRoot + "app.back/";

    static UserPrincipal fileOwner;

    final static String ISO_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSX";
    final static DateTimeFormatter dtISOFormat = DateTimeFormatter.ofPattern(ISO_FORMAT);


    static final Pattern reImgKey = Pattern.compile("(?:/file/)?(\\d{4})(?:/(150|250|600))?/(\\d{4})\\.(png|jpg|svg)$");
    static final Pattern reTextClean0 = Pattern.compile("[\\u0000-\\u001f\\u007f-\\u009f\\u00ad\\u0600-\\u0604\\u070f\\u17b4\\u17b5\\u200c-\\u200f\\u2028-\\u202f\\u2060-\\u206f\\ufeff\\ufff0-\\uffff]", Pattern.MULTILINE);
    static final Pattern reComment = Pattern.compile("\\n\\s*//[^\\n]*(\\n)");
    static final Pattern reCamelCase = Pattern.compile("[-\\s]+(.)");
    public static final Pattern reDgt = Pattern.compile("^\\d+$");
    static final Pattern reDgtPref = Pattern.compile("^(\\d+)");


    public static String redisKeyUrl(String host) {
        return host + ":url";
    }

    @SuppressWarnings("unused")
    public static String redisKeyTree(String host, int idn) {
        return host + ":tree:" + idn;
    }

    @SuppressWarnings("unused")
    public static String redisKeyUser(String host) {
        return host + ":user";
    }

    public static boolean pathExists(String pathName) {
        return Files.exists(Paths.get(pathName));
    }

    public static String pathToImageSrc(String host, String src) {
        return pathToImageFolder(host) + src;
    }

    public static String pathToImageFolder(String host) {
        return DIRDomain + host + "/file/";
    }

    public static ArrayList<ImageCache> pathToImages(String host, String src) {
        ArrayList<ImageCache> paths = pathToImageCache(host, src);
        paths.add(new ImageCache(0, pathToImageSrc(host, src)));
        return paths;
    }

    public static ArrayList<ImageCache> pathToImageCache(String host, String src) {
        String[] srcList = src.split("/");
        String path = VUtil.DIRFile + host + "/file/";
        ArrayList<ImageCache> paths = new ArrayList<>();
        //for (int size : sizes) paths.add(path + srcList[0] + (size > 0 ? ("/" + size + "/") : "/") + srcList[1]);
        for (int size : sizes) {
            //paths.add(path + srcList[0] + ("/" + size + "/") + srcList[1]);
            paths.add(new ImageCache(size, path + srcList[0] + ("/" + size + "/") + srcList[1]));
        }
        return paths;
    }

    public static void pathRemove(String pathName) {
        Path path = Paths.get(pathName);
        if (Files.exists(path)) {
            try {
                Files.delete(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public static String readFile(Path path) {
        String content = null;
        if (Files.exists(path)) {
            try {
                content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                content = content.replaceAll("^\\uFEFF", "");
                if (path.getFileName().endsWith(".html")) {
                    content = VUtil.htmlClean(content);
                }
            } catch (IOException e) {
                System.err.println("FwUtil::readFile::" + path.getFileName());
                e.printStackTrace();
            }
        }
        return content;
    }

    public static String readFile(String pathName) {
        return readFile(Paths.get(pathName));
    }

    public static void writeFile(Path path, String content) {
        writeFile(path, content.getBytes(StandardCharsets.UTF_8));
    }

    public static void writeFile(Path path, byte[] bytes) {
        try {
            Files.write(path, bytes);
        } catch (IOException e) {
            System.err.println("FwUtil::writeFile::" + path);
            e.printStackTrace();
        }
        fileOwner(path);
    }

    public static void fileOwner(Path path) {
        try {
            if (fileOwner == null) {
                UserPrincipalLookupService lookupService = FileSystems.getDefault().getUserPrincipalLookupService();
                fileOwner = lookupService.lookupPrincipalByName("www");
            }
            Files.setOwner(path, fileOwner);
        } catch (IOException e) {
            error("FwUtil::fileOwner::" + path, e.getMessage());
        }
    }


    public static void writeFile(String path, String content) {
        writeFile(Paths.get(path), content);
    }

    static String getCacheFilePath(String host, String url) {
        try {
            url = URLDecoder.decode(url, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            error("FwUtil::writeCacheFile", host, url, e.toString());
        }
        if (url.endsWith("/")) url += "index";

        return DIRHtml + host + url;
    }

    public static String readCacheFile(String host, String url) {
        return readFile(getCacheFilePath(host, url));
    }

    public static void writeCacheFile(String host, String url, String content) {
        String pathString = getCacheFilePath(host, url);
        createDirs(pathString);
        writeFile(pathString + PATH_PREF, content);
    }

    public static void createDirs(Path pathSrc, boolean isFolder) {
        Path path = isFolder ? pathSrc : pathSrc.getParent();
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
                fileOwner(path);
            } catch (IOException e) {
                error("FwUtil::createDirs", e.toString());
            }
        }
    }


    public static void createDirs(String pathName, boolean isFolder) {
        createDirs(Paths.get(pathName), isFolder);
    }

    public static void createDirs(String pathName) {
        createDirs(Paths.get(pathName), pathName.endsWith("/"));
    }

    public static int idnFromFileName(String fileName) {
        int idn = 0;
        Matcher m = reDgtPref.matcher(fileName);
        if (m.find()) {
            idn = Integer.parseInt(m.group(1));
        }
        return idn;
    }


    public static String exec(ArrayList<String> cmd) {
        String s;


        StringBuilder sbOut = new StringBuilder();
        StringBuilder sbErr = new StringBuilder();

        ProcessBuilder b = new ProcessBuilder(cmd);

        try {
            Process proc = b.start();

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

            while ((s = stdInput.readLine()) != null) sbOut.append(s).append("\n");
            while ((s = stdError.readLine()) != null) sbErr.append(s).append("\n");

            proc.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        String err = sbErr.toString().trim();
        if (!err.isEmpty()) {
            error("VUtil::exec" + "\n" + String.join(" ", cmd) + "\n" + err);
        }


        return sbOut.toString().trim();

    }

    public static String exec(String... cmd) {
        if (cmd.length == 1) cmd = cmd[0].split(" ");
        ArrayList<String> res = new ArrayList<>(Arrays.asList(cmd));
        return exec(res);
    }


    public static void tagDrop(Element l) {
        l.parent().insertChildren(l.siblingIndex(), l.childNodes());
        l.remove();
    }

    static int dirlistLastModifiedComp(Path p1, Path p2) {
        try {
            return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static List<Path> dirlistLastModified(String pathName) {
        List<Path> res = new ArrayList<>();
        try {
            res = Files.list(Paths.get(pathName))
                    .filter(Files::isRegularFile)
                    .sorted(VUtil::dirlistLastModifiedComp)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return res;
    }


    public static String lastModified(String pathName) {
        String result = "";
        Path path = Paths.get(pathName);
        try {
            result = Files.getLastModifiedTime(path).toString();
        } catch (IOException e) {
            System.err.println("FwUtil::lastModified::" + pathName);
            e.printStackTrace();
        }
        return result;
    }

    public static String getHash256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().encodeToString(hash).replaceAll("=", "");
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static String getHostKey(String host) {
        return host.replaceAll("[^\\w]", "");
    }

    @Nullable
    public static NodeData readNode(NodeTree tree, String path) {
        NodeData node = null;
        NodeTreeElem elem = tree.byUrl(path);
        if (elem != null) node = readNode(tree.getHost(), elem.idn);
        return node;
    }

    @Nullable
    public static NodeData readNode(String host, int idn) {
        String path = getNodePath(host, idn);
        return readJson(path, NodeData.class);
    }

    public static String jsonString(Object obj) {
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        return gson.toJson(obj);
    }

    @SuppressWarnings("Duplicates")
    public static <T> T jsonData(String jsonString, Class<T> classRef) {
        T o = null;
        try {
            o = new Gson().fromJson(jsonString, classRef);
        } catch (JsonSyntaxException e) {
            println(dateLog(), jsonString);
            e.printStackTrace();
        }
        return o;
    }

    @SuppressWarnings("Duplicates")
    public static <T> T jsonData(String jsonString, Type typeOfT) {
        T o = null;
        try {
            o = new Gson().fromJson(jsonString, typeOfT);
        } catch (JsonSyntaxException e) {
            println(dateLog(), jsonString);
            e.printStackTrace();
        }
        return o;
    }

    public static void writeNode(String host, NodeData node) {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        writeFile(getNodePath(host, node.head.idn), gson.toJson(node));
    }

    public static String endsJson(String path) {
        return isJson(path) ? path : (path + endsJSON);
    }

    static String jsonComment(String text) {
        return text == null ? null : reComment.matcher(text).replaceAll("$1");
    }

    public static void delete(Path path) {
        try {
            if (Files.exists(path)) Files.delete(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Nullable
    public static byte[] getUrlRaw(String url) {
        byte[] res = null;

        try {
            Connection.Response resp = Jsoup.connect(url).timeout(10000).ignoreContentType(true)
                    .userAgent("Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:70.0) Gecko/20100101 Firefox/70.0")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "ru-RU,ru;q=0.8,en-US;q=0.5,en;q=0.3")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .execute();
            if (resp.statusCode() == 200) res = resp.bodyAsBytes();
            else error("getUrlRaw::statusCode(1): " + resp.statusCode(), url);
        } catch (org.jsoup.HttpStatusException e) {
            error("getUrlRaw::statusCode(2): " + e.getStatusCode(), url);
        } catch (IOException e) {
            error("getUrlRaw::statusCode(3): ", url, e.getMessage());
            //e.printStackTrace();
        }
        return res;
    }

    public static URL getURL(String urlText) {
        URL url = null;
        try {
            url = new URL(urlText);
        } catch (MalformedURLException e) {

        }
        return url;
    }

    public static String getUrl(String url) {
        String res = null;
        try {
            Connection.Response resp = Jsoup.connect(url).userAgent("Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:62.0) Gecko/20100101 Firefox/62.0").execute();
            if (resp.statusCode() == 200) res = resp.body();
        } catch (IOException e) {
            System.err.println("---" + e.getMessage());
            //System.err.println(e.getCause());
            System.err.println(e.toString());
            //e.printStackTrace();
        }
        return res;
    }

    @SuppressWarnings("Duplicates")
    public static <T> T readJson(Path path, Class<T> classRef) {

        return getClassFromJson(readFile(path), classRef);
    }

    public static <T> T getClassFromJson(String text, Class<T> classRef) {
        T o = null;
        try {
            o = new Gson().fromJson(jsonComment(text), classRef);
        } catch (JsonSyntaxException e) {
            println(dateLog(), text);
            e.printStackTrace();
        }
        return o;
    }

    @SuppressWarnings("Duplicates")
    public static <T> T readJson(Path path, Type typeOfT) {
        T o = null;
        try {
            o = new Gson().fromJson(jsonComment(readFile(path)), typeOfT);
        } catch (JsonSyntaxException e) {
            println(dateLog(), path.toString());
            e.printStackTrace();
        }
        return o;
    }

    public static <T> T readJson(String path, Class<T> classRef) {
        return readJson(Paths.get(endsJson(path)), classRef);
    }

    public static <T> T readJson(String path, Type typeOfT) {
        return readJson(Paths.get(endsJson(path)), typeOfT);
    }

    @SuppressWarnings("unused")
    public static void writeJson(Path path, Object data) {
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        writeFile(path, gson.toJson(data));
    }

    public static void writeJson(String path, Object data) {
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        writeFile(endsJson(path), gson.toJson(data));
    }

    public static void writeJsonPretty(Path path, Object data) {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        writeFile(path, gson.toJson(data));
    }

    public static void writeJsonPretty(String path, Object data) {
        writeJsonPretty(Paths.get(endsJson(path)), data);
    }

    @Nullable
    public static SiteUserData readUser(String host, int idu) {
        String path = endsJson(getUserPath(host, idu));
        return readJson(path, SiteUserData.class);
    }

    public static void writeUser(String host, SiteUserData user) {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        writeFile(getUserPath(host, user.idu), gson.toJson(user));
    }

    public static NodeStatistic readStatNode(String host, int idn) {
        String path = getStatPath(host, idn);
        NodeStatistic n = readJson(path, NodeStatistic.class);
        if (n == null) {
            n = new NodeStatistic();
            n.year = new HashMap<>();
        }
        return n;
    }

    public static void writeStatNode(String host, int idn, NodeStatistic data) {
        String path = getStatPath(host, idn);
        writeJson(path, data);
    }


    public static int getStatMonth(NodeStatistic ns, int year, String month) {
        int res = 0;
        if (ns != null) {
            final HashMap<String, Integer> y = ns.year.get(year + "");
            if (y != null) {
                Integer m = y.get(month);
                if (m != null) {
                    res = m;
                }
            }
        }
        return res;
    }

    public static String last(String str, int val) {
        return str.length() > val ? str.substring(str.length() - val) : str;
    }

    public static int getNodeViews(String host, int idn) {
        int counterViews = 0;
        VUtil.NodeStatistic d = VUtil.readStatNode(host, idn);
        if (d != null) {
            for (HashMap<String, Integer> year : d.year.values()) {
                for (Integer monthVal : year.values()) {
                    counterViews += monthVal;
                }
            }
        }
        return counterViews;
    }

    public static String getMainFolderPath(String host) {
        return DIRMemory + host + "/folderAdd" + endsJSON;
    }

    public static String getFolder(int id) {
        return ((int) Math.floor(id / 5000.0)) + "/" + id + endsJSON;
    }

    public static String getNodePath(String host, int idn) {
        return endsJson(DIRDomain + host + "/node/" + getFolder(idn));
    }

    public static String getStatPath(String host, int idn) {
        return endsJson(DIRStatistic + "domain/" + host + "/" + getFolder(idn));
    }

    @SuppressWarnings("unused")
    public static String getUsersPath(String host) {
        return VUtil.DIRDomain + host + "/user/";
    }

    public static String getUserPath(String host, int idu) {
        return endsJson(VUtil.DIRDomain + host + "/user/" + getFolder(idu));
    }

    static ArrayList<String> toListString(Object... data) {
        ArrayList<String> l = new ArrayList<>();
        for (Object o : data) {
            if (o != null && o.getClass().toString().contains("Lambda")) {
                try {
                    l.add(((IFunc) o).method() + "");
                } catch (NullPointerException e) {
                    l.add("NullPointerException");
                }
            } else {
                l.add(o + "");
            }
        }
        return l;
    }

    public static void println(Object... data) {
        System.out.println(String.join(", ", toListString(data)));
    }

    public static void error(Object... data) {
        System.err.println(String.join(", ", toListString(data)));
    }

    // ============

    @FunctionalInterface
    public interface IFuncPath {
        void method(Path path);
    }

    @FunctionalInterface
    public interface IFuncNodePath {
        void method(NodeData node);
    }

    @FunctionalInterface
    public interface IFuncUserPath {
        void method(SiteUserData node);
    }

    @SuppressWarnings("SameParameterValue")
    static void readSourceData(String host, String dirName, IFuncPath fn) {
        try {
            Files.walk(Paths.get(VUtil.DIRDomain + host + "/" + dirName + "/"))
                    .filter(Files::isRegularFile)
                    .forEach(fn::method);
        } catch (IOException e) {
            error("VUtil::readSourceData", host, dirName, e);
        }
    }

    public static void readNodes(String host, IFuncNodePath fnNode) {
        readSourceData(host, "node", (Path fileName) -> {
                    NodeData node = readJson(fileName, NodeData.class);
                    fnNode.method(node);
                }
        );
    }

    public static void readUsers(String host, IFuncUserPath fnUser) {
        readSourceData(host, "user", (Path fileName) ->
                fnUser.method(readJson(fileName, SiteUserData.class))
        );
    }

    // ============

    @FunctionalInterface
    public interface IFunc {
        Object method();
    }

    public static class Timer {
        static long tStart;

        public Timer() {
            //System.nanoTime()
            tStart = System.currentTimeMillis();
        }

        public void end() {
            long tEnd = System.currentTimeMillis();
            int seconds = (int) Math.floor((tEnd - tStart) / 1000.0);
            int minutes = (int) Math.floor(seconds / 60.0);
            int msec = (int) ((tEnd - tStart) - seconds * 1000);
            if (minutes > 0) {
                System.out.println(minutes + "m" + (seconds - minutes * 60) + "s");
            } else {
                System.out.println(seconds + "." + msec + "s");
            }
        }

        public void endLog(String label) {
            long tEnd = System.currentTimeMillis();
            int seconds = (int) Math.floor((tEnd - tStart) / 1000.0);
            tEnd = ((tEnd - tStart) % 1000);
            int minutes = (int) Math.floor(seconds / 60.0);
            System.out.printf("%s: %02d:%02d.%03d\n", label, minutes, seconds, tEnd);
        }

        public long ms() {
            long tEnd = System.currentTimeMillis();
            return tEnd - tStart;
        }
    }

    public static Timer timer() {
        return new Timer();
    }

    public static String htmlClean(String html) {
        html = html.replaceAll("\\s+", " ");
        html = html.replaceAll("\\s*(</?(div|li|nav|ul|ol|footer|main|aside|p|table|tr|td|tbody|h\\d)[^>]*>)\\s*", "$1");
        return html;
    }

    public static Document htmlSetting(Document doc) {
        doc.outputSettings().charset("utf-8").indentAmount(0).prettyPrint(false).escapeMode(Entities.EscapeMode.xhtml);
        return doc;
    }

    public static boolean notEmpty(String text) {
        return text != null && !text.isEmpty();
    }

    public static boolean isEmpty(String text) {
        return !notEmpty(text);
    }

    public static String nullToString(String text) {
        return text == null ? "" : text;
    }

    public static String getFileExt(String fileName) {
        return fileName == null ? "" : fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    public static String getFileName(String fileName) {
        return fileName.substring(fileName.lastIndexOf('/') + 1);
    }

    public static String camelcase(String str) {
        Matcher matcher = reCamelCase.matcher(str);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, matcher.group(1).toUpperCase());
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static String html(Document doc) {
        return htmlSetting(doc).body().html();
    }

    public static String imageKey(String path) {
        Matcher m = reImgKey.matcher(path);
        return m.find() ? (m.group(1) + '/' + m.group(3) + '.' + m.group(4)) : "";
    }

    public static int imageSize(String path) {
        Matcher m = reImgKey.matcher(path);
        return m.find() && m.group(2) != null ? Integer.parseInt(m.group(2).trim()) : 0;
    }

    public static String imageSize(String path, int size) {
        String res = "";
        String key = imageKey(path);
        if (!key.isEmpty()) {
            res = "/file/" + (!isSVG(key) && size > 0 ? (key.replace("/", "/" + size + "/")) : key);
            //println(path, res);
        } else {
            error("FwUtil::imageSize:", path);
        }
        return res;
    }

    public static String imageSize600(String path) {
        return imageSize(path, 600);
    }

    public static String textClean(String text) {
        return textClean(text, 0);
    }

    public static String textClean(String text, int flagFull) {
        if (text == null) return "";

        switch (flagFull) {
            case 1:

                text = text.replaceAll("[\\u0000-\\u0009\\u000b-\\u001f\\u007f-\\u009f\\u00ad\\u0600-\\u0604\\u070f\\u17b4\\u17b5\\u200c-\\u200f\\u2028-\\u202f\\u2060-\\u206f\\ufeff\\ufff0-\\uffff]", " ").replaceAll("[^\\S\\n]+", " ");
                break;
            case 2:

                text = text.replaceAll("[\\u0000-\\u0008\\u000b-\\u001f\\u007f-\\u009f\\u00ad\\u0600-\\u0604\\u070f\\u17b4\\u17b5\\u200c-\\u200f\\u2028-\\u202f\\u2060-\\u206f\\ufeff\\ufff0-\\uffff]", " ").replaceAll("[^\\S\\n\\t]+", " ");
                break;
            default:

                text = reTextClean0.matcher(text).replaceAll(" ").replaceAll("\\s+", " ");
                break;
        }
        return text;
    }

    public static String textOnly(@Nullable String html) {
        return textOnly(html, true);
    }

    public static String textOnly(@Nullable String html, boolean flagInline) {
        if (html == null || html.trim().isEmpty()) {
            html = "";
        } else {
            html = html.replaceAll("</?[a-z][^>]*>", " ").replaceAll("&#?([a-z0-9]+);", " ");

            if (flagInline) {
                html = html.replaceAll("[\\n\\r\\t\\s]+", " ");
            } else {
                html = html.replaceAll("[\\r\\t\\s]+", " ");
            }

            html = html.replaceAll("\\s+", " ").trim();

        }
        return html;
    }

    public static int dateToDB(ZonedDateTime d) {
        return (int) (d.toEpochSecond() - 1400000000);
    }

    public static String dateToJSON(ZonedDateTime d) {
        return d.format(dtISOFormat);
    }

    public static String dateToLog(ZonedDateTime d) {
        return d.format(dtISOFormat);
    }

    public static String dateLog() {
        return dateToLog(nowMSK());
    }

    public static void dateLogPrint() {
        println(dateToLog(nowMSK()));
    }

    public static ZonedDateTime dateFromJSON(String text) {

        DateTimeFormatter dtf = DateTimeFormatter.ISO_DATE_TIME;
        return ZonedDateTime.parse(text, dtf);

    }

    public static String dateSimpleStr(String dateGet) {
        return dateGet.substring(0, 19).replace("T", " ");
    }

    public static String toUTCString(ZonedDateTime date) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH);
        return formatter.format(date);
    }

    public static boolean isTrue(Boolean val) {
        return val != null && val.equals(true);
    }

    public static boolean isFalse(Boolean val) {
        return val == null || val.equals(false);
    }

    public static ZonedDateTime nowGMT() {
        return ZonedDateTime.now(ZoneId.of("GMT"));
    }

    public static ZonedDateTime nowMSK() {
        return ZonedDateTime.now(ZoneId.of("Europe/Moscow"));
    }

    public static String dateString(String dateGet) {
        String res;

        ZonedDateTime date = dateFromJSON(dateGet);
        if (date.until(VUtil.nowGMT(), ChronoUnit.DAYS) > 7) {
            res = String.format("%d %s %d", date.getDayOfMonth(), months[date.getMonthValue() - 1], date.getYear());
        } else {
            res = String.format("%d %s %d, %02d:%02d:%02d", date.getDayOfMonth(), months[date.getMonthValue() - 1], date.getYear(), date.getHour(), date.getMinute(), date.getSecond());
        }

        return res;
    }

    public static String decodeURI(String text) {
        try {
            text = URLDecoder.decode(text, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            if (!text.endsWith("%")) error("VUtil::decodeURI", text, e);
            text = "";
        }
        return text;
    }

    public static String encodeURI(String text) {
        try {
            text = URLEncoder.encode(text, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            error("VUtil::encodeURI", text, e);
        }
        return text;
    }

    public static String encodeURIComponent(String s) {
        String result;
        try {
            result = URLEncoder.encode(s, "UTF-8")
                    //
                    .replaceAll("\\+", "%20")
                    .replaceAll("%21", "!")
                    .replaceAll("%27", "'")
                    .replaceAll("%28", "(")
                    .replaceAll("%29", ")")
                    .replaceAll("%7E", "~")
            //.replaceAll("\\%25", "%")
            ;
        } catch (UnsupportedEncodingException e) {
            result = s;
        }
        return result;
    }

    public static boolean isSVG(String src) {
        return src.endsWith(endsSVG);
    }

    public static boolean isJson(String src) {
        return src.endsWith(endsJSON);
    }

    public static boolean isJson(Path src) {
        return isJson(src.getFileName().toString());
    }

    public static int getIntSelect(String str) {
        if (str == null) return 0;
        str = str.replaceAll("\\D", "");
        Matcher m = reDgt.matcher(str);
        return m.find() ? Integer.parseInt(str, 10) : 0;
    }

    public static int getInt(String str) {
        if (str == null) return 0;
        Matcher m = reDgt.matcher(str);
        return m.find() ? Integer.parseInt(str, 10) : 0;
    }

    public static class NodeStatistic {
        public HashMap<String, HashMap<String, Integer>> year;
    }

    public static class Rating {
        public int count;
        public double value;
        public int bestRating = 5;
        public List<Integer> data;

        public List<Integer> init() {
            return Arrays.asList(0, 0, 0, 0, 1);
        }

        public Rating(List<Integer> rating) {

            if (rating == null || rating.size() != 5) rating = init();
            else rating = rating.stream().map(v -> v == null ? 0 : v).collect(Collectors.toList());
            data = rating;

            for (int v : rating) count += v;
            for (int i = 0, im = rating.size(); i < im; i++) value += rating.get(i) * (i + 1);
            value = Math.round(10 * value / (double) count) / 10.0;
        }
    }

    // =========

    public static class ImageCache {
        public String path;
        public int size;

        public ImageCache(int size, String path) {
            this.path = path;
            this.size = size;
        }
    }

}
