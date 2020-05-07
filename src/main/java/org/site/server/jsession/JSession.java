package org.site.server.jsession;


import org.site.server.Router;
import org.site.view.VUtil;

import javax.servlet.ServletRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.regex.Pattern;

public class JSession extends JRequest {
    final static Pattern reSession = Pattern.compile("/on/(\\w+)");

    public String pathToSessionData;
    public String key;
    public String pin;
    //public Object data;

    public void initSession(ServletRequest req) {
        init(req);

        key = request.getHeader("User-State");

        if (key == null) {
            HashMap<String, String> query = Router.queryMap(req);
            key = query.get("user");
        }


        //Matcher m = reSession.matcher(uri);
        //if (m.find()) key = m.group(1);
        //VUtil.println("--");
        //VUtil.println("initSession", request.getHeader("User-State"));
        //VUtil.println("initSession", request.getHeader("user-state"));
        //VUtil.println("initSession", key);
        //uri = uri.replaceAll("/on/([^/]+)", "").substring(request.getContextPath().length());

    }

    public void updateUri() {
        // /api/mail
        // uri = uri.substring(9);
        List<String> list = Arrays.asList(uri.split("/"));
        uri = "/" + String.join("/", list.subList(3, list.size()));
    }

    public String path() {
        return VUtil.DIRSession + host + "/" + key;
    }

    boolean testSession(DataCommon data) {

        String ip = getIP();
        String browser = getBrowser();

        if (data == null) {
            VUtil.println();
            VUtil.println("testSession - data==null", ip, browser);
            printHeaders();
            VUtil.println();
            return false;
        }

        if (ip == null || browser == null || data.ip == null) {
            VUtil.println("testSession - data.ip==" + data.ip);
            printHeaders();
            return false;
        }

        boolean flagIP = data.ip.equals(ip);
        boolean flagBrowser = data.browser.equals(browser);
        if (flagIP && flagBrowser) {
            return true;
        }

        ArrayList<String> missHeaders = new ArrayList<>();
        //, "Content-Length", "Content-Type"
        String[] headers = {"Accept", "Referer", "Accept-Language", "Accept-Encoding"};
        for (String header : headers) {
            if (request.getHeader(header) == null) missHeaders.add(header);
        }
        if (missHeaders.size() > 0) {
            VUtil.println("testSession not headers", key, ip, browser, String.join(",", missHeaders));
            return false;
        }

        if (!flagIP) {
            String[] ip1 = data.ip.split(".");
            String[] ip2 = ip.split(".");
            if (flagBrowser || (ip1.length > 0 && ip2.length > 0 && ip1[0].equals(ip2[0]) && ip1[1].equals(ip2[1]))) {
                VUtil.println("testSession: ip update true", key, data.ip, ip);
                data.ip = ip;
            } else {
                VUtil.println("testSession: ip update false", key, data.ip, ip);
                return false;
            }
        }
        if (!flagBrowser) {
            if (data.browser.length() != browser.length()) {
                VUtil.println("testSession: browser update false (length)", key, data.browser, browser);
                return false;
            }
            int i, im = data.browser.length(), c = 0;
            for (i = 0; i < im; i++) {
                if (Character.codePointAt(data.browser, i) == Character.codePointAt(browser, i)) c++;
            }
            if (im - c > 4) {
                VUtil.println("testSession: browser update false", key, data.browser, browser, im, c);
                return false;
            }

            VUtil.println("testSession: browser update true", key, data.browser, browser);
            data.browser = browser;
        }

        VUtil.println("testSession: writeJson", key, VUtil.jsonString(data));
        VUtil.writeJson(path(), data);

        return true;
    }

    public <T> T loadSession(Class<T> classRef) {
        T data = null;
        if (key != null) {
            data = VUtil.readJson(path(), classRef);
            if (!testSession((DataCommon) data)) {
                VUtil.println("loadSession: session delete", key, VUtil.jsonString(data));
                VUtil.pathRemove(path());
                data = null;
            }
        }
        return data;
    }

    public String genPin(DataCommon data) {
        return VUtil.getHash256(data.ip + "|" + data.browser + "|" + data.device + "|" + data.start);
    }

    public void genKeys(DataCommon data) {
        data.start = System.currentTimeMillis();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            outputStream.write(BigInteger.valueOf(Math.round(Math.random() * 64)).toByteArray());
            outputStream.write(BigInteger.valueOf(data.start - 1500000000000L).toByteArray());
            outputStream.write(BigInteger.valueOf(Math.round(Math.random() * 64)).toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }

        key = Base64.getUrlEncoder().encodeToString(outputStream.toByteArray()).replaceAll("=", "");
        pin = genPin(data);
    }
}
