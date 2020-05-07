package org.site.view;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.site.elements.MailMessage;
import org.site.log.Counter;
import org.site.server.ReadMail;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;


// crontab -e
// SHELL=/bin/sh

public class Crontab {

    public static class Time {
        public int hour;
        public int minute;

        public Time() {
            ZonedDateTime d = VUtil.nowMSK();
            hour = d.getHour();
            minute = d.getMinute();
        }
    }

    final static String LOG_FORMAT = "yyyy-MM-dd HH:mm:ss";
    final static DateTimeFormatter dtLogFormat = DateTimeFormatter.ofPattern(LOG_FORMAT);

    ZonedDateTime d;
    int hour;
    int minute;

    public Crontab() {
        d = VUtil.nowMSK();
        hour = d.getHour();
        minute = d.getMinute();
        redirect();
        init();
    }

    void redirect() {
        try {
            System.setOut(new PrintStream(new FileOutputStream(VUtil.DIRLog + "/output-out.txt", true)));
            System.setErr(new PrintStream(new FileOutputStream(VUtil.DIRLog + "/output-err.txt", true)));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        prnln(d.format(dtLogFormat));
    }

    void prn(String text) {
        System.out.print(" " + text);
    }

    void prnln(String text) {
        System.out.print("\n" + text);
    }

    void init() {
        VUtil.Timer t = VUtil.timer();

        if (hour == 1) {
            if (minute == 3) {
                everyDay();
                prn("everyDay");
            }
        } else if (minute % 3 == 0) {
            prn("update");
            ViewSite.updateAllHostCron(true);
            //fw.cmdJava().site().update().exec('cron');
            //if(hour == 23 && minute == 3){
            //    prn("price");
            //    new PriceScan();
            //}
        }

        if (minute % 5 == 0) {
            prn("5m");
            every5minutes();
        }
        if (minute % 15 == 0) {
            prn("15m");
            every15minutes();
        }

        prn("mail");
        new ReadMail().readAll();
        //processTest();

        t.endLog("Crontab::mail");
    }

    public static void every15minutes() {
        Time t = new Time();
        RAction.commentVoting(t);
        ViewSite.updateAuto();
    }

    public static void every5minutes() {
        Time t = new Time();
        Counter.cron(t);
    }

    public static void everyDay() {
        Time t = new Time();
        RAction.commentVoting(t);
        RAction.everyDay();

        VUtil.exec("find /var/log/postgres -type f -ctime +5d -delete");
        VUtil.exec("find " + MailMessage.DIRMail + "_orig -type f -ctime +5d -delete");

        VUtil.exec("find " + MailMessage.DIRMail + "temp -type f -ctime +1d -delete");

        ViewSite.updateAllHostCron(false);

    }

    @SuppressWarnings("unused")
    public static void processTest() {
        for (String name : new String[]{"node-app", "sshd"}) {
            boolean flag = false;
            String path = "/var/run/" + name + ".pid";
            if (VUtil.pathExists(path)) {
                String res = VUtil.exec("kill", "-0", VUtil.readFile(path).trim());
                flag = res.isEmpty();
            }
            if (!flag) {
                VUtil.error("Crontab::processTest", name);
                switch (name) {
                    case "node-app":
                        VUtil.exec("/root/node-app-start");
                        break;
                    case "sshd":
                        VUtil.exec("/etc/rc.d/sshd start");
                        break;
                }
            }
        }
    }

    public static void updateY() {
        String text = VUtil.getUrl("http://www.youtube.com/feeds/");

        ArrayList<ArrayList<String>> data = new ArrayList<>();

        Document doc = Jsoup.parse(text);
        for (Element entry : doc.getElementsByTag("entry")) {

            Element elLink = entry.getElementsByTag("link").first();
            Element elTitle = entry.getElementsByTag("media:title").first();
            //Element elDescr = entry.getElementsByTag("media:description").first();
            Element elStat = entry.getElementsByTag("media:statistics").first();
            Element elPublished = entry.getElementsByTag("published").first();

            ArrayList<String> line = new ArrayList<>();

            line.add(elLink.attr("href"));
            line.add(elTitle.text());
            line.add("");//(elDescr.text());
            line.add(elStat.attr("views"));
            line.add(elPublished.text().substring(0, 10));

            data.add(line);

        }

        VUtil.writeFile("/usr/local/www/js/xjs", "showMyVideos(" + VUtil.jsonString(data) + ");");
    }
}
