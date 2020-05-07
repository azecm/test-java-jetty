package org.site.server.jsession;

import com.google.gson.reflect.TypeToken;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.eclipse.jetty.http.HttpMethod;
import org.site.view.VUtil;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;


public class JRequest {
    public interface GetNext {
        Path fileName(FileItemStream item);
    }

    public String host;
    public String uri;
    //public String uriName;

    ServletRequest req;
    public HttpServletRequest request;

    public JRequest init(ServletRequest req) {
        HttpServletRequest request = (HttpServletRequest) req;

        this.req = req;
        this.request = request;

        URL url = null;
        try {
            url = new URL(request.getRequestURL().toString());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        host = url.getHost();
        uri = url.getPath();
        //uriName = request.getRequestURL().toString();

        return this;
    }

    public boolean isPost() {
        return request.getMethod().equals(HttpMethod.POST.name());
    }

    public boolean isGet() {
        return request.getMethod().equals(HttpMethod.GET.name());
    }

    public boolean isMultipart() {
        return ServletFileUpload.isMultipartContent(request);
    }

    //"Accept", "Referer", "Accept-Language", "Accept-Encoding"
    public String getBrowser() {
        return request.getHeader("User-Agent");
    }

    public String getIP() {
        return request.getHeader("remote-addr");
    }

    public String getReferer() {
        String ref = getRefererFull();

        final String base = "//" + host + "/";
        ref = ref.contains(base) ? ref.substring(ref.indexOf(base) + base.length() - 1) : "";
        ref = ref.contains("?") ? ref.substring(0, ref.indexOf("?")) : ref;

        return ref;
    }

    public String getRefererFull() {
        String ref = request.getHeader("Referer");
        if (ref == null) ref = "";
        return ref;
    }


    public void printHeaders() {
        Enumeration<String> next = request.getHeaderNames();
        while (next.hasMoreElements()) {
            String header = next.nextElement();
            VUtil.println(header + ": " + request.getHeader(header));
        }
    }

    public String postString() {
        String resultString = null;
        try {
            String s;
            StringBuilder resString = new StringBuilder();
            BufferedReader reader = req.getReader();
            while ((s = reader.readLine()) != null) resString.append(s).append("\n");
            resultString = resString.toString();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return resultString.trim();
    }

    public List<String> postLogin() {
        List<String> lines = Arrays.stream(postString().split("&")).map(VUtil::decodeURI).collect(Collectors.toList());
        List<String> linesRes = new ArrayList<>();

        StringBuilder textRes;
        int numMax = lines.size();
        for (int num = 0; num < numMax; num++) {
            textRes = new StringBuilder();
            String text = lines.get(num);
            int im = text.length();
            for (int i = 0; i < im; i++) {
                int key = (num + i) % 4 + 1;
                textRes.append(
                        Character.toChars(
                                Character.codePointAt(text, i) - ((num + i) % 2 == 1 ? key : -key)
                        )
                );
            }
            linesRes.add(textRes.toString());
        }

        return linesRes;
    }

    @SuppressWarnings("Duplicates")
    public <T> T postJson(Class<T> classRef) {
        T result = null;
        String resultString = postString();
        if (resultString != null) {
            result = VUtil.jsonData(resultString, classRef);
        }
        return result;
    }

    @SuppressWarnings("Duplicates")
    public <T> T postJson(Type typeOfT) {
        T result = null;
        String resultString = postString();
        if (resultString != null) {
            result = VUtil.jsonData(resultString, typeOfT);
        }
        return result;
    }

    public <T> ArrayList<T> postJsonList(Class<T> classRef) {
        ArrayList<T> result = null;
        String resultString = postString();
        if (resultString != null) {
            result = VUtil.jsonData(resultString, TypeToken.getParameterized(ArrayList.class, classRef).getType());
        }
        return result;
    }

    public HashMap<String, String> postForm(GetNext func) {
        HashMap<String, String> map = new HashMap<>();
        if (isMultipart()) {

            ServletFileUpload upload = new ServletFileUpload();
            try {
                //List<FileItem> items = new ServletFileUpload(new DiskFileItemFactory()).parseRequest(request);
                FileItemIterator iter = upload.getItemIterator(request);

                InputStream stream;
                FileItemStream item;
                OutputStream outputStream;
                Path path;
                int read;
                byte[] bytes;

                while (iter.hasNext()) {
                    item = iter.next();

                    //VUtil.println(item.isFormField(), item.getFieldName(), item.getName(), item.getContentType());
                    //true, idn, null, null
                    //false, files, BH3X3470.JPG, image/jpeg

                    if (item.isFormField()) {
                        stream = item.openStream();
                        String result = new BufferedReader(new InputStreamReader(stream))
                                .lines().collect(Collectors.joining("\n"));
                        map.put(item.getFieldName(), result);
                    } else {
                        path = func.fileName(item);
                        //VUtil.println(path);
                        //VUtil.testDir(path);
                        VUtil.createDirs(path, false);
                        if (path != null) {
                            stream = item.openStream();
                            outputStream = Files.newOutputStream(path);
                            bytes = new byte[1024];
                            while ((read = stream.read(bytes)) != -1) {
                                outputStream.write(bytes, 0, read);
                            }
                        }
                    }
                }
            } catch (IOException | FileUploadException e) {
                e.printStackTrace();
            }
        }
        return map;
    }
}
