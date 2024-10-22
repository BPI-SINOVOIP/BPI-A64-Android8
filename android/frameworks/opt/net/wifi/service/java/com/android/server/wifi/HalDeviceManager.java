/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.wifi;

import android.hardware.wifi.V1_0.IWifi;
import android.hardware.wifi.V1_0.IWifiApIface;
import android.hardware.wifi.V1_0.IWifiChip;
import android.hardware.wifi.V1_0.IWifiChipEventCallback;
import android.hardware.wifi.V1_0.IWifiEventCallback;
import android.hardware.wifi.V1_0.IWifiIface;
import android.hardware.wifi.V1_0.IWifiNanIface;
import android.hardware.wifi.V1_0.IWifiP2pIface;
import android.hardware.wifi.V1_0.IWifiRttController;
import android.hardware.wifi.V1_0.IWifiStaIface;
import android.hardware.wifi.V1_0.IfaceType;
import android.hardware.wifi.V1_0.WifiDebugRingBufferStatus;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.hidl.manager.V1_0.IServiceManager;
import android.hidl.manager.V1_0.IServiceNotification;
import android.os.Handler;
import android.os.HwRemoteBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.util.MutableBoolean;
import android.util.MutableInt;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles device management through the HAL (HIDL) interface.
 */
public class HalDeviceManager {
    private static final String TAG = "HalDeviceManager";
    private static final boolean DBG = true;

    private static final int START_HAL_RETRY_INTERVAL_MS = 20;
    // Number of attempts a start() is re-tried. A value of 0 means no retries after a single
    // attempt.
    @VisibleForTesting
    public static final int START_HAL_RETRY_TIMES = 3;
    @VisibleForTesting
    public static final String HAL_INSTANCE_NAME = "default";

    // public API
    public HalDeviceManager() {
        mInterfaceAvailableForRequestListeners.put(IfaceType.STA, new HashSet<>());
        mInterfaceAvailableForRequestListeners.put(IfaceType.AP, new HashSet<>());
        mInterfaceAvailableForRequestListeners.put(IfaceType.P2P, new HashSet<>());
        mInterfaceAvailableForRequestListeners.put(IfaceType.NAN, new HashSet<>());
    }

    /**
     * Actually starts the HalDeviceManager: separate from constructor since may want to phase
     * at a later time.
     *
     * TODO: if decide that no need for separating construction from initialization (e.g. both are
     * done at injector) then move to constructor.
     */
    public void initialize() {
        initializeInternal();
    }

    /**
     * Register a ManagerStatusListener to get information about the status of the manager. Use the
     * isReady() and isStarted() methods to check status immediately after registration and when
     * triggered.
     *
     * It is safe to re-register the same callback object - duplicates are detected and only a
     * single copy kept.
     *
     * @param listener ManagerStatusListener listener object.
     * @param looper Looper on which to dispatch listener. Null implies current looper.
     */
    public void registerStatusListener(ManagerStatusListener listener, Looper looper) {
        synchronized (mLock) {
            if (!mManagerStatusListeners.add(new ManagerStatusListenerProxy(listener,
                    looper == null ? Looper.myLooper() : looper))) {
                Log.w(TAG, "registerStatusListener: duplicate registration ignored");
            }
        }
    }

    /**
     * Returns whether the vendor HAL is supported on this device or not.
     */
    public boolean isSupported() {
        return isSupportedInternal();
    }

    /**
     * Returns the current status of the HalDeviceManager: whether or not it is ready to execute
     * commands. A return of 'false' indicates that the HAL service (IWifi) is not available. Use
     * the registerStatusListener() to listener for status changes.
     */
    public boolean isReady() {
        return mWifi != null;
    }

    /**
     * Returns the current status of Wi-Fi: started (true) or stopped (false).
     *
     * Note: direct call to HIDL.
     */
    public boolean isStarted() {
        return isWifiStarted();
    }

    /**
     * Attempts to start Wi-Fi (using HIDL). Returns the success (true) or failure (false) or
     * the start operation. Will also dispatch any registered ManagerStatusCallback.onStart() on
     * success.
     *
     * Note: direct call to HIDL.
     */
    public boolean start() {
        return startWifi();
    }

    /**
     * Stops Wi-Fi. Will also dispatch any registeredManagerStatusCallback.onStop().
     *
     * Note: direct call to HIDL - failure is not-expected.
     */
    public void stop() {
        stopWifi();
    }

    /**
     * HAL device manager status change listener.
     */
    public interface ManagerStatusListener {
        /**
         * Indicates that the status of the HalDeviceManager has changed. Use isReady() and
         * isStarted() to obtain status information.
         */
        void onStatusChanged();
    }

    /**
     * Return the set of supported interface types across all Wi-Fi chips on the device.
     *
     * @return A set of IfaceTypes constants (possibly empty, e.g. on error).
     */
    public Set<Integer> getSupportedIfaceTypes() {
        return getSupportedIfaceTypesInternal(null);
    }

    /**
     * Return the set of supported interface types for the specified Wi-Fi chip.
     *
     * @return A set of IfaceTypes constants  (possibly empty, e.g. on error).
     */
    public Set<Integer> getSupportedIfaceTypes(IWifiChip chip) {
        return getSupportedIfaceTypesInternal(chip);
    }

    // interface-specific behavior

    /**
     * Create a STA interface if possible. Changes chip mode and removes conflicting interfaces if
     * needed and permitted by priority.
     *
     * @param destroyedListener Optional (nullable) listener to call when the allocated interface
     *                          is removed. Will only be registered and used if an interface is
     *                          created successfully.
     * @param looper The looper on which to dispatch the listener. A null value indicates the
     *               current thread.
     * @return A newly created interface - or null if the interface could not be created.
     */
    public IWifiStaIface createStaIface(InterfaceDestroyedListener destroyedListener,
            Looper looper) {
        return (IWifiStaIface) createIface(IfaceType.STA, destroyedListener, looper);
    }

    /**
     * Create AP interface if possible (see createStaIface doc).
     */
    public IWifiApIface createApIface(InterfaceDestroyedListener destroyedListener,
            Looper looper) {
        return (IWifiApIface) createIface(IfaceType.AP, destroyedListener, looper);
    }

    /**
     * Create P2P interface if possible (see createStaIface doc).
     */
    public IWifiP2pIface createP2pIface(InterfaceDestroyedListener destroyedListener,
            Looper looper) {
        return (IWifiP2pIface) createIface(IfaceType.P2P, destroyedListener, looper);
    }

    /**
     * Create NAN interface if possible (see createStaIface doc).
     */
    public IWifiNanIface createNanIface(InterfaceDestroyedListener destroyedListener,
            Looper looper) {
        return (IWifiNanIface) createIface(IfaceType.NAN, destroyedListener, looper);
    }

    /**
     * Removes (releases/destroys) the given interface. Will trigger any registered
     * InterfaceDestroyedListeners and possibly some InterfaceAvailableForRequestListeners if we
     * can potentially create some other interfaces as a result of removing this interface.
     */
    public boolean removeIface(IWifiIface iface) {
        boolean success = removeIfaceInternal(iface);
        dispatchAvailableForRequestListeners();
        return success;
    }

    /**
     * Returns the IWifiChip corresponding to the specified interface (or null on error).
     *
     * Note: clients must not perform chip mode changes or interface management (create/delete)
     * operations on IWifiChip directly. However, they can use the IWifiChip interface to perform
     * other functions - e.g. calling the debug/trace methods.
     */
    public IWifiChip getChip(IWifiIface iface) {
        String name = getName(iface);
        if (DBG) Log.d(TAG, "getChip: iface(name)=" + name);

        synchronized (mLock) {
            InterfaceCacheEntry cacheEntry = mInterfaceInfoCache.get(name);
            if (cacheEntry == null) {
                Log.e(TAG, "getChip: no entry for iface(name)=" + name);
                return null;
            }

            return cacheEntry.chip;
        }
    }

