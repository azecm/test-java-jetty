package org.site;


import org.site.command.ScanSite;
import org.site.command.UpdatePrice;
import org.site.elements.NodeSearch;
import org.site.elements.NodeTreeDB;
import org.site.elements.PriceKeywords;
import org.site.log.Counter;
import org.site.log.Log;
import org.site.server.ReadMail;
import org.site.server.Router;
import org.site.view.*;


public class Main {
    public static void main(String[] args) {

        Crontab.Time ct = new Crontab.Time();

        if (args.length == 0) {
            ViewSite.updateAllHost(false);
        } else {
            switch (args[0]) {
                case "update-image":
                    if (args.length == 2) {
                        new PriceKeywords(args[1]).init().updateImages();
                    }
                    break;
                case "crontab": {
                    new Crontab();
                    break;
                }
                case "jetty": {
                    new Router().start();
                    break;
                }
                case "cron": {
                    if (args.length == 2) {
                        switch (args[1]) {
                            case "day": {
                                Crontab.everyDay();
                                break;
                            }
                            case "15": {
                                Crontab.every15minutes();
                                break;
                            }
                            case "5":
                                Crontab.every5minutes();
                                break;
                        }
                    }
                    break;
                }
                case "mail": {
                    new ReadMail().readAll();
                    break;
                }
                case "mail-single": {
                    new ReadMail().readSingle();
                    break;
                }
                case "index": {
                    ViewSite.updateAllIndex();
                    break;
                }
                case "update": {
                    ViewSite.updateAllHost(true);
                    break;
                }
                case "scan": {
                    ViewSite.scanAllHost();
                    break;
                }
                case "counter": {
                    Counter.cron(ct);
                    break;
                }
                case "log": {
                    if (args.length == 1) {
                        new Log();
                    } else {
                        int days = VUtil.getInt(args[1]);
                        if (days > 0) new Log(days);
                    }
                    break;
                }
                case "voting": {
                    RAction.commentVoting(ct);
                    break;
                }
                case "update-price": {
                    new UpdatePrice();
                    break;
                }
                case "scan-site": {
                    new ScanSite().startAll();
                    break;
                }
                default: {
                    if (ViewSite.hosts().contains(args[0])) {
                        String host = args[0];
                        if (args.length == 1) {
                            ViewSite.updateHost(host);
                        } else {
                            int idn = VUtil.getInt(args[1]);
                            if (idn > 0) {
                                if (args.length == 2) {
                                    ViewSite.updateHostIdn(host, idn);
                                } else if (args.length == 3 && args[2].equals("twitter")) {
                                    ViewSite.updateHostIdnTwitter(host, idn);
                                }
                            } else {
                                switch (args[1]) {
                                    case "update": {
                                        ViewSite.updateHostMini(host);
                                        break;
                                    }
                                    case "index": {
                                        NodeSearch.indexSite(host);
                                        break;
                                    }
                                    case "tree": {
                                        new NodeTreeDB(host).create().insert().toCVS().finish();
                                        break;
                                    }
                                    //
                                    default: {
                                        if (args[1].startsWith("/")) {
                                            ViewSite.updateHostUrl(host, args[1]);
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    break;
                }
            }
        }
    }


}
