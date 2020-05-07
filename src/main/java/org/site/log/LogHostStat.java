package org.site.log;

import java.util.ArrayList;
import java.util.HashMap;

public class LogHostStat {
    public class Total {
        public int phrase = 0;
        public int referer = 0;
        public int entry = 0;
        public int follow = 0;
        public int affiliate = 0;
        public int go = 0;
    }

    public class Follow {
        public HashMap<String, Integer> src = new HashMap<>();
        public HashMap<String, Integer> dst = new HashMap<>();
    }

    public class HostSrcDst {
        public HashMap<String, Integer> host = new HashMap<>();
        public HashMap<String, Integer> src = new HashMap<>();
        public HashMap<String, Integer> dst = new HashMap<>();
    }

    public int[] user = new int[]{0, 0};//all, valid
    public int[] hit = new int[]{0, 0};//all, valid
    public double[] time = new double[]{0, 0};//all, valid
    public int pages = 0;
    public double duration = 0;// sec
    public long[] amount = new long[]{0, 0};//in,out

    public ArrayList<long[]> period = new ArrayList<>();// user, hitAll, count, amount

    public HashMap<Integer, Integer> status = new HashMap<>();
    public HashMap<String, Integer> phrase = new HashMap<>();
    public HashMap<String, Integer> referer = new HashMap<>();
    public HashMap<String, Integer> entry = new HashMap<>();

    public Total total = new Total();
    public Follow follow = new Follow();

    public HostSrcDst affiliate = new HostSrcDst();
    public HostSrcDst go = new HostSrcDst();

}
