package com.duckduckgo.mobile.android.dialogs;

/*
 * This class is the implementation of a SSL certificate dialog.
 *
 * The file contains code from the Android project.
 *
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Date;

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.SslErrorHandler;
import android.widget.TextView;

import com.duckduckgo.mobile.android.R;

public final class SSLCertificateDialog extends Builder {

	public SSLCertificateDialog(final Context context, final SslErrorHandler handler, final SslError error) {
		super(context);
		
		setView(getCertificateText(context, error.getCertificate()));
	
        setTitle(R.string.WarnSSLTitle);
        setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                handler.proceed();
            }
        });
        setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
            	dialog.dismiss();
            	handler.cancel();
            }
        });
	}

	/**
     * Formats the certificate date to a properly localized date string.
     * @return Properly localized version of the certificate date string and
     * the "" if it fails to localize.
     * 
     * Method replicated from android.net.http.SslCertificate
     * Mirror: https://github.com/android/platform_frameworks_base/blob/master/core/java/android/net/http/SslCertificate.java
     * 
     */
    private String formatCertificateDate(Context context, Date certificateDate) {
        if (certificateDate == null) {
            return "";
        }
        return DateFormat.getDateFormat(context).format(certificateDate);
    }
	
    /**
     * Inflates the SSL certificate view (helper method).
     * @return The resultant certificate view with issued-to, issued-by,
     * issued-on, expires-on, and possibly other fields set.
     * 
     * Method replicated from android.net.http.SslCertificate
     * Mirror: https://github.com/android/platform_frameworks_base/blob/master/core/java/android/net/http/SslCertificate.java
     *
     * @hide Used by Browser and Settings
     */
    public View inflateCertificateView(SslCertificate certificate, Context context) {
        LayoutInflater factory = LayoutInflater.from(context);

        View certificateView = factory.inflate(
            R.layout.ssl_certificate, null);

        // issued to:
        SslCertificate.DName issuedTo = certificate.getIssuedTo();
        if (issuedTo != null) {
            ((TextView) certificateView.findViewById(R.id.to_common))
                    .setText(issuedTo.getCName());
            ((TextView) certificateView.findViewById(R.id.to_org))
                    .setText(issuedTo.getOName());
            ((TextView) certificateView.findViewById(R.id.to_org_unit))
                    .setText(issuedTo.getUName());
        }

        // issued by:
        SslCertificate.DName issuedBy = certificate.getIssuedBy();
        if (issuedBy != null) {
            ((TextView) certificateView.findViewById(R.id.by_common))
                    .setText(issuedBy.getCName());
            ((TextView) certificateView.findViewById(R.id.by_org))
                    .setText(issuedBy.getOName());
            ((TextView) certificateView.findViewById(R.id.by_org_unit))
                    .setText(issuedBy.getUName());
        }

        // issued on:
        String issuedOn = formatCertificateDate(context, certificate.getValidNotBeforeDate());
        ((TextView) certificateView.findViewById(R.id.issued_on))
                .setText(issuedOn);

        // expires on:
        String expiresOn = formatCertificateDate(context, certificate.getValidNotAfterDate());
        ((TextView) certificateView.findViewById(R.id.expires_on))
                .setText(expiresOn);

        return certificateView;
    }
	
	
	private View getCertificateText(Context context, SslCertificate certificate) {
		return inflateCertificateView(certificate, context);
	}

}
