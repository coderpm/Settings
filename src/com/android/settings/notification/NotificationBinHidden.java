package com.android.settings.notification;

import android.animation.LayoutTransition;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.util.ArrayMap;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.*;
import com.android.settings.PinnedHeaderListFragment;
import com.android.settings.R;
import com.android.settings.UserSpinnerAdapter;
import com.android.settings.Utils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/** Just a sectioned list of installed applications, nothing else to index **/
public class NotificationBinHidden extends PinnedHeaderListFragment
       {
    private static final String TAG = "YAAP";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

           private final Handler mHandler = new Handler();
    private final ArrayMap<String, AppRow> mRows = new ArrayMap<>();
    private final ArrayList<AppRow> mSortedRows = new ArrayList<>();
    

    private Context mContext;
    private LayoutInflater mInflater;
    private Parcelable mListViewState;

           private static PackageManager mPM;
    private UserManager mUM;
    private LauncherApps mLauncherApps;

    private NotificationBinAdapter mAdapter;
    private static SharedPreferences preferenceSetting;
    private Editor preferenceSettingEditor;
    protected static String settings_FileName = "notificationbin_settings";


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
        preferenceSetting = mContext.getSharedPreferences(settings_FileName,Context.MODE_PRIVATE);
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
                    getSharedPreferencesData(context);
            }else{
                Log.e("YAAP","Unknown getPreference action intent");
            }
        }
    }

    private static void getSharedPreferencesData(Context context) {

        //Get the Map from SharedPreferences file
        SharedPreferences sharedPrefs;
        sharedPrefs = context.getSharedPreferences(settings_FileName,Context.MODE_PRIVATE);
        Map<String, ?> allEntries = sharedPrefs.getAll();


        Log.d("YAAP","Pref all map "+allEntries);
        Bundle sharedPrefbundle = new Bundle();

        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            sharedPrefbundle.putBoolean(entry.getKey(),(Boolean)entry.getValue());
        }

        Log.d("YAAP","Pref all bundle "+sharedPrefbundle.size());


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
        UserSpinnerAdapter mProfileSpinnerAdapter = Utils.createUserSpinnerAdapter(mUM, mContext);
        if (mProfileSpinnerAdapter != null) {
            Spinner mSpinner = (Spinner) getActivity().getLayoutInflater().inflate(
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
        ImageView icon;
        TextView title;
        CheckBox toggleSwitch;
        View rowDivider;
    
    }

    public static class AppRow {
        
        //Used for AppInfo object         
        public String pkg;
        public int uid;
        public Drawable icon;
        public CharSequence label;
        public Intent settingsIntent;

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

//        @Override
//        public int getItemViewType(int position) {
//            Log.d("YAAP", "in getItemViewType");
//            AppRow r = getItem(position);
//            return r instanceof AppRow ? 1 : 0;
//        }

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
            final View v = mInflater.inflate(R.layout.notificationbin_settings, parent, false);
            final ViewHolder vh = new ViewHolder();
            vh.row = (ViewGroup) v;
            vh.row.setLayoutTransition(new LayoutTransition());
            vh.row.setLayoutTransition(new LayoutTransition());
            vh.title = (TextView) v.findViewById(android.R.id.title);
            vh.icon = (ImageView) v.findViewById(android.R.id.icon);
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
                final TextView tv = (TextView)view.findViewById(android.R.id.title);
                tv.setText(r.pkg);
                return;
            }

            final AppRow row = r;
            final ViewHolder vh = (ViewHolder) view.getTag();
            enableLayoutTransitions(vh.row, animate);
            enableLayoutTransitions(vh.row, animate);
            
/*            //Check if appName is present in pref 
                if yes then get boolean value
                    if boolean is yes --> then set default as checked
                    if boolean is false --> then set default as unchecked
                if no -- appName not in preference 
                    then add appname in preference file     
*/


            if(preferenceSetting.contains(row.pkg+"_normal")){
                boolean checkappName = preferenceSetting.getBoolean(row.pkg+"_normal",false);
                if(checkappName ){
                    //Set default value to it
                    vh.toggleSwitch.setChecked(true);
                    Log.d("YAAP","Appname: "+row.pkg+" checked to true");
                }else {
                    //Set default value to it
                    vh.toggleSwitch.setChecked(false);
                    Log.d("YAAP","Appname: "+row.pkg+" checked to false");
                }
            }else{
                Log.d("YAAP","Appname: "+row.pkg+" is not present");
                //Appname is not present
                preferenceSettingEditor.putBoolean(row.pkg+"_normal",false);
                preferenceSettingEditor.commit();
            }


            vh.toggleSwitch.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    CheckBox buttonView = (CheckBox) view;
                    Boolean isChecked = buttonView.isChecked();
                    preferenceSettingEditor.putBoolean(row.pkg+"_normal",isChecked);
                    preferenceSettingEditor.commit();
                    //Send a intent to SystemUI
                    sendSharedPreferencesData();
                }
            });

            vh.title.setText(row.label);
            vh.icon.setImageDrawable(row.icon);
        }
    } /** End of NotificationBinAdapter class **/

    /** Function to load all the application data and put them into Approw object**/
    public static AppRow loadAppRow(PackageManager pm, ApplicationInfo app){

        final AppRow row = new AppRow();

        row.pkg = app.packageName;
        row.uid = app.uid;
        row.icon = app.loadIcon(pm);

        try {
            row.label = app.loadLabel(pm);
        } catch (Throwable t) {
            Log.e(TAG, "Error loading application label for " + row.pkg, t);
            row.label = row.pkg;
        }
    
        return row;

    } /** end of loadAppRow **/

    private final Runnable mCollectAppsRunnable = new Runnable() {

        @Override
        public void run() {

            synchronized (mRows) {
                
                if (DEBUG) Log.d(TAG, "Collecting applications for notification bin settings...");
                
                mRows.clear();
                mSortedRows.clear();

                // collect all launchable apps, plus any packages that have notification settings
                final List<ApplicationInfo> appInfos = new ArrayList<>();

               final List<LauncherActivityInfo> lais
                        = mLauncherApps.getActivityList(null /* all */,
                            UserHandle.getCallingUserHandle());
                if (DEBUG) Log.d(TAG, "  launchable activities:");
                for (LauncherActivityInfo lai : lais) {
                    if (DEBUG) Log.d(TAG, "    " + lai.getComponentName().toString());
                    appInfos.add(lai.getApplicationInfo());
                }

                Log.d(TAG, "ApplicationInfo size is "+Integer.toString(appInfos.size()));
            
                //Add all applications to mRows
                for (ApplicationInfo info : appInfos) {

                    final String key = info.packageName;
                    if (mRows.containsKey(key)) {
                        continue;
                    }
                    final AppRow row = loadAppRow(mPM, info);
                    mRows.put(key, row);
                }
            
                mSortedRows.addAll(mRows.values());
                Collections.sort(mSortedRows, mRowComparator);
                //Sort the mSortedRows
                for (Approw allApplications : mSortedRows) {
                    System.out.println(name);
                }


                mHandler.post(mRefreshAppsListRunnable);
            } 
        }/** End of run method **/
    };

    private static final Comparator<AppRow> mRowComparator = new Comparator<AppRow>() {
        private final Collator sCollator = Collator.getInstance();
        @Override
        public int compare(AppRow lhs, AppRow rhs) {
            return sCollator.compare(lhs.label, rhs.label);
        }
    };


    /** Write this function if list is not refreshed **/
    private void refreshDisplayedItems() {
        if (DEBUG) Log.d(TAG, "Refreshing applications list...");

        mAdapter.clear();
      
        synchronized (mSortedRows) {
      
            final int N = mSortedRows.size();
            for (final AppRow row : mSortedRows) {
                mAdapter.add(row);
            }
        }

        if (mListViewState != null) {
            if (DEBUG) Log.d(TAG, "Restoring listView state");
            getListView().onRestoreInstanceState(mListViewState);
            mListViewState = null;
        }
    } /** End of refreshDisplayedItems **/
  
  private final Runnable mRefreshAppsListRunnable = new Runnable() {
        @Override
        public void run() {
            refreshDisplayedItems();
        }
    };

} /** End of class */
