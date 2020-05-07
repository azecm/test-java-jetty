package org.site.server.site.admin;

import org.site.elements.NodeData;
import org.site.server.Router;
import org.site.server.jsession.JRequest;
import org.site.server.jsession.JSessionSite;
import org.site.view.VUtil;
import org.site.view.ViewSite;

import javax.servlet.GenericServlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class AdminKeywords extends GenericServlet {
    class PostData {
        String find;
        String from;
        String to;
    }

    public void service(ServletRequest req, ServletResponse res) {

        JSessionSite session = new JSessionSite(req);

        if (!session.isAdmin()) {
            Router.json404(res);
            return;
        }

        PostData data = session.postJson(PostData.class);
        if (!session.isPost()) {
            Router.json404(res);
            return;
        }

        if (VUtil.notEmpty(data.find)) {
            int idn = VUtil.getInt(data.find);
            ArrayList<Integer> result = new ArrayList<>();
            VUtil.readNodes(session.host, (node) -> {
                if (node != null && node.head != null && node.head.keywords != null) {
                    if (node.head.keywords.indexOf(data.find) > -1 || node.head.labels.indexOf(idn) > -1) {
                        result.add(node.head.idn);
                    }
                }
            });
            Router.json200(result, res);
        } else if (VUtil.notEmpty(data.from) && VUtil.notEmpty(data.to)) {

            int idn = VUtil.getInt(data.to);
            if (data.to.equals("+")) {
                NodeData node = new NodeData().appendTo(session.host, ViewSite.getIni(session.host).idnLabel, session.user.idu);
                node.head.link.set(0, data.from);
                VUtil.writeNode(session.host, node);
                idn = node.head.idn;
            }
            final int idnTo = idn;
            final int idnFrom = VUtil.getInt(data.from);
            final boolean isLabelReplace = idnTo > 0 && idnFrom > 0;

            VUtil.println("keywords update, from:" + data.from + " to: " + data.to, "idn:" + "replace From: " + idnTo);
            VUtil.readNodes(session.host, (node) -> {
                if (node != null && node.head != null) {
                    if (isLabelReplace) {
                        if (node.head.labels != null) {
                            int pos = node.head.labels.indexOf(idnFrom);
                            if (pos > -1) {
                                VUtil.println("idn:" + node.head.idn, "replace label From: " + idnFrom, " To: " + idnTo);
                                VUtil.println("old:", String.join(", ", node.head.labels.stream().map(a -> a.toString()).collect(Collectors.toList())));
                                node.head.labels.remove(pos);
                                if (node.head.labels.indexOf(idnTo) == -1) node.head.labels.add(idnTo);
                                VUtil.println("new:", String.join(", ", node.head.labels.stream().map(a -> a.toString()).collect(Collectors.toList())));
                                VUtil.writeNode(session.host, node);
                            }
                        }
                    } else {
                        if (node.head.keywords != null) {
                            int pos = node.head.keywords.indexOf(data.from);
                            if (pos > -1) {
                                VUtil.println("idn:" + node.head.idn, String.join(", ", node.head.keywords));
                                if (idnTo > 0) {
                                    node.head.keywords.remove(pos);
                                    if (node.head.labels.indexOf(idnTo) == -1) node.head.labels.add(idnTo);
                                } else if (data.to.equals("-")) {
                                    node.head.keywords.remove(pos);
                                } else {
                                    node.head.keywords.set(pos, data.to);
                                }
                                VUtil.println("idn:" + node.head.idn, String.join(", ", node.head.keywords));
                                VUtil.writeNode(session.host, node);
                            }
                        }
                    }
                }
            });

            ViewSite.updateKeywords(session.host);

            String resultHtml = "<b>" + data.from + "</b> ";
            if (isLabelReplace) {
                resultHtml += "замена выполнена";
                resultHtml += " <a href=\"/control/edit#" + idnTo + "\" target=\"_blank\">" + idnTo + "</a>";
            } else {
                if (idnTo > 0) {
                    if (data.to.equals("+")) {
                        resultHtml += "создана новая метка";
                    } else {
                        resultHtml += "добавлено в метку";
                    }
                    resultHtml += " <a href=\"/control/edit#" + idnTo + "\" target=\"_blank\">" + idnTo + "</a>";
                } else if (data.to.equals("-")) {
                    resultHtml += "удаление выполнено";
                } else {
                    resultHtml += "замена выполнена";
                }
            }

            Router.json200(resultHtml, res);
        } else {
            Router.json404(res);
            return;
        }
    }
}
