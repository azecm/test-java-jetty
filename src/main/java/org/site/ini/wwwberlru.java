package org.site.ini;

import org.jsoup.nodes.Element;
import org.site.view.ViewSite;

import java.util.ArrayList;

public class wwwberlru extends SiteProperty {

    public wwwberlru() {

        idnLabel = 1466;
        blogStreamIdn = 1424;
        blogPerPage = 20;
        maxReadAlso = 7;
        flagS = true;
        flag2019 = true;

        contentBody = contentCatalog = contentComment = true;

    }

    public void menuMain(ArrayList<ViewSite.MenuLine> menu) {
        menu.add(new ViewSite.MenuLine("/user/", "Пользователи"));
        menu.add(new ViewSite.MenuLine("/note/", "Заметки"));
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
