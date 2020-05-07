package org.site.view;


import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.site.elements.NodeAttach;
import org.site.elements.NodeData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Quiz {
    final Pattern reSummary = Pattern.compile("^(\\d+)(?:-(\\d+))?\\s+\\((\\d+)\\)\\s+(.+)");
    //Pattern.compile("^(<[^>]+>)\\s*(?:(\\d+)-?(\\d+)?)\\s+\\((\\d+)\\)\\s*");
    NodeData node;
    public Element elem = new Element("ul").addClass("quiz unselect");

    public Quiz(NodeData node, List<Integer> groupList) {
        this.node = node;

        boolean isFirst = true;

        //VUtil.println(groupList);

        for (int idf : groupList) {
            NodeAttach imgData = node.attachByKey(idf);
            if (VUtil.textOnly(imgData.content).isEmpty()) {
                VUtil.error("Quiz: content isEmpty", node.head.idn, idf, imgData.src);
                continue;
            }
            Document docContent = imgData.getContent();

            List<Element> docList = new ArrayList<>();
            for (Element t : docContent.getElementsByTag("p")) {
                if (!t.text().trim().isEmpty()) {
                    docList.add(t);
                }
            }

            if (docList.size() == 0 || imgData.quiz == null) continue;

            String fnKey = imgData.quiz;
            Element li = new Element("li").addClass("hide").addClass(fnKey).appendTo(elem);
            if (fnKey.equals("result")) {
                typeResult(li, docList, imgData);
            } else {
                li.appendChild(docList.get(0));
                Object data = null;
                switch (fnKey) {
                    case "single":
                        data = typeSingle(docList);
                        break;
                    case "join":
                        data = typeJoin(docList, imgData, isFirst);
                        break;
                    case "group":
                        data = typeGroup(docList, imgData, isFirst);
                        break;
                    case "pair":
                        data = typePair(docList, imgData, isFirst);
                        break;
                    case "blanks":
                        data = typeBlanks(docList, imgData, isFirst);
                        break;
                    case "write":
                        data = typeWrite(docList, imgData);
                        break;
                    case "order":
                        data = typeOrder(docList, imgData);
                        break;
                    case "select":
                        data = typeSelect(docList, imgData, isFirst);
                        break;
                }
                if (data != null) {
                    //VUtil.println(fnKey, VUtil.jsonString(data));
                    if (imgData.src != null) li.attr("data-src", imgData.src);
                    li.attr("data-data", VUtil.jsonString(data));


                }
            }
            isFirst = false;
        }
    }

    Object typeSelect(List<Element> docList, NodeAttach imgData, boolean isFirst) {
        List<List<String>> data = new ArrayList<>();
        data.add(new ArrayList<>());
        data.add(new ArrayList<>());
        if (!isFirst && imgData.group != null) {
            for (int k : imgData.group) {
                String text = VUtil.textOnly(node.attachByKey(k).content);
                String src = node.attachByKey(k).src;
                if (VUtil.isEmpty(src)) continue;
                data.get(VUtil.notEmpty(text) ? 0 : 1).add(src);
            }
        } else {
            for (int i = 1, im = docList.size(); i < im; i++) {
                String list = docList.get(i).text();
                for (String text : list.split(",")) {
                    text = text.trim();
                    if (VUtil.notEmpty(text)) {
                        data.get(i == 1 ? 0 : 1).add(text);
                    }
                }
            }
        }
        return data;
    }

    Object typeOrder(List<Element> docList, NodeAttach imgData) {
        List<String> data = new ArrayList<>();
        for (int i = 1, im = docList.size(); i < im; i++) {
            String text = docList.get(i).text();
            if (VUtil.isEmpty(text)) continue;
            data.add(text);
        }
        return data;
    }

    Object typeWrite(List<Element> docList, NodeAttach imgData) {
        Pattern reDataAll = Pattern.compile("\\([\\wа-яё]\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.UNICODE_CASE);
        List<String> data = new ArrayList<>();
        for (int i = 1, im = docList.size(); i < im; i++) {
            String text = docList.get(i).text().trim();
            Matcher m = reDataAll.matcher(text);
            if (!m.find()) continue;
            data.add(text);
        }
        return data;
    }

    Object typeBlanks(List<Element> docList, NodeAttach imgData, boolean isFirst) {
        Blanks data = new Blanks();
        for (int i = 1, im = docList.size(); i < im; i++) {
            String text = docList.get(i).text().trim();
            if (text.isEmpty()) continue;
            if (text.contains("((")) {
                data.text.add(docList.get(i).html().trim());
            } else {
                data.phrase.add(text);
            }
        }
        return data;
    }

    Object typePair(List<Element> docList, NodeAttach imgData, boolean isFirst) {
        //let data = {} as { text?:string, src?:string, t1?:string,  };
        List<List<String>> data = new ArrayList<>();

        if (!isFirst && imgData.group != null) {
            HashMap<String, List<String>> dataTest = getDict(imgData);
            boolean flagImageOnly = true, flagImageText = true;
            for (List<String> line : dataTest.values()) {
                if (line.size() != 2) flagImageOnly = false;
                if (line.size() != 1) flagImageText = false;
            }
            if (flagImageOnly) {
                // пары из двух картинок
                for (List<String> line : dataTest.values()) {
                    data.add(line);
                }
            } else if (flagImageText) {
                // пары: картинка - текст
                for (String key : dataTest.keySet()) {
                    dataTest.get(key).get(0);
                    data.add(Arrays.stream(new String[]{dataTest.get(key).get(0), key}).collect(Collectors.toList()));
                }
            }
        } else {
            // пары: текст - текст
            for (int i = 1, im = docList.size(); i < im; i++) {
                String text = docList.get(i).text();
                List<String> d1 = Arrays.stream(text.split("\\*\\*")).map(a -> a.trim()).filter(a -> !a.isEmpty()).collect(Collectors.toList());
                if (d1.size() == 2) {
                    data.add(d1);
                }
            }
        }
        return data;
    }

    HashMap<String, List<String>> getDict(NodeAttach imgData) {
        HashMap<String, List<String>> data = new HashMap<>();
        for (int k : imgData.group) {
            String text = VUtil.textOnly(node.attachByKey(k).content);
            String src = node.attachByKey(k).src;
            if (VUtil.isEmpty(text) || VUtil.isEmpty(src)) continue;
            if (!data.containsKey(text)) data.put(text, new ArrayList<>());
            data.get(text).add(src);
        }
        return data;
    }

    Object typeGroup(List<Element> docList, NodeAttach imgData, boolean isFirst) {
        HashMap<String, List<String>> data = new HashMap<>();

        if (!isFirst && imgData.group != null) {
            data = getDict(imgData);

        } else {
            for (int i = 1, im = docList.size(); i < im; i++) {
                String text = docList.get(i).text();
                String[] d1 = text.split(":");
                if (d1.length == 2) {
                    data.put(d1[0].trim(), Arrays.stream(d1[1].split(",")).map(a -> a.trim()).collect(Collectors.toList()));
                }
            }
        }
        return data;
    }

    Object typeJoin(List<Element> docList, NodeAttach imgData, boolean isFirst) {
        List<List<String>> data = new ArrayList<>();
        if (!isFirst && imgData.group != null) {
            HashMap<String, List<String>> dataDict = getDict(imgData);
            for (List<String> v : dataDict.values()) {
                data.add(v);
            }
        } else {
            for (int i = 1, im = docList.size(); i < im; i++) {
                String text = docList.get(i).text();
                data.add(Arrays.stream(text.split(",")).map(a -> a.trim()).collect(Collectors.toList()));
            }
        }
        return data;
    }

    Object typeSingle(List<Element> docList) {
        List<String> data = new ArrayList<>();
        for (int i = 1, im = docList.size(); i < im; i++) {
            String html = docList.get(i).text();
            data.add(html);
        }
        return data;
    }

    void typeResult(Element liResult, List<Element> docList, NodeAttach imgData) {
        for (int i = 0, im = docList.size(); i < im; i++) {
            String html = docList.get(i).text().trim();
            Matcher data = reSummary.matcher(html);
            if (data.find()) {
                Element newP = new Element("p").text(data.group(4).trim());
                newP.attr("data-min", data.group(1));
                newP.attr("data-max", data.group(2) == null ? data.group(1) : data.group(2));
                newP.attr("data-amount", data.group(3));
                newP.attr("data-src", imgData.src == null ? "" : imgData.src);
                liResult.appendChild(newP);
            } else {
                VUtil.error("Quiz::typeResult", node.head.idn, html);
            }
        }
    }

    public class Blanks {
        public List<String> text = new ArrayList<>();
        public List<String> phrase = new ArrayList<>();
    }
}
