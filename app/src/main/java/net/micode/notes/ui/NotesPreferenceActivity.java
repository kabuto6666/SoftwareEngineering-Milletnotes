/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.ui;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.remote.GTaskSyncService;

/**
 *NotesPreferenceActivity，在小米便签中主要实现的是对背景颜色和字体大小的数据储存。
 * 继承了PreferenceActivity主要功能为对系统信息和配置进行自动保存的Activity
 */
public class NotesPreferenceActivity extends PreferenceActivity {
    public static final String PREFERENCE_NAME = "notes_preferences";//优先名

    public static final String PREFERENCE_SYNC_ACCOUNT_NAME = "pref_key_account_name";//同步账号名

    public static final String PREFERENCE_LAST_SYNC_TIME = "pref_last_sync_time";//上次同步时间

    public static final String PREFERENCE_SET_BG_COLOR_KEY = "pref_key_bg_random_appear";//首选默认背景色 

    private static final String PREFERENCE_SYNC_ACCOUNT_KEY = "pref_sync_account_key";//同步账号密码

    private static final String AUTHORITIES_FILTER_KEY = "authorities";//? 权限过滤密码

    private PreferenceCategory mAccountCategory;//账号分组

    private GTaskReceiver mReceiver;//任务接收器

    private Account[] mOriAccounts;//账户

    private boolean mHasAddedAccount;//账户的hash标记

    @Override
    /*
     * 创建一个activity，在函数里完成所有的正常静态设置
     */
    protected void onCreate(Bundle icicle) {//参数Bundle icicle：存放了 activity 当前的状态
        super.onCreate(icicle);

        /* using the app icon for navigation */
        getActionBar().setDisplayHomeAsUpEnabled(true);//在左上角加一个返回图标

        addPreferencesFromResource(R.xml.preferences);//添加xml来源并显示 xml
        mAccountCategory = (PreferenceCategory) findPreference(PREFERENCE_SYNC_ACCOUNT_KEY);//根据同步账户关键码来初始化分组
        mReceiver = new GTaskReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(GTaskSyncService.GTASK_SERVICE_BROADCAST_NAME);
        registerReceiver(mReceiver, filter);
        //初始化同步组件

        //获取listvivew，ListView用于列出所有选择
        mOriAccounts = null;
        View header = LayoutInflater.from(this).inflate(R.layout.settings_header, null);
        getListView().addHeaderView(header, null, true);//在listview组件上方添加其他组件
    }

    @Override
    /*
     * 交互功能的实现，用于接受用户的输入
     */
    protected void onResume() {
        super.onResume();//先执行父类 的交互实现

        // need to set sync account automatically if user has added a new account
        if (mHasAddedAccount) {
            //若用户新加了账户则自动设置同步账户
            Account[] accounts = getGoogleAccounts();//获取google同步账户
            if (mOriAccounts != null && accounts.length > mOriAccounts.length) {//若原账户不为空且当前账户有增加数据
                //更新账户
                for (Account accountNew : accounts) {
                    boolean found = false;
                    for (Account accountOld : mOriAccounts) {
                        if (TextUtils.equals(accountOld.name, accountNew.name)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        setSyncAccount(accountNew.name);
                        break;
                    }
                }
            }
        }
        refreshUI();//调用refreshUI函数来更新UI页面中的数据显示和标签界面
    }
    @Override
    /*
     * 销毁一个activity
     */
    protected void onDestroy() {
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);//注销接收器
        }
        super.onDestroy();//执行父类的销毁动作
    }

    /*
     * 重新设置账户信息
     */
    private void loadAccountPreference() {
        mAccountCategory.removeAll();//销毁所有的分组
        //建立首选项
        Preference accountPref = new Preference(this);
        final String defaultAccount = getSyncAccountName(this);
        accountPref.setTitle(getString(R.string.preferences_account_title));
        accountPref.setSummary(getString(R.string.preferences_account_summary));
         //设置首选项的大标题和小标题
        accountPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {//建立监听器
                if (!GTaskSyncService.isSyncing()) {
                    if (TextUtils.isEmpty(defaultAccount)) {//若是第一次建立账户显示选择账户提示对话框
                        // the first time to set account
                        showSelectAccountAlertDialog();
                    } else {//若是已经建立账户则显示修改对话框并进行修改操作
                        // if the account has already been set, we need to promp
                        // user about the risk
                        showChangeAccountConfirmAlertDialog();
                    }
                } else {//若没有同步，则在toast中显示不能修改
                    Toast.makeText(NotesPreferenceActivity.this,
                            R.string.preferences_toast_cannot_change_account, Toast.LENGTH_SHORT)
                            .show();
                }
                return true;
            }
        });

        mAccountCategory.addPreference(accountPref);//根据新建首选项编辑新的账户分组
    }
    /*
     * 设置按键的状态和最后同步的时间
     */
    private void loadSyncButton() {
        //获取同步按钮控件和最终同步时间的的窗口
        Button syncButton = (Button) findViewById(R.id.preference_sync_button);
        TextView lastSyncTimeView = (TextView) findViewById(R.id.prefenerece_sync_status_textview);

        // set button state设置按钮的状态
        if (GTaskSyncService.isSyncing()) {//同步状态下设置按钮显示
            syncButton.setText(getString(R.string.preferences_button_sync_cancel));
            syncButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    GTaskSyncService.cancelSync(NotesPreferenceActivity.this);//按钮显示文本为“取消同步”以及监听器
                }
            });
        } else { //若是不同步则设置按钮显示的文本
            syncButton.setText(getString(R.string.preferences_button_sync_immediately));
            syncButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    GTaskSyncService.startSync(NotesPreferenceActivity.this);//按钮显示的文本为“立即同步”以及对应监听器
                }
            });
        }
        syncButton.setEnabled(!TextUtils.isEmpty(getSyncAccountName(this)));//设置按键可用

        // set last sync time设置最终同步时间
        if (GTaskSyncService.isSyncing()) {//同步时
            lastSyncTimeView.setText(GTaskSyncService.getProgressString());
            lastSyncTimeView.setVisibility(View.VISIBLE);
             //根据当前同步服务器设置时间显示框的文本以及可见性
        } else {//非同步时
            long lastSyncTime = getLastSyncTime(this);
            if (lastSyncTime != 0) {
                lastSyncTimeView.setText(getString(R.string.preferences_last_sync_time,
                        DateFormat.format(getString(R.string.preferences_last_sync_time_format),
                                lastSyncTime)));
                lastSyncTimeView.setVisibility(View.VISIBLE);//根据最后同步时间的信息来编辑时间显示框的文本内容和可见性
            } else {//若时间为空
                lastSyncTimeView.setVisibility(View.GONE);//设置为不可见状态
            }
        }
    }
