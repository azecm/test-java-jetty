package org.site.log;

import com.google.gson.reflect.TypeToken;
import org.site.elements.PriceElem;
import org.site.view.Crontab;
import org.site.view.VUtil;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;

public class Counter {
    public class StatJson {
        int user;
        int hits;
        //double time;
        int userAdd;
        int hitsAdd;
        int userPrev;
        int hitsPrev;
    }


    final Pattern reFirstTrue = Pattern.compile("^[a-z0-9-_]+$", Pattern.CASE_INSENSITIVE);

    final String pathToLog = "/var/log/nginx/nginx.log";
    final String nameData = "statData.json";

    HashMap<String, HashMap<String, User>> hosts = new HashMap<>();

    //boolean flagNew;

    public static void cron(Crontab.Time t) {
        new Counter();

        if (t.hour > 0) {
            if (t.minute == 0) new Log();
        } else {
            if (t.minute == 30) new Log(1, true);
        }

    }

    public Counter() {
        init();
        finish();
    }

    void init() {
        //flagNew = dt.getMinute() + dt.getHour() == 0;
        try {
            Files.lines(Paths.get(pathToLog)).forEach(this::readLine);
        } catch (IOException e) {
            System.err.println(e);
        }

    }

    void finish() {

        //Type StatData = new TypeToken<HashMap<String, StatCounter>>() {
        //}.getType();
        //HashMap<String, StatCounter> stat = VUtil.readJson(VUtil.DIRCache + nameData, StatData);
        HashMap<String, StatCounter> stat = VUtil.readJson(VUtil.DIRCache + nameData, TypeToken.getParameterized(HashMap.class, String.class, StatCounter.class).getType());

        for (String hostName : hosts.keySet()) {
            HashMap<String, User> hostData = hosts.get(hostName);
            int hits = 0, user = 0;
            double time = 0;
            for (User userData : hostData.values()) {
                if (userData.enable) {
                    hits += userData.hit > 0 ? userData.hit : 1;
                    time += userData.time;
                    user++;
                }
            }

            StatCounter statHost = stat.get(hostName);
            if (statHost == null) {
                statHost = new StatCounter();
                stat.put(hostName, statHost);
            }

            boolean flagNewDay = user < statHost.user;

            StatJson jsonData = new StatJson();
            jsonData.user = user;
            jsonData.hits = hits;
            //jsonData.time = hits > 0 ? Math.round(time / (float) hits) : 0;
            jsonData.userAdd = user - statHost.user;
            jsonData.hitsAdd = hits - statHost.hits;
            jsonData.userPrev = statHost.puser;
            jsonData.hitsPrev = statHost.phits;

            if (flagNewDay) {
                jsonData.userPrev = statHost.puser = statHost.user;
                jsonData.hitsPrev = statHost.phits = statHost.hits;

                jsonData.userAdd = user;
                jsonData.hitsAdd = hits;
            }

            VUtil.writeJson(VUtil.DIRFile + hostName + "/json/counter.json", jsonData);

            statHost.hits = hits;
            statHost.user = user;

        }

        VUtil.writeJson(VUtil.DIRCache + nameData, stat);

    }

    void readLine(String text) {
        Line log = new Line(text);
        if (log.isEmpty()) return;

        if (Util.reIgnore.matcher(log.browser).find()
                || log.status > 399
                || VUtil.isEmpty(log.folder)
                || !reFirstTrue.matcher(log.folder).find()
        ) return;

        if (!log.isPOST()) return;


        HashMap<String, User> hostData = hosts.get(log.host);
        if (hostData == null) {
            hostData = new HashMap<>();
            hosts.put(log.host, hostData);
        }

        String userKey = log.getUser();
        User user = hostData.get(userKey);
        if (user == null) {
            user = new User();
            hostData.put(userKey, user);
        }

        if (log.url.startsWith(Util.pathJSLogIn)) {
            user.enable = true;
            user.hit++;
        }

        if (log.url.startsWith(Util.pathJSLogOut)) {
            double time = log.timeFromUrl();
            user.time += time;
            if (!user.enable) {
                user.enable = true;
                user.hit++;
            }
        }
    }
}
