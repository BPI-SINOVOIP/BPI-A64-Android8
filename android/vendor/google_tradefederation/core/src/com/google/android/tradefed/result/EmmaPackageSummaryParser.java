// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.tradefed.result;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.EmmaXmlConstants;
import com.android.tradefed.util.xml.AbstractXmlParser;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parser for the Emma code coverage xml report that sums up package coverage totals.
 * <p/>
 * Internally it builds up a tree data structure representing the java package structure.
 */
public class EmmaPackageSummaryParser extends AbstractXmlParser {

    // TODO: share more logic with EmmaXmlReportParser

    // match "0%   (0/114)"
    private static final Pattern COVERAGE_PATTERN = Pattern.compile("\\d+%\\s+\\((\\d+)/(\\d+)\\)");

    /**
     * SAX parser handler for emma xml report.
     * <p/>
     * Parses out block coverage data for each package tag, and builds up PackageNode tree.
     * <p/>
     * Expected format:
     * <code>
     * package name="foo
     *   coverage type ="block" value="X%   (Y/Z)"
     * </code>
     * where Y and Z are the number of blocks covered vs total blocks, respectively.
     *
     */
    private class EmmaXmlHandler extends DefaultHandler {

        PackageNode mCurrentPackage = null;

        @Override
        public void startElement(String uri, String localName, String tagName,
                Attributes attributes) throws SAXException {
            if (EmmaXmlConstants.PACKAGE_TAG.equalsIgnoreCase(tagName)) {
                String packageName = attributes.getValue(EmmaXmlConstants.NAME_ATTR);
                mCurrentPackage = findPackageNode(packageName, true);
            } else if (EmmaXmlConstants.COVERAGE_TAG.equalsIgnoreCase(tagName)) {
                if (mCurrentPackage != null) {
                    String coverageType = attributes.getValue("type");
                    String value = attributes.getValue("value");
                    if (coverageType.contains(EmmaXmlConstants.BLOCK_TAG)) {
                        mCurrentPackage.parseCoverageValues(value);
                    }
                }
            } else {
                // hit another tag, must be out of the immediate package children, reset
                mCurrentPackage = null;
            }
        }

        @Override
        public void endElement(String uri, String localName, String tagName) throws SAXException {
            if (EmmaXmlConstants.PACKAGE_TAG.equalsIgnoreCase(tagName)) {
                mCurrentPackage = null;
            }
        }
    }

    /**
     * Represents code coverage data for one java package, and all its descendants.
     * i.e. coverage data represents total coverage for all classes in the package, as well as all
     * sub-packages.
     */
    public static class PackageNode {
        private int mCoveredBlocks = 0;
        private int mTotalBlocks = 0;
        private String mFullName = null;
        private String mName = null;
        private PackageNode mParent;
        private Map<String, PackageNode> mChildrenMap;

        public PackageNode(String name, PackageNode parent) {
            mName = name;
            mParent = parent;
            mChildrenMap = new HashMap<String, PackageNode>();
            if (mParent != null) {
                if (mParent.mFullName == null) {
                    mFullName = mName;
                } else {
                    mFullName = mParent.mFullName + "." + mName;
                }
            }
        }

        /**
         * Parse the coverage details from the string value, and add it to the current package
         * totals.
         *
         * @param value the coverage data in {@link #COVERAGE_PATTERN} format
         */
        void parseCoverageValues(String value) {
            Matcher m = COVERAGE_PATTERN.matcher(value);
            if (m.find()) {
                String coveredString = m.group(1);
                String totalString = m.group(2);
                try {
                    addCoverageData(Integer.parseInt(coveredString), Integer.parseInt(totalString));
                } catch (NumberFormatException e) {
                    // fall through
                }
                return;
            }
            CLog.e("Could not find coverage data in '%s'", value);
        }

        /**
         * Adds coverage data to current node and all ancestors.
         *
         * @param coveredBlocks
         * @param totalBlocks
         */
        public void addCoverageData(int coveredBlocks, int totalBlocks) {
            mCoveredBlocks += coveredBlocks;
            mTotalBlocks += totalBlocks;
            if (mParent != null) {
                mParent.addCoverageData(coveredBlocks, totalBlocks);
            }
        }

