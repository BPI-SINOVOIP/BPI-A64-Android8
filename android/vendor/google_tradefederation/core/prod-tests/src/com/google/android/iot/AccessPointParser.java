// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.iot;

import com.android.tradefed.log.LogUtil.CLog;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.InputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Parse the access points information and store it in an array
 *
 */
public class AccessPointParser extends DefaultHandler {
    private AccessPointInfo[][] mApInfo =
            new AccessPointInfo[IotUtil.NPS_SIZE][IotUtil.MAX_NPS_AP_NUMBER];
    private int mTotalNumber = 0;
    private InputSource mXmlSource = null;
    private AccessPointInfo mAccessPoint = null;
    private boolean mBrand = false;
    private boolean mModel = false;
    private boolean mFirmware = false;
    private boolean mFrequencyBand = false;
    private boolean mSecurity = false;
    private boolean mIp = false;
    private boolean mNpsId = false;
    private boolean mNpsPlugId = false;

    @Override
    public void startElement(String uri, String localName, String tagName,
            Attributes attributes) throws SAXException {
        if (tagName.equalsIgnoreCase("accesspoint")) {
            mAccessPoint = new AccessPointInfo(null, null);
        }
        if (tagName.equalsIgnoreCase("brand")) {
            mBrand = true;
        }
        if (tagName.equalsIgnoreCase("model")) {
            mModel = true;
        }
        if (tagName.equalsIgnoreCase("firmware")) {
            mFirmware = true;
        }
        if (tagName.equalsIgnoreCase("frequencyband")) {
            mFrequencyBand = true;
        }
        if (tagName.equalsIgnoreCase("security")) {
            mSecurity = true;
        }
        if (tagName.equalsIgnoreCase("ip")) {
            mIp = true;
        }
        if (tagName.equalsIgnoreCase("npsid")) {
            mNpsId = true;
        }
        if (tagName.equalsIgnoreCase("npsplugid")) {
            mNpsPlugId = true;
        }
    }

    @Override
    public void endElement(String uri, String localName, String tagName) throws SAXException {
        if (tagName.equalsIgnoreCase("accesspoint")) {
            if (mAccessPoint.isValid()) {
                addApToStorage();
            }
        }
    }

    private void addApToStorage() {
        int index = IotUtil.NPS.valueOf(mAccessPoint.getNpsId()).ordinal();
        if (index < 0) {
            CLog.v("Invalid NPS id");
            return;
        }
        for (int i = 0; i < IotUtil.MAX_NPS_AP_NUMBER; i++) {
            // search for the array and find an empty spot to store the current AP
            if (mApInfo[index][i] == null) {
                mApInfo[index][i] = mAccessPoint;
                mTotalNumber++;
                break;
            }
        }
    }

    @Override
    public void characters(char ch[], int start, int length) throws SAXException {
        String content = new String(ch, start, length);
        if (mBrand) {
            mAccessPoint.setBrand(content);
            mBrand = false;
        }
        if (mModel) {
            mAccessPoint.setModel(content);
            mModel = false;
        }
        if (mFirmware) {
            mAccessPoint.setFirmware(content);
            mFirmware = false;
        }
        if (mFrequencyBand) {
            if (!mAccessPoint.addFrequencyBand(content)) {
                throw (new SAXException("not a valid frequency band"));
            }
            mFrequencyBand = false;
        }
        if (mSecurity) {
            mAccessPoint.addSecurityType(content);
            mSecurity = false;
        }
        if (mIp) {
            if (!validateIpAddress(content)) {
                throw new SAXException("ip address is not valid");
            } else {
                mAccessPoint.setIpAddress(content);
            }
            mIp = false;
        }
        if (mNpsId) {
            mAccessPoint.setNpsId(content);
            mNpsId = false;
        }
        if (mNpsPlugId) {
            mAccessPoint.setNpsPlugId(content);
            mNpsPlugId = false;
        }
    }

    private boolean validateIpAddress(String ipAddress) {
        String[] tokens = ipAddress.split("\\.");
        if (tokens.length != 4) {
            return false;
        }
        for (String token: tokens) {
            int i = Integer.parseInt(token);
            if (i < 0 || i > 255) {
                return false;
            }
        }
        return true;
    }

    public AccessPointParser(InputStream input) {
        if (input != null) {
            mXmlSource = new InputSource(input);
        }
    }

    public void setXmlInputSource(InputStream input) {
        if (input != null) {
            mXmlSource = new InputSource(input);
        }
    }

    public void parse() throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = factory.newSAXParser();
        saxParser.parse(mXmlSource, this);
    }

    public AccessPointInfo[][] getAccessPointInfo() {
        return mApInfo;
    }

    public int getTotalNumberAps() {
        return mTotalNumber;
    }
}

