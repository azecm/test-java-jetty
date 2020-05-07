package org.site.server.site.front;

import org.site.elements.NodeData;
import org.site.elements.NodeTree;
import org.site.elements.NodeTreeElem;
import org.site.server.Router;
import org.site.server.jsession.JSessionSite;
import org.site.view.RAction;
import org.site.view.Tracker;
import org.site.view.VUtil;
import redis.clients.jedis.Jedis;

import javax.servlet.GenericServlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

public class FrontArticleRating extends GenericServlet {
    JSessionSite session;

    public void service(ServletRequest req, ServletResponse res) {
        Object result = null;
        session = new JSessionSite(req);
        if (session.isPost() && Router.testUserWithParam(session, 1, "rating")) {
            VoteData data = session.postJson(VoteData.class);
            if (data != null && data.val > 0 && data.val < 6 && data.path != null && session.getReferer().equals(data.path)) {
                NodeTree tree = NodeTree.load(session.host);
                NodeTreeElem elem = tree.byUrl(data.path);
                if (elem != null) {

                    int idu = session.enabled() ? session.data.idu : 0;

                    String voteKey = idu > 0 ? (elem.idn + ":" + idu) : (elem.idn + ":" + session.getIP() + ":" + session.getBrowser());
                    //VUtil.println(idu, voteKey);
                    Jedis jedis = RAction.redis();
                    long added = jedis.sadd(RAction.keyArticleRating(session.host), voteKey);
                    jedis.disconnect();

                    //VUtil.println("FrontArticleRating(1)", added, RAction.keyArticleRating(session.host), voteKey);
                    if (added > 0) {
                        //VUtil.println("FrontArticleRating(2)");
                        NodeData node = VUtil.readNode(session.host, elem.idn);
                        if (node != null) {
                            result = articleRating(node, data.val - 1);
                        }
                    }
                }
            }
        }
        Router.json(result, res);
    }

    VoteResult articleRating(NodeData node, int val) {

        VUtil.Rating rating = new VUtil.Rating(node.head.rating);
        if (rating.count < 2) {
            rating.data.set(4, (int) Math.round((Math.random() * 5) + 7));
        }
        rating.data.set(val, rating.data.get(val) + 1);
        node.head.rating = rating.data;
        VUtil.writeNode(session.host, node);

        Tracker.addMark(session.host, node.head.idn);
        VUtil.Rating data = new VUtil.Rating(node.head.rating);

        return new VoteResult(data.value, data.count);
    }

    // ============

    public class VoteData {
        int val;
        String path;
    }

    public class VoteResult {
        double rating;
        int count;

        VoteResult(double rating, int count) {
            this.rating = rating;
            this.count = count;
        }
    }
}
