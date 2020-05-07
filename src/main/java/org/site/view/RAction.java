package org.site.view;

import org.site.elements.NodeAttach;
import org.site.elements.NodeData;
import redis.clients.jedis.Jedis;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;


public class RAction {

    public static String keyCommentVoting(String host) {
        return host + ":commentVoting";
    }

    public static String keyCommentDayVoting(String host) {
        return host + ":commentDayVoting";
    }

    public static String keyArticleRating(String host) {
        return host + ":articleRating";
    }

    public static String keyTree(String host, int idn) {
        return host + ":tree:" + idn;
    }

    // =================

    public static Jedis redis() {
        Jedis jedis = new Jedis("localhost");
        //requirepass
        //jedis.auth("njeXOGGGfJeqLqOnRf3U");
        return jedis;
    }

    public static void everyDay() {
        Jedis jedis = redis();
        for (String host : ViewSite.hosts()) {
            //VUtil.println("RAction::everyDay",host);
            //VUtil.println("RAction::everyDay::keyArticleRating", jedis.del(RAction.keyArticleRating(host)));
            jedis.del(RAction.keyArticleRating(host));
            jedis.del(RAction.keyCommentVoting(host));
            jedis.del(RAction.keyCommentDayVoting(host));
        }
        jedis.disconnect();
    }

    public static void commentVoting(Crontab.Time now) {

        boolean isDayEnd = now.hour + now.minute == 0;

        VUtil.println("RAction::commentVoting", now.hour, now.minute);

        if (isDayEnd) VUtil.println("isDayEnd");

        Jedis jedis = redis();
        for (String host : ViewSite.hosts()) {

            String redisKey = keyCommentVoting(host);
            Set<String> list = jedis.smembers(redisKey);
            jedis.del(redisKey);

            if (isDayEnd) {
                Set<String> list2 = jedis.smembers(redisKey);
                if (list2.size() > 0) VUtil.println("commentVoting::list2", list2.size());
                jedis.del(keyCommentDayVoting(host));
            }

            HashMap<Integer, HashMap<Integer, Integer>> data = new HashMap<>();
            for (String line : list) {
                String[] lineData = line.split(":");
                int idn = VUtil.getInt(lineData[0]);
                int idf = VUtil.getInt(lineData[1]);
                if (idn == 0 || idf == 0) continue;
                data.computeIfAbsent(idn, k -> new HashMap<>());
                data.get(idn).merge(idf, 1, (a, b) -> a + b);
            }

            HashMap<Integer, Integer> idfsAdd;
            for (Map.Entry<Integer, HashMap<Integer, Integer>> ent : data.entrySet()) {
                NodeData node = VUtil.readNode(host, ent.getKey());
                if (node == null) continue;
                idfsAdd = ent.getValue();
                Set<Integer> idfSet = idfsAdd.keySet();
                for (NodeAttach attach : node.attach) {
                    if (!idfSet.contains(attach.idf)) continue;
                    attach.like += idfsAdd.get(attach.idf);
                }
                VUtil.writeNode(host, node);
                Tracker.addMark(host, node.head.idn);
            }
        }
        jedis.disconnect();
    }
}
