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

package net.micode.notes.gtask.remote;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

 
/*
 * Service是在一段不定的时间运行在后台，不和用户交互的应用组件
 * 主要方法：
 * private void startSync()  启动一个同步工作
 * private void cancelSync() 取消同步
 * public void onCreate()
 * public int onStartCommand(Intent intent, int flags, int startId)  service生命周期的组成部分，相当于重启service（比如在被暂停之后），而不是创建一个新的service
 * public void onLowMemory()  在没有内存的情况下如果存在service则结束掉这的service
 * public IBinder onBind()
 * public void sendBroadcast(String msg)   发送同步的相关通知
 * public static void startSync(Activity activity)
 * public static void cancelSync(Context context) 
 * public static boolean isSyncing()  判读是否在进行同步
 * public static String getProgressString()  获取当前进度的信息
 */
 
public class GTaskSyncService extends Service {
    public final static String ACTION_STRING_NAME = "sync_action_type";
 
    public final static int ACTION_START_SYNC = 0;
 
    public final static int ACTION_CANCEL_SYNC = 1;
 
    public final static int ACTION_INVALID = 2;
 
    public final static String GTASK_SERVICE_BROADCAST_NAME = "net.micode.notes.gtask.remote.gtask_sync_service";
 
    public final static String GTASK_SERVICE_BROADCAST_IS_SYNCING = "isSyncing";
 
    public final static String GTASK_SERVICE_BROADCAST_PROGRESS_MSG = "progressMsg";
 
    private static GTaskASyncTask mSyncTask = null;
 
    private static String mSyncProgress = "";
    
    //开始一个同步的工作
    private void startSync() {
        if (mSyncTask == null) {
            mSyncTask = new GTaskASyncTask(this, new GTaskASyncTask.OnCompleteListener() {
                public void onComplete() {
                    mSyncTask = null;
                    sendBroadcast("");
                    stopSelf();  
                }
            });
            sendBroadcast("");
            mSyncTask.execute(); //这个函数让任务是以单线程队列方式或线程池队列方式运行
        }
    }
    
    
    private void cancelSync() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }
 
    @Override
    public void onCreate() {  //初始化一个service
        mSyncTask = null;
    }
 
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle bundle = intent.getExtras();
        if (bundle != null && bundle.containsKey(ACTION_STRING_NAME)) {
            switch (bundle.getInt(ACTION_STRING_NAME, ACTION_INVALID)) {
            //两种情况，开始同步或者取消同步
                case ACTION_START_SYNC:
                    startSync();
                    break;
                case ACTION_CANCEL_SYNC:
                    cancelSync();
                    break;
                default:
                    break;
            }
            return START_STICKY; //等待新的intent来是这个service继续运行
        }
        return super.onStartCommand(intent, flags, startId);
    }
 
    @Override
    public void onLowMemory() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }
 
    public IBinder onBind(Intent intent) {  //不知道干吗用的
        return null;
    }
 
    public void sendBroadcast(String msg) {
        mSyncProgress = msg;
        Intent intent = new Intent(GTASK_SERVICE_BROADCAST_NAME);  //创建一个新的Intent
        intent.putExtra(GTASK_SERVICE_BROADCAST_IS_SYNCING, mSyncTask != null); //附加INTENT中的相应参数的值
        intent.putExtra(GTASK_SERVICE_BROADCAST_PROGRESS_MSG, msg);
        sendBroadcast(intent);   //发送这个通知
    }
 
    public static void startSync(Activity activity) {//执行一个service，service的内容里的同步动作就是开始同步
        GTaskManager.getInstance().setActivityContext(activity);
        Intent intent = new Intent(activity, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_START_SYNC);
        activity.startService(intent);
    }
 
    public static void cancelSync(Context context) {//执行一个service，service的内容里的同步动作就是取消同步
        Intent intent = new Intent(context, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_CANCEL_SYNC);
        context.startService(intent);
    }
 
    public static boolean isSyncing() {
        return mSyncTask != null;
    }
 
    public static String getProgressString() {
        return mSyncProgress;
    }
}