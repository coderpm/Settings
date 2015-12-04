package com.android.settings.notification;

import android.animation.LayoutTransition;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.*;
import android.service.notification.StatusBarNotification;
import android.util.ArrayMap;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import com.android.settings.PinnedHeaderListFragment;
import com.android.settings.R;
import com.android.settings.UserSpinnerAdapter;
import com.android.settings.Utils;

import java.util.ArrayList;
import java.util.Map;


/**
 * Just a sectioned list of installed applications, nothing else to index
 **/
public class NotificationBinClear extends PinnedHeaderListFragment {
    private static final String TAG = "YAAP";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final ArrayList<AppRow> mSortedRows = new ArrayList<>();


    private Context mContext;
    private LayoutInflater mInflater;
    private UserManager mUM;

    private NotificationBinAdapter mAdapter;
    private static SharedPreferences preferenceSetting;
    private Editor preferenceSettingEditor;
    static String settings_FileName = "notificationbin_settings";


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getActivity();
        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mAdapter = new NotificationBinAdapter(mContext);
        mUM = UserManager.get(mContext);

        getActivity().setTitle(R.string.notificationbin_settings_title);
        preferenceSetting = mContext.getSharedPreferences(settings_FileName, Context.MODE_PRIVATE);
        preferenceSettingEditor = preferenceSetting.edit();

