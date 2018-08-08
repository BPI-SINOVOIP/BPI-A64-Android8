package android.aw;

import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.util.Singleton;

import java.util.List;

/**
 * @hide
 */
public final class BackgroundManager {
    private static final String TAG = "BackgroundManager";

    public static final String SERVICE = "background";

    /** @hide */
    public BackgroundManager() {
    }

    public static IBackgroundManager getService() {
        return IBackgroundManagerSingleton.get();
    }

    private static final Singleton<IBackgroundManager> IBackgroundManagerSingleton =
            new Singleton<IBackgroundManager>() {
                @Override
                protected IBackgroundManager create() {
                    final IBinder b = ServiceManager.getService(SERVICE);
                    final IBackgroundManager am = IBackgroundManager.Stub.asInterface(b);
                    return am;
                }
            };

    /**
     * set debug
     */
    public static void setDebug(boolean debug) {
        IBackgroundManager bm = getService();
        if (bm == null) return;
        try {
            bm.setDebug(debug);
        } catch (RemoteException e) {
            Log.w(TAG, "setDebug error", e);
        }
    }

    /**
     * set current inputmethod
     * @param inputmethod current default inputmethod
     */
    public static void setCurrentInputMethod(String inputmethod) {
        IBackgroundManager bm = getService();
        if (bm == null) return;
        try {
            bm.setCurrentInputMethod(inputmethod);
        } catch (RemoteException e) {
            Log.w(TAG, "setCurrentInputMethod error", e);
        }
    }

    /**
     * set user whitelist
     * @param whitelist user whitelist
     */
    public static void setUserWhitelist(List<String> whitelist) {
        IBackgroundManager bm = getService();
        if (bm == null) return;
        try {
            bm.setUserWhitelist(whitelist);
        } catch (RemoteException e) {
            Log.w(TAG, "setUserWhitelist error", e);
        }
    }

    /**
     * return user whitelist
     */
    public static List<String> getSystemWhitelist() {
        IBackgroundManager bm = getService();
        if (bm == null) return null;
        try {
            return bm.getSystemWhitelist();
        } catch (RemoteException e) {
            Log.w(TAG, "getSystemWhitelist error", e);
            return null;
        }
    }

    /**
     * set limit background count
     * @param limit background count
     */
    public static void setLimitBackgroundCount(int limit) {
        IBackgroundManager bm = getService();
        if (bm == null) return;
        try {
            bm.setLimitBackgroundCount(limit);
        } catch (RemoteException e) {
            Log.w(TAG, "setLimitBackgroundCount error", e);
        }
    }

    /**
     * return limit background count
     */
    public static int getLimitBackgroundCount() {
        IBackgroundManager bm = getService();
        if (bm == null) return -1;
        try {
            return bm.getLimitBackgroundCount();
        } catch (RemoteException e) {
            Log.w(TAG, "getLimitBackgroundCount error", e);
            return -1;
        }
    }

    /**
     * set music playing
     * @param pid player pid
     * @param playing music playing
     */
    public static void setBgmusic(int pid, boolean playing) {
        IBackgroundManager bm = getService();
        if (bm == null) return;
        try {
            bm.setBgmusic(pid, playing);
        } catch (RemoteException e) {
            Log.w(TAG, "setBgmusic " + pid + " error", e);
        }
    }

    /**
     * resolver receivers and remove not in whitelist
     * @param Intent target intent
     * @param receivers resolver receivers
     */
    public static String[] resolverReceiver(Intent intent, List<ResolveInfo> receivers) {
        IBackgroundManager bm = getService();
        if (bm == null) return null;
        try {
            return bm.resolverReceiver(intent, receivers);
        } catch (RemoteException e) {
            Log.w(TAG, "resolverReceiver " + intent + " error", e);
            return null;
        }
    }

    /**
     * check skip service
     * @param Intent service intent
     */
    public static boolean skipService(Intent service) {
        IBackgroundManager bm = getService();
        if (bm == null) return false;
        try {
            return bm.skipService(service);
        } catch (RemoteException e) {
            Log.w(TAG, "skipService " + service + " error", e);
            return false;
        }
    }
}