        /**
         * Recursively find the package node among descendants that matches the given relative name.
         *
         * @param packageSegs the java package name to look for, relative to current node. If empty
         *            will return current node.
         * @param create controls behavior if a {@link PackageNode} with given name is not found. if
         *            <code>true</code>, one will be created. If <code>false</code>, returns
         *            <code>null</code>.
         * @return the {@link PackageNode}
         */
        public PackageNode findPackageNode(Queue<String> packageSegs, boolean create) {
            if (packageSegs.isEmpty()) {
                return this;
            }
            String seg = packageSegs.remove();
            PackageNode child = mChildrenMap.get(seg);
            if (child == null) {
                if (create) {
                    child = new PackageNode(seg, this);
                    mChildrenMap.put(seg, child);
                } else {
                    return null;
                }
            }
            return child.findPackageNode(packageSegs, create);
        }

        /**
         * Debugging method. Recursively dumps content of package and all descendants to stdout.
         */
        void dump() {
            dump(Integer.MAX_VALUE);
        }

        /**
         * Debugging method. Recursively dumps content of package and descendants up to given depth.
         */
        void dump(int depth) {
            System.out.printf("%s: %d:%d\n", mName, mCoveredBlocks, mTotalBlocks);
            if (depth >= 1) {
                List<PackageNode> nodes = new ArrayList<PackageNode>(mChildrenMap.values());
                Collections.sort(nodes, new PackageNodeComparator());
                for (PackageNode n : nodes) {
                    n.dump(depth-1);
                }
            }
        }

        public int getCoveredBlocks() {
            return mCoveredBlocks;
        }

        public int getTotalBlocks() {
            return mTotalBlocks;
        }

        /**
         * Get the percent code coverage as a string
         */
        public String getPercentString() {
            return  String.valueOf(mCoveredBlocks*100/mTotalBlocks);
        }

        /**
         * Returns an immutable collection of this package's immediate children.
         */
        public Collection<PackageNode> getChildren() {
            return mChildrenMap.values();
        }

        /**
         * Gets this node name. This will be the last segment in the full package name.
         * eg if full package name is "com.example.foo", getName() will return "foo".
         */
        public String getName() {
            return mName;
        }

        /**
         * Helper method to directly create a PackageNode with given relative name in tree.
         * Intended for unit testing.
         */
        public PackageNode createPackageNode(String... segs) {
            return findPackageNode(new LinkedList<String>(Arrays.asList(segs)), true);
        }
    }

    /**
     * A {@link Comparator} for {@link PackageNode} that sorts alphabetically by package name.
     */
    private static class PackageNodeComparator implements Comparator<PackageNode> {
        @Override
        public int compare(PackageNode arg0, PackageNode arg1) {
            return arg0.mName.compareTo(arg1.mName);
        }
    }

    private final PackageNode mRootNode;

    /**
     * Creates a {@link EmmaPackageSummaryParser}

     */
    public EmmaPackageSummaryParser() {
        mRootNode = new PackageNode(null, null);
    }

    /**
     * Finds PackageNode from full java package name from parsed data.
     * <p/>
     * {@link #parse(java.io.InputStream)} must be called before this method.
     *
     * @param name the full java package to find.
     * @return the {@link PackageNode} or <code>null</code> if it cannot be found
     */
    public PackageNode findPackageNode(String name) {
        return findPackageNode(name, false);
    }

    PackageNode findPackageNode(String name, boolean create) {
        String[] packageSegments = name.split("\\.");
        Queue<String> packageSegList = new LinkedList<String>(Arrays.asList((packageSegments)));
        return mRootNode.findPackageNode(packageSegList, create);
    }

    /**
     * Return the root {@link PackageNode} from parsed data.
     * </p>
     * {@link #parse(java.io.InputStream)} must be called before this method.
     */
    PackageNode getRoot() {
        return mRootNode;
    }

    @Override
    protected DefaultHandler createXmlHandler() {
        return new EmmaXmlHandler();
    }
}
