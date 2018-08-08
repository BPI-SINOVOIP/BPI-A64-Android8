package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.build.OtaDeviceBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil;
import java.io.File;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import junit.framework.TestCase;
import org.easymock.EasyMock;

public class OtaFaultInjectionPreparerTest extends TestCase {

    private IDeviceBuildInfo mMockDeviceBuildInfo;
    private ITestDevice mMockDevice;
    private OtaFaultInjectionPreparer mFaultInjectionPreparer;
    private File mOtaPackage = null;
    private File mStubZipBaseDir = null;
    private File mSubFile = null;
    private ZipFile mZipFile = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mStubZipBaseDir = FileUtil.createTempDir("libotafault");
        File stubChildDir = new File(mStubZipBaseDir, "somedir");
        assertTrue(stubChildDir.mkdir());
        mSubFile = new File(stubChildDir, "foo.txt");
        FileUtil.writeToFile("contents", mSubFile);
        mOtaPackage = ZipUtil.createZip(mStubZipBaseDir);

        mMockDevice = EasyMock.createMock(ITestDevice.class);
        mMockDeviceBuildInfo = EasyMock.createNiceMock(IDeviceBuildInfo.class);
        EasyMock.expect(mMockDeviceBuildInfo.getOtaPackageFile()).andReturn(mOtaPackage);

        mFaultInjectionPreparer = new OtaFaultInjectionPreparer();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (mZipFile != null) {
            mZipFile.close();
        }
        mOtaPackage.delete();
        mOtaPackage = null;
        FileUtil.recursiveDelete(mStubZipBaseDir);
    }

    public void testAddSingleFault_read() throws Exception {
        mFaultInjectionPreparer.mReadFaultFile = "/foo/bar";
        EasyMock.replay(mMockDevice, mMockDeviceBuildInfo);
        mFaultInjectionPreparer.setUp(mMockDevice, mMockDeviceBuildInfo);
        EasyMock.verify(mMockDevice, mMockDeviceBuildInfo);
        mZipFile = new ZipFile(mOtaPackage);

        ZipEntry ze = mZipFile.getEntry(OtaFaultInjectionPreparer.CFG_BASE + "/READ");

        assertTrue(ZipUtil.isZipFileValid(mOtaPackage, true));
        assertNotNull(ze);

        InputStream entryReader = mZipFile.getInputStream(ze);
        byte buf[] = new byte[8];
        int numBytesRead = entryReader.read(buf);

        assertEquals(numBytesRead, 8);
        assertEquals(new String(buf), "/foo/bar");

        ze = mZipFile.getEntry(mStubZipBaseDir.getName() + "/somedir/foo.txt");
        assertNotNull(ze);

        entryReader = mZipFile.getInputStream(ze);
        buf = new byte[8];
        numBytesRead = entryReader.read(buf);

        assertEquals(numBytesRead, 8);
        assertEquals(new String(buf), "contents");
    }

    public void testReceiveFullPackage() throws Exception {
        mFaultInjectionPreparer.mReadFaultFile = "/foo/bar";
        OtaDeviceBuildInfo stubOuterBuild = new OtaDeviceBuildInfo();
        stubOuterBuild.setOtaBuild(mMockDeviceBuildInfo);
        stubOuterBuild.setBaselineBuild(new DeviceBuildInfo());
        EasyMock.replay(mMockDevice, mMockDeviceBuildInfo);
        mFaultInjectionPreparer.setUp(mMockDevice, stubOuterBuild);
        EasyMock.verify(mMockDevice, mMockDeviceBuildInfo);
        assertNotNull(mOtaPackage);
    }
}

