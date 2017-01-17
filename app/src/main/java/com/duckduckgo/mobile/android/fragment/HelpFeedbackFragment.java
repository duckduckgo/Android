package com.duckduckgo.mobile.android.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.duckduckgo.mobile.android.R;
import com.duckduckgo.mobile.android.bus.BusProvider;
import com.duckduckgo.mobile.android.events.RequestOpenWebPageEvent;
import com.duckduckgo.mobile.android.util.DDGUtils;
import com.duckduckgo.mobile.android.util.SESSIONTYPE;

public class HelpFeedbackFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceClickListener {

    public static final String TAG = "help_feedback_fragment";

    private Preference help;
    private Preference feedback;
    private Preference rate;

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        addPreferencesFromResource(R.xml.help_feedback);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = super.onCreateView(inflater, container, savedInstanceState);
        rootView.setBackgroundColor(getResources().getColor(R.color.background));
        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP) {
            getListView().setPadding(0, 0, 0, 0);
        }

        help = findPreference("help_center");
        help.setOnPreferenceClickListener(this);

        feedback = findPreference("leave_feedback");
        feedback.setOnPreferenceClickListener(this);

        rate = findPreference("rate_app");
        rate.setOnPreferenceClickListener(this);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if(preference==help) {
            BusProvider.getInstance().post(new RequestOpenWebPageEvent(
                    getActivity().getResources().getString(R.string.help_link), SESSIONTYPE.SESSION_BROWSE));
            return true;
        } else if(preference==feedback) {
            Intent feedbackIntent = DDGUtils.newEmailIntent(getActivity().getResources().getString(R.string.FeedbackTo),
                    getActivity().getResources().getString(R.string.FeedbackSubject), DDGUtils.getBuildInfo(getActivity()), "");
            startActivity(Intent.createChooser(
                    feedbackIntent, getActivity().getResources().getString(R.string.select_application)));
            return true;
        } else if(preference==rate) {
            Intent rateIntent = new Intent(Intent.ACTION_VIEW);
            rateIntent.setData(Uri.parse(getString(R.string.LinkToApp_Google)));
            startActivity(rateIntent);
            return true;
        }
        return false;
    }
}
