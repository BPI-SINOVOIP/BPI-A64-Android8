// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import com.android.tradefed.log.LogUtil.CLog;
import com.google.android.tradefed.result.BlackboxPostUtil.TestResultsBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.EnumMap;
import java.util.Map;
import java.util.Scanner;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link DefaultHandler} that can be used to parse a JaCoCo XML report and add the coverage data
 * to a {@link TestResultsBuilder}.
 */
class JacocoXmlHandler extends DefaultHandler {
    // XML Tags
    private static final String PACKAGE_TAG = "package";
    private static final String CLASS_TAG = "class";
    private static final String METHOD_TAG = "method";
    private static final String COUNTER_TAG = "counter";

    // Current scope
    private String mPackage;
    private String mClass;
    private String mMethod;

    // Coverage counters
    enum CoverageType {
        INSTRUCTION,
        LINE,
        BRANCH;
    }
    private EnumMap<CoverageType, Integer> mMissed = new EnumMap<>(CoverageType.class);
    private EnumMap<CoverageType, Integer> mTotal = new EnumMap<>(CoverageType.class);

    // Current location in the document
    private Locator mLocator = null;

    private TestResultsBuilder mResults;
    private String mBaseUrl;

    /**
     * Constructs a new JacocoXmlHandler.
     * @param results The {@link TestResultsBuilder} to add the coverage results to.
     * @param baseUrl The base URL of the HTML report or null. Used to support deep linking.
     */
    JacocoXmlHandler(TestResultsBuilder results, String baseUrl) {
        mResults = results;
        mBaseUrl = baseUrl;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDocumentLocator(Locator locator) {
        mLocator = locator;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXParseException {

        // <package name="...">
        if (PACKAGE_TAG.equals(qName)) {
            // Convert package name from 'com/example' form to 'com.example'
            mPackage = getStringValue(attributes, "name").replaceAll("/", ".");

        // <class name="...">
        } else if (CLASS_TAG.equals(qName)) {
            // Convert class name from 'com/example/Foo' form to 'Foo'
            String className = getStringValue(attributes, "name").replaceAll("/", ".");
            if (className.startsWith(mPackage)) {
                className = className.substring(mPackage.length() + 1);
            }
            mClass = className;

        // <method name="..." desc="..." line="...">
        } else if (METHOD_TAG.equals(qName)) {
            String methodName = getStringValue(attributes, "name");
            // Convert constructors from "<init>" to "ClassName(args)"
            if ("<init>".equals(methodName)) {
                mMethod = getMethodSignature(mClass, getStringValue(attributes, "desc"));
            // Convert static initializers from "<clinit>" to "static {...}"
            } else if ("<clinit>".equals(methodName)) {
                mMethod = "static {...}";
            // Normal methods
            } else {
                // Use getMethodSignature(..) to convert from
                // name="someMethod", desc="(Ljava/lang/String;)V" to "someMethod(java.lang.String)"
                mMethod = getMethodSignature(methodName, getStringValue(attributes, "desc"));
            }

        // <counter type="..." missed="..." covered="...">
        } else if (COUNTER_TAG.equals(qName)) {
            // Only read counters that are inside a method block
            if (mMethod != null) {
                CoverageType type;
                try {
                    type = CoverageType.valueOf(getStringValue(attributes, "type"));
                } catch (IllegalArgumentException e) {
                    CLog.w("Skipping unknown counter type '%s'",
                            getStringValue(attributes, "type"));
                    return;
                }
                int missed = getIntValue(attributes, "missed");
                int covered = getIntValue(attributes, "covered");

                // Update counters
                mMissed.put(type, missed);
                mTotal.put(type, missed + covered);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endElement(String uri, String localName, String qName) {

        // </package>
        if (PACKAGE_TAG.equals(qName)) {
            mPackage = null;

        // </class>
        } else if (CLASS_TAG.equals(qName)) {
            mClass = null;

        // </method>
        } else if (METHOD_TAG.equals(qName)) {
            // Only report methods with source code
            if (mTotal.containsKey(CoverageType.LINE)) {
                // Branch coverage isn't reported if there's a single branch. In this case, consider
                // the branch covered if any lines were covered.
                mTotal.putIfAbsent(CoverageType.BRANCH, 1);
                mMissed.putIfAbsent(CoverageType.BRANCH,
                        mMissed.get(CoverageType.LINE) == 0 ? 0 : 1);

                // Link to the specific page in the report that contains this method
                String classUrl = mBaseUrl != null
                        ? String.format("%s/%s/%s.html", mBaseUrl, mPackage, mClass)
                        : null;

                // Add the coverage results
                mResults.addCoverageResult(String.format("%s.%s#%s", mPackage, mClass, mMethod),
                        mMissed.get(CoverageType.LINE), mTotal.get(CoverageType.LINE),
                        mMissed.get(CoverageType.BRANCH), mTotal.get(CoverageType.BRANCH),
                        mMissed.get(CoverageType.INSTRUCTION), mTotal.get(CoverageType.INSTRUCTION),
                        classUrl);
            }

            // Reset counters
            mMethod = null;
            mMissed.clear();
            mTotal.clear();
        }
    }

    /**
     * Throws a {@link SAXParseException} with the current document {@link Locator}.
     * @param format A format string for the exception message
     * @param args Any arguments to the format string
     */
    private void throwParseException(String format, Object... args) throws SAXParseException {
        throw new SAXParseException(String.format(format, args), mLocator);
    }

    /**
     * Returns the string attribute with the given key.
     *
     * @param attributes The {@link Attributes} from which to retrieve the value
     * @param key The name of the attribute to return
     * @throws SAXParseException If the attribute does not exist
     */
    private String getStringValue(Attributes attributes, String key) throws SAXParseException {
        String value = attributes.getValue(key);
        if (value == null) {
            throwParseException("Missing %s attribute", key);
        }
        return value;
    }

    /**
     * Returns the int attribute with the given key.
     *
     * @param attributes The {@link Attributes} from which to retrieve the value
     * @param key The name of the attribute to return
     * @throws SAXParseException If the attribute does not exist or is not an integer.
     */
    private int getIntValue(Attributes attributes, String key) throws SAXParseException {
        try {
            return Integer.parseInt(getStringValue(attributes, key));
        } catch (NumberFormatException e) {
            throw new SAXParseException(String.format("%s is not an integer", key), mLocator, e);
        }
    }

    /**
     * Returns a method signature string of the form "method(arg1, arg2...)" for the given
     * method and descriptor.
     *
     * @param method The name of the method
     * @param desc The method descriptor
     * @return The method signature
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.3">
     * Java Virtual Machine Specification - Method Descriptors</a>
     */
    @VisibleForTesting
    String getMethodSignature(String method, String desc) throws SAXParseException {
        // The method signature should look like "method(arg1, arg2...)"
        // Use a StringJoiner so we can easily add comma-separated arguments to the signature
        StringJoiner signature = new StringJoiner(", ", method + "(", ")");

        // Extract the parameter descriptors from the method descriptor.
        // The method descriptor has the form: (ParameterDescriptor*)ReturnDescriptor
        Matcher methodMatcher = Pattern.compile("\\((?<parameters>.*)\\).*").matcher(desc);
        if (!methodMatcher.matches()) {
            throwParseException("Failed to parse method descriptor: %s", desc);
        }

        // Use a Scanner to read the parameter descriptors. Each parameter descriptor consists
        // of any number of leading ['s, then one of [BCDFIJSZ] (basic types), or LclassName;
        Pattern paramDescriptor = Pattern.compile("\\G\\[*([BCDFIJSZ]|L[^\\.\\[;]+;)");
        try (Scanner scanner = new Scanner(methodMatcher.group("parameters"))) {
            for (String current = scanner.findInLine(paramDescriptor); current != null;
                    current = scanner.findInLine(paramDescriptor)) {
                // Convert the current descriptor to the full type name and add it to the signature
                signature.add(getTypeName(current));
            }
            // There shouldn't be any left over characters in the scanner
            if (scanner.hasNext()) {
                throwParseException("Failed to parse param descriptor. Remaining characters: %s",
                        scanner.findInLine(".*"));
            }
        }

        // Return the method signature
        return signature.toString();
    }

    /**
     * A map of functions used to translate between field descriptors and type names.
     *
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.2">
     * Java Virtual Machine Specification - Field Descriptors</a>
     */
    private Map<Character, UnaryOperator<String>> descriptorTranslationMap =
            ImmutableMap.<Character, UnaryOperator<String>>builder()
            .put('B', s -> "byte")
            .put('C', s -> "char")
            .put('D', s -> "double")
            .put('F', s -> "float")
            .put('I', s -> "int")
            .put('J', s -> "long")
            .put('S', s -> "short")
            .put('Z', s -> "boolean")
            .put('L', s -> s.substring(1, s.length() - 1).replaceAll("/", "."))
            .build();

    /**
     * Converts a field descriptor to the full type name string.
     *
     * @param descriptor The field descriptor
     * @return The full type name
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.2">
     * Java Virtual Machine Specification - Field Descriptors</a>
     */
    @VisibleForTesting
    String getTypeName(String descriptor) throws SAXParseException {
        // Count and skip over any leading ['s which are used to indicate an array's dimensions
        int arrayDimensions = descriptor.lastIndexOf("[") + 1;
        descriptor = descriptor.substring(arrayDimensions);

        // The first character after any leading ['s is used to indicate the type. Use this
        // character to look up the appropriate translation function and apply it.
        UnaryOperator<String> translation = descriptorTranslationMap.get(descriptor.charAt(0));
        if (translation == null) {
            throwParseException("Unknown field type %s", descriptor.charAt(0));
        }
        StringBuilder ret = new StringBuilder(translation.apply(descriptor));

        // Append []'s as necessary for array types
        ret.append(Strings.repeat("[]", arrayDimensions));

        return ret.toString();
    }
}