    /**
     * Register an InterfaceDestroyedListener to the specified iface - returns true on success
     * and false on failure. This listener is in addition to the one registered when the interface
     * was created - allowing non-creators to monitor interface status.
     *
     * Listener called-back on the specified looper - or on the current looper if a null is passed.
     */
    public boolean registerDestroyedListener(IWifiIface iface,
            InterfaceDestroyedListener destroyedListener,
            Looper looper) {
        String name = getName(iface);
        if (DBG) Log.d(TAG, "registerDestroyedListener: iface(name)=" + name);

        synchronized (mLock) {
            InterfaceCacheEntry cacheEntry = mInterfaceInfoCache.get(name);
            if (cacheEntry == null) {
                Log.e(TAG, "registerDestroyedListener: no entry for iface(name)=" + name);
                return false;
            }

            return cacheEntry.destroyedListeners.add(
                    new InterfaceDestroyedListenerProxy(destroyedListener,
                            looper == null ? Looper.myLooper() : looper));
        }
    }

    /**
     * Register a listener to be called when an interface of the specified type could be requested.
     * No guarantees are provided (some other entity could request it first). The listener is
     * active from registration until unregistration - using
     * unregisterInterfaceAvailableForRequestListener().
     *
     * Only a single instance of a listener will be registered (even if the specified looper is
     * different).
     *
     * Note that if it is possible to create the specified interface type at registration time
     * then the callback will be triggered immediately.
     *
     * @param ifaceType The interface type (IfaceType) to be monitored.
     * @param listener Listener to call when an interface of the requested
     *                 type could be created
     * @param looper The looper on which to dispatch the listener. A null value indicates the
     *               current thread.
     */
    public void registerInterfaceAvailableForRequestListener(int ifaceType,
            InterfaceAvailableForRequestListener listener, Looper looper) {
        if (DBG) Log.d(TAG, "registerInterfaceAvailableForRequestListener: ifaceType=" + ifaceType);

        synchronized (mLock) {
            mInterfaceAvailableForRequestListeners.get(ifaceType).add(
                    new InterfaceAvailableForRequestListenerProxy(listener,
                            looper == null ? Looper.myLooper() : looper));
        }

        WifiChipInfo[] chipInfos = getAllChipInfo();
        if (chipInfos == null) {
            Log.e(TAG,
                    "registerInterfaceAvailableForRequestListener: no chip info found - but "
                            + "possibly registered pre-started - ignoring");
            return;
        }
        dispatchAvailableForRequestListenersForType(ifaceType, chipInfos);
    }

    /**
     * Unregisters a listener registered with registerInterfaceAvailableForRequestListener().
     */
    public void unregisterInterfaceAvailableForRequestListener(
            int ifaceType,
            InterfaceAvailableForRequestListener listener) {
        if (DBG) {
            Log.d(TAG, "unregisterInterfaceAvailableForRequestListener: ifaceType=" + ifaceType);
        }

        synchronized (mLock) {
            Iterator<InterfaceAvailableForRequestListenerProxy> it =
                    mInterfaceAvailableForRequestListeners.get(ifaceType).iterator();
            while (it.hasNext()) {
                if (it.next().mListener == listener) {
                    it.remove();
                    return;
                }
            }
        }
    }

    /**
     * Return the name of the input interface or null on error.
     */
    public static String getName(IWifiIface iface) {
        if (iface == null) {
            return "<null>";
        }

        Mutable<String> nameResp = new Mutable<>();
        try {
            iface.getName((WifiStatus status, String name) -> {
                if (status.code == WifiStatusCode.SUCCESS) {
                    nameResp.value = name;
                } else {
                    Log.e(TAG, "Error on getName: " + statusString(status));
                }
            });
        } catch (RemoteException e) {
            Log.e(TAG, "Exception on getName: " + e);
        }

        return nameResp.value;
    }

    /**
     * Called when interface is destroyed.
     */
    public interface InterfaceDestroyedListener {
        /**
         * Called for every interface on which registered when destroyed - whether
         * destroyed by releaseIface() or through chip mode change or through Wi-Fi
         * going down.
         *
         * Can be registered when the interface is requested with createXxxIface() - will
         * only be valid if the interface creation was successful - i.e. a non-null was returned.
         */
        void onDestroyed();
    }

    /**
     * Called when an interface type is possibly available for creation.
     */
    public interface InterfaceAvailableForRequestListener {
        /**
         * Registered when an interface type could be requested. Registered with
         * registerInterfaceAvailableForRequestListener() and unregistered with
         * unregisterInterfaceAvailableForRequestListener().
         */
        void onAvailableForRequest();
    }

    /**
     * Creates a IWifiRttController corresponding to the input interface. A direct match to the
     * IWifiChip.createRttController() method.
     *
     * Returns the created IWifiRttController or a null on error.
     */
    public IWifiRttController createRttController(IWifiIface boundIface) {
        if (DBG) Log.d(TAG, "createRttController: boundIface(name)=" + getName(boundIface));
        synchronized (mLock) {
            if (mWifi == null) {
                Log.e(TAG, "createRttController: null IWifi -- boundIface(name)="
                        + getName(boundIface));
                return null;
            }

            IWifiChip chip = getChip(boundIface);
            if (chip == null) {
                Log.e(TAG, "createRttController: null IWifiChip -- boundIface(name)="
                        + getName(boundIface));
                return null;
            }

            Mutable<IWifiRttController> rttResp = new Mutable<>();
            try {
                chip.createRttController(boundIface,
                        (WifiStatus status, IWifiRttController rtt) -> {
                            if (status.code == WifiStatusCode.SUCCESS) {
                                rttResp.value = rtt;
                            } else {
                                Log.e(TAG, "IWifiChip.createRttController failed: " + statusString(
                                        status));
                            }
                        });
            } catch (RemoteException e) {
                Log.e(TAG, "IWifiChip.createRttController exception: " + e);
            }

            return rttResp.value;
        }
    }

    // internal state

    /* This "PRIORITY" is not for deciding interface elimination (that is controlled by
     * allowedToDeleteIfaceTypeForRequestedType. This priority is used for:
     * - Comparing 2 configuration options
     * - Order of dispatch of available for request listeners
     */
    private static final int[] IFACE_TYPES_BY_PRIORITY =
            {IfaceType.AP, IfaceType.STA, IfaceType.P2P, IfaceType.NAN};

    private final Object mLock = new Object();

    private IServiceManager mServiceManager;
    private IWifi mWifi;
    private final WifiEventCallback mWifiEventCallback = new WifiEventCallback();
    private final Set<ManagerStatusListenerProxy> mManagerStatusListeners = new HashSet<>();
    private final SparseArray<Set<InterfaceAvailableForRequestListenerProxy>>
            mInterfaceAvailableForRequestListeners = new SparseArray<>();
    private final SparseArray<IWifiChipEventCallback.Stub> mDebugCallbacks = new SparseArray<>();

    /*
     * This is the only place where we cache HIDL information in this manager. Necessary since
     * we need to keep a list of registered destroyed listeners. Will be validated regularly
     * in getAllChipInfoAndValidateCache().
     */
    private final Map<String, InterfaceCacheEntry> mInterfaceInfoCache = new HashMap<>();

    private class InterfaceCacheEntry {
        public IWifiChip chip;
        public int chipId;
        public String name;
        public int type;
        public Set<InterfaceDestroyedListenerProxy> destroyedListeners = new HashSet<>();

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{name=").append(name).append(", type=").append(type)
                    .append(", destroyedListeners.size()=").append(destroyedListeners.size())
                    .append("}");
            return sb.toString();
        }
    }

    private class WifiIfaceInfo {
        public String name;
        public IWifiIface iface;
    }

    private class WifiChipInfo {
        public IWifiChip chip;
        public int chipId;
        public ArrayList<IWifiChip.ChipMode> availableModes;
        public boolean currentModeIdValid;
        public int currentModeId;
        public WifiIfaceInfo[][] ifaces = new WifiIfaceInfo[IFACE_TYPES_BY_PRIORITY.length][];

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{chipId=").append(chipId).append(", availableModes=").append(availableModes)
                    .append(", currentModeIdValid=").append(currentModeIdValid)
                    .append(", currentModeId=").append(currentModeId);
            for (int type: IFACE_TYPES_BY_PRIORITY) {
                sb.append(", ifaces[" + type + "].length=").append(ifaces[type].length);
            }
            sb.append(")");
            return sb.toString();
        }
    }

    /**
     * Wrapper function to access the HIDL services. Created to be mockable in unit-tests.
     */
    protected IWifi getWifiServiceMockable() {
        try {
            return IWifi.getService();
        } catch (RemoteException e) {
            Log.e(TAG, "Exception getting IWifi service: " + e);
            return null;
        }
    }

