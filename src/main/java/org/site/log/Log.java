package org.site.log;

import org.site.elements.Affiliate;
import org.site.elements.NodeTree;
import org.site.elements.NodeTreeElem;
import org.site.elements.StatData;
import org.site.view.VUtil;
import org.site.view.ViewSite;

import java.io.IOException;
import java.net.IDN;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class Log {

    int dayAgo = 0;
    boolean flagVisit = false;
    ZonedDateTime now = VUtil.nowMSK();
    String path1 = "/var/log/nginx/nginx.log";
    String path2 = "/var/log/nginx/nginx.traffic.log";
    String[] dayName = new String[]{"", "понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье"};


    String dateStr;

    HashMap<String, LogHostStat> stat = new HashMap<>();
    HashMap<String, HashMap<String, User>> hash = new HashMap<>();
    HashMap<String, HashMap<String, Integer>> pageStat = new HashMap<>();

    Affiliate affiliate = new Affiliate();

    HashSet<String> hosts = new HashSet<>(ViewSite.hosts());


    final Pattern reRefHost = Pattern.compile("//([^/:]+)(?::\\d+)?(/.*)$");
    final Pattern reRefPage = Pattern.compile("[^#?]+");
    final Pattern excludeExt = Pattern.compile("\\.(gif|png|rss|js)");
    final Pattern rePhrase = Pattern.compile("^[a-zа-яё][\\w\\sа-яё,.!-]+$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    final Pattern reGoto = Pattern.compile("^/goto/(?:s/)?([^/]+)(.*)$");
    final Pattern reTraf = Pattern.compile("([^\\s]+) (\\d\\d\\d) \\[([^]]+)] ([^\\s]+) (\\d+) (\\d+) \"([^\"]+)\" \"([^\"]+)\"");

    HashSet<String> folderDisabled = new HashSet<>(Arrays.asList("js", "goto", "xhr", "json"));

    final Pattern enabledUrlParam = Pattern.compile("^(q|p|text|query|s|search|searchfor|kwd|pq|ust|oq|usstr|questiontext)$");

    public Log() {
        init();
    }

    public Log(int days) {
        dayAgo = days;
        init();
    }

    public Log(int days, boolean flag) {
        dayAgo = days;
        flagVisit = flag;
        init();
    }

    void init() {

        if (dayAgo > 0) {

            // /usr/local/www/cache/nginx.log.0

            now = now.minusDays(dayAgo);
            path1 = "/usr/local/www/cache/nginx.log." + (dayAgo - 1);
            path2 = "/usr/local/www/cache/nginx.traffic.log." + (dayAgo - 1);

            if (VUtil.pathExists(path1)) VUtil.pathRemove(path1);
            if (VUtil.pathExists(path2)) VUtil.pathRemove(path2);

            VUtil.exec("cp /var/log/nginx/nginx.log." + (dayAgo - 1) + ".bz2 /usr/local/www/cache/");
            VUtil.exec("gzip -d /usr/local/www/cache/nginx.log." + (dayAgo - 1) + ".bz2");
            VUtil.exec("cp /var/log/nginx/nginx.traffic.log." + (dayAgo - 1) + ".bz2 /usr/local/www/cache/");
            VUtil.exec("gzip -d /usr/local/www/cache/nginx.traffic.log." + (dayAgo - 1) + ".bz2");

        }

        dateStr = now.toString().substring(0, 10).replaceAll("-", ".") + " (" + dayName[now.getDayOfWeek().getValue()] + ")";

        readFirst();
        readSecond();
        finish();

    }

    void finish() {
        if (dayAgo > 0) {
            VUtil.exec("find", "/usr/local/www/cache", "-name", "nginx.*", "-type", "f", "-maxdepth", "1", "-delete");
        }

        for (String hostname : hash.keySet()) {
            LogHostStat statHost = stat.get(hostname);
            for (User user : hash.get(hostname).values()) {
                statHost.user[0]++;
                statHost.time[0] += user.time;
                if (user.enable) {
                    statHost.user[1]++;
                    statHost.hit[1] += user.hit > 0 ? user.hit : 1;
                    statHost.time[1] += user.time;
                }
            }
            statHost.time[0] = Math.round(statHost.time[0]);
            statHost.time[1] = Math.round(statHost.time[1]);
        }

        for (String hostname : stat.keySet()) {
            String pathToSitemap = VUtil.DIRDomain + hostname + "/sitemap.xml";
            if (!VUtil.pathExists(pathToSitemap)) continue;
            LogHostStat statHost = stat.get(hostname);

            statHost.pages = VUtil.readFile(pathToSitemap).split("<loc>").length - 1;
            statHost.duration = Math.round(statHost.duration);

            statHost.status = setStatus(statHost.status);

            statHost.total.referer = statHost.referer.values().stream().mapToInt(i -> i).sum();
            statHost.total.phrase = statHost.phrase.values().stream().mapToInt(i -> i).sum();
            statHost.total.entry = statHost.entry.values().stream().mapToInt(i -> i).sum();

            statHost.referer = setTopValue(statHost.referer);
            statHost.phrase = setTopValue(statHost.phrase);
            statHost.entry = setTopValue(statHost.entry);

            statHost.affiliate.host = setTopValue(statHost.affiliate.host);
            statHost.affiliate.src = setTopValue(statHost.affiliate.src);
            statHost.affiliate.dst = setTopValue(statHost.affiliate.dst);

            statHost.go.host = setTopValue(statHost.go.host);
            statHost.go.src = setTopValue(statHost.go.src);
            statHost.go.dst = setTopValue(statHost.go.dst);

            statHost.follow.src = setTopValue(statHost.follow.src);
            statHost.follow.dst = setTopValue(statHost.follow.dst);

        }

        String pathStat = VUtil.DIRStat + now.toString().substring(0, 10).replaceAll("-", "/") + VUtil.endsJSON;
        StatData data = new StatData();
        data.date = dateStr;
        data.hosts = stat;
        //VUtil.testDir(Paths.get(pathStat).getParent());
        VUtil.createDirs(Paths.get(pathStat).getParent(), true);
        VUtil.writeJson(pathStat, data);

        if (flagVisit) {
            for (String hostName : pageStat.keySet()) {
                NodeTree tree = new NodeTree(hostName);

                HashMap<String, Integer> elemStat = pageStat.get(hostName);
                for (String path : elemStat.keySet()) {
                    NodeTreeElem et = tree.byUrl(path);
                    if (et == null) continue;

                    VUtil.NodeStatistic n = VUtil.readStatNode(hostName, et.idn);

                    String ykey = now.toString().substring(0, 4);
                    String mkey = now.toString().substring(5, 7);

                    n.year.computeIfAbsent(ykey, k -> new HashMap<>());
                    HashMap<String, Integer> y = n.year.get(ykey);
                    y.merge(mkey, elemStat.get(path), (a, b) -> a + b);

                    VUtil.writeStatNode(hostName, et.idn, n);

                }
            }
        }
    }

    HashMap<String, Integer> setTopValue(HashMap<String, Integer> obj) {
        HashMap<String, Integer> sorted = obj
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(50)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (e1, e2) -> e1, LinkedHashMap::new));
        return sorted;
    }

    HashMap<Integer, Integer> setStatus(HashMap<Integer, Integer> obj) {
        HashMap<Integer, Integer> sorted = obj
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (e1, e2) -> e1, LinkedHashMap::new));
        return sorted;
    }

    void readFirst() {
        try {
            Files.lines(Paths.get(path1)).forEach(this::readFirstLine);
        } catch (IOException e) {
            System.err.println(e);
        }
    }

    void readFirstLine(String text) {
        Line line = new Line(text);
        if (line.isEmpty()) return;

        if (!hosts.contains(line.host)) return;


        if (Util.reIgnore.matcher(line.browser).find()) return;

        int timeInd = getTimeInd(line.timePosition());
        setTraffic(line.host, line.status, line.duration, line.bytesReceived, line.bytesSent, timeInd);


        String hostName = line.host;
        String url = line.url;

        LogHostStat statHost = stat.get(hostName);
        HashMap<String, User> hashHost = hash.get(hostName);

        String userKey = line.getUser();

        User user = hashHost.get(userKey);
        if (user == null) {
            user = new User();
            hashHost.put(userKey, user);
        }

        Matcher mref = reRefHost.matcher(line.referer);
        boolean flagref = mref.find();
        String refHost = flagref ? (mref.group(1).contains("xn--") ? IDN.toUnicode(mref.group(1)) : mref.group(1)) : "";
        String refPage = flagref ? mref.group(2) : "";

        Matcher mPagePath = reRefPage.matcher(refPage);
        String refPagePath = mPagePath.find() ? mPagePath.group(0) : "";

        if (VUtil.notEmpty(VUtil.decodeURI(url))) {
            if (refHost.equals(line.host)) {

                if (line.status == 200
                        && !line.url.startsWith(Util.pathJSLog)
                        && !Util.adminsubnet.matcher(line.ip).find()
                        && !excludeExt.matcher(line.url).find()
                        && !line.referer.substring(line.referer.lastIndexOf("/")).equals(url.substring(url.lastIndexOf("/")))
                ) {
                    statHost.total.follow++;

                    String t4 = VUtil.decodeURI(url);
                    inc(statHost.follow.dst, t4);

                    t4 = VUtil.decodeURI(refPage);
                    if (VUtil.notEmpty(t4)) {
                        inc(statHost.follow.src, t4);
                    }
                }
            } else if (VUtil.notEmpty(refHost)) {
                inc(statHost.referer, refHost);

                int pos = refPage.indexOf('?');
                if (pos > -1) {
                    for (String l : refPage.substring(pos + 1).split("&")) {
                        pos = l.indexOf("=");
                        if (pos == -1) continue;

                        String key = l.substring(0, pos).toLowerCase();
                        String val = l.substring(pos + 1);

                        if (!enabledUrlParam.matcher(key).find()) continue;
                        String val2 = VUtil.decodeURI(val).replaceAll("\\+", " ").replaceAll("\\?", "").trim();
                        if (VUtil.isEmpty(val2)) continue;

                        if (!rePhrase.matcher(val2).find() || val2.length() < 3 || val2.length() > 100) continue;

                        inc(statHost.phrase, val2);
                    }
                }
            }
        }

        Matcher mgoto = reGoto.matcher(url);
        if (mgoto.find()) {
            String goHost = mgoto.group(1);
            String goPage = mgoto.group(2);
            if (line.isGET() && line.status < 400) {
                if (user.enable) {

                    LogHostStat.HostSrcDst goObj;

                    Matcher mGotoHost = affiliate.reHosts.matcher(goHost);
                    if (mGotoHost.find()) {
                        statHost.total.affiliate++;
                        goObj = statHost.affiliate;
                        goHost = mGotoHost.group(1);
                    } else {
                        statHost.total.go++;
                        goObj = statHost.go;
                    }

                    inc(goObj.host, goHost);
                    inc(goObj.dst, goHost + goPage);

                    if (refHost.equals(hostName) && VUtil.notEmpty(refPage)) {
                        inc(goObj.src, refPage);
                    }
                }
            }
        }

        if (line.status < 400 && line.status > 199) {
            if (line.isPOST()) {
                if (line.url.startsWith(Util.pathJSLog)) {
                    if (line.url.startsWith(Util.pathJSLogIn)) {
                        user.hit++;
                        onUser(line, user, timeInd, refPage);
                        if (flagVisit) {
                            pageStat.computeIfAbsent(hostName, t -> new HashMap<>());
                            inc(pageStat.get(hostName), refPagePath);
                        }

                    } else if (line.url.startsWith(Util.pathJSLogOut)) {
                        double time = line.timeFromUrl();
                        user.time += time;
                        if (onUser(line, user, timeInd, refPage)) {
                            user.hit++;
                        }
                    }
                }
            } else if (line.isGET()) {
                if (!folderDisabled.contains(line.folder)) {
                    statHost.period.get(timeInd)[1]++;
                    statHost.hit[0]++;
                }
            }
        }

    }

    void readSecond() {
        try {
            Files.lines(Paths.get(path2)).forEach(this::readSecondLine);
        } catch (IOException e) {
            System.err.println(e);
        }
    }

    void readSecondLine(String text) {
        Matcher m = reTraf.matcher(text);
        if (!m.find()) return;

        String hostName = m.group(1);
        if (!hosts.contains(hostName)) return;

        int status = Integer.parseInt(m.group(2), 10);
        int timeInd = getTimeInd(Util.timePosition(m.group(3)));
        double duration = Double.parseDouble(m.group(4));
        long bytesReceived = Long.parseLong(m.group(5), 10);
        long bytesSent = Long.parseLong(m.group(6), 10);

        String[] reqData = m.group(7).split(" ");
        if (reqData.length != 3) {
            return;
        }
        String url = reqData[1];

        if (url.equals("/xhr/test-page")) return;

        setTraffic(hostName, status, duration, bytesReceived, bytesSent, timeInd);


    }

    // =======

    boolean onUser(Line line, User user, int timeInd, String refPage) {
        boolean flag = !user.enable;
        if (!user.enable) {
            user.enable = true;
            LogHostStat statHost = stat.get(line.host);
            inc(statHost.entry, refPage);
            statHost.period.get(timeInd)[0]++;
        }
        return flag;
    }

    int getTimeInd(int seconds) {
        return (int) Math.floor(seconds / 900.0);
    }

    <T> void inc(HashMap<T, Integer> h, T key) {
        h.merge(key, 1, (a, b) -> a + b);
        //Integer counterVal = h.get(key);
        //if (counterVal == null) counterVal = 0;
        //h.put(key, ++counterVal);
    }

    // =======

    void setTraffic(String hostName, int status, double duration, long bytesReceived, long bytesSent, int timeInd) {

        LogHostStat statHost = stat.get(hostName);
        if (statHost == null) {
            statHost = new LogHostStat();
            for (int i = 0; i < 96; i++) statHost.period.add(new long[]{0, 0, 0, 0});

            stat.put(hostName, statHost);
            hash.put(hostName, new HashMap<>());
        }

        inc(statHost.status, status);

        statHost.duration += duration;
        statHost.amount[0] += bytesReceived;
        statHost.amount[1] += bytesSent;

        //period: user, hitAll, count, amount
        long[] periodLine = statHost.period.get(timeInd);
        periodLine[2]++;
        periodLine[3] += bytesReceived + bytesSent;

    }

}
