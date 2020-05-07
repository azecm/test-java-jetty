package org.site.server.mail;

import org.apache.commons.lang3.math.NumberUtils;
import org.site.elements.MailMessage;
import org.site.server.Router;
import org.site.server.jsession.JSessionMail;
import org.site.view.VUtil;

import javax.servlet.GenericServlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class ReadFolder extends GenericServlet {
    private static int perPage = 30;
    private static String folderInbox = "inbox";
    private static HashSet<String> folders = new HashSet<>(Arrays.asList(folderInbox, "ready", "sent", "trash"));

    public void service(ServletRequest req, ServletResponse res) {

        JSessionMail session = new JSessionMail(req);

        HashMap<String, String> query = Router.queryMap(req);
        String folderName = query.get("folder");
        String state = query.get("state");
        int page = NumberUtils.toInt(query.get("page"));
        if (folderName == null || !folders.contains(folderName)) {
            Router.json404(res);
            return;
        }

        String userFolder = session.email();

        String pathToFolder = MailMessage.pathToFolder(userFolder, folderName);
        if (!VUtil.pathExists(pathToFolder)) {
            Router.json404(res);
            return;
        }

        List<Path> list = new ArrayList<>();
        try {
            list = Files.list(Paths.get(pathToFolder))
                    //.filter(Files::isRegularFile)
                    .sorted(Collections.reverseOrder())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (folderName.equals(folderInbox)) {
            HashSet<String> prevList = Router.folderInboxPrev.get(userFolder);
            List<Path> listFiltered = null;
            if (state != null && state.equals("last") && prevList != null) {
                listFiltered = list.stream().filter(p -> !prevList.contains(p.getFileName().toString())).collect(Collectors.toList());
            }
            Router.folderInboxPrev.put(userFolder, new HashSet<>(list.stream().map(p -> p.getFileName().toString()).collect(Collectors.toList())));
            if (listFiltered != null) list = listFiltered;
        } else {
            int start = page * perPage;
            int end = (page + 1) * perPage;
            if (start > list.size()) start = list.size();
            if (end > list.size()) end = list.size();

            if (end > 0) list = list.subList(start, end);
        }

        //VUtil.println("folderInboxPrev", folderName, state, list.size(), Router.folderInboxPrev);

        ArrayList<MailMessage> result = new ArrayList<>();
        for (Path path : list) {
            MailMessage data = MailMessage.load(path);
            if (data != null) result.add(data);
        }

        Router.json200(result, res);
    }
}