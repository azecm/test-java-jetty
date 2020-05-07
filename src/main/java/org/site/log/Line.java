package org.site.log;


import org.site.view.VUtil;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.regex.Matcher;

public class Line {
    String ip;
    String host;
    int status;

    // 20/Jun/2018:00:00:33 +0300
    String dateTime;

    double duration;
    long bytesReceived; // $request_length
    long bytesSent; // $bytes_sent

    String type;
    String url;
    String protocol;

    String folder;

    String referer;
    String browser;

    Line(String text) {

        Matcher mdata = Util.reLine.matcher(text);

        if (!mdata.find()) {
            if (!text.contains("logfile turned over")) {
                VUtil.println("LOG LINE ERR(0)", text);
            }
            return;
        }

        ip = mdata.group(1);
        host = mdata.group(2);
        status = Integer.parseInt(mdata.group(3), 10);

        // 20/Jun/2018:00:00:33 +0300
        dateTime = mdata.group(4);

        duration = Double.parseDouble(mdata.group(5));
        bytesReceived = Long.parseLong(mdata.group(6), 10);
        bytesSent = Long.parseLong(mdata.group(7), 10);

        String[] reqData = mdata.group(8).split("\\s+");

        switch (reqData.length) {
            case 2:
                type = reqData[0];
                url = reqData[1];
                protocol = "";
                VUtil.println("LOG LINE ERR(1)", mdata.group(8), "|||", text);
                break;
            case 3:
                type = reqData[0];
                url = reqData[1];
                protocol = reqData[2];
                break;
            default:
                VUtil.println("LOG LINE ERR(2)", mdata.group(8), "|||", text);
                break;
        }

        referer = mdata.group(9);
        browser = mdata.group(10);

        if (type == null) type = "";
        if (protocol == null) protocol = "";
        if (url == null) url = "";

        Matcher m = Util.reFolder.matcher(url);
        if (m.find()) folder = m.group(1);
    }

    boolean isEmpty() {
        return ip == null;
    }

    public boolean isGET() {
        return this.type.equals("GET");
    }

    public boolean isPOST() {
        return this.type.equals("POST");
    }

    public String getUser() {
        String res = "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update((browser + ":" + ip + ":" + host).getBytes("UTF-8"));
            //new BigIntege(1, md.digest()).toString(16);
            res = Base64.getEncoder().encodeToString((md.digest()));
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return res;
    }

    public double timeFromUrl() {
        return Util.timeFromUrlParam(url);
    }

    public int timePosition() {
        return Util.timePosition(dateTime);
    }

}
