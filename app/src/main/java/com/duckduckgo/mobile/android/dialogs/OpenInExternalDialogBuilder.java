package com.duckduckgo.mobile.android.dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;

import com.duckduckgo.mobile.android.R;
import com.duckduckgo.mobile.android.util.DDGUtils;

public final class OpenInExternalDialogBuilder extends AlertDialog.Builder{

	public OpenInExternalDialogBuilder(final Context context, final String touchedUrl) {
		super(context);
        setTitle(context.getResources().getString(R.string.OpenInExternalBrowser));
        setMessage(context.getString(R.string.ConfirmExternalBrowser));
        setCancelable(false);
        setPositiveButton(context.getResources().getString(R.string.Yes), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int buttonId) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(touchedUrl));
                DDGUtils.execIntentIfSafe(context, browserIntent);
            }
        });
        setNegativeButton(context.getResources().getString(R.string.No), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int buttonId) {
                dialog.dismiss();
            }
        });
        setIcon(android.R.drawable.ic_dialog_info);
	}
}
