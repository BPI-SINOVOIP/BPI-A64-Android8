// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import static org.mockito.Mockito.doReturn;

import com.google.android.tradefed.result.BlackboxPostUtil.TestResultsBuilder;
import com.google.common.collect.ImmutableMap;

import junit.framework.TestCase;

import org.mockito.Mockito;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.IOException;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/** Unit tests for {@link JacocoXmlHandler}. */
public class JacocoXmlHandlerTest extends TestCase {

    private static final String FAKE_URL = "www.example.com/coverage-report";

    private TestResultsBuilder mMockBuilder = null;
    private JacocoXmlHandler mHandler = null;

    @Override
    public void setUp() {
        mMockBuilder = Mockito.mock(TestResultsBuilder.class);
        mHandler = new JacocoXmlHandler(mMockBuilder, FAKE_URL);
    }

    public void testParse() throws IOException, ParserConfigurationException, SAXException {
        // Get a SAXParser
        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
        // Instruct the parser to skip dtd validation
        parserFactory.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        parserFactory.setFeature("http://xml.org/sax/features/validation", false);
        SAXParser parser = parserFactory.newSAXParser();

        // Parse the XML
        parser.parse(getClass().getResourceAsStream("/testdata/jacoco_basic_report.xml"), mHandler);

        // Verify that the method coverage data was reported
        Mockito.verify(mMockBuilder).addCoverageResult(
                "com.android.vcard.VCardEntryCounter#VCardEntryCounter()", 1, 1, 1, 1, 2, 2,
                FAKE_URL + "/com.android.vcard/VCardEntryCounter.html");
        Mockito.verify(mMockBuilder).addCoverageResult(
                "com.android.vcard.VCardEntryCounter#getCount()", 1, 1, 1, 1, 2, 2,
                FAKE_URL + "/com.android.vcard/VCardEntryCounter.html");
        Mockito.verify(mMockBuilder).addCoverageResult(
                "com.android.vcard.VCardEntryCounter#onEntryEnded()", 2, 2, 1, 1, 4, 4,
                FAKE_URL + "/com.android.vcard/VCardEntryCounter.html");
        Mockito.verify(mMockBuilder).addCoverageResult(
                "com.android.vcard.VCardEntryCounter#onEntryStarted()", 1, 1, 1, 1, 1, 1,
                FAKE_URL + "/com.android.vcard/VCardEntryCounter.html");
        Mockito.verify(mMockBuilder).addCoverageResult(
                "com.android.vcard.VCardEntryCounter#onVCardEnded()", 1, 1, 1, 1, 1, 1,
                FAKE_URL + "/com.android.vcard/VCardEntryCounter.html");
        Mockito.verify(mMockBuilder).addCoverageResult(
                "com.android.vcard.VCardEntryCounter#onVCardStarted()", 1, 1, 1, 1, 1, 1,
                FAKE_URL + "/com.android.vcard/VCardEntryCounter.html");
        Mockito.verify(mMockBuilder).addCoverageResult(
                "com.android.vcard.VCardEntry$PostalData#getCountry()", 1, 1, 1, 1, 2, 2,
                FAKE_URL + "/com.android.vcard/VCardEntry$PostalData.html");
        Mockito.verify(mMockBuilder).addCoverageResult(
                "com.android.vcard.VCardEntry$PostalData#isEmpty()", 0, 7, 4, 12, 11, 27,
                FAKE_URL + "/com.android.vcard/VCardEntry$PostalData.html");
        Mockito.verify(mMockBuilder).addCoverageResult(
                "com.android.vcard.exception.VCardException#VCardException()", 0, 2, 0, 1, 0, 2,
                FAKE_URL + "/com.android.vcard.exception/VCardException.html");
        Mockito.verify(mMockBuilder).addCoverageResult(
                "com.android.vcard.exception.VCardException#VCardException(java.lang.String)",
                0, 2, 0, 1, 0, 2,
                FAKE_URL + "/com.android.vcard.exception/VCardException.html");
    }

