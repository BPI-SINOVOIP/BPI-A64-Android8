// Copyright 2014 Google Inc. All Rights Reserved.
package com.google.android.tradefed.util.hostmetric;

import com.google.android.tradefed.util.CloudTask;
import com.google.android.tradefed.util.CloudTaskQueueException;
import com.google.android.tradefed.util.ICloudTaskQueue;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for {@link HostMetricAgent}.
 */
public class HostMetricAgentTest extends TestCase {

    private static final String TASK_QUEUE_NAME = "task-queue";
    private static final String TASK_QUEUE_ACCOUNT = "account@google.com";

    private ICloudTaskQueue mMockTaskQueueHelper;
    private HostMetricAgent mAgent;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mMockTaskQueueHelper = EasyMock.createMock(ICloudTaskQueue.class);
        mAgent = new HostMetricAgent() {
            @Override
            ICloudTaskQueue getTaskQueueHelper() {
                return mMockTaskQueueHelper;
            }
        };
        mAgent.setTaskQueueName(TASK_QUEUE_NAME);
        mAgent.setTaskQueueAccount(TASK_QUEUE_ACCOUNT);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testEmitValue() {
        emitValues(1);
        assertEquals(1, mAgent.getMetrics().size());
    }

    public void testFlush_singleInsert() throws CloudTaskQueueException {
        mMockTaskQueueHelper.insertTask(EasyMock.eq(TASK_QUEUE_NAME),
                EasyMock.<CloudTask> anyObject());
        EasyMock.expectLastCall().times(1);
        EasyMock.replay(mMockTaskQueueHelper);

        emitValues(30);
        assertEquals(30, mAgent.getMetrics().size());
        mAgent.flush();
        EasyMock.verify(mMockTaskQueueHelper);
    }

    public void testFlush_multipleInserts() throws CloudTaskQueueException {
        mMockTaskQueueHelper.insertTask(EasyMock.eq(TASK_QUEUE_NAME),
                EasyMock.<CloudTask> anyObject());
        EasyMock.expectLastCall().times(3);
        EasyMock.replay(mMockTaskQueueHelper);

        emitValues(120);
        assertEquals(120, mAgent.getMetrics().size());
        mAgent.flush();
        EasyMock.verify(mMockTaskQueueHelper);
    }

    private void emitValues(int count) {
        String name = "/android/tradefed/metric";
        int value = 100;
        Map<String, String> data = new HashMap<>();
        data.put("foo", "foo-value");
        data.put("bar", "bar-value");
        for (int i = 0; i < count; i++) {
            mAgent.emitValue(name, value, data);
        }
    }
}
