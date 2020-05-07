package org.site.view;

import com.google.gson.reflect.TypeToken;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.site.elements.*;
import org.site.ini.SiteProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ViewPage {
    public ViewSite site;
    public Document doc;
    public NodeData node;
    public PageParam param = new PageParam();
    Element docContent;
    Element docSidebarContent;

    ViewPage(ViewSite site) {
        this.site = site;
        this.site.page = this;

        param.js.addAll(site.ini.js);
        if (param.js.size() == 0) {
            param.js.add("login");
            param.js.add("search");
            param.js.add("gallery");
        }
        init();
    }

    ViewPage byIdn(int idn) {
        pageByIdn(idn);
        start();
        return this;
    }

    ViewPage byUrl(String url) {

        Matcher mpages = site.rePageUrl.matcher(url);
        if (mpages.find()) {
            url = "/";
            param.pageNumber = Integer.parseInt(mpages.group(1));
        }

        NodeTreeElem elTree = site.tree.byUrl(url);
        if (elTree == null) {
            pageByUrl(url);
        } else {
            pageByIdn(elTree.idn);
        }

        if (param.type == null) {
            VUtil.error("ViewPage::byUrl - bad url: " + url);
        } else {
            start();
        }

        return this;
    }


    void start() {
        if (site.ini.flagApp && param.pageNumber < 2) {
            new AppJSON(site).save();
        }
        pageContentBody();
        pageFinish();
        pageToHtml();
    }


    void init() {

        doc = Jsoup.parse("<html lang=\"ru\"><head></head><body class=\"site\" itemscope=\"\" itemtype=\"https://schema.org/WebPage\">" + site.srcTemplate + "</body></html>");

        Elements els = doc.getElementsByClass(site.nameContent + "layout");
        if (els.size() == 1) docContent = els.first();


        if (site.ini.flagNewsNotification && !site.newsNotificationTested) {
            site.newsNotificationTested = true;
            site.ini.flagNewsNotification = site.newsNotificationTest(site.host);
        }

        if (site.ini.flagNewsNotification) {
            Element elBody = doc.getElementsByTag("body").get(0);
            elBody.attr("data-with-news", "");
        }

        els = doc.getElementsByClass("sidebar-content");
        if (els.size() == 1) docSidebarContent = els.first();

        docContent.attr("itemprop", "mainContentOfPage");
    }

    List<String> getPageKeyWords() {
        List<String> list = new ArrayList<>();
        list.addAll(node.head.keywords);
        list.addAll(node.head.labels.stream().map(id -> site.tree.byIdn(id).text).collect(Collectors.toList()));
        return list;
    }

    void pageByIdn(int idn) {

        node = VUtil.readNode(site.host, idn);

        if (site.ini.flagPriceByKeywords) {
            site.priceKeywords.useOnlyT = node.head.keywords.contains("to");
            site.priceKeywords.createGoodsList(node.head.labels, node.head.idn);
        }

        param.path = site.getUrlByIdn(idn);

        if (param.path.equals("/") && param.pageNumber == 0) {
            param.pageNumber = 1;
        }

        if (param.pageNumber > 0) {
            param.type = SiteProperty.PageType.root;
            if (param.pageNumber > 1) {
                param.path = "/page/" + param.pageNumber;
            }
        }


        param.noindex = !node.head.flagValid || node.head.flagBlock;
        param.title = node.head.title;
        param.description = VUtil.textOnly(node.descr);
        //VUtil.println(node.head.getLabels().stream().map(id -> site.tree.byIdn(id)).collect(Collection<>));
        param.keywords = getPageKeyWords();
        //node.head.labels.stream().map(id -> site.tree.byIdn(id).text).toArray(String[]::new);

        //List<String> j = node.head.getLabels().stream().map(id -> site.tree.byIdn(id).text).collect(Collectors.toList());

        if (param.type == null) {
            if (node.isFolder()) {
                param.type = SiteProperty.PageType.folder;
            } else {
                param.type = site.ini.idnLabel == node.head.idp ? SiteProperty.PageType.label : SiteProperty.PageType.article;
            }
        }

        pageContent();

        if (site.ini.blogPerPage > 0) {
            switch (param.type) {
                case root: {
                    blogRoot();
                    break;
                }
                case label: {
                    blogLabel();
                    break;
                }
                case folder: {
                    if (site.ini.idnLabel == node.head.idn) {
                        blogLabels();
                    } else {
                        blogSection();
                    }
                    break;
                }
            }
        }
    }

    void pageByUrl(String url) {

        switch (url) {
            case "/search/": {
                param.path = url;
                param.title = "Результаты поиска";
                param.keywords = Arrays.asList(new String[]{"поиск"});
                param.description = param.title;
                param.content = jTag("p").text("Ожидайте... идет поиск.");
                param.noindex = true;
                param.type = SiteProperty.PageType.outside;
                break;
            }
            case "/tracker/": {
                param.path = url;
                param.title = "Новости сайта " + site.host;
                param.keywords = Arrays.asList(new String[]{"новости", site.host});
                param.description = param.title;
                param.content = blogTracker();
                param.noindex = false;
                param.type = SiteProperty.PageType.outside;

                param.css.add("tracker");

                break;
            }
            case "/user/": {
                param.path = url;
                param.title = "Список пользователей сайта " + site.host;
                param.keywords = Arrays.asList(new String[]{"список", "пользователи"});
                param.description = param.title;
                param.content = blogUsers();
                param.noindex = false;
                param.type = SiteProperty.PageType.outside;

                break;
            }
            case "/200": {

                param.path = url;
                param.title = "Страница пользователя";
                param.noindex = true;
                param.type = SiteProperty.PageType.outside;

                Collections.addAll(param.css, "pages", "page200");
                Collections.addAll(param.js, "login", "search", "pageUser", "gallery");

                break;
            }
            case "/404": {
                param.path = url;
                param.title = "Страница не найдена";
                param.keywords = Arrays.asList(new String[]{"ошибка 404", "страница не найдена"});
                param.description = param.title;
                param.content = jTag("h1").text(param.title);
                param.noindex = true;
                param.type = SiteProperty.PageType.outside;

                //Collections.addAll(param.js, "gallery");
                break;
            }
            case "/410": {
                param.path = url;
                param.title = "Страница удалена";
                param.keywords = Arrays.asList(new String[]{"ошибка 410", "страница удалена"});
                param.description = param.title;
                param.content = jTag("h1").text(param.title);
                param.noindex = true;
                param.type = SiteProperty.PageType.outside;

                //Collections.addAll(param.js, "gallery");
                break;
            }
            case "/50x": {
                param.path = url;
                param.title = "Сервис не доступен";
                param.keywords = Arrays.asList(new String[]{"ошибка 500", "сервис не доступен"});
                param.description = param.title;
                param.content = jTag("h1").text(param.title);
                param.noindex = true;
                param.type = SiteProperty.PageType.outside;

                //Collections.addAll(param.js, "gallery");
                break;
            }
        }

        if (param.type != null) {
            pageContent();
        }
    }

    Element getContentBody() {
        Elements els = doc.getElementsByClass("content-body");
        return els.first();//els.size() > 0 ? els.first() : null;
    }

    void htmlHead() {
        Element head = doc.head();

        meta().charset("utf-8").appendTo(head);

        new Element("base")
                .attr("href", "//" + site.host + "/")
                .appendTo(head);


        if (param.noindex) {
            meta().name("robots").content("noindex, nofollow, noarchive").appendTo(head);
        }

        if (site.ini.flagSSL) {
            // always origin never
            meta().name("referrer").content("always").appendTo(head);
        }

        new Element("link")
                .attr("rel", "canonical")
                .attr("href", site.protocol + site.host + param.path)
                .appendTo(head);

        if (param.keywords.size() > 0) {
            meta().name("keywords").content(String.join(", ", param.keywords)).appendTo(head);
        }

        if (param.description != null) {
            meta().name("description").content(param.description).appendTo(head);
        }

        if (param.title != null) {
            new Element("title").text(param.title).appendTo(head);
        }

        //docHead.append(this.doc.comment('[if lt IE 9]><script src="/js/lib/html5.js"></script><![endif]'));

        // https://bitsofco.de/all-about-favicons-and-touch-icons/

        new Element("link")
                .attr("href", "/favicon.ico")
                .attr("rel", "shortcut icon")
                .appendTo(head);


        new Element("link")
                .attr("href", "/apple-touch-icon-76x76.png")
                .attr("rel", "apple-touch-icon")
                .attr("sizes", "76x76")
                .appendTo(head);


        new Element("link")
                .attr("href", "/apple-touch-icon-152x152.png")
                .attr("rel", "apple-touch-icon")
                .attr("sizes", "152x152")
                .appendTo(head);


        meta().name("viewport").content("width=device-width, initial-scale=1.0").appendTo(head);

        if (param.description != null && param.title != null && param.og.imgSrc != null) {
            // https://developers.facebook.com/tools/debug/

            //param.type == SiteProperty.PageType.root
            switch (param.type) {
                case article:
                    // https://ogp.me/#type_article
                    // https://developers.facebook.com/docs/sharing/webmasters?locale=ru_RU
                    meta().property("og:type").content("article").appendTo(head);
                default:
                    meta().property("og:type").content("website").appendTo(head);
                    break;
            }

            meta().property("og:title").content(param.title).appendTo(head);
            meta().property("og:description").content(param.description).appendTo(head);
            meta().property("og:url").content(site.protocol + site.host + param.path).appendTo(head);
            meta().property("og:image")
                    .content(site.protocol + site.host + VUtil.imageSize600(param.og.imgSrc))
                    .appendTo(head);

            if (param.og.videoSrc != null) {

                if (!param.og.videoSrc.startsWith("http")) {
                    param.og.videoSrc = site.protocol + (param.og.videoSrc.startsWith("//") ? param.og.videoSrc.substring(2) : param.og.videoSrc);
                }

                meta().property("og:video").content(param.og.videoSrc).appendTo(head);

                if (param.og.videoType != null) {
                    meta().property("og:video:type").content(param.og.videoType).appendTo(head);
                }
            }
        }


        if (site.ini.flag2019) {
            new Element("style")
                    .attr("nonce", "abcdefg")
                    .appendChild(new Comment(pageCss2019()))
                    .appendTo(head);
        } else {
            new Element("link")
                    .attr("rel", "stylesheet")
                    .attr("href", "/css/" + pageCss() + ".css")
                    .appendTo(head);
        }


        if (param.flagRSS) {
            new Element("link")
                    .attr("rel", "alternate")
                    .attr("type", "application/rss+xml")
                    .attr("href", "/index.rss")
                    .appendTo(head);
        }

        if (param.pageNumberNext > 0) {
            new Element("link")
                    .attr("rel", "next")
                    .attr("href", "//" + site.host + "/page/" + param.pageNumberNext)
                    .appendTo(head);
        }

        if (param.pageNumberPrev > 0) {
            String prevLink = param.pageNumberPrev == 1 ? "//" + site.host + "/" : "//" + site.host + "/page/" + param.pageNumberPrev;
            new Element("link")
                    .attr("rel", "prev")
                    .attr("href", prevLink)
                    .appendTo(head);
        }

    }

    void pageContent() {
        boolean isOutside = param.type == SiteProperty.PageType.outside;
        boolean notRootParent = node != null && node.head.idp > 0;

        // header
        pageContentBlock(site.ini.contentHeader(param.type, notRootParent), "header");

        // body
        if (site.ini.contentBody) {
            TagBase elBody = div().addClass(site.nameContent + "body").appendTo(docContent);
            if (isOutside) {
                elBody.append(param.content);
            } else {
                elBody.data("number", node.head.idn).append(node.content);
            }
        }

        // catalog
        if (!isOutside && site.ini.contentCatalog) pageContentCatalog();

        // footer
        pageContentBlock(site.ini.contentFooter(param.type, notRootParent), "footer");

        if (param.type == SiteProperty.PageType.article) {
            if (site.srcBannerBottom != null) {
                jTag("div").addClass("content-news").appendTo(docContent).append(site.srcBannerBottom);
            } else {
                //pageYandexHoriz(docContent, false);
            }
        }

        // comment
        if (!isOutside && site.ini.contentComment) pageContentComment();

    }

    void pageYandexHoriz(Element target, boolean before) {

    }

    void pageContentBlock(ArrayList<SiteProperty.Block> list, String jTagKey) {
        if (list.size() == 0) return;
        TagBase jTagParent = div().addClass(site.nameContent + jTagKey).appendTo(docContent);
        for (SiteProperty.Block key : list) {
            TagBase jTagBlock = null;
            switch (key) {
                case labels:
                    jTagBlock = pageBlockLabelsBefore();
                    break;
                case breadcrumb:
                    jTagBlock = pageBlockBreadcrumb();
                    break;
                case contents:
                    jTagBlock = pageBlockContents();
                    break;
                case publisher:
                    jTagBlock = pageBlockPublisher();
                    break;
                case rating:
                    jTagBlock = pageBlockRating();
                    break;
                case readalso:
                    jTagBlock = pageBlockReadalso();
                    break;
            }
            if (jTagBlock != null) {
                jTagBlock.appendTo(jTagParent);
            }
        }
    }

    TagBase pageBlockLabelsBefore() {
        List<Integer> list = new ArrayList<>();
        if (node != null && node.head.keywords != null) {
            list.addAll(node.head.labels);
        }
        return pageBlockLabels(list);
    }

    TagBase pageBlockLabels(List<Integer> list) {
        TagBase jTagElem = null;
        if (list.size() > 0) {
            jTagElem = jTag("ul").addClass("labels");
            for (Integer idn : list) {
                NodeTreeElem t = site.tree.byIdn(idn);
                if (t != null) {
                    jTag("li").appendTo(jTagElem).append(
                            jTag("a").attr("href", t.idn).attr("rel", "tag").text(t.text)
                    );
                }
            }
        }
        return jTagElem;
    }

    TagBase pageBlockBreadcrumb() {
        TagBase jTagElem = null;

        List<LinkData> list = new ArrayList<>();

        switch (param.type) {
            case root:
            case label:
            case article:
            case folder: {
                int idn = node.head.idn;
                while (idn > 0) {
                    NodeTreeElem t = site.tree.byIdn(idn);
                    if (idn == node.head.idn || site.folderEnabled(t.idn)) {
                        list.add(new LinkData().path(site.getUrlByIdn(idn)).text(idn == node.head.idn ? node.head.title : t.text));
                    }
                    idn = t.idp;
                }
                break;
            }
            case outside: {
                if (!param.noindex) {
                    list.add(new LinkData().path(param.path).text(param.title));
                }
                break;
            }
        }

        if (list.size() > 0) {
            jTagElem = jTag("p").itemprop("breadcrumb");
            list.add(new LinkData().path("/").text("Главная"));
            int i = list.size();
            while (i > 0) {
                LinkData link = list.get(--i);
                TagBase linkElem = jTag("a").attr("href", link.path()).text(link.text());
                if (link.path().endsWith("/")) {
                    linkElem.attr("rel", "up");
                }
                jTagElem.append(
                        linkElem.elem,
                        i > 0 ? new TextNode(" →  ") : null
                );
            }
        }

        return jTagElem;
    }

    TagBase pageBlockPublisher() {
        String dateAdd = node.head.date.get(0);
        TagBase jTagDate = jTag("p").addClass("published").append(
                jTag("span").addClass("author").data("text", site.getUser(node.head.idu)),
                jTag("time").itemprop("datePublished").attr("datetime", dateAdd).text(VUtil.dateString(dateAdd))
        );
        param.dateJTag = jTagDate;
        return jTagDate;
    }

    void pageBlockPublisherFinish() {
        if (param.dateJTag == null) return;
        String add = node.head.date.get(0);
        String last = node.head.date.get(1);
        if (param.dateLast != null) {
            long d = VUtil.dateFromJSON(last).until(VUtil.dateFromJSON(param.dateLast), ChronoUnit.MINUTES);

            if (d > 0) {
                last = param.dateLast;
                meta().itemprop("lastReviewed").content(VUtil.dateSimpleStr(node.head.date.get(1))).appendTo(param.dateJTag);
            }
        }

        if (VUtil.dateFromJSON(add).until(VUtil.dateFromJSON(last), ChronoUnit.DAYS) > 30) {
            jTag("time").itemprop("dateModified").attr("datetime", last).text(VUtil.dateString(last)).appendTo(param.dateJTag);
        } else {
            meta().itemprop("dateModified").content(VUtil.dateSimpleStr(last)).appendTo(param.dateJTag);
        }

        //VUtil.println(VUtil.dateSimpleStr(last), VUtil.dateString(last), param.dateJTag.elem.html());

        jTag("span").addClass("viewsData").data("count", VUtil.getNodeViews(site.host, node.head.idn)).appendTo(param.dateJTag);

        //int v = site.getNodeViews(node.head.idn);
        //Span().as('viewsData').data('count', fw.getNodeViews(this.pageData.host, this.pageData.idn)).last(out);
    }

    TagBase pageBlockReadalso() {

        TagBase jTagElem = null;

        if (node.head.keywords != null) {
            List<int[]> idns = new ArrayList<>();
            int idnDoc = node.head.idn;

            for (HashMap.Entry<Integer, HashSet<String>> entry : site.keywords.entrySet()) {
                HashSet<String> v = entry.getValue();
                int idnEnt = entry.getKey();
                if (idnEnt == node.head.idn) continue;
                int counter = 0;
                for (String w : node.head.keywords) if (v.contains(w)) counter++;
                for (Integer w : node.head.labels) if (v.contains(w.toString())) counter++;
                if (counter > 0) {
                    double dateWeidht = idnDoc > idnEnt ? idnEnt / (double) idnDoc : idnDoc / (double) idnEnt;
                    idns.add(new int[]{idnEnt, (int) Math.round((counter + dateWeidht) * 100000)});
                }
            }

            if (idns.size() > 0) {
                param.css.add("readalso");

                idns.sort((p1, p2) -> p2[1] - p1[1]);
                idns = idns.subList(0, idns.size() > site.ini.maxReadAlso ? site.ini.maxReadAlso : idns.size());

                if (site.ini.flagSite2016) {
                    jTagElem = jTag("nav").addClass("readalso-image");
                    TagBase jTagUl = jTag("ul").appendTo(jTagElem);

                    for (int[] k : idns) {
                        int idn = k[0];
                        NodeTreeElem n = site.tree.byIdn(idn);
                        if (n != null) {
                            Announce ann = site.getAnnounce(idn);
                            if (ann != null && ann.img != null && ann.img.src != null) {
                                jTag("li").data("image", ann.img.src).append(
                                        jTag("a").attr("href", idn).text(n.text)
                                ).appendTo(jTagUl);

                            } else {
                                //FwUtil.error("ViewPage::pageBlockReadalso - Announce not correct", site.host, node.head.idn + "");
                            }
                        }
                    }
                } else {
                    jTagElem = jTag("ul").addClass("readalso");
                    for (int[] k : idns) {
                        NodeTreeElem n = site.tree.byIdn(k[0]);
                        if (n != null) {
                            jTag("li").appendTo(jTagElem).append(
                                    jTag("a").attr("href", n.idn).text(n.text)
                            );
                        }
                    }
                }
            }
        }


        return jTagElem;
    }

    TagBase pageBlockRating() {

        TagBase res;

        param.js.add("rating");
        param.css.add("rating");

        VUtil.Rating data = new VUtil.Rating(node.head.rating);

        if (site.ini.flagSite2016) {
            res = jTag("div").addClass("rating").append(
                    div().addClass("rating-text").append(
                            new TextNode("Рейтинг статьи: "),
                            jTag("span").text(data.value).elem,
                            new TextNode(" из "),
                            jTag("span").text(data.bestRating).elem,
                            new TextNode(", голосов: "),
                            jTag("span").text(data.count).elem
                    ),
                    div().addClass("rating-voting").id("ratingVoting")
            );

        } else {
            res = jTag("div").addClass("rating-float").append(
                    div().id("ratingVoting"),
                    div().addClass("rating-text").append(
                            new TextNode("Оценка: "),
                            jTag("span").text(data.value).elem,
                            new TextNode(" из "),
                            jTag("span").text(data.bestRating).elem,
                            jTag("br").elem,
                            new TextNode("Голосов: "),
                            jTag("span").text(data.count).elem
                    )
            );

        }

        return res;
    }

    TagBase pageBlockContents() {
        return jTag("nav").addClass("contents");
    }

    void pageContentCatalog() {
        ArrayList<NodeAttach> list = new ArrayList<>();
        for (NodeAttach elem : node.attach) {
            if (VUtil.isTrue(elem.flagCatalog) && elem.src != null && site.reCatalogExtList.matcher(elem.src).find()) {
                list.add(elem);
            }
        }
        if (list.size() > 0) {
            div().addClass(site.nameContent + "catalog")
                    .appendTo(docContent)
                    .append(
                            elemCatalog(list, 150, false).addClass("col3")
                    );
        }
    }

    void pageContentComment() {

        NodeHead h = node.head;
        if (h.idp == 0 || h.idp == site.ini.idnLabel || h.flagFolder || h.flagBlock || !h.flagValid) {
            return;
        }
        param.css.add("comments");
        param.js.add("comments");

        if (site.ini.flagPriceByKeywords) {
            TagBase tagList = site.priceKeywords.insertGoodsList();
            if (tagList != null) {
                tagList.appendTo(docContent);
            }
        }

        TagBase divComment = div().addClass(site.nameContent + "comment").appendTo(docContent);

        List<NodeAttach> list = node.attach.stream().filter(e -> VUtil.isTrue(e.flagComment)).collect(Collectors.toList());

        if (list.size() > 0) {
            meta().itemprop("commentCount").content(list.size() + "").appendTo(divComment);
        }

        int commentsPerPage = site.commentsPerPage;

        int pages = (int) Math.ceil(list.size() / (double) commentsPerPage) - 1;
        if (pages < 0) pages = 0;

        if (pages > 0) {
            // сохраняем в json
            String path = VUtil.DIRFile + site.host + "/json/comment/";
            VUtil.createDirs(path);

            for (int i = 0; i < pages; i++) {
                int start = i * commentsPerPage;

                ArrayList<JsonComment> data = new ArrayList<>();
                for (NodeAttach line : list.subList(start, start + commentsPerPage)) {
                    String content = line.content == null ? "" : line.content;
                    if (content.contains("<a")) {
                        Document doc2 = line.getContent();
                        //doc2.getElementsByJTag("a").stream().forEach(e->elemLink(e));
                        for (Element e : doc2.getElementsByTag("a")) site.elemLink(e, node == null ? 0 : node.head.idn);
                        content = VUtil.html(doc2);
                    }
                    data.add(new JsonComment(
                            line.idf, line.like,
                            line.src, content,
                            site.getUser(line.idu, line.anonym), line.date
                    ));
                }

                VUtil.writeJson(path + node.head.idn + "-" + (i + 1), data);
            }
            list = list.subList(pages * commentsPerPage, list.size());
        }

        //itemscope: '',
        //itemtype: 'https://schema.org/UserComments'
        TagBase ulElem = jTag("ul")
                .addClass("content-comment__items")
                .data("last", (list.size() > 0 ? list.get(list.size() - 1).idf : 0))
                .data("pages", pages)
                .appendTo(divComment);

        for (NodeAttach attach : list) {
            TagBase elLi = jTag("li")
                    .addClass("content-comment__item")
                    .appendTo(ulElem).itemscope("").itemtype("https://schema.org/Comment")
                    .data("idf", attach.idf)
                    .data("like", attach.like);
            if (attach.src != null && !attach.src.isEmpty()) {
                elemImage(attach, 150).appendTo(elLi.elem);
            }

            div().itemprop("text").append(attach.content).appendTo(elLi);
            String user = site.getUser(attach.idu, attach.anonym);
            div().addClass("published").appendTo(elLi).append(
                    jTag("time").itemprop("dateCreated").attr("datetime", attach.date).text(VUtil.dateString(attach.date)).elem,
                    new TextNode(" "),
                    jTag("span").addClass("author").data("text", user).itemprop("creator").text(user).elem
            );

            param.dateLast = attach.date;
        }
    }

    void pageScript() {


        ArrayList<String> js = new ArrayList<>();
        if (param.js.contains("login")) {
            js.add("login");
            param.js.removeIf(e -> e.equals("login"));
        }
        js.addAll(param.js);

        if (site.ini.flag2019 && !site.ini.flag2020) {
            new Element("script")
                    .attr("src", "/js/main-err.js?ver=" + site.versionJs)
                    .attr("async", "")
                    .appendTo(doc.body())
            ;
        }

        if (param.jsPage != null) {
            String path = VUtil.DIRPublic + param.jsPage;
            if (VUtil.pathExists(path)) {
                String ver = VUtil.lastModified(path).replaceAll("[^\\d-]", "");
                new Element("script")
                        .attr("src", param.jsPage + "?ver=" + ver)
                        .attr("async", "")
                        .appendTo(doc.body());
            }
        }


        Element script = new Element("script").appendTo(doc.body());

        if (site.ini.flag2020) {
            script
                    .attr("id", "param")
                    .attr("defer", "")
                    .attr("src", "/js/" + site.ini.jsFile + "?ver=" + site.versionJs)
            ;
            //site.ini.yandex
        } else if (site.ini.flag2019) {
            String jsName = site.ini.flagSite2016 ? "main-tbt.js" : "main-2019.js";
            script.attr("id", "param").attr("async", "").attr("src", "/js/" + jsName + "?ver=" + site.versionJs);
        } else {
            script.attr("data-main", "index")
                    .attr("id", "param").attr("async", "")
                    .attr("src", "/js/" + site.ini.jsFile + "?ver=" + site.versionJs)
                    .attr("data-site", String.join(" ", js));
        }

        for (Map.Entry<String, String> entry : param.jsData.entrySet()) {
            script.attr("data-" + entry.getKey(), entry.getValue());
        }


    }

    void pageContentBody() {

        for (Element e : doc.getElementsByTag("nav")) {
            elemNav(e);
        }

        if (node != null && node.head != null && site.keywordsLink != null && node.head.labels != null) {
            for (Integer idn : node.head.labels) {
                Element n = site.keywordsLink.get(idn);
                if (n != null) {
                    Element h1 = docContent.getElementsByTag("h1").first();
                    if (h1 != null) {
                        h1.after(n);
                    }
                    break;
                }
            }
        }

        if (site.ini.flagBodyURL) {
            doc.body().addClass("site " + (param.path.equals("/") ? "main" : param.path.replaceAll("/", "")));
        }

        // =====================

        Element elBody = getContentBody();
        if (elBody == null) {
            return;
        }

        //for (Element e : elBody.getElementsByClass("shop")) elemShop(e);

        int paragInd = 0;
        for (Element e : elBody.getElementsByTag("p")) {
            //if (e.hasClass("shop")) {
            //elemShop(e);
            //} else
            if (e.hasClass("like")) {
                param.css.add("like");
                param.js.add("like");
            } else if (e.hasClass("command")) {
                //site.tree, site.ini
                new PCommand(site, node, param, e);
            } else {
                if (param.type == SiteProperty.PageType.label || param.type == SiteProperty.PageType.article) {
                    if (paragInd++ == 2) {
                        //if (site.ini.yandex != null && site.ini.yandex.horiz != null) {
                        //pageYandexHoriz(e, true);
                        //} else
                        if (site.ini.flagPriceByKeywords && node.head.labels != null) {

                            ArrayList<PriceElem> dataOut = new ArrayList<>();

                            String dataSource = "1";

                            // && node.head.flagElastic
                            //if (site.ini.flagElastic) {
                            List<String> tags = new ArrayList<>();
                            List<String> labels = new ArrayList<>();
                            if (node.head.searchPhrase != null) {
                                labels.add(node.head.searchPhrase);
                            }
                            labels.add(node.head.title);

                            tags = getPageKeyWords();
                            if (tags.size() < 5) {
                                String parentTitle = site.tree.byIdn(node.head.idp).text;
                                if (!parentTitle.isEmpty()) {
                                    tags.add(parentTitle);
                                }
                                //VUtil.println(l);
                            }


                            dataSource = "2";


                            dataOut = site.priceKeywords.insertGoodsNew(site.host, node.head.idn, labels, tags, node.head.flagBook);


                            if (!dataOut.isEmpty()) {

                                e.before(jTag("div")
                                        .addClass("view-price")
                                        .addClass("view-price-h")
                                        .addClass("unselect")
                                        .append(
                                                jTag("script").attr("type", "html/template").attr("data-source", dataSource).text(VUtil.jsonString(dataOut))
                                        ).elem);

                                param.css.add("price");
                            }

                        }
                    }
                }
            }
        }

        for (Element e : elBody.getElementsByTag("blockquote")) {
            elemBlockQuote(e);
        }


        int i = 0;
        for (Element e : elBody.getElementsByTag("img")) {
            if (i++ == 0) {
                param.og.imgSrc = e.attr("src");
            }
            elemImg(e, i);
        }

    }


    // ============

    void iterIdp(ArrayList<ViewSite.SQLIdn> rowsFolder, List<Integer> idps, int idp) {
        idps.add(idp);
        for (ViewSite.SQLIdn row : rowsFolder) {
            if (row.idp == idp) {
                iterIdp(rowsFolder, idps, row.idn);
            }
        }
    }

    void blogSection() {

        PostgreSQL db = new PostgreSQL(site.host);
        db.connect();
        ArrayList<ViewSite.SQLIdn> rowsFolder = db.select(ViewSite.SQLIdn.class)
                .fromTree()
                .and("flagFolder", true)
                .and("flagBlock", false)
                .and("flagValid", true)
                .exec();


        if (rowsFolder == null) return;

        List<Integer> idps = new ArrayList<>();

        iterIdp(rowsFolder, idps, node.head.idn);

        ArrayList<ViewSite.SQLIdn> select1 = db.select(ViewSite.SQLIdn.class)
                .fromTree()
                .and("flagFolder", false)
                .and("flagBlock", false)
                .and("flagValid", true)
                .andIn("idp", String.join(",", idps.stream().map(a -> a + "").collect(Collectors.toList())))
                .order("dateAdd", false)
                .exec();

        db.close();

        if (select1 == null) return;

        List<Integer> idns = select1.stream().map(a -> a.idn).collect(Collectors.toList());

        pageRSS(idns.subList(0, idns.size() > 20 ? 20 : idns.size()));

        int perPage = 10;

        if (idns.size() > perPage) {
            VUtil.writeJson(site.getDirAnnonce() + node.head.idn, idns);
            param.js.add("pageNext");
        }

        param.css.add("pages");
        param.css.add("announce");

        TagBase main = div().addClass("content-announce").appendTo(docContent);
        TagBase ul = jTag("ul").appendTo(main);

        for (int i = 0, im = idns.size() > perPage ? perPage : idns.size(); i < im; i++) {
            ul.append(
                    elemAnnounce(idns.get(i), true)
            );
            if (i == 2) blogAdv(ul);
        }
    }

    void blogAdv(TagBase ul) {
        if (site.srcBannerBottom != null) {
            jTag("li").appendTo(ul).append(site.srcBannerBottom);
        } else {
            pageYandexHoriz(jTag("li").appendTo(ul).elem, false);
        }
    }

    void blogLabels() {

        param.js.add("sortingTable");
        param.css.add("sorting");

        Element elBody = getContentBody();


        TagBase table = jTag("table").addClass("sorting col-2").appendTo(elBody);
        jTag("thead").appendTo(table).append(
                jTag("tr").append(
                        jTag("th").text("Указатель"),
                        jTag("th").text("Публикаций")
                )
        );

        TagBase tbody = jTag("tbody").appendTo(table);


        HashMap<String, Integer> wordcount = ViewSite.loadWordCount(site.host);

        for (Map.Entry<String, Integer> ent : wordcount.entrySet()) {
            if (VUtil.reDgt.matcher(ent.getKey()).find()) {
                int idn = Integer.parseInt(ent.getKey(), 10);
                tbody.append(
                        jTag("tr").append(
                                jTag("td").append(
                                        jTag("a").attr("href", idn).text(site.tree.byIdn(idn).text)
                                ),
                                jTag("td").text(ent.getValue())
                        )
                );
            }
        }
    }

    @SuppressWarnings("Duplicates")
    void blogLabel() {
        int perPage = 20;

        ArrayList<Integer> idnsOrdered = site.getOrderedIdnList();
        if (idnsOrdered == null || idnsOrdered.size() == 0) return;

        Set<Integer> idns = new HashSet<>();

        String idnStr = node.head.idn + "";
        for (Map.Entry<Integer, HashSet<String>> ent : site.keywords.entrySet()) {
            if (ent.getValue().contains(idnStr)) {
                idns.add(ent.getKey());
            }
        }

        List<Integer> idnSelected = idnsOrdered.stream().filter(v -> idns.contains(v)).collect(Collectors.toList());
        if (idnSelected.size() > perPage) {
            VUtil.writeJson(site.getDirAnnonce() + node.head.idn, idnSelected);
            param.js.add("pageNext");
        }

        param.css.add("pages");
        param.css.add("announce-mini");

        TagBase main = div().addClass(site.ini.flag2019 ? "announce-mini" : "content-announce").appendTo(docContent);
        TagBase ul = jTag("ul").appendTo(main);

        for (int i = 0, im = idnSelected.size() > perPage ? perPage : idnSelected.size(); i < im; i++) {
            Announce data = site.getAnnounce(idnSelected.get(i));
            if (data == null) continue;

            TagBase img = null;
            if (data.img != null) {
                ViewSite.Size size = new ViewSite.Size(100, data.img.width, data.img.height);
                img = jTag("a").attr("href", data.title[0]).append(
                        new TagCanvas()
                                .dataSrc(VUtil.imageSize(data.img.src, 150))
                                .width(size.width)
                                .height(size.height)
                );
            }

            jTag("li").appendTo(ul).append(
                    div().addClass("col1").append(img),
                    div().addClass("col2").append(
                            div().addClass("section").append(
                                    jTag("a").attr("href", data.section[0]).text(data.section[1])
                            ),
                            div().addClass("title").append(
                                    jTag("a").attr("href", data.title[0]).text(data.title[1])
                            )
                    )
            );
        }
    }

    void blogRoot() {
        if (site.ini.blogPerPage == 0) return;

        int pageNum = param.pageNumber - 1;
        int perPage = site.ini.blogPerPage;

        List<Integer> orderedIdnList = site.getOrderedIdnList();

        int allArticle = orderedIdnList.size();

        if (allArticle == 0) {
            VUtil.error("blogRoot", this.site.host + param.path, (VUtil.IFunc) () -> node.head.idn, param.pageNumber);
            return;
        }

        param.css.add("announce");
        param.css.add("pages");

        TagBase main = div().addClass("content-announce").appendTo(docContent);
        TagBase ul = jTag("ul").appendTo(main);

        if (pageNum > 0) {
            getContentBody().remove();
        }

        //int num = 0;
        int last = (pageNum + 1) * perPage;
        List<Integer> idns = orderedIdnList.subList(pageNum * perPage, orderedIdnList.size() > last ? last : orderedIdnList.size());

        int counter = 0;
        for (int idn : idns) {
            TagBase elAnn = elemAnnounce(idn, false);
            if (elAnn != null) {
                ul.append(elAnn);
                if (++counter == 2) blogAdv(ul);
            } else {
                VUtil.error("not Announce", idn);
            }
        }

        if (param.pageNumber == 1) {
            pageRSS(idns);

            //  && param.type== SiteProperty.PageType.root
            if (site.host.equals("www.to.com")) {
                VUtil.println("rssYandexZen");
                rssYandexZen(orderedIdnList.subList(0, 30));
            }
        }

        int im = (int) Math.ceil(allArticle / (double) perPage);
        if (param.pageNumber < im) param.pageNumberNext = param.pageNumber + 1;
        if (param.pageNumber > 1) param.pageNumberPrev = param.pageNumber - 1;

        main = div().addClass("content-pages").appendTo(docContent);
        ul = jTag("ul").appendTo(main);

        boolean flagAll = site.ini.sapeUpdate;
        boolean flagPrev = false;

        for (int i = 0; i < im; i++) {
            int num = i + 1;
            TagBase elLink = jTag("a").attr("href", i > 0 ? "/page/" + num : "/");

            if (num == 1) {
                elLink.attr("rel", "first");
            } else if (num == im) {
                elLink.attr("rel", "last");
            } else if (num < param.pageNumber) {
                elLink.attr("rel", "prev");
            } else {
                elLink.attr("rel", "next");
            }

            if (i < 9 || i > im - 3 || (num > param.pageNumber - 3 && num < param.pageNumber + 3)) {
                if (num == param.pageNumber) {
                    ul.append(jTag("li").addClass("number").append("<b>" + num + "</b>"));
                } else {
                    ul.append(jTag("li").addClass("number").append(elLink.text(num)));
                }
                flagPrev = true;
            } else if (flagAll) {
                ul.append(jTag("li").append(elLink.text(".")));
            } else {
                if (flagPrev) {
                    flagPrev = false;
                    ul.append(jTag("li").addClass("number").text("..."));
                }
            }
        }
    }

    TagBase blogUsers() {

        param.js.add("sortingTable~1~orderDesc");
        param.css.add("sorting");

        TagBase table = jTag("table").addClass("sorting col-2");
        jTag("thead").appendTo(table).append(
                jTag("tr").append(
                        jTag("th").text("Пользователь"),
                        jTag("th").text("Последний визит")
                )
        );
        TagBase tbody = jTag("tbody").appendTo(table);

        String pathUser = VUtil.DIRDomain + site.host + "/user/";

        try {
            Files.list(Paths.get(pathUser))
                    .filter(Files::isDirectory).forEach(folder -> {
                try {
                    Files.list(folder)
                            .filter(Files::isRegularFile).forEach(fileName -> {
                        int idn = VUtil.idnFromFileName(fileName.getFileName().toString());
                        if (idn > 0) {
                            SiteUserData user = VUtil.readUser(site.host, idn);
                            if (user != null) {
                                tbody.append(
                                        jTag("tr").append(
                                                jTag("td").addClass("author").text(user.name),
                                                jTag("td").text(user.dateLast.substring(0, user.dateLast.indexOf('T')))
                                        )
                                );
                            }
                        }
                    });
                } catch (IOException e) {
                    VUtil.error("ViewPage::blogUsers(1)", e);
                }
            });
        } catch (IOException e) {
            VUtil.error("ViewPage::blogUsers(2)", e);
        }

        TagBase main = div().append(
                jTag("h1").addClass("center").text("Список пользователей"),
                jTag("p").addClass("center").text("Всего: " + (tbody.elem.childNodes().size() - 1)),
                table
        );

        return main;
    }

    TagBase blogTracker() {

        String path = Tracker.folderTracker(site.host);// VUtil.DIRMemory + site.host + "/tracker/";

        TagBase ulComment = jTag("ul");
        //TagBase ulGameRelult = jTag("ul");
        //boolean flagGame = false;

        //File[] fileList = VUtil.dirlistLastModified(path);

        for (Path f : VUtil.dirlistLastModified(path)) {
            String fileName = f.getFileName().toString();
            if (!VUtil.isJson(fileName)) continue;

            int idn = VUtil.idnFromFileName(fileName);
            ViewSite.DataAnonym data = VUtil.readJson(f, ViewSite.DataAnonym.class);

            NodeTreeElem ntree = site.tree.byIdn(idn);
            if (ntree == null) continue;

            String user = site.getUser(data.idu, data.anonym);

            if (site.tree.byIdn(ntree.idp).text.length() < 5) {
                VUtil.println("blogTracker", site.host, fileName, ntree.idp);
                continue;
            }

            TagBase li = jTag("li").append(
                    new TextNode(site.tree.byIdn(ntree.idp).text),
                    jTag("br").elem,
                    jTag("a").addClass("link").attr("href", idn).text(ntree.text).elem,
                    jTag("br").elem
            );

            if (!fileName.contains("-")) {
                li.addClass("article");
            } else {
                boolean flagComment = true; // data.flagComment;
                li.addClass(flagComment ? "comment" : "catalog");
                if (VUtil.notEmpty(data.src) && data.w > 0 && data.h > 0) {
                    ViewSite.Size size = new ViewSite.Size(150, data.w, data.h);
                    new TagCanvas()
                            .dataSrc(VUtil.imageSize(data.src, 150))
                            .width(size.width)
                            .height(size.height)
                            .addClass("imgl").appendTo(li);
                }
                if (VUtil.notEmpty(data.content)) {
                    div().addClass("text").append(data.content).appendTo(li);
                }

            }

            div().addClass("published").append(
                    jTag("time").attr("datetime", data.date),
                    jTag("span").addClass("author").data("text", user).text(user)
            ).appendTo(li);

            jTag("br").addClass("both").appendTo(li);

            ulComment.append(li);
        }


        return div().addClass("tracker").append(
                jTag("h1").addClass("center").text("Новости сайта"),
                ulComment
        );
    }

    // ============

    Announce getAnnounce(int idn) {
        Announce data = site.getAnnounce(idn);
        if (data == null) {
            site.createAnnonce(idn);
            data = site.getAnnounce(idn);
        }
        return data;
    }

    TagBase elemAnnounce(int idn, boolean flagNotSection) {
        TagBase out = null;

        Announce data = getAnnounce(idn);
        if (data != null) {
            TagBase outLine = null;
            if (data.com > 0 || data.cat > 0 || data.views > 0) {
                ArrayList<String> outAdd = new ArrayList<>();
                if (data.com > 0) {
                    outAdd.add("Комментарии: " + data.com);
                }
                if (data.cat > 0) {
                    outAdd.add("Каталог: " + data.cat);
                }
                //if (data.views > 0) {
                //    outAdd.add("Просмотры: " + data.views);
                //}
                if (outAdd.size() > 0) {
                    outLine = jTag("small").text(String.join(", ", outAdd));
                }
            }

            Document doc = Jsoup.parse(data.body);
            for (Element img : doc.getElementsByTag("img")) {

                String src = site.protocol + site.host + img.attr("src");
                img.replaceWith(
                        jTag("span").itemprop("image").itemscope("").itemtype("https://schema.org/ImageObject").append(
                                new TagCanvas().dataSrc(img.attr("src")).width(img.attr("width")).height(img.attr("height")).addClass(img.attr("class")),
                                meta().itemprop("url").content(src),
                                meta().itemprop("width").content(img.attr("width")),
                                meta().itemprop("height").content(img.attr("height"))
                        ).elem
                );

            }

            TagBase labelEl = data.labelList == null ? null : pageBlockLabels(data.labelList);


            TagBase publisher = null;
            if (site.ini.logo != null) {
                SiteProperty.Logo logo = site.ini.logo;
                TagBase elLogo = div().itemprop("logo").itemscope("").itemtype("https://schema.org/ImageObject").append(
                        meta().itemprop("url").content(site.protocol + site.host + logo.src),
                        meta().itemprop("width").content(logo.width + ""),
                        meta().itemprop("height").content(logo.height + "")
                );

                publisher = div().itemprop("publisher").itemscope("").itemtype("https://schema.org/Organization").append(
                        elLogo,
                        meta().itemprop("name").content("to")
                );
            }

            TagBase divBody = div();
            doc.body().childNodes().stream().collect(Collectors.toList()).forEach(el -> divBody.append(el));

            out = jTag("li").append(
                    jTag("article").itemscope("").itemtype("https://schema.org/NewsArticle").append(
                            flagNotSection ? null : div().addClass("section").append(
                                    jTag("a").attr("href", data.section[0]).text(data.section[1])
                            ),
                            jTag("h4").addClass("title").itemprop("name headline").append(
                                    jTag("a").itemprop("url mainEntityOfPage").attr("href", data.title[0]).text(data.title[1])
                            ),
                            labelEl,
                            divBody.addClass("body").itemprop("description"),
                            jTag("footer").addClass("footer").append(
                                    div().append(
                                            jTag("time").itemprop("datePublished").attr("datetime", data.add).text(VUtil.dateString(data.add)),
                                            jTag("span").addClass("author").data("text", data.user)
                                    ),
                                    outLine
                            ),
                            meta().itemprop("author").content(data.user),
                            meta().itemprop("dateModified").content(VUtil.dateSimpleStr(data.last)),
                            publisher,
                            data.com > 0 ? meta().itemprop("interactionCount").content("UserComments:" + data.com) : null
                    )
            );
        }

        return out;
    }

    void elemBlockQuote(Element elem) {
        String text0 = elem.text().trim();
        if (!text0.startsWith("?")) return;

        Pattern reSum = Pattern.compile("^((\\d+)-(\\d+)\\s\\((\\d+)\\))");
        Pattern reAns = Pattern.compile("^(\\d+)(.*)$");

        TagBase main = jTag("blockquote").addClass("testing");
        elem.before(main.elem);

        TagBase ul1 = jTag("ul").appendTo(main);
        TagBase ul2 = null, li;
        Matcher m;

        for (Node dataNode : elem.childNodes()) {
            if (!dataNode.nodeName().equals("p")) continue;

            Element dataTag = (Element) dataNode;

            String text = dataTag.text();
            if (text.startsWith("?")) {
                li = jTag("li").appendTo(ul1).append("<p>" + text.substring(1).trim() + "</p>");
                ul2 = jTag("ul").appendTo(li);
            } else {
                m = reSum.matcher(text);
                if (m.find()) {
                    main.append(
                            jTag("p")
                                    .data("min", m.group(2))
                                    .data("max", m.group(3))
                                    .data("amount", m.group(4))
                                    .append(
                                            text.substring(m.group(1).length()).trim()
                                    )
                    );
                } else {
                    m = reAns.matcher(text);
                    if (m.find() && ul2 != null) {
                        ul2.append(
                                jTag("li")
                                        .data("value", m.group(1))
                                        .append(m.group(2).trim())
                        );
                    }
                }
            }
        }

        param.js.add("ctrlTesting");
        param.css.add("ctrlTesting");
        elem.remove();

    }


    void elemNav(Element elem) {
        if (elem.attr("class").isEmpty()) return;

        TagBase t = null;

        if (elem.hasClass("main")) t = menuMain();
        else if (elem.hasClass("section")) t = menuSection(elem);
            //else if (elem.hasClass("subsection")) t = menuSub();
        else if (elem.hasClass("contents")) t = menuContents();
        else menuDefault(elem);

        if (t != null) {
            elem.appendChild(t.elem);
        }
    }

    void elemImg(Element elem, int ind) {
        String attrSrc = elem.attr("src");
        if (attrSrc.isEmpty()) {
            VUtil.error("ViewPage::elemImg - src == null", site.host, (VUtil.IFunc) () -> node.head.idn);
            return;
        }


        String dataType = elem.attr("data-type");
        elem.removeAttr("data-type");

        NodeAttach fileData = node.attachByKey(VUtil.imageKey(elem.attr("src")));
        if (fileData != null && fileData.group != null && !dataType.isEmpty()) {

            switch (dataType) {
                case "slideshow":
                    elemImgSlideshow(elem, fileData, ind);
                    break;
                case "catalog":
                    elemImgCatalog(elem, fileData);
                    break;
                case "audio":
                    elemImgAudio(elem, fileData);
                    break;
                case "video":
                    elemImgVideo(elem, fileData);
                    break;
                case "quiz":
                    elemImgQuiz(elem, fileData);
                    break;
                default:
                    VUtil.error("ViewPage::elemImg - dataType: " + dataType, site.host, node.head.idn, elem.outerHtml());
                    break;
            }
        } else {
            elemImgImage(elem);
        }
    }

    void elemImgSlideshow(Element elem, NodeAttach fileData, int ind) {

        elem.addClass("slideshow");

        String keyData = "slideshow" + ind;
        List<String[]> datalist = new ArrayList<>();

        for (int idf : fileData.group) {
            NodeAttach elemFile = node.attachByKey(idf);
            if (elemFile == null || elemFile.w == 0) continue;
            String text = VUtil.textOnly(elemFile.content);
            datalist.add(new String[]{text, elemFile.src});
        }

        elem.attr("data-key", keyData);


        doc.body().appendChild(
                new Comment(keyData + ":" + VUtil.jsonString(datalist))
        );

        site.elemCanvasFromImg(elem);
    }

    void elemImgCatalog(Element elem, NodeAttach fileData) {

        int srcSize = VUtil.imageSize(elem.attr("src"));

        Element elemParent = elem.parent();
        if (elemParent == null || !elemParent.tagName().equals("p")) return;

        boolean flagLike = elemParent.hasClass("like");

        String dataParam = elem.attr("data-param");
        ArrayList<NodeAttach> ctgList = new ArrayList<>();

        for (int idf : fileData.group) {
            NodeAttach elemFile = node.attachByKey(idf);

            if (elemFile != null && elemFile.src != null) {
                if (site.reCatalogExtList.matcher(elemFile.src).find()) {
                    ctgList.add(elemFile);
                }
            } else {
                VUtil.error("ViewPage::elemImgCatalog -  not found", idf + "", node.head.idn + "", site.host);
            }
        }

        TagBase catalog = null;
        if (dataParam.startsWith("col")) {
            int colCount = VUtil.getInt(dataParam.substring(3));
            if (colCount > 1) {
                catalog = elemCatalog(ctgList, srcSize, flagLike);
                catalog.addClass("col" + colCount);
            }
        } else {
            param.css.add("catalogFigure2");
            catalog = div().addClass("catalog-" + dataParam);
            boolean flagLeft = dataParam.equals("left");
            for (NodeAttach attachData : ctgList) {
                if (VUtil.notEmpty(attachData.src)) {
                    TagBase figure = jTag("figure").appendTo(catalog);
                    TagBase figcaption = div();

                    if (VUtil.notEmpty(attachData.content) && !VUtil.textOnly(attachData.content).isEmpty()) {
                        figcaption = jTag("figcaption").append(attachData.content);
                    }

                    TagBase div = div().append(
                            elemImage(attachData, 250, flagLike)
                    );

                    if (flagLeft) {
                        figure.append(div);
                        figure.append(figcaption);
                    } else {
                        figure.append(figcaption);
                        figure.append(div);
                    }
                }
            }
        }

        if (catalog != null) {
            if (flagLike) {
                catalog.addClass("like");
                param.css.add("like");
                param.js.add("like");
            }
            elemParent.before(catalog.elem);
        }
        elem.remove();
        if (elemParent.text().trim().isEmpty()) elemParent.remove();

    }

    void elemImgAudio(Element elem, NodeAttach fileData) {
        // всего 25 статей

        Element elemParent = site.getParag(elem);
        if (elemParent == null) return;

        ViewSite.CatalogDict ctgText = new ViewSite.CatalogDict();
        ViewSite.CatalogDict ctgDict = new ViewSite.CatalogDict();

        for (int idf : fileData.group) {
            NodeAttach elemFile = node.attachByKey(idf);
            if (elemFile != null && elemFile.src != null) {
                String ext = VUtil.getFileExt(elemFile.src);
                ctgDict.set(ext, elemFile);
                if (VUtil.notEmpty(elemFile.content)) {
                    ctgText.set(ext, elemFile);
                }
            }
        }

        if (ctgDict.jpg != null && ctgDict.mp3 != null) {
            String attrCl = elem.attr("class");
            TagBase figure = jTag("figure").addClass((attrCl.isEmpty() ? "imgl" : attrCl) + " w250");

            elemParent.before(figure.elem);
            figure.append(elemImage(fileData, 250), elemAudio(ctgDict.mp3.src));

            if (ctgText.jpg != null || ctgText.mp3 != null) {
                String t1 = "";
                String t2 = "";
                if (ctgText.jpg != null) {
                    t1 = ctgText.jpg.content.replaceAll("<p>", "<br>").replaceAll("</p>", "");
                }
                if (ctgText.mp3 != null) {
                    t2 = ctgText.mp3.content.replaceAll("<p>", "<br>").replaceAll("</p>", "");
                }
                figure.append(jTag("figcaption").append((t1 + ' ' + t2).trim()));
            }
            elem.remove();
        }

    }

    void elemImgVideo(Element elem, NodeAttach fileData) {

        ViewSite.CatalogDict ctgDict = new ViewSite.CatalogDict();

        for (int idf : fileData.group) {
            NodeAttach elemFile = node.attachByKey(idf);
            if (elemFile != null && elemFile.src != null) {
                ctgDict.set(VUtil.getFileExt(elemFile.src), elemFile);
            }
        }

        if (ctgDict.jpg != null && ctgDict.mp4 != null) {

            String src = "//" + site.host + "/file/video/" + ctgDict.mp4.src;
            String srcType = "video/mp4";

            TagBase video = jTag("video");
            video.attr("poster", "//" + site.host + "/file/" + ctgDict.jpg.src);
            video.attr("src", src);
            video.attr("type", srcType);
            video.attr("width", ctgDict.jpg.w);
            video.attr("height", ctgDict.jpg.h);

            boolean isAutoplay = site.reCheckAuto.matcher(elem.attr("data-param")).find();

            if (isAutoplay) {
                video.attr("autoplay", "");
                //video.attr("preload", "auto");
                video.attr("muted", "");
                video.attr("playsinline", "");
            } else {
                video.attr("controls", "");
            }

            param.og.videoSrc = src;
            param.og.videoType = srcType;

            elem.replaceWith(video.elem);
        }
    }

    void elemImgQuiz(Element elem, NodeAttach fileData) {

        Element elemParent = site.getParag(elem);
        if (elemParent == null) return;

        elemParent.before(new Quiz(node, fileData.group).elem);

        param.js.add("ctrlQuiz");
        param.css.add("ctrlQuiz");

        elemParent.remove();
    }

    void elemImgImage(Element elem) {

        //VUtil.println(node.head.idn);

        Element elemParent = site.getParag(elem);
        if (elemParent == null) {
            site.elemCanvasFromImg(elem);
            return;
        }


        String attrSrc = elem.attr("src");
        String attrClass = elem.attr("class");
        int srcSize = VUtil.imageSize(attrSrc);

        NodeAttach fileData = node.attachByKey(VUtil.imageKey(attrSrc));

        boolean flagFigure = fileData != null && VUtil.notEmpty(fileData.content);

        if (flagFigure) {

            TagBase figure = jTag("figure").addClass(attrClass);
            if (srcSize > 0) {
                boolean flag = srcSize == 150 || srcSize == 250;
                if (flag) {
                    String cl = "w" + srcSize;
                    elem.addClass(cl);
                    figure.addClass(cl + "a");
                }
            }

            figure.append(
                    elemImage(fileData, elem),
                    jTag("figcaption").append(VUtil.nullToString(fileData.content)).elem
            );

            //VUtil.println("");
            //VUtil.println(elem.outerHtml());
            //VUtil.println(elemParent.outerHtml());

            // && elemParent.childNodes().size() == 1
            // <p class="center"><img src="/file/0034/8784.jpg" width="1600" height="1067"><br></p>

            if (elemParent.getElementsByTag("img").size() == 1 && VUtil.isEmpty(elemParent.text())) {
                elemParent.replaceWith(figure.elem);
            } else {
                if (elem.siblingIndex() < elemParent.childNodes().size() * 0.7) {
                    elemParent.before(figure.elem);
                } else {
                    elemParent.after(figure.elem);
                }
                elem.remove();
            }

        } else {
            if (fileData != null) {
                elem.replaceWith(elemImage(fileData, elem).addClass(attrClass));
            } else {
                site.elemCanvasFromImg(elem);
            }
        }
    }

    // ============

    ViewSite.MenuLine menuNewLine(int idn) {
        return new ViewSite.MenuLine(site.getUrlByIdn(idn), site.tree.byIdn(idn).text);
    }

    void menuView(ArrayList<ViewSite.MenuLine> menu, TagBase parent, boolean flagStarts, boolean flagNotLink) {

        for (ViewSite.MenuLine line : menu) {
            //itemscope="itemscope" itemtype="http:https://schema.org/SiteNavigationElement"
            TagBase li = jTag("li").appendTo(parent);
            if (flagStarts) {
                if (param.path.startsWith(line.path)) li.addClass("select");
            } else {
                if (param.path.equals(line.path)) li.addClass("select");
            }

            TagBase elLink = null;
            if (line.text.contains("</")) {
                if (flagNotLink) li.append(line.text);
                else elLink = jTag("a").attr("href", line.path).append(line.text);
            } else {
                if (flagNotLink) li.text(line.text);
                elLink = jTag("a").attr("href", line.path).text(line.text);
            }

            if (elLink != null) {
                li.append(elLink);
            }

            if (line.sub != null) {
                menuView(line.sub, jTag("ul").addClass("subsection").appendTo(li), flagStarts, false);
            }
        }

    }

    TagBase menuMain() {
        TagBase tag = jTag("ul");

        ArrayList<ViewSite.MenuLine> menu;
        if (site.menuMain == null) {
            menu = new ArrayList<>();
            int idNext = site.tree.byIdn(0).first;
            while (idNext > 0) {
                NodeHead head = site.getNodeHead(idNext);
                if (head != null && !head.flagFolder && !head.flagBlock && head.flagValid) {
                    menu.add(menuNewLine(idNext));
                }
                idNext = site.tree.byIdn(idNext).next;
            }
            site.menuMain = VUtil.jsonString(menu);
        } else {
            //Type listType = new TypeToken<ArrayList<ViewSite.MenuLine>>() {
            //}.getType();
            menu = VUtil.jsonData(site.menuMain, TypeToken.getParameterized(ArrayList.class, ViewSite.MenuLine.class).getType());
        }

        site.ini.menuMain(menu);
        menuView(menu, tag, false, false);

        return tag;
    }

    TagBase menuSection(Element elem) {
        TagBase tag = jTag("ul");

        String elemText = elem.text().trim();
        boolean flagSub = elemText.contains("withsub");
        boolean flagOnly = elemText.contains("onlysub");
        elem.html("");
        if (flagOnly) flagSub = true;

        ArrayList<ViewSite.MenuLine> menu;
        if (site.menuSection == null) {
            menu = new ArrayList<>();
            NodeHead head;

            int idNext1 = site.tree.byIdn(site.ini.blogStreamIdn).first;
            while (idNext1 > 0) {
                head = site.getNodeHead(idNext1);
                if (head != null && head.flagFolder && !head.flagBlock && head.flagValid && head.idn != site.ini.idnLabel) {
                    ViewSite.MenuLine mline = menuNewLine(idNext1);
                    menu.add(mline);
                    if (flagSub) {
                        int idNext2 = site.tree.byIdn(idNext1).first;
                        while (idNext2 > 0) {
                            head = site.getNodeHead(idNext2);
                            if (head != null && (flagOnly || head.flagFolder) && !head.flagBlock && head.flagValid) {
                                mline.add(menuNewLine(idNext2));
                            }
                            idNext2 = site.tree.byIdn(idNext2).next;
                        }
                    }
                }
                idNext1 = site.tree.byIdn(idNext1).next;
            }

            site.menuSection = VUtil.jsonString(menu);
        } else {
            //Type listType = new TypeToken<ArrayList<ViewSite.MenuLine>>() {
            //}.getType();
            menu = VUtil.jsonData(site.menuSection, TypeToken.getParameterized(ArrayList.class, ViewSite.MenuLine.class).getType());
        }

        site.ini.menuSection(menu);
        menuView(menu, tag, true, flagOnly);

        return tag;
    }

    void menuDefault(Element elem) {
        for (Element el : elem.getElementsByTag("a")) {
            String href = el.attr("href");
            if (href.isEmpty()) continue;
            if (param.path.startsWith(href) && href.equals("/") || param.path.equals(href)) {
                Element pnt = el.parent();
                while (!pnt.tagName().equals("li")) pnt = pnt.parent();
                pnt.addClass("select");
                if (pnt.parent().parent().parent().tagName().equals("div")) {
                    pnt = el.parent().parent().parent().parent();
                    if (pnt.tagName().equals("li")) {
                        pnt.addClass("select");
                    }
                }
            }
        }
    }

    TagBase menuContents() {
        TagBase ul = jTag("ul");
        int idp = site.tree.byIdn(node.head.idn).first > 0 ? node.head.idn : site.tree.byIdn(node.head.idn).idp;
        int idNext = site.tree.byIdn(idp).first;
        while (idNext > 0) {
            NodeTreeElem nt = site.tree.byIdn(idNext);
            NodeHead head = site.getNodeHead(idNext);
            if (head != null && !head.flagFolder && !head.flagBlock && head.flagValid) {
                String href = site.getUrlByIdn(idNext);
                jTag("li").appendTo(ul).addClass(param.path.startsWith(href) ? "select" : null).append(
                        jTag("a").attr("href", idNext + "").text(nt.text)
                );
            }
            idNext = nt.next;
        }

        return ul;
    }

    /*
    TagBase menuSub() {
        TagBase tag = null;
        return tag;
    }
    */

    // ============

    void pageFinish() {

        for (Element a : doc.getElementsByTag("a")) site.elemLink(a, node == null ? 0 : node.head.idn);

        pageBlockPublisherFinish();


        if (docSidebarContent != null) {
            Elements els = doc.getElementsByClass("sidebar-layout template");
            if (els.size() == 1) {
                Element docSidebarLayout = els.first();
                TagBase template = jTag("script").attr("type", "html/template").id("sidebar").append(
                        jTag("div").addClass("sidebar-layout template").append(
                                jTag("aside").addClass("sidebar-content").itemscope("").itemtype("https://schema.org/WPSideBar").append(
                                        site.srcSidebar.childNodesCopy()
                                )
                        )
                );
                docSidebarLayout.after(template.elem);
                docSidebarLayout.remove();
            } else {
                docSidebarContent.attr("itemscope", "").attr("itemtype", "https://schema.org/WPSideBar");
                if (site.srcSidebar != null) {
                    for (Node n : site.srcSidebar.childNodesCopy()) {
                        docSidebarContent.appendChild(n);
                    }
                }
                site.ini.sidebar(docSidebarContent);
            }
        }

        if (site.ini.flagS) {
            site.loaSData();
        }

        site.ini.onFinish(this);

        htmlHead();
        pageScript();

        for (Element link : doc.getElementsByTag("a")) {
            String dataLink = link.attr("data-link");
            if (dataLink.isEmpty()) continue;
            if (dataLink.startsWith("oz")) {
                VUtil.tagDrop(link);
            }
            if (dataLink.startsWith("la")) {
                VUtil.tagDrop(link);
            }
        }
    }

    void rssYandexZen(List<Integer> idns) {

        if (idns.size() == 0) return;

        String rssPath = site.getUrlByIdn(node.head.idn) + "feed/yandex-zen.xml";

        Document doc = Jsoup.parse("<?xml version=\"1.0\" encoding=\"UTF-8\"?>", "", Parser.xmlParser());
        TagBase rssElem = jTag("rss")
                .attr("version", "2.0")
                .attr("xmlns:content", "http://purl.org/rss/1.0/modules/content/")
                .attr("xmlns:dc", "http://search.yahoo.com/mrss/")
                .attr("xmlns:media", "http://search.yahoo.com/mrss/")
                .attr("xmlns:georss", "http://www.georss.org/georss")
                .attr("xmlns:atom", "http://www.w3.org/2005/Atom")
                .appendTo(doc);
        TagBase channelElem = jTag("channel").appendTo(rssElem);

        jTag("title").text(VUtil.textOnly(node.head.title)).appendTo(channelElem);
        jTag("description").text(VUtil.textOnly(node.descr)).appendTo(channelElem);
        jTag("language").text("ru-RU").appendTo(channelElem);
        jTag("link").text(site.protocol + site.host + "/").appendTo(channelElem);
        jTag("atom:link")
                .attr("href", site.protocol + site.host + rssPath)
                .attr("rel", "self")
                .attr("type", "application/rss+xml").appendTo(channelElem);

        ArrayList<String> authorList = new ArrayList<>();


        for (int idn : idns) {
            NodeData data = VUtil.readNode(site.host, idn);
            if (data == null) continue;
            String link = site.protocol + site.host + site.getUrlByIdn(idn);
            String author = authorList.get(idn % authorList.size());

            TagBase itemElem = jTag("item").append(
                    jTag("title").text(VUtil.textOnly(data.head.title)),
                    jTag("link").text(link),
                    //jTag("guid").text(link),

                    jTag("pdalink").text(link),
                    jTag("media:rating").attr("scheme", "urn:simple").text("nonadult"),
                    // <media:rating scheme="urn:simple">nonadult</media:rating>
                    jTag("author").text(author),

                    jTag("category").text("Дом"),
                    jTag("category").text("Хобби"),

                    jTag("description").text(VUtil.textOnly(data.descr)),

                    jTag("pubDate").text(VUtil.toUTCString(VUtil.dateFromJSON(data.head.date.get(0))))
            );

            ArrayList<String> paragraphs = new ArrayList<>();
            for (Element parag : data.getContent().getElementsByTag("p")) {
                for (Element img : parag.getElementsByTag("img")) {
                    String src = img.attr("src");
                    String url = site.protocol + site.host + VUtil.imageSize600(src);
                    if (url.endsWith(".jpg")) {
                        jTag("enclosure").appendTo(itemElem)
                                .attr("url", url)
                                .attr("type", "image/jpeg");
                    }
                }
                String text = VUtil.textOnly(parag.html());
                if (text.length() > 2) {
                    paragraphs.add("<p>" + VUtil.textOnly(parag.html()) + "</p>");
                }
            }


            if (paragraphs.size() > 0) {
                itemElem.appendTo(channelElem);

                itemElem.appendTo(channelElem).append(
                        jTag("content:encoded")//.text(
                                //        String.join("", paragraphs)
                                //)
                                .append(
                                        new CDataNode(String.join("", paragraphs))
                                )
                );

            }
        }

        doc.outputSettings().indentAmount(0).prettyPrint(false).escapeMode(Entities.EscapeMode.xhtml);
        VUtil.writeCacheFile(site.host, rssPath, doc.html());

    }

    void pageRSS(List<Integer> idns) {
        if (idns.size() == 0) return;

        String rssPath = site.getUrlByIdn(node.head.idn) + "index.rss";
        //String rssPathWeb = site.protocol + site.host + rssPath;
        String rssPathWeb = "//" + site.host + rssPath;

        Document doc = Jsoup.parse("<?xml version=\"1.0\" encoding=\"UTF-8\"?>", "", Parser.xmlParser());

        TagBase channel = jTag("channel");
        jTag("rss").appendTo(doc)
                .attr("version", "2.0")
                .attr("xmlns:atom", "http://www.w3.org/2005/Atom")
                .append(
                        channel.append(
                                jTag("atom:link")
                                        .attr("href", rssPathWeb)
                                        .attr("rel", "self")
                                        .attr("type", "application/rss+xml"),
                                jTag("title").text(VUtil.textOnly(node.head.title)),
                                jTag("link").text(site.protocol + site.host + "/"),
                                jTag("description").text(VUtil.textOnly(node.descr)),
                                jTag("language").text("ru-RU"),
                                jTag("lastBuildDate").text(VUtil.toUTCString(VUtil.nowGMT())),
                                //jTag("docs").text(rssPathWeb),
                                jTag("generator").text(site.protocol + site.host + "/")
                        )
                );

        for (int idn : idns) {
            NodeData data = VUtil.readNode(site.host, idn);
            if (data == null) continue;
            String link = site.protocol + site.host + site.getUrlByIdn(idn);
            jTag("item").append(
                    //<category domain="http://chexed.com/Poems">Poems</category>
                    jTag("title").text(VUtil.textOnly(data.head.title)),
                    jTag("link").text(link),
                    jTag("guid").text(link),
                    //jTag("description").text(VUtil.textOnly(data.descr)),
                    jTag("description").append(new CDataNode(data.descr.replaceAll("src=\"/", "src=\"" + site.protocol + site.host + "/"))),
                    jTag("pubDate").text(VUtil.toUTCString(VUtil.dateFromJSON(data.head.date.get(0))))
            ).appendTo(channel);
        }

        param.flagRSS = true;
        doc.outputSettings().indentAmount(0).prettyPrint(false).escapeMode(Entities.EscapeMode.xhtml);
        VUtil.writeCacheFile(site.host, rssPath, doc.html());

    }

    String pageCss2019() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : site.cssMapCommon.entrySet()) {
            if (doc.selectFirst(entry.getKey()) == null) continue;
            sb.append(entry.getValue());
        }

        for (Map.Entry<String, String> entry : site.cssMapHost.entrySet()) {
            if (doc.selectFirst(entry.getKey()) == null) continue;
            sb.append(entry.getValue());
        }


        sb.append(site.srcStyle);

        Matcher m = site.reCSS4Var.matcher(sb.toString());
        StringBuffer strBuf = new StringBuffer();
        while (m.find()) {
            String value = site.css4Vars.get(m.group(1));
            if (value == null) value = m.group(0);
            m.appendReplacement(strBuf, Matcher.quoteReplacement(value));
        }
        m.appendTail(strBuf);

        //String cssText = testCss(strBuf.toString());

        return strBuf.toString();
    }


    String pageCss() {
        String cssKey = String.join("|", param.css);

        String cssHash = site.cssKeyList.get(cssKey);
        if (cssHash == null) {
            Pattern reRootFile = Pattern.compile("^(common|font|dialog|auth)$");
            ArrayList<String> dateList = new ArrayList<>();
            ArrayList<String> cssList = new ArrayList<>();

            ArrayList<String> css = new ArrayList<>();
            css.add("common");
            css.add("font");
            css.add("dialog");
            css.add("auth");
            if (param.js.contains("shop")) css.add("shop");

            css.addAll(param.css);

            for (String fileKey : css) {
                String fileName = reRootFile.matcher(fileKey).find() || fileKey.contains("/") ? fileKey : ("component/" + fileKey);
                String path = VUtil.DIRPublic + "style/" + fileName + ".css";
                if (VUtil.pathExists(path)) {
                    dateList.add(fileKey + "::" + VUtil.lastModified(path));

                    String data = site.cssDataList.get(fileName);
                    if (data == null) {
                        data = VUtil.readFile(path);
                        site.cssDataList.put(fileName, data);
                    }
                    if (data != null) {
                        cssList.add("/* " + fileKey + " */");
                        cssList.add(data);
                    }
                } else {
                    VUtil.error("ViewPage::pageCss not exists", fileName);
                }
            }
            dateList.add(site.versionCss);

            cssList.add("/* style */");
            cssList.add(site.srcStyle);

            cssHash = VUtil.PATH_PREF + VUtil.getHash256(String.join("|", dateList));
            site.cssKeyList.put(cssKey, cssHash);

            VUtil.writeFile(VUtil.DIRFile + site.host + "/css/" + cssHash + ".css", String.join("\n", cssList));
        }

        return cssHash;
    }

    void pageToHtml() {


        if (site.ini.flag2019) {
            // <canvas data-src="/file/0001/150/0294.jpg" width="150" height="107"></canvas>
            String img64;
            for (Element elem : doc.getElementsByTag("canvas")) {
                // srcset="data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw=="
                //img64 = "data:image/svg+xml;charset=UTF-8,%3Csvg%20role%3D%22img%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%22" + elem.attr("width") + "%22%20height%3D%22" + elem.attr("height") + "%22%3E%3C%2Fsvg%3E";
                //elem.tagName("img").attr("src", "data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw==").attr("alt", "");

                //.attr("src", img64)
                elem.tagName("img").attr("alt", "");
            }
        }


        String html = "<!DOCTYPE html>" + VUtil.htmlSetting(doc).html();
        VUtil.writeCacheFile(site.host, param.path, VUtil.htmlClean(html));

    }

    // ===============

    TagBase jTag(String name) {
        return new TagBase(name);
    }

    TagMeta meta() {
        return new TagMeta();
    }

    TagBase div() {
        return new TagBase("div");
    }

    Element elemAudio(String src) {
        return new Element("audio")
                .attr("controls", "true")
                .text("Тег audio не поддерживается вашим браузером.")
                .appendChild(
                        new Element("source")
                                .attr("src", "/file/" + src)
                                .attr("type", "audio/mpeg")
                );
    }


    Element elemImage(NodeAttach attach, Element elem) {

        String attrSrc = elem.attr("src");
        int srcSize = VUtil.imageSize(attrSrc);
        int[] sizes = new int[]{srcSize};
        if (srcSize == 0) {
            sizes = new int[]{srcSize, Integer.parseInt(elem.attr("width")), Integer.parseInt(elem.attr("height"))};
        }

        return elemImage(attach, sizes, false);
    }

    Element elemImage(NodeAttach attach, int size) {
        return elemImage(attach, new int[]{size}, false);
    }

    Element elemImage(NodeAttach attach, int size, boolean flagNotOpen) {
        return elemImage(attach, new int[]{size}, flagNotOpen);
    }

    Element elemImage(NodeAttach attach, int[] sizes, boolean flagNotOpen) {
        Element elem = null;
        if (attach.src != null) {

            int size = sizes[0];

            if (attach.src.endsWith(".mp3")) {
                elem = elemAudio(attach.src);
            } else if (attach.w > 0 && attach.h > 0) {

                String src = VUtil.imageSize(attach.src, size);

                int width = 0, height = 0;
                if (size > 0) {
                    double k = Math.max((double) attach.w / size, (double) attach.h / size);
                    if (k > 1) {
                        width = (int) Math.round(attach.w / k);
                        height = (int) Math.round(attach.h / k);
                    } else {
                        width = attach.w;
                        height = attach.h;
                    }
                } else if (sizes.length == 3) {
                    width = sizes[1];
                    height = sizes[2];
                } else {
                    VUtil.error("ViewPage::elemImage - image size=0", site.host, (VUtil.IFunc) () -> node.head.idn);

                }

                Element canvas = new Element("canvas")
                        .attr("data-src", src)
                        .attr("width", width + "")
                        .attr("height", height + "");

                if (!flagNotOpen && attach.w < width * 1.7) {
                    flagNotOpen = true;
                }


                if (flagNotOpen) {
                    elem = canvas.attr("data-thumb", "");
                } else {
                    elem = new Element("a")
                            .addClass("thumbnail")
                            .attr("aria-label", "Image")
                            .attr("href", size > 0 ? VUtil.imageSize(attach.src, 600) : src)
                            .appendChild(canvas)
                    ;
                }

            } else {

                if (!VUtil.isSVG(attach.src)) {
                    VUtil.error("ViewPage::elemImage - width or height == 0", site.host, (VUtil.IFunc) () -> node.head.idn, attach.src, attach.idf + "", attach.w + "", attach.h + "");
                }
                elem = new Element("img").attr("src", "/file/" + attach.src).addClass("size" + size);
            }
        } else {
            VUtil.error("ViewPage::elemImage::null");
        }
        return elem;
    }

    Element elemCatalogImage(NodeAttach attach, int size, boolean flagNotOpen) {

        if (attach.src.endsWith(".mp3")) {
            return elemAudio(attach.src);
        }

        int width, height;
        double k = Math.max((double) attach.w / size, (double) attach.h / size);
        if (k > 1) {
            width = (int) Math.round(attach.w / k);
            height = (int) Math.round(attach.h / k);
        } else {
            width = attach.w;
            height = attach.h;
        }

        String src600 = VUtil.imageSize(attach.src, 600);
        String src = VUtil.imageSize(attach.src, size);

        Element image = new Element("img")
                .attr("src", src600)
                .attr("data-src", src)
                .attr("width", width + "")
                .attr("height", height + "");

        if (flagNotOpen) {
            image.attr("data-thumb", "");
        } else {
            image.attr("data-openable", "");
        }

        return image;
    }

    public TagBase elemCatalog(ArrayList<NodeAttach> list, int size, boolean flagLike) {
        param.css.add("catalogFigure");
        TagBase section = div().addClass("catalog");

        TagBase noscript = new TagBase("noscript").appendTo(section);

        for (NodeAttach elem : list) {
            TagBase figure = jTag("figure").appendTo(noscript);
            TagBase figcaption = null;

            if (flagLike) {
                figure.attr("data-idf", elem.idf + "");
                if (elem.like > 0) {
                    figure.attr("data-like", elem.like + "");
                }
            }

            if (!VUtil.textOnly(elem.content).isEmpty()) {
                figcaption = jTag("figcaption").append(elem.content);

                //if (elem.price > 0) {
                //    figcaption.attr("data-price", elem.price + "");
                //}
            }

            //figure.append(elemImage(elem, size, flagLike));
            figure.append(elemCatalogImage(elem, size, flagLike));
            if (elem.idu != node.head.idu) {
                div().addClass("published").appendTo(figure).append(
                        jTag("span").addClass("author").data("text", site.getUser(elem.idu, elem.anonym))
                );
            }

            if (figcaption != null) figcaption.appendTo(figure);
        }

        return section;
    }

    // ===============

    class OG {
        String imgSrc;
        String videoSrc;
        String videoType;
    }

    public class PageParam {
        boolean noindex = true;
        String path = "";
        String dateLast;
        HashMap<String, String> jsData = new HashMap<>();
        List<String> keywords = new ArrayList<>();
        String description;
        String title;
        TagBase content;
        boolean flagRSS = false;
        int pageNumber = 0;
        int pageNumberNext = 0;
        int pageNumberPrev = 0;
        OG og = new OG();
        TagBase dateJTag;

        SiteProperty.PageType type;

        public Set<String> css = new HashSet<>();
        public Set<String> js = new HashSet<>();
        public String jsPage = null;
    }

    public class JsonComment {
        public int idf;
        public int like;
        public String src;
        public String content;
        public String user;
        public String date;

        JsonComment(int idf, int like, String src, String content, String user, String date) {
            this.idf = idf;
            this.like = like;
            this.src = src == null ? "" : src;
            this.content = content;
            this.user = user;
            this.date = date;
        }
    }

    class LinkData {
        String _path;
        String _text;

        public LinkData path(String path) {
            _path = path;
            return this;
        }

        public String path() {
            return _path;
        }

        public LinkData text(String text) {
            _text = text;
            return this;
        }

        public String text() {
            return _text;
        }
    }

}