    private Attributes getMockAttributes(Map<String, String> attributes) {
        Attributes ret = Mockito.mock(Attributes.class);
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            doReturn(entry.getValue()).when(ret).getValue(entry.getKey());
        }
        return ret;
    }

    private void startPackage(String path) throws SAXParseException {
        mHandler.startElement(null, null, "package",
                getMockAttributes(ImmutableMap.of("name", path)));
    }

    private void endPackage() {
        mHandler.endElement(null, null, "package");
    }

    private void startClass(String path) throws SAXParseException {
        mHandler.startElement(null, null, "class",
                getMockAttributes(ImmutableMap.of("name", path)));
    }

    private void endClass() {
        mHandler.endElement(null, null, "class");
    }

    private void startMethod(String name, String desc) throws SAXParseException {
        mHandler.startElement(null, null, "method",
                getMockAttributes(ImmutableMap.of("name", name, "desc", desc)));
    }

    private void endMethod() {
        mHandler.endElement(null, null, "method");
    }

    private void reportLineCoverage(int missed, int covered) throws SAXParseException {
        mHandler.startElement(null, null, "counter",
                getMockAttributes(ImmutableMap.of(
                        "type", "LINE",
                        "missed", Integer.toString(missed),
                        "covered", Integer.toString(covered))));
        mHandler.endElement(null, null, "counter");
    }

    private void reportBranchCoverage(int missed, int covered) throws SAXParseException {
        mHandler.startElement(null, null, "counter",
                getMockAttributes(ImmutableMap.of(
                        "type", "BRANCH",
                        "missed", Integer.toString(missed),
                        "covered", Integer.toString(covered))));
        mHandler.endElement(null, null, "counter");
    }

    private void reportInstructionCoverage(int missed, int covered) throws SAXParseException {
        mHandler.startElement(null, null, "counter",
                getMockAttributes(ImmutableMap.of(
                        "type", "INSTRUCTION",
                        "missed", Integer.toString(missed),
                        "covered", Integer.toString(covered))));
        mHandler.endElement(null, null, "counter");
    }

    public void testSingleMethod_completelyCovered() throws SAXParseException {
        // Prepare test data
        int linesCovered = 2;
        int linesMissed = 0;
        int instructionsCovered = 4;
        int instructionsMissed = 0;
        int branchesCovered = 2;
        int branchesMissed = 0;

        // Simulate parsing the XML report
        startPackage("com/android/vcard");
        startClass("com/android/vcard/VCardEntryCounter");
        startMethod("getCount", "()V");
        reportLineCoverage(linesMissed, linesCovered);
        reportBranchCoverage(branchesMissed, branchesCovered);
        reportInstructionCoverage(instructionsMissed, instructionsCovered);
        endMethod();
        endClass();
        endPackage();

        // Verify that the coverage results were added correctly
        Mockito.verify(mMockBuilder).addCoverageResult(
                "com.android.vcard.VCardEntryCounter#getCount()",
                linesMissed, linesCovered + linesMissed,
                branchesMissed, branchesCovered + branchesMissed,
                instructionsMissed, instructionsCovered + instructionsMissed,
                FAKE_URL + "/com.android.vcard/VCardEntryCounter.html");
    }

    public void testSingleMethod_partiallyCovered() throws SAXParseException {
        // Prepare test data
        int linesCovered = 1;
        int linesMissed = 1;
        int instructionsCovered = 2;
        int instructionsMissed = 2;
        int branchesCovered = 1;
        int branchesMissed = 1;

        // Simulate parsing the XML report
        startPackage("com/android/vcard");
        startClass("com/android/vcard/VCardEntryCounter");
        startMethod("getCount", "()V");
        reportLineCoverage(linesMissed, linesCovered);
        reportBranchCoverage(branchesMissed, branchesCovered);
        reportInstructionCoverage(instructionsMissed, instructionsCovered);
        endMethod();
        endClass();
        endPackage();

        // Verify that the coverage results were added correctly
        Mockito.verify(mMockBuilder).addCoverageResult(
                "com.android.vcard.VCardEntryCounter#getCount()",
                linesMissed, linesCovered + linesMissed,
                branchesMissed, branchesCovered + branchesMissed,
                instructionsMissed, instructionsCovered + instructionsMissed,
                FAKE_URL + "/com.android.vcard/VCardEntryCounter.html");
    }

    public void testSingleMethod_completelyMissed() throws SAXParseException {
        // Prepare test data
        int linesCovered = 0;
        int linesMissed = 2;
        int instructionsCovered = 0;
        int instructionsMissed = 4;
        int branchesCovered = 0;
        int branchesMissed = 2;

        // Simulate parsing the XML report
        startPackage("com/android/vcard");
        startClass("com/android/vcard/VCardEntryCounter");
        startMethod("getCount", "()V");
        reportLineCoverage(linesMissed, linesCovered);
        reportBranchCoverage(branchesMissed, branchesCovered);
        reportInstructionCoverage(instructionsMissed, instructionsCovered);
        endMethod();
        endClass();
        endPackage();

        // Verify that the coverage results were added correctly
        Mockito.verify(mMockBuilder).addCoverageResult(
                "com.android.vcard.VCardEntryCounter#getCount()",
                linesMissed, linesCovered + linesMissed,
                branchesMissed, branchesCovered + branchesMissed,
                instructionsMissed, instructionsCovered + instructionsMissed,
                FAKE_URL + "/com.android.vcard/VCardEntryCounter.html");
    }

    public void testSingleMethod_singleBranchCovered() throws SAXParseException {
        // Prepare test data
        int linesCovered = 2;
        int linesMissed = 0;
        int instructionsCovered = 4;
        int instructionsMissed = 0;
        int branchesCovered = 1;
        int branchesMissed = 0;

        // Simulate parsing the XML report
        startPackage("com/android/vcard");
        startClass("com/android/vcard/VCardEntryCounter");
        startMethod("getCount", "()V");
        reportLineCoverage(linesMissed, linesCovered);
        reportInstructionCoverage(instructionsMissed, instructionsCovered);
        endMethod();
        endClass();
        endPackage();

        // Verify that the coverage results were added correctly
        Mockito.verify(mMockBuilder).addCoverageResult(
                "com.android.vcard.VCardEntryCounter#getCount()",
                linesMissed, linesCovered + linesMissed,
                branchesMissed, branchesCovered + branchesMissed,
                instructionsMissed, instructionsCovered + instructionsMissed,
                FAKE_URL + "/com.android.vcard/VCardEntryCounter.html");
    }

    public void testSingleMethod_singleBranchMissed() throws SAXParseException {
        // Prepare test data
        int linesCovered = 0;
        int linesMissed = 2;
        int instructionsCovered = 0;
        int instructionsMissed = 4;
        int branchesCovered = 0;
        int branchesMissed = 1;

        // Simulate parsing the XML report
        startPackage("com/android/vcard");
        startClass("com/android/vcard/VCardEntryCounter");
        startMethod("getCount", "()V");
        reportLineCoverage(linesMissed, linesCovered);
        reportInstructionCoverage(instructionsMissed, instructionsCovered);
        endMethod();
        endClass();
        endPackage();

        // Verify that the coverage results were added correctly
        Mockito.verify(mMockBuilder).addCoverageResult(
                "com.android.vcard.VCardEntryCounter#getCount()",
                linesMissed, linesCovered + linesMissed,
                branchesMissed, branchesCovered + branchesMissed,
                instructionsMissed, instructionsCovered + instructionsMissed,
                FAKE_URL + "/com.android.vcard/VCardEntryCounter.html");
    }

    public void testSingleMethod_innerClass() throws SAXParseException {
        // Prepare test data
        int linesCovered = 1;
        int linesMissed = 1;
        int instructionsCovered = 2;
        int instructionsMissed = 2;
        int branchesCovered = 1;
        int branchesMissed = 1;

        // Simulate parsing the XML report
        startPackage("com/android/vcard");
        startClass("com/android/vcard/VCardUtils$TextUtilsPort");
        startMethod("isPrintableAscii", "(C)Z");
        reportLineCoverage(linesMissed, linesCovered);
        reportBranchCoverage(branchesMissed, branchesCovered);
        reportInstructionCoverage(instructionsMissed, instructionsCovered);
        endMethod();
        endClass();
        endPackage();

        // Verify that the coverage results were added correctly
        Mockito.verify(mMockBuilder).addCoverageResult(
                "com.android.vcard.VCardUtils$TextUtilsPort#isPrintableAscii(char)",
                linesMissed, linesCovered + linesMissed,
                branchesMissed, branchesCovered + branchesMissed,
                instructionsMissed, instructionsCovered + instructionsMissed,
                FAKE_URL + "/com.android.vcard/VCardUtils$TextUtilsPort.html");
    }

    public void testSingleMethod_unknownCounter() throws SAXParseException {
        // Prepare test data
        int linesCovered = 2;
        int linesMissed = 0;
        int instructionsCovered = 4;
        int instructionsMissed = 0;
        int branchesCovered = 2;
        int branchesMissed = 0;

        // Simulate parsing the XML report
        startPackage("com/android/vcard");
        startClass("com/android/vcard/VCardEntryCounter");
        startMethod("getCount", "()V");
        reportLineCoverage(linesMissed, linesCovered);
        reportBranchCoverage(branchesMissed, branchesCovered);
        reportInstructionCoverage(instructionsMissed, instructionsCovered);
        mHandler.startElement(null, null, "counter",
                getMockAttributes(ImmutableMap.of(
                        "type", "SOME_WEIRD_COUNTER",
                        "missed", Integer.toString(5),
                        "covered", Integer.toString(10))));
        mHandler.endElement(null, null, "counter");
        endMethod();
        endClass();
        endPackage();

        // Verify that the coverage results were added correctly
        Mockito.verify(mMockBuilder).addCoverageResult(
                "com.android.vcard.VCardEntryCounter#getCount()",
                linesMissed, linesCovered + linesMissed,
                branchesMissed, branchesCovered + branchesMissed,
                instructionsMissed, instructionsCovered + instructionsMissed,
                FAKE_URL + "/com.android.vcard/VCardEntryCounter.html");
    }

    public void testMultipleMethods_sameClass() throws SAXParseException {
        // Prepare test data
        int linesCovered1 = 0;
        int linesMissed1 = 2;
        int instructionsCovered1 = 0;
        int instructionsMissed1 = 4;
        int branchesCovered1 = 0;
        int branchesMissed1 = 1;

        int linesCovered2 = 2;
        int linesMissed2 = 4;
        int instructionsCovered2 = 2;
        int instructionsMissed2 = 4;
        int branchesCovered2 = 1;
        int branchesMissed2 = 1;

        // Simulate parsing the XML report
        startPackage("com/android/vcard");
        startClass("com/android/vcard/VCardEntryCounter");
        startMethod("getCount", "()V");
        reportLineCoverage(linesMissed1, linesCovered1);
        reportBranchCoverage(branchesMissed1, branchesCovered1);
        reportInstructionCoverage(instructionsMissed1, instructionsCovered1);
        endMethod();
        startMethod("onVCardEnded", "()V");
        reportLineCoverage(linesMissed2, linesCovered2);
        reportBranchCoverage(branchesMissed2, branchesCovered2);
        reportInstructionCoverage(instructionsMissed2, instructionsCovered2);
        endMethod();
        endClass();
        endPackage();

        // Verify that the coverage results were added correctly
        Mockito.verify(mMockBuilder).addCoverageResult(
                "com.android.vcard.VCardEntryCounter#getCount()",
                linesMissed1, linesCovered1 + linesMissed1,
                branchesMissed1, branchesCovered1 + branchesMissed1,
                instructionsMissed1, instructionsCovered1 + instructionsMissed1,
                FAKE_URL + "/com.android.vcard/VCardEntryCounter.html");
        Mockito.verify(mMockBuilder).addCoverageResult(
                "com.android.vcard.VCardEntryCounter#onVCardEnded()",
                linesMissed2, linesCovered2 + linesMissed2,
                branchesMissed2, branchesCovered2 + branchesMissed2,
                instructionsMissed2, instructionsCovered2 + instructionsMissed2,
                FAKE_URL + "/com.android.vcard/VCardEntryCounter.html");
    }

    public void testMultipleMethods_differentClasses() throws SAXParseException {
        // Prepare test data
        int linesCovered1 = 0;
        int linesMissed1 = 2;
        int instructionsCovered1 = 0;
        int instructionsMissed1 = 4;
        int branchesCovered1 = 0;
        int branchesMissed1 = 1;

        int linesCovered2 = 2;
        int linesMissed2 = 4;
        int instructionsCovered2 = 2;
        int instructionsMissed2 = 4;
        int branchesCovered2 = 1;
        int branchesMissed2 = 1;

        // Simulate parsing the XML report
        startPackage("com/android/vcard");
        startClass("com/android/vcard/VCardEntryCounter");
        startMethod("getCount", "()V");
        reportLineCoverage(linesMissed1, linesCovered1);
        reportBranchCoverage(branchesMissed1, branchesCovered1);
        reportInstructionCoverage(instructionsMissed1, instructionsCovered1);
        endMethod();
        endClass();
        startClass("com/android/vcard/VCardParser_V40");
        startMethod("cancel", "()V");
        reportLineCoverage(linesMissed2, linesCovered2);
        reportBranchCoverage(branchesMissed2, branchesCovered2);
        reportInstructionCoverage(instructionsMissed2, instructionsCovered2);
        endMethod();
        endClass();
        endPackage();

        // Verify that the coverage results were added correctly
        Mockito.verify(mMockBuilder).addCoverageResult(
                "com.android.vcard.VCardEntryCounter#getCount()",
                linesMissed1, linesCovered1 + linesMissed1,
                branchesMissed1, branchesCovered1 + branchesMissed1,
                instructionsMissed1, instructionsCovered1 + instructionsMissed1,
                FAKE_URL + "/com.android.vcard/VCardEntryCounter.html");
        Mockito.verify(mMockBuilder).addCoverageResult(
                "com.android.vcard.VCardParser_V40#cancel()",
                linesMissed2, linesCovered2 + linesMissed2,
                branchesMissed2, branchesCovered2 + branchesMissed2,
                instructionsMissed2, instructionsCovered2 + instructionsMissed2,
                FAKE_URL + "/com.android.vcard/VCardParser_V40.html");
    }

    public void testMultipleMethods_differentPackages() throws SAXParseException {
        // Prepare test data
        int linesCovered1 = 0;
        int linesMissed1 = 2;
        int instructionsCovered1 = 0;
        int instructionsMissed1 = 4;
        int branchesCovered1 = 0;
        int branchesMissed1 = 1;

        int linesCovered2 = 2;
        int linesMissed2 = 4;
        int instructionsCovered2 = 2;
        int instructionsMissed2 = 4;
        int branchesCovered2 = 1;
        int branchesMissed2 = 1;

        // Simulate parsing the XML report
        startPackage("com/android/vcard");
        startClass("com/android/vcard/VCardEntryCounter");
        startMethod("getCount", "()V");
        reportLineCoverage(linesMissed1, linesCovered1);
        reportBranchCoverage(branchesMissed1, branchesCovered1);
        reportInstructionCoverage(instructionsMissed1, instructionsCovered1);
        endMethod();
        endClass();
        endPackage();
        startPackage("com/android/vcard/exception");
        startClass("com/android/vcard/exception/VCardException");
        startMethod("<init>", "()V");
        reportLineCoverage(linesMissed2, linesCovered2);
        reportBranchCoverage(branchesMissed2, branchesCovered2);
        reportInstructionCoverage(instructionsMissed2, instructionsCovered2);
        endMethod();
        endClass();
        endPackage();

        // Verify that the coverage results were added correctly
        Mockito.verify(mMockBuilder).addCoverageResult(
                "com.android.vcard.VCardEntryCounter#getCount()",
                linesMissed1, linesCovered1 + linesMissed1,
                branchesMissed1, branchesCovered1 + branchesMissed1,
                instructionsMissed1, instructionsCovered1 + instructionsMissed1,
                FAKE_URL + "/com.android.vcard/VCardEntryCounter.html");
        Mockito.verify(mMockBuilder).addCoverageResult(
                "com.android.vcard.exception.VCardException#VCardException()",
                linesMissed2, linesCovered2 + linesMissed2,
                branchesMissed2, branchesCovered2 + branchesMissed2,
                instructionsMissed2, instructionsCovered2 + instructionsMissed2,
                FAKE_URL + "/com.android.vcard.exception/VCardException.html");
    }

    public void testGetMethodSignature_noArguments() throws SAXParseException {
        assertEquals("foo()", mHandler.getMethodSignature("foo", "()V"));
        assertEquals("foo()", mHandler.getMethodSignature("foo", "()I"));
        assertEquals("foo()", mHandler.getMethodSignature("foo", "()Z"));
        assertEquals("foo()", mHandler.getMethodSignature("foo", "()Lcom/example/Fred;"));
    }

    public void testGetMethodSignature_singleArgument() throws SAXParseException {
        assertEquals("bar(int)", mHandler.getMethodSignature("bar", "(I)V"));
        assertEquals("bar(boolean)", mHandler.getMethodSignature("bar", "(Z)V"));
        assertEquals("bar(java.lang.String)",
                mHandler.getMethodSignature("bar", "(Ljava/lang/String;)V"));
    }

    public void testGetMethodSignature_multipleArguments() throws SAXParseException {
        assertEquals("baz(int, boolean)", mHandler.getMethodSignature("baz", "(IZ)V"));
        assertEquals("baz(int, int, int)", mHandler.getMethodSignature("baz", "(III)V"));
        assertEquals("baz(java.lang.String, boolean)",
                mHandler.getMethodSignature("baz", "(Ljava/lang/String;Z)V"));
        assertEquals("baz(java.util.List, java.lang.String, int)",
                mHandler.getMethodSignature("baz", "(Ljava/util/List;Ljava/lang/String;I)V"));
    }

    public void testGetMethodSignature_invalidMethodDescriptor() {
        try {
            mHandler.getMethodSignature("foo", "V");
            fail("Exception not thrown");
        } catch (SAXParseException e) {
            // Expected
        }
        try {
            mHandler.getMethodSignature("foo", "");
            fail("Exception not thrown");
        } catch (SAXParseException e) {
            // Expected
        }
    }

    public void testGetMethodSignature_invalidTypeDescriptor() {
        try {
            mHandler.getMethodSignature("foo", "(A)V");
            fail("Exception not thrown");
        } catch (SAXParseException e) {
            // Expected
        }
        try {
            mHandler.getMethodSignature("foo", "([]I)V");
            fail("Exception not thrown");
        } catch (SAXParseException e) {
            // Expected
        }
        try {
            mHandler.getMethodSignature("foo", "(Ljava/lang/String)V");
            fail("Exception not thrown");
        } catch (SAXParseException e) {
            // Expected
        }
    }

    public void testGetTypeName_basicTypes() throws SAXParseException {
        assertEquals("byte", mHandler.getTypeName("B"));
        assertEquals("char", mHandler.getTypeName("C"));
        assertEquals("double", mHandler.getTypeName("D"));
        assertEquals("float", mHandler.getTypeName("F"));
        assertEquals("int", mHandler.getTypeName("I"));
        assertEquals("long", mHandler.getTypeName("J"));
        assertEquals("short", mHandler.getTypeName("S"));
        assertEquals("boolean", mHandler.getTypeName("Z"));
    }

    public void testGetTypeName_referenceType() throws SAXParseException {
        assertEquals("com.example.Foo", mHandler.getTypeName("Lcom/example/Foo;"));
        assertEquals("com.example.Bar$Baz", mHandler.getTypeName("Lcom/example/Bar$Baz;"));
        assertEquals("Xyzzy", mHandler.getTypeName("LXyzzy;"));
    }

    public void testGetTypeName_arrayTypes() throws SAXParseException {
        assertEquals("int[]", mHandler.getTypeName("[I"));
        assertEquals("int[][]", mHandler.getTypeName("[[I"));
        assertEquals("int[][][][]", mHandler.getTypeName("[[[[I"));
        assertEquals("com.example.Quux[][]", mHandler.getTypeName("[[Lcom/example/Quux;"));
    }

    public void testGetTypeName_invalidType() {
        try {
            mHandler.getTypeName("A");
            fail("Exception not thrown");
        } catch (SAXParseException e) {
            // Expected
        }
        try {
            mHandler.getTypeName("[A");
            fail("Exception not thrown");
        } catch (SAXParseException e) {
            // Expected
        }
    }
}