    protected IServiceManager getServiceManagerMockable() {
        try {
            return IServiceManager.getService();
        } catch (RemoteException e) {
            Log.e(TAG, "Exception getting IServiceManager: " + e);
            return null;
        }
    }

    // internal implementation

    private void initializeInternal() {
        initIServiceManagerIfNecessary();
        if (isSupportedInternal()) {
            initIWifiIfNecessary();
        }
    }

    private void teardownInternal() {
        managerStatusListenerDispatch();
        dispatchAllDestroyedListeners();
        mInterfaceAvailableForRequestListeners.get(IfaceType.STA).clear();
        mInterfaceAvailableForRequestListeners.get(IfaceType.AP).clear();
        mInterfaceAvailableForRequestListeners.get(IfaceType.P2P).clear();
        mInterfaceAvailableForRequestListeners.get(IfaceType.NAN).clear();
    }

    private final HwRemoteBinder.DeathRecipient mServiceManagerDeathRecipient =
            cookie -> {
                Log.wtf(TAG, "IServiceManager died: cookie=" + cookie);
                synchronized (mLock) {
                    mServiceManager = null;
                    // theoretically can call initServiceManager again here - but
                    // there's no point since most likely system is going to reboot
                }
            };

    private final IServiceNotification mServiceNotificationCallback =
            new IServiceNotification.Stub() {
                @Override
                public void onRegistration(String fqName, String name,
                                           boolean preexisting) {
                    Log.d(TAG, "IWifi registration notification: fqName=" + fqName
                            + ", name=" + name + ", preexisting=" + preexisting);
                    synchronized (mLock) {
                        initIWifiIfNecessary();
                    }
                }
            };

    /**
     * Failures of IServiceManager are most likely system breaking in any case. Behavior here
     * will be to WTF and continue.
     */
    private void initIServiceManagerIfNecessary() {
        if (DBG) Log.d(TAG, "initIServiceManagerIfNecessary");

        synchronized (mLock) {
            if (mServiceManager != null) {
                return;
            }

            mServiceManager = getServiceManagerMockable();
            if (mServiceManager == null) {
                Log.wtf(TAG, "Failed to get IServiceManager instance");
            } else {
                try {
                    if (!mServiceManager.linkToDeath(
                            mServiceManagerDeathRecipient, /* don't care */ 0)) {
                        Log.wtf(TAG, "Error on linkToDeath on IServiceManager");
                        mServiceManager = null;
                        return;
                    }

                    if (!mServiceManager.registerForNotifications(IWifi.kInterfaceName, "",
                            mServiceNotificationCallback)) {
                        Log.wtf(TAG, "Failed to register a listener for IWifi service");
                        mServiceManager = null;
                    }
                } catch (RemoteException e) {
                    Log.wtf(TAG, "Exception while operating on IServiceManager: " + e);
                    mServiceManager = null;
                }
            }
        }
    }

    /**
     * Uses the IServiceManager to query if the vendor HAL is present in the VINTF for the device
     * or not.
     * @return true if supported, false otherwise.
     */
    private boolean isSupportedInternal() {
        if (DBG) Log.d(TAG, "isSupportedInternal");

        synchronized (mLock) {
            if (mServiceManager == null) {
                Log.e(TAG, "isSupported: called but mServiceManager is null!?");
                return false;
            }
            try {
                return (mServiceManager.getTransport(IWifi.kInterfaceName, HAL_INSTANCE_NAME)
                        != IServiceManager.Transport.EMPTY);
            } catch (RemoteException e) {
                Log.wtf(TAG, "Exception while operating on IServiceManager: " + e);
                return false;
            }
        }
    }

    private final HwRemoteBinder.DeathRecipient mIWifiDeathRecipient =
            cookie -> {
                Log.e(TAG, "IWifi HAL service died! Have a listener for it ... cookie=" + cookie);
                synchronized (mLock) { // prevents race condition with surrounding method
                    mWifi = null;
                    teardownInternal();
                    // don't restart: wait for registration notification
                }
            };

    /**
     * Initialize IWifi and register death listener and event callback.
     *
     * - It is possible that IWifi is not ready - we have a listener on IServiceManager for it.
     * - It is not expected that any of the registrations will fail. Possible indication that
     *   service died after we obtained a handle to it.
     *
     * Here and elsewhere we assume that death listener will do the right thing!
    */
    private void initIWifiIfNecessary() {
        if (DBG) Log.d(TAG, "initIWifiIfNecessary");

        synchronized (mLock) {
            if (mWifi != null) {
                return;
            }

            try {
                mWifi = getWifiServiceMockable();
                if (mWifi == null) {
                    Log.e(TAG, "IWifi not (yet) available - but have a listener for it ...");
                    return;
                }

                if (!mWifi.linkToDeath(mIWifiDeathRecipient, /* don't care */ 0)) {
                    Log.e(TAG, "Error on linkToDeath on IWifi - will retry later");
                    return;
                }

                WifiStatus status = mWifi.registerEventCallback(mWifiEventCallback);
                if (status.code != WifiStatusCode.SUCCESS) {
                    Log.e(TAG, "IWifi.registerEventCallback failed: " + statusString(status));
                    mWifi = null;
                    return;
                }
                // Stopping wifi just in case. This would also trigger the status callback.
                stopWifi();
            } catch (RemoteException e) {
                Log.e(TAG, "Exception while operating on IWifi: " + e);
            }
        }
    }

