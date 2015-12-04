package com.android.settings.notification;

import android.animation.LayoutTransition;
import android.app.INotificationManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.util.ArrayMap;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.android.settings.PinnedHeaderListFragment;
import com.android.settings.R;
import com.android.settings.Settings.NotificationAppListActivity;
import com.android.settings.UserSpinnerAdapter;
import com.android.settings.Utils;

import java.text.Collator;
import java.util.*;
import java.util.Map;

import android.service.notification.StatusBarNotification;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;


/** Just a sectioned list of installed applications, nothing else to index **/
public class NotificationBinClear extends PinnedHeaderListFragment
       {
    private static final String TAG = "YAAP";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final String EMPTY_SUBTITLE = "";
    private static final String SECTION_BEFORE_A = "*";
    private static final String SECTION_AFTER_Z = "**";
    private static final Intent APP_NOTIFICATION_PREFS_CATEGORY_INTENT
            = new Intent(Intent.ACTION_MAIN)
                .addCategory(Notification.INTENT_CATEGORY_NOTIFICATION_PREFERENCES);

    private final Handler mHandler = new Handler();
    private final ArrayMap<String, AppRow> mRows = new ArrayMap<String, AppRow>();
    private final ArrayList<AppRow> mSortedRows = new ArrayList<AppRow>();
    

    private Context mContext;
    private LayoutInflater mInflater;
    private Signature[] mSystemSignature;
    private Parcelable mListViewState;
    private UserSpinnerAdapter mProfileSpinnerAdapter;
    private Spinner mSpinner;

    private static PackageManager mPM;
    private UserManager mUM;
    private LauncherApps mLauncherApps;

    private static ArrayMap<String,StatusBarNotification> mAllNotifications = new ArrayMap<>();
    private ArrayMap<String,AppRow> mStick = new ArrayMap<String,AppRow>();
     
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
        mPM = mContext.getPackageManager();
        mLauncherApps = (LauncherApps) mContext.getSystemService(Context.LAUNCHER_APPS_SERVICE);
       
        getActivity().setTitle(R.string.notificationbin_settings_title);

        //TODO: GET the shared preference data from xml
        preferenceSetting = mContext.getSharedPreferences(settings_FileName,Context.MODE_WORLD_WRITEABLE);
        preferenceSettingEditor = preferenceSetting.edit();

        Log.d("YAAP", "Inside onCreate of NotificationBin Settings");
             
    } //End of onCreate Method

    /** Static intent receiver to receive request from SystemUI for 
        sending the file contents back to SystemUI
     **/
    public static  class PrefReceiverStat extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();
            Log.d("YAAP","Preferences Receiver "+ action);

            if (action.equals("com.android.systemui.getPreference")){
                    getSharedPreferencesData(context,intent);
            }else{
                Log.e("YAAP","Unknown getPreference action intent");
            }
        }
    }

    private static void getSharedPreferencesData(Context context, Intent intent) {

        //Get the Map from SharedPreferences file
        SharedPreferences sharedPrefs;
        sharedPrefs = context.getSharedPreferences(settings_FileName,Context.MODE_PRIVATE);
        Map<String, ?> allEntries = sharedPrefs.getAll();
        Bundle sharedPrefbundle = new Bundle();

        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            sharedPrefbundle.putBoolean(entry.getKey(),(Boolean)entry.getValue());
        } 


        //Make a new intent and broadcast it for system ui to catch it
        Intent sendPref = new Intent();
        sendPref.setAction("com.android.settings.sendPref");
        sendPref.putExtra("com.android.settings.prefBundle",sharedPrefbundle);
        context.sendBroadcast(sendPref);
    }

    private void sendSharedPreferencesData() {

        //Get the Map from SharedPreferences file
        Map<String, ?> allEntries = preferenceSetting.getAll();
        Bundle sharedPrefbundle = new Bundle();

        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            sharedPrefbundle.putBoolean(entry.getKey(),(Boolean)entry.getValue());
        } 

        //Make a new intent and broadcast it for system ui to catch it
        Intent sendPref = new Intent();
        sendPref.setAction("com.android.settings.sendPref");
        sendPref.putExtra("com.android.settings.prefBundle",sharedPrefbundle);
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
        mProfileSpinnerAdapter = Utils.createUserSpinnerAdapter(mUM, mContext);
        if (mProfileSpinnerAdapter != null) {
            mSpinner = (Spinner) getActivity().getLayoutInflater().inflate(
                    R.layout.spinner_view, null);
            mSpinner.setAdapter(mProfileSpinnerAdapter);
//            mSpinner.setOnItemSelectedListener(this);
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
        mListViewState = getListView().onSaveInstanceState();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mListViewState = null;  // you're dead to me
    }

    @Override
    public void onResume() {
        super.onResume();
        loadAppsList();
    }

    public void loadAppsList() {
        Log.d("YAAP","in FUnction loadAppsList");
        AsyncTask.execute(mCollectAppsRunnable);
    }

    private void repositionScrollbar() {
    
        final int sbWidthPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                getListView().getScrollBarSize(),
                getResources().getDisplayMetrics());
        final View parent = (View)getView().getParent();
        final int eat = Math.min(sbWidthPx, parent.getPaddingEnd());
        if (eat <= 0) return;
        if (DEBUG) Log.d(TAG, String.format("Eating %dpx into %dpx padding for %dpx scroll, ld=%d",
                eat, parent.getPaddingEnd(), sbWidthPx, getListView().getLayoutDirection()));
        parent.setPaddingRelative(parent.getPaddingStart(), parent.getPaddingTop(),
                parent.getPaddingEnd() - eat, parent.getPaddingBottom());
    }

    /** Corresponds to entries in R.layout.notificationbin_settings **/
    private static class ViewHolder {
        ViewGroup row;
        TextView title;
        CheckBox toggleSwitch;
        View rowDivider;
    
    }

    public static class AppRow {
        
        //Used for AppInfo object         
        public String pkg;
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
//                final TextView tv = (TextView)view.findViewById(android.R.id.title);
//                tv.setText(r.pkg);
                return;
            }

            final AppRow row = r;
            final ViewHolder vh = (ViewHolder) view.getTag();
            enableLayoutTransitions(vh.row, animate);
            enableLayoutTransitions(vh.row, animate);
            
            vh.title.setText(row.pkg);
        }
    } /** End of NotificationBinAdapter class **/


    private final Runnable mCollectAppsRunnable = new Runnable() {

        @Override
        public void run() {

            mSortedRows.clear();
            AppRow row = new AppRow();
            row.pkg = "First";

            mSortedRows.add(row);
    
            mAdapter.clear();
            mAdapter.add(row);

        }/** End of run method **/
    };

    

} /** End of class */
