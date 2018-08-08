// Copyright 2013 Google Inc. All Rights Reserved.
package com.google.android.notifilter;

import com.android.notifilter.Dispatcher;
import com.android.notifilter.Notifilter;
import com.android.notifilter.util.testtrie.TestTrie;
import com.android.notifilter.common.IDatastore;
import com.android.notifilter.common.IDatastore.Notif;
import com.android.notifilter.common.config.ConfigurationException;

import java.util.List;
import java.sql.SQLException;

/**
 * Google-proprietary Notifilter entrypoint which sets the sender address to something meaningful
 */
public class GoogleNotifilter extends Notifilter {
    private static final String SENDER = "Android Test Result Service <notifilter-role@google.com>";

    /**
     * Program entrypoint for Google Notifilter
     */
    public static void main(String[] args) throws Exception {
        GoogleNotifilter nf = new GoogleNotifilter(args);
        nf.run();
    }

    /**
     * No-op constructor; exposed for unit tests
     */
    protected GoogleNotifilter() throws SQLException {
        super();
    }

    public GoogleNotifilter(String[] args) throws SQLException, ConfigurationException {
        super(args);
    }

    /**
     * Unit test constructor.  Does not run dispatcher.
     */
    protected GoogleNotifilter(IDatastore ds, List<Notif> sharedNotifList) throws SQLException {
        super(ds, sharedNotifList);
    }

    /**
     * Run the {@code Dispatcher} with our custom sender address.
     */
    @Override
    public Dispatcher createDispatcher(List<Notif> sharedNotifList, TestTrie[] trieHolder) {
        return new Dispatcher(sharedNotifList, SENDER, trieHolder);
    }
}