    /**
     * Registers event listeners on all IWifiChips after a successful start: DEBUG only!
     *
     * We don't need the listeners since any callbacks are just confirmation of status codes we
     * obtain directly from mode changes or interface creation/deletion.
     *
     * Relies (to the degree we care) on the service removing all listeners when Wi-Fi is stopped.
     */
    private void initIWifiChipDebugListeners() {
        if (DBG) Log.d(TAG, "initIWifiChipDebugListeners");

        if (!DBG) {
            return;
        }

        synchronized (mLock) {
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                Mutable<ArrayList<Integer>> chipIdsResp = new Mutable<>();

                // get all chip IDs
                mWifi.getChipIds((WifiStatus status, ArrayList<Integer> chipIds) -> {
                    statusOk.value = status.code == WifiStatusCode.SUCCESS;
                    if (statusOk.value) {
                        chipIdsResp.value = chipIds;
                    } else {
                        Log.e(TAG, "getChipIds failed: " + statusString(status));
                    }
                });
                if (!statusOk.value) {
                    return;
                }

                if (DBG) Log.d(TAG, "getChipIds=" + chipIdsResp.value);
                if (chipIdsResp.value.size() == 0) {
                    Log.e(TAG, "Should have at least 1 chip!");
                    return;
                }

                // register a callback for each chip
                Mutable<IWifiChip> chipResp = new Mutable<>();
                for (Integer chipId: chipIdsResp.value) {
                    mWifi.getChip(chipId, (WifiStatus status, IWifiChip chip) -> {
                        statusOk.value = status.code == WifiStatusCode.SUCCESS;
                        if (statusOk.value) {
                            chipResp.value = chip;
                        } else {
                            Log.e(TAG, "getChip failed: " + statusString(status));
                        }
                    });
                    if (!statusOk.value) {
                        continue; // still try next one?
                    }

                    IWifiChipEventCallback.Stub callback =
                            new IWifiChipEventCallback.Stub() {
                                @Override
                                public void onChipReconfigured(int modeId) throws RemoteException {
                                    Log.d(TAG, "onChipReconfigured: modeId=" + modeId);
                                }

                                @Override
                                public void onChipReconfigureFailure(WifiStatus status)
                                        throws RemoteException {
                                    Log.d(TAG, "onChipReconfigureFailure: status=" + statusString(
                                            status));
                                }

                                @Override
                                public void onIfaceAdded(int type, String name)
                                        throws RemoteException {
                                    Log.d(TAG, "onIfaceAdded: type=" + type + ", name=" + name);
                                }

                                @Override
                                public void onIfaceRemoved(int type, String name)
                                        throws RemoteException {
                                    Log.d(TAG, "onIfaceRemoved: type=" + type + ", name=" + name);
                                }

                                @Override
                                public void onDebugRingBufferDataAvailable(
                                        WifiDebugRingBufferStatus status,
                                        ArrayList<Byte> data) throws RemoteException {
                                    Log.d(TAG, "onDebugRingBufferDataAvailable");
                                }

                                @Override
                                public void onDebugErrorAlert(int errorCode,
                                        ArrayList<Byte> debugData)
                                        throws RemoteException {
                                    Log.d(TAG, "onDebugErrorAlert");
                                }
                            };
                    mDebugCallbacks.put(chipId, callback); // store to prevent GC: needed by HIDL
                    WifiStatus status = chipResp.value.registerEventCallback(callback);
                    if (status.code != WifiStatusCode.SUCCESS) {
                        Log.e(TAG, "registerEventCallback failed: " + statusString(status));
                        continue; // still try next one?
                    }
                }
            } catch (RemoteException e) {
                Log.e(TAG, "initIWifiChipDebugListeners: exception: " + e);
                return;
            }
        }
    }

    /**
     * Get current information about all the chips in the system: modes, current mode (if any), and
     * any existing interfaces.
     *
     * Intended to be called whenever we need to configure the chips - information is NOT cached (to
     * reduce the likelihood that we get out-of-sync).
     */
    private WifiChipInfo[] getAllChipInfo() {
        if (DBG) Log.d(TAG, "getAllChipInfo");

        synchronized (mLock) {
            if (mWifi == null) {
                Log.e(TAG, "getAllChipInfo: called but mWifi is null!?");
                return null;
            }

            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                Mutable<ArrayList<Integer>> chipIdsResp = new Mutable<>();

                // get all chip IDs
                mWifi.getChipIds((WifiStatus status, ArrayList<Integer> chipIds) -> {
                    statusOk.value = status.code == WifiStatusCode.SUCCESS;
                    if (statusOk.value) {
                        chipIdsResp.value = chipIds;
                    } else {
                        Log.e(TAG, "getChipIds failed: " + statusString(status));
                    }
                });
                if (!statusOk.value) {
                    return null;
                }

                if (DBG) Log.d(TAG, "getChipIds=" + chipIdsResp.value);
                if (chipIdsResp.value.size() == 0) {
                    Log.e(TAG, "Should have at least 1 chip!");
                    return null;
                }

                int chipInfoIndex = 0;
                WifiChipInfo[] chipsInfo = new WifiChipInfo[chipIdsResp.value.size()];

                Mutable<IWifiChip> chipResp = new Mutable<>();
                for (Integer chipId: chipIdsResp.value) {
                    mWifi.getChip(chipId, (WifiStatus status, IWifiChip chip) -> {
                        statusOk.value = status.code == WifiStatusCode.SUCCESS;
                        if (statusOk.value) {
                            chipResp.value = chip;
                        } else {
                            Log.e(TAG, "getChip failed: " + statusString(status));
                        }
                    });
                    if (!statusOk.value) {
                        return null;
                    }

                    Mutable<ArrayList<IWifiChip.ChipMode>> availableModesResp = new Mutable<>();
                    chipResp.value.getAvailableModes(
                            (WifiStatus status, ArrayList<IWifiChip.ChipMode> modes) -> {
                                statusOk.value = status.code == WifiStatusCode.SUCCESS;
                                if (statusOk.value) {
                                    availableModesResp.value = modes;
                                } else {
                                    Log.e(TAG, "getAvailableModes failed: " + statusString(status));
                                }
                            });
                    if (!statusOk.value) {
                        return null;
                    }

                    MutableBoolean currentModeValidResp = new MutableBoolean(false);
                    MutableInt currentModeResp = new MutableInt(0);
                    chipResp.value.getMode((WifiStatus status, int modeId) -> {
                        statusOk.value = status.code == WifiStatusCode.SUCCESS;
                        if (statusOk.value) {
                            currentModeValidResp.value = true;
                            currentModeResp.value = modeId;
                        } else if (status.code == WifiStatusCode.ERROR_NOT_AVAILABLE) {
                            statusOk.value = true; // valid response
                        } else {
                            Log.e(TAG, "getMode failed: " + statusString(status));
                        }
                    });
                    if (!statusOk.value) {
                        return null;
                    }

                    Mutable<ArrayList<String>> ifaceNamesResp = new Mutable<>();
                    MutableInt ifaceIndex = new MutableInt(0);

                    chipResp.value.getStaIfaceNames(
                            (WifiStatus status, ArrayList<String> ifnames) -> {
                                statusOk.value = status.code == WifiStatusCode.SUCCESS;
                                if (statusOk.value) {
                                    ifaceNamesResp.value = ifnames;
                                } else {
                                    Log.e(TAG, "getStaIfaceNames failed: " + statusString(status));
                                }
                            });
                    if (!statusOk.value) {
                        return null;
                    }

                    WifiIfaceInfo[] staIfaces = new WifiIfaceInfo[ifaceNamesResp.value.size()];
                    for (String ifaceName: ifaceNamesResp.value) {
                        chipResp.value.getStaIface(ifaceName,
                                (WifiStatus status, IWifiStaIface iface) -> {
                                    statusOk.value = status.code == WifiStatusCode.SUCCESS;
                                    if (statusOk.value) {
                                        WifiIfaceInfo ifaceInfo = new WifiIfaceInfo();
                                        ifaceInfo.name = ifaceName;
                                        ifaceInfo.iface = iface;
                                        staIfaces[ifaceIndex.value++] = ifaceInfo;
                                    } else {
                                        Log.e(TAG, "getStaIface failed: " + statusString(status));
                                    }
                                });
                        if (!statusOk.value) {
                            return null;
                        }
                    }

                    ifaceIndex.value = 0;
                    chipResp.value.getApIfaceNames(
                            (WifiStatus status, ArrayList<String> ifnames) -> {
                                statusOk.value = status.code == WifiStatusCode.SUCCESS;
                                if (statusOk.value) {
                                    ifaceNamesResp.value = ifnames;
                                } else {
                                    Log.e(TAG, "getApIfaceNames failed: " + statusString(status));
                                }
                            });
                    if (!statusOk.value) {
                        return null;
                    }

                    WifiIfaceInfo[] apIfaces = new WifiIfaceInfo[ifaceNamesResp.value.size()];
                    for (String ifaceName: ifaceNamesResp.value) {
                        chipResp.value.getApIface(ifaceName,
                                (WifiStatus status, IWifiApIface iface) -> {
                                    statusOk.value = status.code == WifiStatusCode.SUCCESS;
                                    if (statusOk.value) {
                                        WifiIfaceInfo ifaceInfo = new WifiIfaceInfo();
                                        ifaceInfo.name = ifaceName;
                                        ifaceInfo.iface = iface;
                                        apIfaces[ifaceIndex.value++] = ifaceInfo;
                                    } else {
                                        Log.e(TAG, "getApIface failed: " + statusString(status));
                                    }
                                });
                        if (!statusOk.value) {
                            return null;
                        }
                    }

                    ifaceIndex.value = 0;
                    chipResp.value.getP2pIfaceNames(
                            (WifiStatus status, ArrayList<String> ifnames) -> {
                                statusOk.value = status.code == WifiStatusCode.SUCCESS;
                                if (statusOk.value) {
                                    ifaceNamesResp.value = ifnames;
                                } else {
                                    Log.e(TAG, "getP2pIfaceNames failed: " + statusString(status));
                                }
                            });
                    if (!statusOk.value) {
                        return null;
                    }

                    WifiIfaceInfo[] p2pIfaces = new WifiIfaceInfo[ifaceNamesResp.value.size()];
                    for (String ifaceName: ifaceNamesResp.value) {
                        chipResp.value.getP2pIface(ifaceName,
                                (WifiStatus status, IWifiP2pIface iface) -> {
                                    statusOk.value = status.code == WifiStatusCode.SUCCESS;
                                    if (statusOk.value) {
                                        WifiIfaceInfo ifaceInfo = new WifiIfaceInfo();
                                        ifaceInfo.name = ifaceName;
                                        ifaceInfo.iface = iface;
                                        p2pIfaces[ifaceIndex.value++] = ifaceInfo;
                                    } else {
                                        Log.e(TAG, "getP2pIface failed: " + statusString(status));
                                    }
                                });
                        if (!statusOk.value) {
                            return null;
                        }
                    }

                    ifaceIndex.value = 0;
                    chipResp.value.getNanIfaceNames(
                            (WifiStatus status, ArrayList<String> ifnames) -> {
                                statusOk.value = status.code == WifiStatusCode.SUCCESS;
                                if (statusOk.value) {
                                    ifaceNamesResp.value = ifnames;
                                } else {
                                    Log.e(TAG, "getNanIfaceNames failed: " + statusString(status));
                                }
                            });
                    if (!statusOk.value) {
                        return null;
                    }

                    WifiIfaceInfo[] nanIfaces = new WifiIfaceInfo[ifaceNamesResp.value.size()];
                    for (String ifaceName: ifaceNamesResp.value) {
                        chipResp.value.getNanIface(ifaceName,
                                (WifiStatus status, IWifiNanIface iface) -> {
                                    statusOk.value = status.code == WifiStatusCode.SUCCESS;
                                    if (statusOk.value) {
                                        WifiIfaceInfo ifaceInfo = new WifiIfaceInfo();
                                        ifaceInfo.name = ifaceName;
                                        ifaceInfo.iface = iface;
                                        nanIfaces[ifaceIndex.value++] = ifaceInfo;
                                    } else {
                                        Log.e(TAG, "getNanIface failed: " + statusString(status));
                                    }
                                });
                        if (!statusOk.value) {
                            return null;
                        }
                    }

                    WifiChipInfo chipInfo = new WifiChipInfo();
                    chipsInfo[chipInfoIndex++] = chipInfo;

                    chipInfo.chip = chipResp.value;
                    chipInfo.chipId = chipId;
                    chipInfo.availableModes = availableModesResp.value;
                    chipInfo.currentModeIdValid = currentModeValidResp.value;
                    chipInfo.currentModeId = currentModeResp.value;
                    chipInfo.ifaces[IfaceType.STA] = staIfaces;
                    chipInfo.ifaces[IfaceType.AP] = apIfaces;
                    chipInfo.ifaces[IfaceType.P2P] = p2pIfaces;
                    chipInfo.ifaces[IfaceType.NAN] = nanIfaces;
                }

                return chipsInfo;
            } catch (RemoteException e) {
                Log.e(TAG, "getAllChipInfoAndValidateCache exception: " + e);
            }
        }

        return null;
    }

    /**
     * Checks the local state of this object (the cached state) against the input 'chipInfos'
     * state (which is a live representation of the Wi-Fi firmware status - read through the HAL).
     * Returns 'true' if there are no discrepancies - 'false' otherwise.
     *
     * A discrepancy is if any local state contains references to a chip or interface which are not
     * found on the information read from the chip.
     */
    private boolean validateInterfaceCache(WifiChipInfo[] chipInfos) {
        if (DBG) Log.d(TAG, "validateInterfaceCache");

        synchronized (mLock) {
            for (InterfaceCacheEntry entry: mInterfaceInfoCache.values()) {
                // search for chip
                WifiChipInfo matchingChipInfo = null;
                for (WifiChipInfo ci: chipInfos) {
                    if (ci.chipId == entry.chipId) {
                        matchingChipInfo = ci;
                        break;
                    }
                }
                if (matchingChipInfo == null) {
                    Log.e(TAG, "validateInterfaceCache: no chip found for " + entry);
                    return false;
                }

                // search for interface
                WifiIfaceInfo[] ifaceInfoList = matchingChipInfo.ifaces[entry.type];
                if (ifaceInfoList == null) {
                    Log.e(TAG, "validateInterfaceCache: invalid type on entry " + entry);
                    return false;
                }

                boolean matchFound = false;
                for (WifiIfaceInfo ifaceInfo: ifaceInfoList) {
                    if (ifaceInfo.name.equals(entry.name)) {
                        matchFound = true;
                        break;
                    }
                }
                if (!matchFound) {
                    Log.e(TAG, "validateInterfaceCache: no interface found for " + entry);
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isWifiStarted() {
        if (DBG) Log.d(TAG, "isWifiStart");

        synchronized (mLock) {
            try {
                if (mWifi == null) {
                    Log.w(TAG, "isWifiStarted called but mWifi is null!?");
                    return false;
                } else {
                    return mWifi.isStarted();
                }
            } catch (RemoteException e) {
                Log.e(TAG, "isWifiStarted exception: " + e);
                return false;
            }
        }
    }

    private boolean startWifi() {
        if (DBG) Log.d(TAG, "startWifi");

        synchronized (mLock) {
            try {
                if (mWifi == null) {
                    Log.w(TAG, "startWifi called but mWifi is null!?");
                    return false;
                } else {
                    int triedCount = 0;
                    while (triedCount <= START_HAL_RETRY_TIMES) {
                        WifiStatus status = mWifi.start();
                        if (status.code == WifiStatusCode.SUCCESS) {
                            initIWifiChipDebugListeners();
                            managerStatusListenerDispatch();
                            if (triedCount != 0) {
                                Log.d(TAG, "start IWifi succeeded after trying "
                                         + triedCount + " times");
                            }
                            return true;
                        } else if (status.code == WifiStatusCode.ERROR_NOT_AVAILABLE) {
                            // Should retry. Hal might still be stopping.
                            Log.e(TAG, "Cannot start IWifi: " + statusString(status)
                                    + ", Retrying...");
                            try {
                                Thread.sleep(START_HAL_RETRY_INTERVAL_MS);
                            } catch (InterruptedException ignore) {
                                // no-op
                            }
                            triedCount++;
                        } else {
                            // Should not retry on other failures.
                            Log.e(TAG, "Cannot start IWifi: " + statusString(status));
                            return false;
                        }
                    }
                    Log.e(TAG, "Cannot start IWifi after trying " + triedCount + " times");
                    return false;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "startWifi exception: " + e);
                return false;
            }
        }
    }

    private void stopWifi() {
        if (DBG) Log.d(TAG, "stopWifi");

        synchronized (mLock) {
            try {
                if (mWifi == null) {
                    Log.w(TAG, "stopWifi called but mWifi is null!?");
                } else {
                    WifiStatus status = mWifi.stop();
                    if (status.code != WifiStatusCode.SUCCESS) {
                        Log.e(TAG, "Cannot stop IWifi: " + statusString(status));
                    }

                    // even on failure since WTF??
                    teardownInternal();
                }
            } catch (RemoteException e) {
                Log.e(TAG, "stopWifi exception: " + e);
            }
        }
    }

    private class WifiEventCallback extends IWifiEventCallback.Stub {
        @Override
        public void onStart() throws RemoteException {
            if (DBG) Log.d(TAG, "IWifiEventCallback.onStart");
            // NOP: only happens in reaction to my calls - will handle directly
        }

        @Override
        public void onStop() throws RemoteException {
            if (DBG) Log.d(TAG, "IWifiEventCallback.onStop");
            // NOP: only happens in reaction to my calls - will handle directly
        }

        @Override
        public void onFailure(WifiStatus status) throws RemoteException {
            Log.e(TAG, "IWifiEventCallback.onFailure: " + statusString(status));
            teardownInternal();

            // No need to do anything else: listeners may (will) re-start Wi-Fi
        }
    }

    private void managerStatusListenerDispatch() {
        synchronized (mLock) {
            for (ManagerStatusListenerProxy cb : mManagerStatusListeners) {
                cb.trigger();
            }
        }
    }

    private class ManagerStatusListenerProxy  extends
            ListenerProxy<ManagerStatusListener> {
        ManagerStatusListenerProxy(ManagerStatusListener statusListener,
                Looper looper) {
            super(statusListener, looper, "ManagerStatusListenerProxy");
        }

        @Override
        protected void action() {
            mListener.onStatusChanged();
        }
    }

    Set<Integer> getSupportedIfaceTypesInternal(IWifiChip chip) {
        Set<Integer> results = new HashSet<>();

        WifiChipInfo[] chipInfos = getAllChipInfo();
        if (chipInfos == null) {
            Log.e(TAG, "getSupportedIfaceTypesInternal: no chip info found");
            return results;
        }

        MutableInt chipIdIfProvided = new MutableInt(0); // NOT using 0 as a magic value
        if (chip != null) {
            MutableBoolean statusOk = new MutableBoolean(false);
            try {
                chip.getId((WifiStatus status, int id) -> {
                    if (status.code == WifiStatusCode.SUCCESS) {
                        chipIdIfProvided.value = id;
                        statusOk.value = true;
                    } else {
                        Log.e(TAG, "getSupportedIfaceTypesInternal: IWifiChip.getId() error: "
                                + statusString(status));
                        statusOk.value = false;
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "getSupportedIfaceTypesInternal IWifiChip.getId() exception: " + e);
                return results;
            }
            if (!statusOk.value) {
                return results;
            }
        }

        for (WifiChipInfo wci: chipInfos) {
            if (chip != null && wci.chipId != chipIdIfProvided.value) {
                continue;
            }

            for (IWifiChip.ChipMode cm: wci.availableModes) {
                for (IWifiChip.ChipIfaceCombination cic: cm.availableCombinations) {
                    for (IWifiChip.ChipIfaceCombinationLimit cicl: cic.limits) {
                        for (int type: cicl.types) {
                            results.add(type);
                        }
                    }
                }
            }
        }

        return results;
    }

    private IWifiIface createIface(int ifaceType, InterfaceDestroyedListener destroyedListener,
            Looper looper) {
        if (DBG) Log.d(TAG, "createIface: ifaceType=" + ifaceType);

        synchronized (mLock) {
            WifiChipInfo[] chipInfos = getAllChipInfo();
            if (chipInfos == null) {
                Log.e(TAG, "createIface: no chip info found");
                stopWifi(); // major error: shutting down
                return null;
            }

            if (!validateInterfaceCache(chipInfos)) {
                Log.e(TAG, "createIface: local cache is invalid!");
                stopWifi(); // major error: shutting down
                return null;
            }

            IWifiIface iface = createIfaceIfPossible(chipInfos, ifaceType, destroyedListener,
                    looper);
            if (iface != null) { // means that some configuration has changed
                if (!dispatchAvailableForRequestListeners()) {
                    return null; // catastrophic failure - shut down
                }
            }

            return iface;
        }
    }

    private IWifiIface createIfaceIfPossible(WifiChipInfo[] chipInfos, int ifaceType,
            InterfaceDestroyedListener destroyedListener, Looper looper) {
        if (DBG) {
            Log.d(TAG, "createIfaceIfPossible: chipInfos=" + Arrays.deepToString(chipInfos)
                    + ", ifaceType=" + ifaceType);
        }
        synchronized (mLock) {
            IfaceCreationData bestIfaceCreationProposal = null;
            for (WifiChipInfo chipInfo: chipInfos) {
                for (IWifiChip.ChipMode chipMode: chipInfo.availableModes) {
                    for (IWifiChip.ChipIfaceCombination chipIfaceCombo : chipMode
                            .availableCombinations) {
                        int[][] expandedIfaceCombos = expandIfaceCombos(chipIfaceCombo);
                        if (DBG) {
                            Log.d(TAG, chipIfaceCombo + " expands to "
                                    + Arrays.deepToString(expandedIfaceCombos));
                        }

                        for (int[] expandedIfaceCombo: expandedIfaceCombos) {
                            IfaceCreationData currentProposal = canIfaceComboSupportRequest(
                                    chipInfo, chipMode, expandedIfaceCombo, ifaceType);
                            if (compareIfaceCreationData(currentProposal,
                                    bestIfaceCreationProposal)) {
                                if (DBG) Log.d(TAG, "new proposal accepted");
                                bestIfaceCreationProposal = currentProposal;
                            }
                        }
                    }
                }
            }

            if (bestIfaceCreationProposal != null) {
                IWifiIface iface = executeChipReconfiguration(bestIfaceCreationProposal, ifaceType);
                if (iface != null) {
                    InterfaceCacheEntry cacheEntry = new InterfaceCacheEntry();

                    cacheEntry.chip = bestIfaceCreationProposal.chipInfo.chip;
                    cacheEntry.chipId = bestIfaceCreationProposal.chipInfo.chipId;
                    cacheEntry.name = getName(iface);
                    cacheEntry.type = ifaceType;
                    if (destroyedListener != null) {
                        cacheEntry.destroyedListeners.add(
                                new InterfaceDestroyedListenerProxy(destroyedListener,
                                        looper == null ? Looper.myLooper() : looper));
                    }

                    if (DBG) Log.d(TAG, "createIfaceIfPossible: added cacheEntry=" + cacheEntry);
                    mInterfaceInfoCache.put(cacheEntry.name, cacheEntry);
                    return iface;
                }
            }
        }

        return null;
    }

    // similar to createIfaceIfPossible - but simpler code: not looking for best option just
    // for any option (so terminates on first one).
    private boolean isItPossibleToCreateIface(WifiChipInfo[] chipInfos, int ifaceType) {
        if (DBG) {
            Log.d(TAG, "isItPossibleToCreateIface: chipInfos=" + Arrays.deepToString(chipInfos)
                    + ", ifaceType=" + ifaceType);
        }

        for (WifiChipInfo chipInfo: chipInfos) {
            for (IWifiChip.ChipMode chipMode: chipInfo.availableModes) {
                for (IWifiChip.ChipIfaceCombination chipIfaceCombo : chipMode
                        .availableCombinations) {
                    int[][] expandedIfaceCombos = expandIfaceCombos(chipIfaceCombo);
                    if (DBG) {
                        Log.d(TAG, chipIfaceCombo + " expands to "
                                + Arrays.deepToString(expandedIfaceCombos));
                    }

                    for (int[] expandedIfaceCombo: expandedIfaceCombos) {
                        if (canIfaceComboSupportRequest(chipInfo, chipMode, expandedIfaceCombo,
                                ifaceType) != null) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Expands (or provides an alternative representation) of the ChipIfaceCombination as all
     * possible combinations of interface.
     *
     * Returns [# of combinations][4 (IfaceType)]
     *
     * Note: there could be duplicates - allow (inefficient but ...).
     * TODO: optimize by using a Set as opposed to a []: will remove duplicates. Will need to
     * provide correct hashes.
     */
    private int[][] expandIfaceCombos(IWifiChip.ChipIfaceCombination chipIfaceCombo) {
        int numOfCombos = 1;
        for (IWifiChip.ChipIfaceCombinationLimit limit: chipIfaceCombo.limits) {
            for (int i = 0; i < limit.maxIfaces; ++i) {
                numOfCombos *= limit.types.size();
            }
        }

        int[][] expandedIfaceCombos = new int[numOfCombos][IFACE_TYPES_BY_PRIORITY.length];

        int span = numOfCombos; // span of an individual type (or sub-tree size)
        for (IWifiChip.ChipIfaceCombinationLimit limit: chipIfaceCombo.limits) {
            for (int i = 0; i < limit.maxIfaces; ++i) {
                span /= limit.types.size();
                for (int k = 0; k < numOfCombos; ++k) {
                    expandedIfaceCombos[k][limit.types.get((k / span) % limit.types.size())]++;
                }
            }
        }

        return expandedIfaceCombos;
    }

    private class IfaceCreationData {
        public WifiChipInfo chipInfo;
        public int chipModeId;
        public List<WifiIfaceInfo> interfacesToBeRemovedFirst;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{chipInfo=").append(chipInfo).append(", chipModeId=").append(chipModeId)
                    .append(", interfacesToBeRemovedFirst=").append(interfacesToBeRemovedFirst)
                    .append(")");
            return sb.toString();
        }
    }

    /**
     * Checks whether the input chip-iface-combo can support the requested interface type: if not
     * then returns null, if yes then returns information containing the list of interfaces which
     * would have to be removed first before the requested interface can be created.
     *
     * Note: the list of interfaces to be removed is EMPTY if a chip mode change is required - in
     * that case ALL the interfaces on the current chip have to be removed first.
     *
     * Response determined based on:
     * - Mode configuration: i.e. could the mode support the interface type in principle
     * - Priority information: i.e. are we 'allowed' to remove interfaces in order to create the
     *   requested interface
     */
    private IfaceCreationData canIfaceComboSupportRequest(WifiChipInfo chipInfo,
            IWifiChip.ChipMode chipMode, int[] chipIfaceCombo, int ifaceType) {
        if (DBG) {
            Log.d(TAG, "canIfaceComboSupportRequest: chipInfo=" + chipInfo + ", chipMode="
                    + chipMode + ", chipIfaceCombo=" + chipIfaceCombo + ", ifaceType=" + ifaceType);
        }

        // short-circuit: does the chipIfaceCombo even support the requested type?
        if (chipIfaceCombo[ifaceType] == 0) {
            if (DBG) Log.d(TAG, "Requested type not supported by combo");
            return null;
        }

        boolean isChipModeChangeProposed =
                chipInfo.currentModeIdValid && chipInfo.currentModeId != chipMode.id;

        // short-circuit: can't change chip-mode if an existing interface on this chip has a higher
        // priority than the requested interface
        if (isChipModeChangeProposed) {
            for (int type: IFACE_TYPES_BY_PRIORITY) {
                if (chipInfo.ifaces[type].length != 0) {
                    if (!allowedToDeleteIfaceTypeForRequestedType(type, ifaceType)) {
                        if (DBG) {
                            Log.d(TAG, "Couldn't delete existing type " + type
                                    + " interfaces for requested type");
                        }
                        return null;
                    }
                }
            }

            // but if priority allows the mode change then we're good to go
            IfaceCreationData ifaceCreationData = new IfaceCreationData();
            ifaceCreationData.chipInfo = chipInfo;
            ifaceCreationData.chipModeId = chipMode.id;

            return ifaceCreationData;
        }

        // possibly supported
        List<WifiIfaceInfo> interfacesToBeRemovedFirst = new ArrayList<>();

        for (int type: IFACE_TYPES_BY_PRIORITY) {
            int tooManyInterfaces = chipInfo.ifaces[type].length - chipIfaceCombo[type];

            // need to count the requested interface as well
            if (type == ifaceType) {
                tooManyInterfaces += 1;
            }

            if (tooManyInterfaces > 0) { // may need to delete some
                if (!allowedToDeleteIfaceTypeForRequestedType(type, ifaceType)) {
                    if (DBG) {
                        Log.d(TAG, "Would need to delete some higher priority interfaces");
                    }
                    return null;
                }

                // arbitrarily pick the first interfaces to delete
                for (int i = 0; i < tooManyInterfaces; ++i) {
                    interfacesToBeRemovedFirst.add(chipInfo.ifaces[type][i]);
                }
            }
        }

        IfaceCreationData ifaceCreationData = new IfaceCreationData();
        ifaceCreationData.chipInfo = chipInfo;
        ifaceCreationData.chipModeId = chipMode.id;
        ifaceCreationData.interfacesToBeRemovedFirst = interfacesToBeRemovedFirst;

        return ifaceCreationData;
    }

    /**
     * Compares two options to create an interface and determines which is the 'best'. Returns
     * true if proposal 1 (val1) is better, other false.
     *
     * Note: both proposals are 'acceptable' bases on priority criteria.
     *
     * Criteria:
     * - Proposal is better if it means removing fewer high priority interfaces
     */
    private boolean compareIfaceCreationData(IfaceCreationData val1, IfaceCreationData val2) {
        if (DBG) Log.d(TAG, "compareIfaceCreationData: val1=" + val1 + ", val2=" + val2);

        // deal with trivial case of one or the other being null
        if (val1 == null) {
            return false;
        } else if (val2 == null) {
            return true;
        }

        for (int type: IFACE_TYPES_BY_PRIORITY) {
            // # of interfaces to be deleted: the list or all interfaces of the type if mode change
            int numIfacesToDelete1 = 0;
            if (val1.chipInfo.currentModeIdValid
                    && val1.chipInfo.currentModeId != val1.chipModeId) {
                numIfacesToDelete1 = val1.chipInfo.ifaces[type].length;
            } else {
                numIfacesToDelete1 = val1.interfacesToBeRemovedFirst.size();
            }

            int numIfacesToDelete2 = 0;
            if (val2.chipInfo.currentModeIdValid
                    && val2.chipInfo.currentModeId != val2.chipModeId) {
                numIfacesToDelete2 = val2.chipInfo.ifaces[type].length;
            } else {
                numIfacesToDelete2 = val2.interfacesToBeRemovedFirst.size();
            }

            if (numIfacesToDelete1 < numIfacesToDelete2) {
                if (DBG) {
                    Log.d(TAG, "decision based on type=" + type + ": " + numIfacesToDelete1
                            + " < " + numIfacesToDelete2);
                }
                return true;
            }
        }

        // arbitrary - flip a coin
        if (DBG) Log.d(TAG, "proposals identical - flip a coin");
        return false;
    }

    /**
     * Returns true if we're allowed to delete the existing interface type for the requested
     * interface type.
     *
     * Rules:
     * 1. Request for AP or STA will destroy any other interface (except see #4)
     * 2. Request for P2P will destroy NAN-only
     * 3. Request for NAN will not destroy any interface
     * --
     * 4. No interface will be destroyed for a requested interface of the same type
     */
    private boolean allowedToDeleteIfaceTypeForRequestedType(int existingIfaceType,
            int requestedIfaceType) {
        // rule 4
        if (existingIfaceType == requestedIfaceType) {
            return false;
        }

        // rule 3
        if (requestedIfaceType == IfaceType.NAN) {
            return false;
        }

        // rule 2
        if (requestedIfaceType == IfaceType.P2P) {
            return existingIfaceType == IfaceType.NAN;
        }

        // rule 1, the requestIfaceType is either AP or STA
        return true;
    }

    /**
     * Performs chip reconfiguration per the input:
     * - Removes the specified interfaces
     * - Reconfigures the chip to the new chip mode (if necessary)
     * - Creates the new interface
     *
     * Returns the newly created interface or a null on any error.
     */
    private IWifiIface executeChipReconfiguration(IfaceCreationData ifaceCreationData,
            int ifaceType) {
        if (DBG) {
            Log.d(TAG, "executeChipReconfiguration: ifaceCreationData=" + ifaceCreationData
                    + ", ifaceType=" + ifaceType);
        }
        synchronized (mLock) {
            try {
                // is this a mode change?
                boolean isModeConfigNeeded = !ifaceCreationData.chipInfo.currentModeIdValid
                        || ifaceCreationData.chipInfo.currentModeId != ifaceCreationData.chipModeId;
                if (DBG) Log.d(TAG, "isModeConfigNeeded=" + isModeConfigNeeded);

                // first delete interfaces/change modes
                if (isModeConfigNeeded) {
                    // remove all interfaces pre mode-change
                    // TODO: is this necessary? note that even if we don't want to explicitly
                    // remove the interfaces we do need to call the onDeleted callbacks - which
                    // this does
                    for (WifiIfaceInfo[] ifaceInfos: ifaceCreationData.chipInfo.ifaces) {
                        for (WifiIfaceInfo ifaceInfo: ifaceInfos) {
                            removeIfaceInternal(ifaceInfo.iface); // ignore return value
                        }
                    }

                    WifiStatus status = ifaceCreationData.chipInfo.chip.configureChip(
                            ifaceCreationData.chipModeId);
                    if (status.code != WifiStatusCode.SUCCESS) {
                        Log.e(TAG, "executeChipReconfiguration: configureChip error: "
                                + statusString(status));
                        return null;
                    }
                } else {
                    // remove all interfaces on the delete list
                    for (WifiIfaceInfo ifaceInfo: ifaceCreationData.interfacesToBeRemovedFirst) {
                        removeIfaceInternal(ifaceInfo.iface); // ignore return value
                    }
                }

                // create new interface
                Mutable<WifiStatus> statusResp = new Mutable<>();
                Mutable<IWifiIface> ifaceResp = new Mutable<>();
                switch (ifaceType) {
                    case IfaceType.STA:
                        ifaceCreationData.chipInfo.chip.createStaIface(
                                (WifiStatus status, IWifiStaIface iface) -> {
                                    statusResp.value = status;
                                    ifaceResp.value = iface;
                                });
                        break;
                    case IfaceType.AP:
                        ifaceCreationData.chipInfo.chip.createApIface(
                                (WifiStatus status, IWifiApIface iface) -> {
                                    statusResp.value = status;
                                    ifaceResp.value = iface;
                                });
                        break;
                    case IfaceType.P2P:
                        ifaceCreationData.chipInfo.chip.createP2pIface(
                                (WifiStatus status, IWifiP2pIface iface) -> {
                                    statusResp.value = status;
                                    ifaceResp.value = iface;
                                });
                        break;
                    case IfaceType.NAN:
                        ifaceCreationData.chipInfo.chip.createNanIface(
                                (WifiStatus status, IWifiNanIface iface) -> {
                                    statusResp.value = status;
                                    ifaceResp.value = iface;
                                });
                        break;
                }

                if (statusResp.value.code != WifiStatusCode.SUCCESS) {
                    Log.e(TAG, "executeChipReconfiguration: failed to create interface ifaceType="
                            + ifaceType + ": " + statusString(statusResp.value));
                    return null;
                }

                return ifaceResp.value;
            } catch (RemoteException e) {
                Log.e(TAG, "executeChipReconfiguration exception: " + e);
                return null;
            }
        }
    }

    private boolean removeIfaceInternal(IWifiIface iface) {
        String name = getName(iface);
        if (DBG) Log.d(TAG, "removeIfaceInternal: iface(name)=" + name);

        synchronized (mLock) {
            if (mWifi == null) {
                Log.e(TAG, "removeIfaceInternal: null IWifi -- iface(name)=" + name);
                return false;
            }

            IWifiChip chip = getChip(iface);
            if (chip == null) {
                Log.e(TAG, "removeIfaceInternal: null IWifiChip -- iface(name)=" + name);
                return false;
            }

            if (name == null) {
                Log.e(TAG, "removeIfaceInternal: can't get name");
                return false;
            }

            int type = getType(iface);
            if (type == -1) {
                Log.e(TAG, "removeIfaceInternal: can't get type -- iface(name)=" + name);
                return false;
            }

            WifiStatus status = null;
            try {
                switch (type) {
                    case IfaceType.STA:
                        status = chip.removeStaIface(name);
                        break;
                    case IfaceType.AP:
                        status = chip.removeApIface(name);
                        break;
                    case IfaceType.P2P:
                        status = chip.removeP2pIface(name);
                        break;
                    case IfaceType.NAN:
                        status = chip.removeNanIface(name);
                        break;
                    default:
                        Log.wtf(TAG, "removeIfaceInternal: invalid type=" + type);
                        return false;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "IWifiChip.removeXxxIface exception: " + e);
            }

            // dispatch listeners no matter what status
            dispatchDestroyedListeners(name);

            if (status != null && status.code == WifiStatusCode.SUCCESS) {
                return true;
            } else {
                Log.e(TAG, "IWifiChip.removeXxxIface failed: " + statusString(status));
                return false;
            }
        }
    }

    // dispatch all available for request listeners of the specified type AND clean-out the list:
    // listeners are called once at most!
    private boolean dispatchAvailableForRequestListeners() {
        if (DBG) Log.d(TAG, "dispatchAvailableForRequestListeners");

        synchronized (mLock) {
            WifiChipInfo[] chipInfos = getAllChipInfo();
            if (chipInfos == null) {
                Log.e(TAG, "dispatchAvailableForRequestListeners: no chip info found");
                stopWifi(); // major error: shutting down
                return false;
            }
            if (DBG) {
                Log.d(TAG, "dispatchAvailableForRequestListeners: chipInfos="
                        + Arrays.deepToString(chipInfos));
            }

            for (int ifaceType : IFACE_TYPES_BY_PRIORITY) {
                dispatchAvailableForRequestListenersForType(ifaceType, chipInfos);
            }
        }

        return true;
    }

    private void dispatchAvailableForRequestListenersForType(int ifaceType,
            WifiChipInfo[] chipInfos) {
        if (DBG) Log.d(TAG, "dispatchAvailableForRequestListenersForType: ifaceType=" + ifaceType);

        Set<InterfaceAvailableForRequestListenerProxy> listeners =
                mInterfaceAvailableForRequestListeners.get(ifaceType);

        if (listeners.size() == 0) {
            return;
        }

        if (!isItPossibleToCreateIface(chipInfos, ifaceType)) {
            if (DBG) Log.d(TAG, "Creating interface type isn't possible: ifaceType=" + ifaceType);
            return;
        }

        if (DBG) Log.d(TAG, "It is possible to create the interface type: ifaceType=" + ifaceType);
        for (InterfaceAvailableForRequestListenerProxy listener : listeners) {
            listener.trigger();
        }
    }

    // dispatch all destroyed listeners registered for the specified interface AND remove the
    // cache entry
    private void dispatchDestroyedListeners(String name) {
        if (DBG) Log.d(TAG, "dispatchDestroyedListeners: iface(name)=" + name);

        synchronized (mLock) {
            InterfaceCacheEntry entry = mInterfaceInfoCache.get(name);
            if (entry == null) {
                Log.e(TAG, "dispatchDestroyedListeners: no cache entry for iface(name)=" + name);
                return;
            }

            for (InterfaceDestroyedListenerProxy listener : entry.destroyedListeners) {
                listener.trigger();
            }
            entry.destroyedListeners.clear(); // for insurance (though cache entry is removed)
            mInterfaceInfoCache.remove(name);
        }
    }

    // dispatch all destroyed listeners registered to all interfaces
    private void dispatchAllDestroyedListeners() {
        if (DBG) Log.d(TAG, "dispatchAllDestroyedListeners");

        synchronized (mLock) {
            Iterator<Map.Entry<String, InterfaceCacheEntry>> it =
                    mInterfaceInfoCache.entrySet().iterator();
            while (it.hasNext()) {
                InterfaceCacheEntry entry = it.next().getValue();
                for (InterfaceDestroyedListenerProxy listener : entry.destroyedListeners) {
                    listener.trigger();
                }
                entry.destroyedListeners.clear(); // for insurance (though cache entry is removed)
                it.remove();
            }
        }
    }

    private abstract class ListenerProxy<LISTENER>  {
        private static final int LISTENER_TRIGGERED = 0;

        protected LISTENER mListener;
        private Handler mHandler;

        // override equals & hash to make sure that the container HashSet is unique with respect to
        // the contained listener
        @Override
        public boolean equals(Object obj) {
            return mListener == ((ListenerProxy<LISTENER>) obj).mListener;
        }

        @Override
        public int hashCode() {
            return mListener.hashCode();
        }

        void trigger() {
            mHandler.sendMessage(mHandler.obtainMessage(LISTENER_TRIGGERED));
        }

        protected abstract void action();

        ListenerProxy(LISTENER listener, Looper looper, String tag) {
            mListener = listener;
            mHandler = new Handler(looper) {
                @Override
                public void handleMessage(Message msg) {
                    if (DBG) {
                        Log.d(tag, "ListenerProxy.handleMessage: what=" + msg.what);
                    }
                    switch (msg.what) {
                        case LISTENER_TRIGGERED:
                            action();
                            break;
                        default:
                            Log.e(tag, "ListenerProxy.handleMessage: unknown message what="
                                    + msg.what);
                    }
                }
            };
        }
    }

    private class InterfaceDestroyedListenerProxy extends
            ListenerProxy<InterfaceDestroyedListener> {
        InterfaceDestroyedListenerProxy(InterfaceDestroyedListener destroyedListener,
                Looper looper) {
            super(destroyedListener, looper, "InterfaceDestroyedListenerProxy");
        }

        @Override
        protected void action() {
            mListener.onDestroyed();
        }
    }

    private class InterfaceAvailableForRequestListenerProxy extends
            ListenerProxy<InterfaceAvailableForRequestListener> {
        InterfaceAvailableForRequestListenerProxy(
                InterfaceAvailableForRequestListener destroyedListener, Looper looper) {
            super(destroyedListener, looper, "InterfaceAvailableForRequestListenerProxy");
        }

        @Override
        protected void action() {
            mListener.onAvailableForRequest();
        }
    }

    // general utilities

    private static String statusString(WifiStatus status) {
        if (status == null) {
            return "status=null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(status.code).append(" (").append(status.description).append(")");
        return sb.toString();
    }

    // Will return -1 for invalid results! Otherwise will return one of the 4 valid values.
    private static int getType(IWifiIface iface) {
        MutableInt typeResp = new MutableInt(-1);
        try {
            iface.getType((WifiStatus status, int type) -> {
                if (status.code == WifiStatusCode.SUCCESS) {
                    typeResp.value = type;
                } else {
                    Log.e(TAG, "Error on getType: " + statusString(status));
                }
            });
        } catch (RemoteException e) {
            Log.e(TAG, "Exception on getType: " + e);
        }

        return typeResp.value;
    }

    private static class Mutable<E> {
        public E value;

        Mutable() {
            value = null;
        }

        Mutable(E value) {
            this.value = value;
        }
    }

    /**
     * Dump the internal state of the class.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("HalDeviceManager:");
        pw.println("  mServiceManager: " + mServiceManager);
        pw.println("  mWifi: " + mWifi);
        pw.println("  mManagerStatusListeners: " + mManagerStatusListeners);
        pw.println("  mInterfaceAvailableForRequestListeners: "
                + mInterfaceAvailableForRequestListeners);
        pw.println("  mInterfaceInfoCache: " + mInterfaceInfoCache);
    }
}