/*
 * 刷新标签界面
 */
    private void refreshUI() {
        loadAccountPreference();
        loadSyncButton();
    }
    /*
     * 显示账户选择的对话框并进行账户的设置
     */
    private void showSelectAccountAlertDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);//创建一个新的对话框
        //初始化对话框
        View titleView = LayoutInflater.from(this).inflate(R.layout.account_dialog_title, null);
        TextView titleTextView = (TextView) titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(getString(R.string.preferences_dialog_select_account_title));
        TextView subtitleTextView = (TextView) titleView.findViewById(R.id.account_dialog_subtitle);
        subtitleTextView.setText(getString(R.string.preferences_dialog_select_account_tips));
        //设置对话框标题以及子标题的内容
        dialogBuilder.setCustomTitle(titleView);
        dialogBuilder.setPositiveButton(null, null);
        //设置对话框的自定义标题
        Account[] accounts = getGoogleAccounts();
        String defAccount = getSyncAccountName(this);
        //获步账户信息
        mOriAccounts = accounts;
        mHasAddedAccount = false;

        if (accounts.length > 0) {//若账户不为空
            CharSequence[] items = new CharSequence[accounts.length];
            final CharSequence[] itemMapping = items;
            int checkedItem = -1;
            int index = 0;
            for (Account account : accounts) {
                if (TextUtils.equals(account.name, defAccount)) {
                    checkedItem = index;//在账户列表中查询到所需账户
                }
                items[index++] = account.name;
            }
            dialogBuilder.setSingleChoiceItems(items, checkedItem,//在对话框建立一个单选的复选框
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            setSyncAccount(itemMapping[which].toString());
                            dialog.dismiss();//取消对话框
                            refreshUI();
                        }//设置点击后执行的事件，包括检录新同步账户和刷新标签界面
                    });
        }

        View addAccountView = LayoutInflater.from(this).inflate(R.layout.add_account_text, null);//给新加账户对话框设置自定义样式
        dialogBuilder.setView(addAccountView);

        final AlertDialog dialog = dialogBuilder.show();//显示对话框
        addAccountView.setOnClickListener(new View.OnClickListener() {//建立新加账户对话框的监听器
            public void onClick(View v) {
                mHasAddedAccount = true;//将新加账户的hash置true
                Intent intent = new Intent("android.settings.ADD_ACCOUNT_SETTINGS");//建立网络建立组件
                intent.putExtra(AUTHORITIES_FILTER_KEY, new String[] {
                    "gmail-ls"
                });
                startActivityForResult(intent, -1);//跳回上一个选项
                dialog.dismiss();
            }
        });
    }
    /*
     * 显示账户选择对话框和相关账户操作
     */
    private void showChangeAccountConfirmAlertDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);//创建一个新的对话框
        //初始化对话框
        View titleView = LayoutInflater.from(this).inflate(R.layout.account_dialog_title, null);
        TextView titleTextView = (TextView) titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(getString(R.string.preferences_dialog_change_account_title,
                getSyncAccountName(this)));
        TextView subtitleTextView = (TextView) titleView.findViewById(R.id.account_dialog_subtitle);
        subtitleTextView.setText(getString(R.string.preferences_dialog_change_account_warn_msg));
        //根据同步修改的账户信息设置标题以及子标题的内容
        dialogBuilder.setCustomTitle(titleView);
      //设置对话框自定义标题
        CharSequence[] menuItemArray = new CharSequence[] {
                getString(R.string.preferences_menu_change_account),
                getString(R.string.preferences_menu_remove_account),
                getString(R.string.preferences_menu_cancel)
        };
        dialogBuilder.setItems(menuItemArray, new DialogInterface.OnClickListener() {//设置对话框要显示的一个list，用于显示几个命令时,即change，remove，cancel等
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    showSelectAccountAlertDialog();//进入账户选择对话框
                } else if (which == 1) {
                    removeSyncAccount();
                    refreshUI();//删除账户并且跟新便签界面
                }
            }
        });
        dialogBuilder.show();//显示对话框
    }
    /*
     * 获取谷歌账户
     */
    private Account[] getGoogleAccounts() {
        AccountManager accountManager = AccountManager.get(this);
        return accountManager.getAccountsByType("com.google");
    }
    /*
     * 设置同步账户
     */
    private void setSyncAccount(String account) {
        if (!getSyncAccountName(this).equals(account)) {//假如该账号不在同步账号列表中
            SharedPreferences settings = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            //编辑共享的首选项
            if (account != null) {
                editor.putString(PREFERENCE_SYNC_ACCOUNT_NAME, account);
            } else {
                editor.putString(PREFERENCE_SYNC_ACCOUNT_NAME, "");
            }
            editor.commit();//将该账号加入到首选项中，提交

            // clean up last sync time
            setLastSyncTime(this, 0);//将最后同步时间清零

            // clean up local gtask related info
            new Thread(new Runnable() {//重置当地同步任务的信息
                public void run() {
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.GTASK_ID, "");
                    values.put(NoteColumns.SYNC_ID, 0);
                    getContentResolver().update(Notes.CONTENT_NOTE_URI, values, null, null);
                }
            }).start();

            Toast.makeText(NotesPreferenceActivity.this,
                    getString(R.string.preferences_toast_success_set_accout, account),
                    Toast.LENGTH_SHORT).show();//将toast的文本信息置为“设置账户成功”并显示出来
        }
    }
    /*
     * 删除同步账户
     */
    private void removeSyncAccount() {
        SharedPreferences settings = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();//设置共享首选项
        if (settings.contains(PREFERENCE_SYNC_ACCOUNT_NAME)) {//当前首选项中有账户就删除
            editor.remove(PREFERENCE_SYNC_ACCOUNT_NAME);
        }
        if (settings.contains(PREFERENCE_LAST_SYNC_TIME)) {//删除当前首选项中同步时间
            editor.remove(PREFERENCE_LAST_SYNC_TIME);
        }
        editor.commit();//提交更新后的数据

        // clean up local gtask related info
        new Thread(new Runnable() {//重置本地同步任务信息
            public void run() {
                ContentValues values = new ContentValues();
                values.put(NoteColumns.GTASK_ID, "");
                values.put(NoteColumns.SYNC_ID, 0);
                getContentResolver().update(Notes.CONTENT_NOTE_URI, values, null, null);
            }
        }).start();
    }
    /*
     * 获取同步账户名称
     */
    public static String getSyncAccountName(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        return settings.getString(PREFERENCE_SYNC_ACCOUNT_NAME, "");
    }
    /*
     * 设置最终同步的时间
     */
    public static void setLastSyncTime(Context context, long time) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();// 从共享首选项中找到相关账户并获取其编辑器
        editor.putLong(PREFERENCE_LAST_SYNC_TIME, time);
        editor.commit();//编辑最终同步时间并提交更新
    }
    /*
     * 获取最终同步时间
     */
    public static long getLastSyncTime(Context context) {//通过共享的首选项里的信息直接获取
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        return settings.getLong(PREFERENCE_LAST_SYNC_TIME, 0);
    }

    /*
     * 接受同步信息
     */
    private class GTaskReceiver extends BroadcastReceiver {//继承BroadcastReceiver

        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUI();
            if (intent.getBooleanExtra(GTaskSyncService.GTASK_SERVICE_BROADCAST_IS_SYNCING, false)) {
                TextView syncStatus = (TextView) findViewById(R.id.prefenerece_sync_status_textview);
                syncStatus.setText(intent
                        .getStringExtra(GTaskSyncService.GTASK_SERVICE_BROADCAST_PROGRESS_MSG));
            }

        }
    }
    /*
     * 处理菜单的选项
     */
    public boolean onOptionsItemSelected(MenuItem item) {//参数MenuItem菜单选项
        switch (item.getItemId()) {//根据选项的id选择
            case android.R.id.home://这里只有一个主页
                Intent intent = new Intent(this, NotesListActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;//在主页情况下在创建连接组件intent，发出清空的信号并开始一个相应的activity
            default:
                return false;
        }
    }
}
