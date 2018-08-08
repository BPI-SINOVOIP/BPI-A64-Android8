package android.aw;

import android.content.Intent;
import android.content.pm.ResolveInfo;

/**
 * System private interface to the background manager.
 *
 * {@hide}
 */
interface IBackgroundManager
{
    /**
     * set debug
     */
    void setDebug(boolean debug);

    /**
     * set current inputmethod
     * @param inputmethod current default inputmethod
     */
    void setCurrentInputMethod(String inputmethod);

    /**
     * gcm list
     * @param list gcm list
     */
    void setGcmList(in List<String> list);

    /**
     * set user whitelist
     * @param whitelist user whitelist
     */
    void setUserWhitelist(in List<String> whitelist);

    /**
     * return user whitelist
     */
    List<String> getSystemWhitelist();

    /**
     * set limit background count
     * @param limit background count
     */
    void setLimitBackgroundCount(int limit);

    /**
     * return limit background count
     */
    int getLimitBackgroundCount();

    /**
     * set music playing
     * @param pid player pid
     * @param playing music playing
     */
    void setBgmusic(int pid, boolean playing);

    /**
     * resolver receivers and remove not in whitelist
     * @param Intent target intent
     * @param receivers resolver receivers
     */
    String[] resolverReceiver(in Intent intent, inout List<ResolveInfo> receivers);

    /**
     * check skip service
     * @param Intent service intent
     */
    boolean skipService(in Intent service);
}
