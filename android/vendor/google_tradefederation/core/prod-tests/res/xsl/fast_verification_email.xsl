<?xml version="1.0"?>
<!-- Copyright 2012 Google Inc. All Rights Reserved -->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  version="1.0">
  <!--
  This file has a lot of repeated css styles, this is due to Gmail not supporting <style> tag.
  -->
  <xsl:template match="verification">
    <xsl:param name="status" select="@status" />
    <tr>
      <td style="font-size: 9pt; width: 1ex;"/>
      <xsl:choose>
        <xsl:when test="$status='VERIFIED'">
          <td style="font-size: 9pt; font-weight: bold; text-align: center; background-color: #00FF00; color: black; width: 14ex;">VERIFIED</td>
        </xsl:when>
        <xsl:when test="$status='UNVERIFIED'">
          <td style="font-size: 9pt; font-weight: bold; text-align: center; background-color: #FFFF00; color: black; width: 14ex;">UNVERIFIED</td>
        </xsl:when>
        <xsl:when test="$status='FAILED'">
          <td style="font-size: 9pt; font-weight: bold; text-align: center; background-color: #FF0000; color: black; width: 14ex;">FAILED</td>
        </xsl:when>
        <xsl:otherwise>
          <td style="font-size: 9pt; font-weight: bold; text-align: center; background-color: #FF0000; color: black; width: 14ex;">WTF</td>
        </xsl:otherwise>
      </xsl:choose>
      <td style="font-size: 9pt; width: 1ex;"/>
      <td style="font-size: 11pt;">
        <xsl:value-of select="@description"/>
      </td>
    </tr>
    <tr>
      <td style="font-size: 9pt; width: 1ex;"/>
      <td style="font-size: 8pt; font-style: italic; color: #AAAAAA; padding-left: 2ex;" colspan="3">
        By:
        <xsl:value-of select="@verifier"/>
      </td>
    </tr>
    <tr>
      <td style="font-size: 9pt; width: 1ex;"/>
      <td style="font-size: 9pt; padding-left: 2ex;" colspan="3">
        <pre>
Notes:
<xsl:value-of select="footnote"/>
        </pre>
      </td>
    </tr>
  </xsl:template>
  <xsl:template match="/">
    <html>
      <head>
        <title>Geppetto FAST Automation Report</title>
      </head>
      <body style="font-family: sans-serif,arial; padding: 1em;">
        <xsl:for-each select="invocation/manifest">
          <span style="font-size: 14pt;">
            Manifest:
            <xsl:value-of select="@description" />
          </span>
          <xsl:for-each select="usecase">
            <table style="border: 1px solid #CCCCCC; margin: 1ex; padding: 1ex; width: 100%;">
              <tr>
                <td colspan="4" style="font-size: 12pt; line-height: 2em;">
                  Use Case:
                  <xsl:value-of select="@description" />
                </td>
              </tr>
              <xsl:apply-templates select="verification" />
            </table>
          </xsl:for-each>
        </xsl:for-each>
        <pre style="font-size: 8pt;">[$BUILD_INFO]</pre>
        <div style="font-size: 12pt; line-height: 2em;">Processed Logcat Summary</div>
        <div style="font-size: 10pt; line-height: 1.5em;">ANRs</div>
        <pre style="font-size: 8pt;">[$LOGCAT_ANR]</pre>
        <div style="font-size: 10pt; line-height: 1.5em;">Java Crashes</div>
        <pre style="font-size: 8pt;">[$LOGCAT_JC]</pre>
        <div style="font-size: 10pt; line-height: 1.5em;">Native Crashes</div>
        <pre style="font-size: 8pt;">[$LOGCAT_NC]</pre>
      </body>
    </html>
  </xsl:template>
</xsl:stylesheet>
