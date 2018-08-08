// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.cluster;

import com.android.tradefed.device.DeviceAllocationState;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A class to encapsulate cluster device info to be uploaded.
 */
public class ClusterDeviceInfo {
    private String mSerialId;
    private String mRunTarget;
    private String mBuildId;
    private String mProduct;
    private String mProductVariant;
    private String mSdkVersion;
    private String mBatteryLevel;
    private String mMacAddress;
    private String mSimState;
    private String mSimOperator;
    private DeviceAllocationState mState;
    private String mGroupName;

    private ClusterDeviceInfo() {
    }

    public String getSerialId() {
        return mSerialId;
    }

    public String getRunTarget() {
        return mRunTarget;
    }

    public String getBuildId() {
        return mBuildId;
    }

    public String getProduct() {
        return mProduct;
    }

    public String getProductVariant() {
        return mProductVariant;
    }

    public String getSdkVersion() {
        return mSdkVersion;
    }

    public String getBatteryLevel() {
        return mBatteryLevel;
    }

    public String getMacAddress() {
        return mMacAddress;
    }

    public String getSimState() {
        return mSimState;
    }

    public String getSimOperator() {
        return mSimOperator;
    }

    public DeviceAllocationState getDeviceState() {
        return mState;
    }

    public String getGroupName() {
        return mGroupName;
    }

    public static class Builder {
        private String mSerialId;
        private String mRunTarget;
        private String mBuildId;
        private String mProduct;
        private String mProductVariant;
        private String mSdkVersion;
        private DeviceAllocationState mState;
        private String mBatteryLevel;
        private String mMacAddress;
        private String mSimState;
        private String mSimOperator;
        private String mGroupName;

        public Builder() {
        }

        public Builder setSerialId(final String serialId) {
            mSerialId = serialId;
            return this;
        }

        public Builder setRunTarget(final String runTarget) {
            mRunTarget = runTarget;
            return this;
        }

        public Builder setBuildId(final String buildId) {
            mBuildId = buildId;
            return this;
        }

        public Builder setProduct(final String product) {
            mProduct = product;
            return this;
        }

        public Builder setProductVariant(final String productVariant) {
            mProductVariant = productVariant;
            return this;
        }

        public Builder setSdkVersion(final String sdkVersion) {
            mSdkVersion = sdkVersion;
            return this;
        }

        public Builder setState(final DeviceAllocationState state) {
            mState = state;
            return this;
        }

        public Builder setBatteryLevel(final String batteryLevel) {
            mBatteryLevel = batteryLevel;
            return this;
        }

        public Builder setMacAddress(final String macAddress) {
            mMacAddress = macAddress;
            return this;
        }

        public Builder setSimState(final String simState) {
            mSimState = simState;
            return this;
        }

        public Builder setSimOperator(final String simOperator) {
            mSimOperator = simOperator;
            return this;
        }

        public Builder setGroupName(final String groupName) {
            mGroupName = groupName;
            return this;
        }

        public ClusterDeviceInfo build() {
            final ClusterDeviceInfo deviceInfo = new ClusterDeviceInfo();
            deviceInfo.mSerialId = mSerialId;
            deviceInfo.mRunTarget = mRunTarget;
            deviceInfo.mBuildId = mBuildId;
            deviceInfo.mProduct = mProduct;
            deviceInfo.mProductVariant = mProductVariant;
            deviceInfo.mSdkVersion = mSdkVersion;
            deviceInfo.mState = mState;
            deviceInfo.mBatteryLevel = mBatteryLevel;
            deviceInfo.mMacAddress = mMacAddress;
            deviceInfo.mSimState = mSimState;
            deviceInfo.mSimOperator = mSimOperator;
            deviceInfo.mGroupName = mGroupName;
            return deviceInfo;
        }
    }

    /**
     * Generates the JSON Object for this device info.
     * @return JSONObject equivalent of this device info.
     * @throws JSONException
     */
    public JSONObject toJSON() throws JSONException {
        final JSONObject json = new JSONObject();
        json.put("device_serial", this.getSerialId());
        json.put("run_target", this.getRunTarget());
        json.put("build_id", this.getBuildId());
        json.put("product", this.getProduct());
        json.put("product_variant", this.getProductVariant());
        json.put("sdk_version", this.getSdkVersion());
        json.put("battery_level", this.getBatteryLevel());
        json.put("mac_address", this.getMacAddress());
        json.put("sim_state", this.getSimState());
        json.put("sim_operator", this.getSimOperator());
        json.put("state", this.getDeviceState());
        json.put("group_name", this.getGroupName());
        return json;
    }
}
