package com.android.settings.notification;

import static com.android.settings.notification.AppNotificationSettings.EXTRA_HAS_SETTINGS_INTENT;
import static com.android.settings.notification.AppNotificationSettings.EXTRA_SETTINGS_INTENT;

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
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.SectionIndexer;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Button;

import com.android.settings.PinnedHeaderListFragment;
import com.android.settings.R;
import com.android.settings.Settings.NotificationAppListActivity;
import com.android.settings.UserSpinnerAdapter;
import com.android.settings.Utils;

import java.text.Collator;
import java.util.*;


import android.service.notification.StatusBarNotification;


/** Just a sectioned list of installed applications, nothing else to index **/
public class NotificationBinStored extends PinnedHeaderListFragment
        implements OnItemSelectedListener {
    private static final String TAG = "NotificationAppList";
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
    private final ArrayList<String> mSections = new ArrayList<String>();

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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getActivity();
        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mAdapter = new NotificationBinAdapter(mContext);
        mUM = UserManager.get(mContext);
        mPM = mContext.getPackageManager();
        mLauncherApps = (LauncherApps) mContext.getSystemService(Context.LAUNCHER_APPS_SERVICE);



        SbnRecevier sbnRecevier = new SbnRecevier();
        IntentFilter inF = new IntentFilter();
        inF.addAction("com.android.systemui.updateMap");
        mContext.registerReceiver(sbnRecevier,inF);

        getActivity().setTitle(R.string.notificationbin_stored_title);

        Intent publishMapIn = new Intent();
        publishMapIn.setAction("com.android.settings.publishSbn");
        mContext.sendBroadcast(publishMapIn);

        Log.d("YAAP", "Inside onCreate of NotificationBinStored");
             
    }

    public static class SbnRecevierStat extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {


            String action = intent.getAction();
            Log.d("YAAP","SBN Receiver "+ action);

            if (action.equals("com.android.systemui.updateMapStat")){
                updateArrayMap(intent);
            }else{
                Log.e("YAAP","Unknown sbn action intent");
            }
        }
    }

    public  class SbnRecevier extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {


            String action = intent.getAction();
            Log.d("YAAP","SBN Receiver "+ action);

            if (action.equals("com.android.systemui.updateMap")){
                updateArrayMap(intent);
                loadAppsList();
            }else{
                Log.e("YAAP","Unknown sbn action intent");
            }
        }
    }

    private static void updateArrayMap(Intent intent) {
        Bundle sbnBundle = intent.getBundleExtra("com.android.systemui.sbnMap");
        ArrayMap<String, StatusBarNotification> arrayMap = new ArrayMap<>();
        for (String sbnKey : sbnBundle.keySet()){
            StatusBarNotification sbn  = (StatusBarNotification) sbnBundle.get(sbnKey);
            arrayMap.put(sbnKey,sbn);
        }
        mAllNotifications = arrayMap;
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.notification_bin_list, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mProfileSpinnerAdapter = Utils.createUserSpinnerAdapter(mUM, mContext);
        if (mProfileSpinnerAdapter != null) {
            mSpinner = (Spinner) getActivity().getLayoutInflater().inflate(
                    R.layout.spinner_view, null);
            mSpinner.setAdapter(mProfileSpinnerAdapter);
            mSpinner.setOnItemSelectedListener(this);
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

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Log.d("YAAP", "in onItemSelected");
        UserHandle selectedUser = mProfileSpinnerAdapter.getUserHandle(position);
        if (selectedUser.getIdentifier() != UserHandle.myUserId()) {
            Intent intent = new Intent(getActivity(), NotificationAppListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            mContext.startActivityAsUser(intent, selectedUser);
            // Go back to default selection, which is the first one; this makes sure that pressing
            // the back button takes you into a consistent state
            mSpinner.setSelection(0);
        }
    }


    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    public void loadAppsList() {
        Log.d("YAAP","in FUnction loadAppsList");
        AsyncTask.execute(mCollectAppsRunnable);
    }

    private String getSection(CharSequence label) {
        if (label == null || label.length() == 0) return SECTION_BEFORE_A;
        final char c = Character.toUpperCase(label.charAt(0));
        if (c < 'A') return SECTION_BEFORE_A;
        if (c > 'Z') return SECTION_AFTER_Z;
        return Character.toString(c);
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

    /** Corresponds to entries in R.layout.notification_bin_list **/
    private static class ViewHolder {
        ViewGroup row;
        ImageView icon;
        TextView title;
        TextView subtitle;
        View rowDivider;
        Button removeNotificationButton;
    }

    public static class AppRow {
        
        //Used for StatusBarNotification object fields        
        public String pkg;
        public int id;
        public String tag;
      
        public int uid;
        public String opPkg;
        public int initialPid;
        public Notification notification;
        public UserHandle user;
        public long postTime;
        public String key;

        public Drawable icon;
        public PendingIntent contentIntent;
        public CharSequence tickerText;


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
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            Log.d("YAAP", "in getItemViewType");
            AppRow r = getItem(position);
            return r instanceof AppRow ? 1 : 0;
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
            final View v = mInflater.inflate(R.layout.notification_bin, parent, false);
            final ViewHolder vh = new ViewHolder();
            vh.row = (ViewGroup) v;
            vh.row.setLayoutTransition(new LayoutTransition());
            vh.row.setLayoutTransition(new LayoutTransition());
            vh.title = (TextView) v.findViewById(android.R.id.title);
            vh.subtitle = (TextView) v.findViewById(android.R.id.text1);
            vh.icon = (ImageView)v.findViewById(android.R.id.icon);
            vh.removeNotificationButton = (Button) v.findViewById(R.id.button_send);
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
           
            //onCLick Listener for each row
            vh.row.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent pendIntent = new Intent();
                //TODO: Write code to include row.contentIntent into pendIntent
                //pendIntent.getActivity(row.contentIntent)

                    mContext.startActivity(pendIntent);                    
//                    mContext.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
//                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
//                           .putExtra(Settings.EXTRA_APP_PACKAGE, row.pkg)
//                            .putExtra(Settings.EXTRA_APP_UID, row.uid));
//                            .putExtra(EXTRA_HAS_SETTINGS_INTENT, row.settingsIntent != null)
  //                          .putExtra(EXTRA_SETTINGS_INTENT, row.settingsIntent));
                }
            }); /** End of onClick row listener  **/

            //Implement Listener for Button
            vh.removeNotificationButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent removeIn = new Intent();
                    removeIn.setAction("com.android.settings.unhideNotif");
                    removeIn.putExtra("com.android.settings.sbnKey",row.key);
                    mContext.sendBroadcast(removeIn);
                    mAllNotifications.remove(row.key);
                    loadAppsList();
                }
            });  /** End of onClick button listener  **/

            
            enableLayoutTransitions(vh.row, animate);
            vh.title.setText(row.pkg);
            Log.d("YAAP","Bind view Row "+row.icon);

            vh.icon.setImageDrawable(row.icon);
            final String sub = getSubtitle(row);
            vh.subtitle.setText(sub);
            vh.subtitle.setVisibility(!sub.isEmpty() ? View.VISIBLE : View.GONE);
        }

        private String getSubtitle(AppRow row) {
            CharSequence tickerText = row.tickerText;
            String ticker = tickerText.toString();
            return ticker;
        }


    } /** End of NotificationBinAdapter class **/


    public static AppRow loadAppRow(StatusBarNotification statusBarObj) {
        final AppRow row = new AppRow();
        Log.d("YAAP","Loading StatusBarNotification data in loadAppRow");
        row.pkg = statusBarObj.getPackageName();
        row.id = statusBarObj.getId();
        row.tag = statusBarObj.getTag();
        row.uid = statusBarObj.getUid();
        row.opPkg = statusBarObj.getOpPkg();
        row.initialPid = statusBarObj.getInitialPid();
        row.notification = statusBarObj.getNotification();
        row.postTime = statusBarObj.getPostTime();
        row.key = statusBarObj.getKey();

        row.tickerText = statusBarObj.getNotification().tickerText;
        row.contentIntent = statusBarObj.getNotification().contentIntent;

        try{
            ApplicationInfo appIn = mPM.getApplicationInfo(row.pkg,0);
            row.icon = mPM.getApplicationIcon(appIn);

        }catch(NameNotFoundException e){
            Log.d("YAAP","Application name not found in APPINFO");
            e.printStackTrace();
        }
        if(row.icon == null){
            Log.d("YAAP","Setting row.icon "+row.icon);
            row.icon = mPM.getDefaultActivityIcon();
        }
    
        return row;
    }

    private final Runnable mCollectAppsRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (mRows) {
                
                if (DEBUG) Log.d(TAG, "Collecting notifications...");
                mRows.clear();
                mSortedRows.clear();
//                ArrayMap<String,StatusBarNotification> mAllNotifications = new ArrayMap<String,StatusBarNotification>();
//                mAllNotifications = hiddenNotificationObj.getDisplayMap(mContext);
                Log.d("YAAP", "SIze of mAllNotifications ArrayMap is"+Integer.toString(mAllNotifications.size()));

                //Collect all stored sticky notifications from map
                ArrayList<StatusBarNotification> mNotifications = new ArrayList<StatusBarNotification>();
                mNotifications.addAll(mAllNotifications.values());
                Log.d("YAAP", "SIze of mNotifications ArrayList is"+Integer.toString(mNotifications.size()));
   

                for(StatusBarNotification notifs : mNotifications){
                    String key = notifs.getPackageName() + Long.toString(notifs.getPostTime()) + Integer.toString(notifs.getId());

                    final AppRow row = loadAppRow(notifs);
                    mRows.put(key, row);
                }

                mSortedRows.addAll(mRows.values());
                mHandler.post(mRefreshAppsListRunnable);
            } 
        }/** End of run method **/
    };

    /** Write this function if list is not refreshed **/
    private void refreshDisplayedItems() {
        if (DEBUG) Log.d(TAG, "Refreshing notifications...");
        mAdapter.clear();
        synchronized (mSortedRows) {
            String section = null;
            final int N = mSortedRows.size();
            boolean first = true;
            for (int i = 0; i < N; i++) {
                final AppRow row = mSortedRows.get(i);
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
}
