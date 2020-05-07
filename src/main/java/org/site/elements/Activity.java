package org.site.elements;


import org.site.view.VUtil;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Activity {
    class Game {
        public String key;
        public String title;

        public Game(String key, String title) {
            this.key = key;
            this.title = title;
        }
    }

    public static String host = "www.to.com";
    public ArrayList<Game> gameList = new ArrayList<>();

    Pattern reFunny = Pattern.compile("<p class=\"command\">\\s*funny\\(([\\w\\-]+)");
    Pattern reGame = Pattern.compile("<p class=\"shop\">\\s*game-([\\w\\-]+)");

    //========
    public class Param1 {
        public String link;
        public String title;

        Param1 link(String link) {
            this.link = link;
            return this;
        }

        Param1 title(String title) {
            this.title = title;
            return this;
        }
    }

    public class Param2 {
        public String title;
        public ArrayList<Integer> list = new ArrayList<>();

        Param2 title(String title) {
            this.title = title;
            return this;
        }

        Param2 add(int val) {
            list.add(val);
            return this;
        }
    }

    public class Games {
        HashMap<Integer, Param1> dict = new HashMap<>();
        List<Param2> items = new ArrayList<>();
    }

    public class Quiz {
        public ArrayList<ResultItem> items;
        public HashMap<Integer, Param1> dict;

        public Quiz(ArrayList<ResultItem> items, HashMap<Integer, Param1> dict) {
            this.dict = dict;
            this.items = items;
        }
    }

    class Theme {
        int idn;
        int order;
        String title;

        Theme(int idn, String title, int order) {
            this.idn = idn;
            this.title = title;
            this.order = order;
        }
    }

    public class ResultItem {
        public int key;
        public int order;
        public String title;
        public ArrayList<Integer> list = new ArrayList<>();

        ResultItem key(int key) {
            this.key = key;
            return this;
        }

        ResultItem order(int order) {
            this.order = order;
            return this;
        }

        ResultItem title(String title) {
            this.title = title;
            return this;
        }

        ResultItem add(int val) {
            list.add(val);
            return this;
        }
    }

    int ordered = 0, posZero = -1;
    ArrayList<Theme> theme = new ArrayList<>();
    Games games;
    HashMap<String, Param2> gamesList;
    NodeTree tree;

    HashMap<Integer, Param1> resultDict;
    ArrayList<ResultItem> resultItems;

    //========

    public Activity() {
        gameList.add(new Game("mah", "Ма"));

    }

    public void genList() {

        games = new Games();
        gamesList = new HashMap<>();

        for (Game game : gameList) addGame(game.key, game.title);

        addTheme(2365, "лит");


        // ===========

        class ActivityDict {
            int funny;
            int game;
            int quiz;
        }

        ActivityDict activity = new ActivityDict();

        resultDict = new HashMap<>();
        resultItems = new ArrayList<>();
        tree = new NodeTree(host);


        VUtil.readNodes(host, (node) -> {
            if (node.content != null && node.attach != null && !node.head.flagFolder && !node.head.flagBlock && node.head.flagValid) {

                Matcher m = reFunny.matcher(node.content);
                if (m.find()) {
                    activity.funny++;
                    addIdnGame(m.group(1), node.head.idn);
                }
                m = reGame.matcher(node.content);
                if (m.find()) {
                    activity.game++;
                    addIdnGame(m.group(1), node.head.idn);
                    VUtil.println(node.head.idn);
                }

                int quizCounter = 0;
                HashSet<String> set = new HashSet<>();
                for (NodeAttach attach : node.attach) {
                    if (attach.quiz != null) {
                        quizCounter++;
                        set.add(attach.idf + "::" + attach.content);
                    }
                }
                if (quizCounter > 0) {
                    if (quizCounter > 6) {
                        activity.quiz++;
                        boolean findTheme = false;
                        for (Integer k : node.head.labels) {
                            int pos = -1, i = -1;
                            for (Theme t : theme) {
                                i++;
                                if (k.equals(t.idn)) {
                                    pos = i;
                                    break;
                                }
                            }
                            if (pos > -1) {
                                findTheme = true;
                                Theme t = theme.get(pos);
                                addIdn(t.idn, t.title, t.order, node.head.idn);
                            }
                        }
                        if (!findTheme) {
                            Theme t = theme.get(posZero);
                            addIdn(t.idn, t.title, t.order, node.head.idn);
                        }

                    } else {
                        VUtil.println("Activity::genList - quizErr", node.head.idn, node.head.title, quizCounter);
                        VUtil.println(set);
                    }
                }
            }
        });

        resultItems.sort(Comparator.comparingInt(a -> a.order));

        String pathQuiz = VUtil.DIRFile + host + "/json/quiz-list.json";
        VUtil.writeJson(pathQuiz, new Quiz(resultItems, resultDict));

        games.items = new ArrayList<>(gamesList.values());
        games.items.sort((a, b) -> b.list.size() - a.list.size());

        String pathGame = VUtil.DIRFile + host + "/json/game-list.json";
        VUtil.writeJson(pathGame, games);

        VUtil.println("game:" + games.dict.keySet().size(), "funny:" + activity.funny, "game:" + activity.game, "quiz:" + activity.quiz);

    }

    void addGame(String key, String title) {
        gamesList.put(key, new Param2().title(title));
    }

    void addTheme(int idn, String title) {
        theme.add(new Theme(idn, title, ++ordered));
        if (idn == -1) posZero = theme.size() - 1;
    }

    void addIdn(int key, String title, int order, int idn) {
        boolean find = false;
        for (ResultItem line : resultItems) {
            if (line.key == key) {
                line.add(idn);
                find = true;
                break;
            }
        }
        if (!find) {
            resultItems.add(new ResultItem().key(key).title(title).order(order).add(idn));
        }
        resultDict.put(idn, new Param1().link(tree.getUrl(idn)).title(tree.byIdn(idn).text));
    }

    void addIdnGame(String key, int idn) {
        if (gamesList.keySet().contains(key)) {
            games.dict.put(idn, new Param1().link(tree.getUrl(idn)).title(tree.byIdn(idn).text));
            gamesList.get(key).add(idn);

        } else {
            VUtil.println("Activity::addIdnGame", key, idn);
        }
    }

    public static void findGame() {
        VUtil.readNodes(Activity.host, (node) -> {
            if (node.content != null && node.attach != null && !node.head.flagFolder && !node.head.flagBlock && node.head.flagValid) {
                if (node.content.contains("game-repeat")) {
                    VUtil.println(node.head.idn);
                }
            }
        });
    }


}
