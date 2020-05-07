package org.site.log;

import org.site.view.VUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Util {

    final static Pattern adminsubnet = Pattern.compile("^(83\\.149\\.|195\\.16\\.|31\\.173\\.|83\\.220\\.|185\\.86\\.)");

    final static String pathJSLog = "/js/log";
    final static String pathJSLogIn = "/js/log/in";
    final static String pathJSLogOut = "/js/log/out";
    final static String pathJSLogError = "/js/log/error";

    final static Pattern reJSLogTime = Pattern.compile("time=([0-9\\.]+)");
    final static Pattern reFolder = Pattern.compile("^/([^/]*)");
    final static Pattern reLine = Pattern.compile("([^\\s]+) ([^\\s]+) (\\d\\d\\d) \\[([^]]+)] ([^\\s]+) (\\d+) (\\d+) \"([^\"]+)\" \"([^\"]*)\" \"([^\"]*)\"");
    final static Pattern reIgnore = Pattern.compile("(www\\.host-tracker\\.com|Siege/\\d\\.\\d\\.\\d)");

    // 23/Feb/2015:00:00:37 +0300
    final static Pattern reDateTime = Pattern.compile("^(\\d{2})/(\\w{3})/(\\d{4}):(\\d{2}):(\\d{2}):(\\d{2}) \\+(\\d{4})");

    public static double timeFromUrlParam(String url) {
        double t = 0;
        Matcher m = reJSLogTime.matcher(url);
        if (m.find()) {
            double d = 0.0;
            try {
                d = Double.parseDouble(m.group(1));
            } catch (NumberFormatException e) {
                VUtil.println("Util::timeFromUrlParam::Double.parseDouble", m.group(1));
            }
            t = (d > 2000 ? 2000 : d);
        }
        return t;
    }

    public static int timePosition(String logDateTime) {
        int time = 0;
        Matcher m = reDateTime.matcher(logDateTime);
        if (m.find()) {
            time = Integer.parseInt(m.group(4), 10) * 3600 + Integer.parseInt(m.group(5), 10) * 60 + Integer.parseInt(m.group(6), 10);
        }
        return time;
    }
}
