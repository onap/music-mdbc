package org.onap.music.mdbc;

import org.onap.music.logging.EELFLoggerDelegate;
import org.onap.music.main.MusicUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

public class TestUtils {
    private static EELFLoggerDelegate logger = EELFLoggerDelegate.getLogger(TestUtils.class);

    public static void populateMusicUtilsWithProperties(Properties prop){
        //TODO: Learn how to do this properly within music
        String[] propKeys = MusicUtil.getPropkeys();
        for (int k = 0; k < propKeys.length; k++) {
            String key = propKeys[k];
            if (prop.containsKey(key) && prop.get(key) != null) {
                switch (key) {
                    case "zookeeper.host":
                        MusicUtil.setMyZkHost(prop.getProperty(key));
                        break;
                    case "cassandra.host":
                        MusicUtil.setMyCassaHost(prop.getProperty(key));
                        break;
                    case "music.ip":
                        MusicUtil.setDefaultMusicIp(prop.getProperty(key));
                        break;
                    case "debug":
                        MusicUtil.setDebug(Boolean
                                .getBoolean(prop.getProperty(key).toLowerCase()));
                        break;
                    case "version":
                        MusicUtil.setVersion(prop.getProperty(key));
                        break;
                    case "music.rest.ip":
                        MusicUtil.setMusicRestIp(prop.getProperty(key));
                        break;
                    case "music.properties":
                        MusicUtil.setMusicPropertiesFilePath(prop.getProperty(key));
                        break;
                    case "lock.lease.period":
                        MusicUtil.setDefaultLockLeasePeriod(
                                Long.parseLong(prop.getProperty(key)));
                        break;
                    case "my.id":
                        MusicUtil.setMyId(Integer.parseInt(prop.getProperty(key)));
                        break;
                    case "all.ids":
                        String[] ids = prop.getProperty(key).split(":");
                        MusicUtil.setAllIds(new ArrayList<String>(Arrays.asList(ids)));
                        break;
                    case "public.ip":
                        MusicUtil.setPublicIp(prop.getProperty(key));
                        break;
                    case "all.public.ips":
                        String[] ips = prop.getProperty(key).split(":");
                        if (ips.length == 1) {
                            // Future use
                        } else if (ips.length > 1) {
                            MusicUtil.setAllPublicIps(
                                    new ArrayList<String>(Arrays.asList(ips)));
                        }
                        break;
                    case "cassandra.user":
                        MusicUtil.setCassName(prop.getProperty(key));
                        break;
                    case "cassandra.password":
                        MusicUtil.setCassPwd(prop.getProperty(key));
                        break;
                    case "aaf.endpoint.url":
                        MusicUtil.setAafEndpointUrl(prop.getProperty(key));
                        break;
                    default:
                        logger.error(EELFLoggerDelegate.errorLogger,
                                "No case found for " + key);
                }
            }
        }


    }
}
