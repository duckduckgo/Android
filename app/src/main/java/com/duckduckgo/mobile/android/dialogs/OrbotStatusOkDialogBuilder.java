package com.duckduckgo.mobile.android.dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import com.duckduckgo.mobile.android.R;


/*
Shows a dialog to alert the user the feedrequest failed, asking him to try again.
 */
public final class OrbotStatusOkDialogBuilder extends AlertDialog.Builder{
	public OrbotStatusOkDialogBuilder(final Context context) {
		super(context);
        setTitle(R.string.OrbotStatusOkTitle);
        setMessage(R.string.OrbotStatusOkMessage);
        setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        });
	}
}
