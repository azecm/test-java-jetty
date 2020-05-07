package org.site.ini;

import org.jsoup.nodes.Element;
import org.site.view.ViewSite;

import java.util.ArrayList;

public class wwwapxuru extends SiteProperty {

    public wwwapxuru() {

        idnLabel = 2516;
        blogStreamIdn = 2436;
        blogPerPage = 20;
        maxReadAlso = 7;
        flagS = true;
        flag2019 = true;

        contentBody = contentCatalog = contentComment = true;

    }

    public void menuMain(ArrayList<ViewSite.MenuLine> menu) {
        menu.add(new ViewSite.MenuLine("/user/", "Пользователи"));
        menu.add(new ViewSite.MenuLine("/memo/", "Памятка"));
    }

    public void sidebar(Element elSidebar) {
        sidebarSape(elSidebar);
    }

    public ArrayList<Block> contentHeader(PageType type, boolean notRootParent) {
        return contentHeaderDef(type, notRootParent);
    }

    public ArrayList<Block> contentFooter(PageType type, boolean notRootParent) {
        return contentFooterDef(type, notRootParent);
    }

}
