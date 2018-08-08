package com.softwinner.awreadingmode;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Window;

import com.softwinner.AWDisplay;

public class ReadingModeActivity extends Activity implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener {
    private boolean mReadingModeEnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        mReadingModeEnable = AWDisplay.getReadingMode();
        Dialog dlg = new AlertDialog.Builder(this)
                .setTitle(mReadingModeEnable ? R.string.readingmode_title_readingmode : R.string.readingmode_title_normal)
                .setMessage(R.string.readingmode_summary)
                .setOnDismissListener(this)
                .setPositiveButton(android.R.string.ok, this)
                .setNegativeButton(android.R.string.cancel, this)
                .create();
        dlg.show();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (DialogInterface.BUTTON_POSITIVE == which) {
            mReadingModeEnable = !mReadingModeEnable;
            AWDisplay.setReadingMode(mReadingModeEnable);
        } else if (DialogInterface.BUTTON_NEGATIVE == which) {
        }
    }

    public void onDismiss(DialogInterface dialog) {
        finish();
    }
}
