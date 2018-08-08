// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.iot;

import com.android.tradefed.log.LogUtil.CLog;

import java.util.ArrayList;
import java.util.List;

/**
 * Store access point information
 */
public class AccessPointInfo {
    public static final int NUMBER_SECURITY = 5;
    public static final String WIFI_FREQUENCY_BAND_2GHZ = "2.4";
    public static final String WIFI_FREQUENCY_BAND_5GHZ = "5.0";
    public static final String WIFI_DUAL_BAND = "dual";
    private String mBrand = null;
    private String mModel = null;
    private String mFirmware = null;
    private List<String> mFrequencyBand = null;
    private List<String> mSecurityTypes = null;
    private String mIpAddress = null;
    private String mNpsId = null;
    private String mNpsPlugId = null;

    public AccessPointInfo(String brandName, String modelName) {
        mBrand = brandName;
        mModel = modelName;
        mFirmware = null;
        mFrequencyBand = new ArrayList<String>(2);
        mSecurityTypes = new ArrayList<String>(NUMBER_SECURITY);
        mIpAddress = null;
        mNpsId = null;
        mNpsPlugId = null;
    }

    public void setBrand(String brandName) {
        mBrand = brandName;
    }

    public void setModel(String modelName) {
        mModel = modelName;
    }

    public void setFirmware(String firmware) {
        mFirmware = firmware;
    }

    public boolean addFrequencyBand(String band) {
        CLog.d("add frequency band: " + band);
        if ((WIFI_FREQUENCY_BAND_2GHZ.compareTo(band) == 0) ||
                (WIFI_FREQUENCY_BAND_5GHZ.compareTo(band) == 0)) {
            mFrequencyBand.add(band);
            return true;
        } else if ((WIFI_DUAL_BAND.compareToIgnoreCase(band) == 0)) {
            mFrequencyBand.add(WIFI_FREQUENCY_BAND_2GHZ);
            mFrequencyBand.add(WIFI_FREQUENCY_BAND_5GHZ);
            return true;
        } else {
            CLog.d("not a valid frequency band");
            return false;
        }
    }

    public void addSecurityType(String securityType) {
        mSecurityTypes.add(securityType);
    }

    public void setIpAddress(String ipAddress) {
        mIpAddress = ipAddress;
    }

    public void setNpsId(String id) {
        mNpsId = id;
    }

    public void setNpsPlugId(String plugId) {
        mNpsPlugId = plugId;
    }

    public int getNumberSecurityTypes() {
        return mSecurityTypes.size();
    }

    public boolean isDualBand() {
        return mFrequencyBand.contains(WIFI_FREQUENCY_BAND_2GHZ) &&
                mFrequencyBand.contains(WIFI_FREQUENCY_BAND_5GHZ);
    }

    /* Verify whether AP information is valid */
    public boolean isValid() {
        boolean validBand = mFrequencyBand.contains(WIFI_FREQUENCY_BAND_2GHZ) ||
                mFrequencyBand.contains(WIFI_FREQUENCY_BAND_5GHZ);
        return (validBand && (mBrand != null) && (mModel != null) && (mFrequencyBand != null) &&
                (mSecurityTypes != null) && (mIpAddress != null));
    }

    public String getBrand() {
        return mBrand;
    }
    public String getModel() {
        return mModel;
    }

    public String getBrandModel() {
        return String.format("%s_%s", mBrand, mModel);
    }

    public String getFirmware() {
        return mFirmware;
    }

    public String getApKey() {
        return String.format("%s_%s", getBrandModel(), mFirmware);
    }

    public List<String> getFrequencyBand() {
        return mFrequencyBand;
    }

    public List<String> getSecurityTypes() {
        return mSecurityTypes;
    }

    public String getIpAddress() {
        return mIpAddress;
    }

    public String getNpsId() {
        return mNpsId;
    }

    public String getNpsPlugId() {
        return mNpsPlugId;
    }
}
