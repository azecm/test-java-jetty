package org.site.server.site.front;

import com.google.gson.reflect.TypeToken;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.site.elements.NodeAttach;
import org.site.elements.NodeData;
import org.site.server.Router;
import org.site.server.jsession.JSessionSite;
import org.site.view.RAction;
import org.site.view.Tracker;
import org.site.view.VUtil;
import redis.clients.jedis.Jedis;

import javax.servlet.GenericServlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FrontActivity extends GenericServlet {
    JSessionSite session;
    final static String hostTo = "www.to.com";

    public void service(ServletRequest req, ServletResponse res) {
        Object result = null;
        session = new JSessionSite(req);
        if (session.isPost()) {
            ActivityPost data = session.postJson(ActivityPost.class);
            if (data != null) {
                if (data.testing != null) data.route = "testing";
                switch (data.route) {
                    case "testing": {
                        if (Router.testUser(session, 10)) {
                            result = postTesting(data);
                        }
                        break;
                    }
                    case "quiz-results": {
                        result = getQuizResults(data);
                        break;
                    }
                    case "game-results": {
                        result = getGameResults(data);
                        break;
                    }
                    case "game-records": {
                        result = getGameRecords(data);
                        break;
                    }
                    case "like-text": {
                        if (data.like != null && data.like.idn > 0 && data.like.ind >= 0) {
                            if (Router.testUserWithParam(session, 1, "idn:" + data.like.idn + "|" + "ind:" + data.like.ind)) {
                                result = postLikeText(data);
                            }
                        }
                        break;
                    }
                    case "like-image": {
                        if (data.like != null && data.like.idn > 0 && data.like.idf > 0) {
                            if (Router.testUserWithParam(session, 1, "idn:" + data.like.idn + "|" + "idf:" + data.like.idf)) {
                                result = postLikeImage(data);
                            }
                        }
                        break;
                    }
                    case "user-key": {
                        result = getUserKey();
                        break;
                    }
                    case "game-result": {
                        if (Router.testUser(session, 10)) {
                            if (data.game != null && data.idn > 0) {
                                result = postGame(data);
                            }
                        }
                        break;
                    }
                    case "quiz-result": {
                        if (Router.testUser(session, 10)) {
                            if (data.quiz != null && data.quiz.idn > 0) {
                                result = postQuiz(data);
                            }
                        }
                        break;
                    }
                }
            }
        }
        Router.json(result, res);
    }

    long testUni(int idn, int ind) {
        String voteKey = session.getIP() + "::" + session.getBrowser() + "::" + idn + "::" + ind;
        Jedis jedis = RAction.redis();
        long res = jedis.sadd(RAction.keyCommentDayVoting(session.host), voteKey);
        jedis.disconnect();
        return res;
    }

    String postLikeImage(ActivityPost data) {
        if (data.like.idn < 1 || data.like.idf < 1) return "";
        NodeData node = VUtil.readNode(session.host, data.like.idn);
        if (node == null) return "";

        NodeAttach attach = node.attachByKey(data.like.idf);
        if (attach == null) return "";

        if (testUni(data.like.idn, data.like.idf) == 0) return "";

        attach.like++;
        VUtil.writeNode(session.host, node);
        Tracker.addMark(session.host, node.head.idn);

        return "ok";
    }

    String postLikeText(ActivityPost data) {
        if (data.like.idn == 0 || data.like.ind < 0) return "";
        NodeData node = VUtil.readNode(session.host, data.like.idn);
        if (node == null) return "";

        Document doc = node.getContent();
        Element elem = doc.getElementsByClass("like").get(data.like.ind);
        if (elem == null) return "";

        if (testUni(data.like.idn, data.like.ind) == 0) return "";

        String likeStr = elem.attr("data-like");
        elem.attr("data-like", likeStr == null ? "1" : (VUtil.getInt(likeStr) + 1) + "");
        node.setContent(doc);
        VUtil.writeNode(session.host, node);
        Tracker.addMark(session.host, node.head.idn);

        return "ok";
    }

    String getUserKey() {
        return VUtil.getHash256(session.host + "::" + session.getBrowser() + "::" + session.getIP());
    }

    NodeAttach addComment(NodeData node, String message, GameResult result) {
        int idu = session.enabled() ? session.user.idu : 0;
        NodeAttach attach = node.attachNew(idu);
        attach.flagComment = true;
        attach.gameResult = result;
        attach.content = message.startsWith("<p>") ? message : ("<p>" + message + "</p>");
        return attach;

    }

    @SuppressWarnings("unchecked")
    String postGame(ActivityPost data) {
        NodeData node = VUtil.readNode(session.host, data.idn);
        if (node == null) return "";

        if (VUtil.notEmpty(data.game.message)) {
            addComment(node, data.game.message, new GameResult(data.game.type, data.game.data));
            VUtil.writeNode(session.host, node);
            Tracker.addMark(session.host, node.head.idn);
        }

        if (data.game.flag) {
            HashMap<String, HashMap<String, Object>> gameData = getGameRecords(data);
            if (gameData == null) {
                gameData = new HashMap<>();
            }

            HashMap<String, Object> gameDataType = gameData.get(data.game.type);
            if (gameDataType == null) {
                gameData.put(data.game.type, new HashMap<>());
                gameDataType = gameData.get(data.game.type);
            }

            if (session.enabled()) {
                data.game.name = session.user.name;
            } else if (!data.game.name.startsWith("гость (")) {
                data.game.name = "гость";
            }

            gameDataType.put(data.game.name, data.game.data);
            VUtil.writeJson(nodeDataPath(data.idn), gameData);

        }

        String path = pathToResults(data);

        if (VUtil.isEmpty(path)) {
            VUtil.println("postGame::userError", VUtil.jsonString(data), path);
            return "ok";
        }
        //else{
        //    VUtil.println("postGame::userOKK", VUtil.jsonString(data), path);
        //}

        UserDataFull userData = VUtil.readJson(path, UserDataFull.class);
        if (userData == null) userData = new UserDataFull();
        if (userData.game == null) userData.game = new HashMap<>();

        UserDataItem dataItem = userData.game.get(data.idn + "");
        if (dataItem == null) {
            dataItem = new UserDataItem();
            dataItem.played = 1;
        } else {
            dataItem.played++;
        }
        userData.game.put(data.idn + "", dataItem);

        //VUtil.println("postGame::", path, userData);
        VUtil.writeJson(path, userData);

        return "ok";
    }

    String postQuiz(ActivityPost data) {

        NodeData node = VUtil.readNode(session.host, data.quiz.idn);
        if (node == null) return "";

        NodeAttach attResult = null;
        for (NodeAttach attach : node.attach) {
            if (attach.quiz != null && attach.quiz.equals("result")) {
                attResult = attach;
                break;
            }
        }

        if (attResult == null || attResult.content == null) return "";

        addComment(node, data.quiz.message, new GameResult("quiz"));

        Pattern reResult = Pattern.compile(data.quiz.min + "\\-" + data.quiz.max + "\\s?\\((\\d+)\\)");
        Matcher m = reResult.matcher(attResult.content);
        if (!m.find()) return "";
        int prev = Integer.parseInt(m.group(1));
        attResult.content = reResult.matcher(attResult.content).replaceFirst(data.quiz.min + "-" + data.quiz.max + " (" + (++prev) + ")");


        VUtil.writeNode(session.host, node);
        Tracker.addMark(session.host, node.head.idn);


        String path = pathToResults(data);
        UserDataFull userData = VUtil.readJson(path, UserDataFull.class);
        if (userData == null) userData = new UserDataFull();
        if (userData.quiz == null) userData.quiz = new HashMap<>();

        UserDataItem dataItem = userData.quiz.get(data.quiz.idn + "");
        if (dataItem == null) {
            dataItem = new UserDataItem();
            dataItem.min = data.quiz.error;
            dataItem.played = 1;
        } else {
            dataItem.min = Math.min(dataItem.min, data.quiz.error);
            dataItem.played++;
        }
        userData.quiz.put(data.quiz.idn + "", dataItem);
        VUtil.writeJson(path, userData);


        return "ok";
    }

    String nodeDataPath(int idn) {
        return VUtil.DIRDomain + session.host + "/node-data/" + idn;
    }


    //HashMap<String, HashMap<String, Object>> getGameRecords(ActivityPost data) {
    HashMap<String, HashMap<String, Object>> getGameRecords(ActivityPost data) {
        return VUtil.readJson(nodeDataPath(data.idn), new TypeToken<HashMap<String, HashMap<String, Object>>>() {
        }.getType());
        //return VUtil.readJson(nodeDataPath(data.idn), new TypeToken<HashMap<String, Object>>(){}.getType());
    }

    UserDataGame getGameResults(ActivityPost data) {
        String path = pathToResults(data);
        UserDataGame dataRes = VUtil.readJson(path, UserDataGame.class);
        return dataRes == null ? new UserDataGame() : dataRes;
    }

    UserDataQuiz getQuizResults(ActivityPost data) {
        String path = pathToResults(data);
        UserDataQuiz dataRes = VUtil.readJson(path, UserDataQuiz.class);
        return dataRes == null ? new UserDataQuiz() : dataRes;
    }

    String pathToResults(ActivityPost data) {
        String path = "";
        if (session.data != null) {

            path = VUtil.DIRDomain + hostTo + "/user-data/" + session.data.idu + ".json";
        } else if (data.key != null) {

            path = VUtil.DIRMemory + hostTo + "/user-data/" + data.key + ".json";
        }
        return path;
    }

    String postTesting(ActivityPost data) {
        int min = data.testing.get(0);
        int max = data.testing.get(1);
        int idn = data.testing.get(2);

        if (idn > 0 && max > 0) {
            NodeData node = VUtil.readNode(session.host, idn);
            if (node != null) {
                Pattern re = Pattern.compile(">" + min + "\\-" + max + "\\s*\\((\\d+)\\)");
                Matcher m = re.matcher(node.content);
                if (m.find()) {
                    int next = VUtil.getInt(m.group(1)) + 1;
                    node.content = re.matcher(node.content).replaceAll(">" + min + "-" + max + " (" + next + ")");
                    VUtil.writeNode(session.host, node);
                    Tracker.addMark(session.host, idn);
                }
            }
        }

        return "ok";
    }

    // ==========

    public static class GameResult {
        String type;
        Object data;

        GameResult(String type) {
            this.type = type;
        }

        GameResult(String type, Object data) {
            this.type = type;
            this.data = data;
        }
    }

    // ==========

    public static class PostQuiz {
        int idn;
        int min;
        int max;
        int error;
        String message;
    }

    public static class PostGame {
        Object data;
        boolean flag;
        String name;
        String type;
        String message;
    }

    public static class PostLike {
        int idn;
        int ind;
        int idf;
    }

    public static class ActivityPost {
        int idn;
        String key;
        String route;
        ArrayList<Integer> testing;
        PostQuiz quiz;
        PostGame game;
        PostLike like;
    }

    // ==========

    public static class UserDataItem {
        Integer played;
        Integer min;
    }

    public static class UserDataQuiz {
        HashMap<String, UserDataItem> quiz;

        UserDataQuiz() {
            quiz = new HashMap<>();
        }
    }

    public static class UserDataGame {
        HashMap<String, UserDataItem> game;

        UserDataGame() {
            game = new HashMap<>();
        }
    }

    public static class UserDataFull {
        HashMap<String, UserDataItem> game;
        HashMap<String, UserDataItem> quiz;
    }

}