        Log.d("YAAP", "Inside onCreate of NotificationBin Settings");

    } //End of onCreate Method

    private void sendSharedPreferencesData() {

        //Get the Map from SharedPreferences file
        Map<String, ?> allEntries = preferenceSetting.getAll();
        Bundle sharedPrefbundle = new Bundle();

        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            sharedPrefbundle.putBoolean(entry.getKey(), (Boolean) entry.getValue());
        }

        //Make a new intent and broadcast it for system ui to catch it
        Intent sendPref = new Intent();
        sendPref.setAction("com.android.settings.sendPref");
        sendPref.putExtra("com.android.settings.prefBundle", sharedPrefbundle);
        mContext.sendBroadcast(sendPref);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.notificationbin_app_list, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        UserSpinnerAdapter mProfileSpinnerAdapter = Utils.createUserSpinnerAdapter(mUM, mContext);
        if (mProfileSpinnerAdapter != null) {
            Spinner mSpinner = (Spinner) getActivity().getLayoutInflater().inflate(
                    R.layout.spinner_view, null);
            mSpinner.setAdapter(mProfileSpinnerAdapter);
            setPinnedHeaderView(mSpinner);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        repositionScrollbar();
        getListView().setAdapter(mAdapter);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (DEBUG) Log.d(TAG, "Saving listView state");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadAppsList();
    }

    public void loadAppsList() {
        AsyncTask.execute(mCollectAppsRunnable);
    }

    private void repositionScrollbar() {

        final int sbWidthPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                getListView().getScrollBarSize(),
                getResources().getDisplayMetrics());
        final View parent = (View) getView().getParent();
        final int eat = Math.min(sbWidthPx, parent.getPaddingEnd());
        if (eat <= 0) return;
        if (DEBUG) Log.d(TAG, String.format("Eating %dpx into %dpx padding for %dpx scroll, ld=%d",
                eat, parent.getPaddingEnd(), sbWidthPx, getListView().getLayoutDirection()));
        parent.setPaddingRelative(parent.getPaddingStart(), parent.getPaddingTop(),
                parent.getPaddingEnd() - eat, parent.getPaddingBottom());
    }

    /**
     * Corresponds to entries in R.layout.notificationbin_settings
     **/
    private static class ViewHolder {
        ViewGroup row;
        TextView title;
        TextView subtitle;
        CheckBox toggleSwitch;
        View rowDivider;

    }

    public static class AppRow {

        //Used for AppInfo object         
        public String pkg;
        public String subText;
        public Boolean hasCheckBox;
    }

    private class NotificationBinAdapter extends ArrayAdapter<AppRow> {
        public NotificationBinAdapter(Context context) {
            super(context, 0, 0);
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            AppRow r = getItem(position);
            View v;
            if (convertView == null) {
                v = newView(parent, r);
            } else {
                v = convertView;
            }
            bindView(v, r, false /*animate*/);
            return v;
        }

        public View newView(ViewGroup parent, AppRow r) {
            if (!(r instanceof AppRow)) {
                return mInflater.inflate(R.layout.notification_app_section, parent, false);
            }
            final View v = mInflater.inflate(R.layout.notificationbin_clear_list, parent, false);
            final ViewHolder vh = new ViewHolder();
            vh.row = (ViewGroup) v;
            vh.row.setLayoutTransition(new LayoutTransition());
            vh.row.setLayoutTransition(new LayoutTransition());
            vh.title = (TextView) v.findViewById(android.R.id.title);
            vh.subtitle = (TextView) v.findViewById(android.R.id.text1);
            vh.toggleSwitch = (CheckBox) v.findViewById(R.id.binswitch);
            vh.rowDivider = v.findViewById(R.id.row_divider);
            v.setTag(vh);
            return v;
        }

        private void enableLayoutTransitions(ViewGroup vg, boolean enabled) {
            if (enabled) {
                vg.getLayoutTransition().enableTransitionType(LayoutTransition.APPEARING);
                vg.getLayoutTransition().enableTransitionType(LayoutTransition.DISAPPEARING);
            } else {
                vg.getLayoutTransition().disableTransitionType(LayoutTransition.APPEARING);
                vg.getLayoutTransition().disableTransitionType(LayoutTransition.DISAPPEARING);
            }
        }

        public void bindView(final View view, AppRow r, boolean animate) {
            if (!(r instanceof AppRow)) {
                // it's a section row
                final TextView tv = (TextView) view.findViewById(android.R.id.title);
                tv.setText(r.pkg);
                return;
            }

            final AppRow row = r;
            final ViewHolder vh = (ViewHolder) view.getTag();
            enableLayoutTransitions(vh.row, animate);
            enableLayoutTransitions(vh.row, animate);

            vh.title.setText(row.pkg);
            vh.subtitle.setText(row.subText);
            if (row.hasCheckBox) {

                boolean isChecked = preferenceSetting.getBoolean("clearAll", false);
                vh.toggleSwitch.setChecked(isChecked);

                vh.toggleSwitch.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        CheckBox checkBox = (CheckBox) view;
                        Boolean isChecked = checkBox.isChecked();
                        preferenceSettingEditor.putBoolean("clearAll", isChecked);
                        preferenceSettingEditor.commit();
                        sendSharedPreferencesData();
                    }
                });
            }
            vh.toggleSwitch.setVisibility(row.hasCheckBox ? View.VISIBLE : View.GONE);

        }
    }

    /**
     * End of NotificationBinAdapter class
     **/


    private final Runnable mCollectAppsRunnable = new Runnable() {

        @Override
        public void run() {

            mSortedRows.clear();
            mAdapter.clear();

            AppRow row = new AppRow();
            row.pkg = "Clear All Sticky";
            row.subText = "Clear all sticky notifications";
            row.hasCheckBox = true;
            mSortedRows.add(row);
            mAdapter.add(row);

            AppRow row2 = new AppRow();
            row2.pkg = "Info";
            row2.subText = "Check this to clear all sticky notifications when clear all button is pressed";
            row2.hasCheckBox = false;
            mSortedRows.add(row2);
            mAdapter.add(row2);

            AppRow row3 = new AppRow();
            row3.pkg = "Help";
            row3.subText = "Add whatever u want";
            row3.hasCheckBox = false;
            mSortedRows.add(row3);
            mAdapter.add(row3);

        }/** End of run method **/
    };


} /**
 * End of class
 */
