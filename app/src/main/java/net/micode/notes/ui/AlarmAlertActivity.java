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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowManager;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;

import java.io.IOException;


public class AlarmAlertActivity extends Activity implements OnClickListener, OnDismissListener {
    /**
     * 文本在数据库存储中的ID号
     */
    private long mNoteId;
    /**
     * 闹钟提示时出现的文本片段
     */
    private String mSnippet;
    private static final int SNIPPET_PREW_MAX_LEN = 60;
    MediaPlayer mPlayer;

    /**
     * onCreate()函数是在activity初始化的时候调用的，即创建闹钟时的回调函数
     * @param savedInstanceState If the activity is being re-initialized after
     *     previously being shut down then this Bundle contains the data it most
     *     recently supplied in {@link #onSaveInstanceState}.
     * <b><i>Note: Otherwise it is null.</i></b>
     *
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Bundle类型的数据与Map类型的数据相似，都是以key-value的形式存储数据的
        //onsaveInstanceState方法是用来保存Activity的状态的
        //能从onCreate的参数savedInsanceState中获得状态数据
        super.onCreate(savedInstanceState);
        //设置当前窗体的显示特征(如全屏、无标题等)
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        final Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        if (!isScreenOn()) {
            win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON//保持窗体点亮
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON//将窗体点亮
                    | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON//允许窗体点亮时锁屏
                    | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR);//在手机锁屏后如果到了闹钟提示时间，点亮屏幕
        }

        Intent intent = getIntent();

        try {
            //根据ID从数据库中获取标签的内容；
            mNoteId = Long.valueOf(intent.getData().getPathSegments().get(1));
            //getContentResolver（）是实现数据共享，实例存储。
            mSnippet = DataUtils.getSnippetById(this.getContentResolver(), mNoteId);
            //判断标签片段是否达到符合长度
            mSnippet = mSnippet.length() > SNIPPET_PREW_MAX_LEN ? mSnippet.substring(0,
                    SNIPPET_PREW_MAX_LEN) + getResources().getString(R.string.notelist_string_info)
                    : mSnippet;
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return;
        }

        mPlayer = new MediaPlayer();
        if (DataUtils.visibleInNoteDatabase(getContentResolver(), mNoteId, Notes.TYPE_NOTE)) {
            //弹出对话框
            showActionDialog();
            //闹钟提示音激发
            playAlarmSound();
        } else {
            //完成闹钟动作
            finish();
        }
    }

    /**
     * 判断屏幕是否锁屏，调用系统函数判断，最后返回值是布尔类型
     */
    private boolean isScreenOn() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm.isScreenOn();
    }

    /**
     * 播放闹铃
     */
    private void playAlarmSound() {
        //调用系统的铃声管理URI，得到闹钟提示音
        Uri url = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);

        int silentModeStreams = Settings.System.getInt(getContentResolver(),
                Settings.System.MODE_RINGER_STREAMS_AFFECTED, 0);

        if ((silentModeStreams & (1 << AudioManager.STREAM_ALARM)) != 0) {
            mPlayer.setAudioStreamType(silentModeStreams);
        } else {
            mPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
        }
        try {
            //根据Url设置多媒体数据来源
            mPlayer.setDataSource(this, url);
            //准备同步
            mPlayer.prepare();
            //设置是否循环播放
            mPlayer.setLooping(true);
            //开始播放
            mPlayer.start();
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalStateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * 展示对话框
     */
    private void showActionDialog() {
        //AlertDialog的构造方法全部是Protected的
        //所以不能直接通过new一个AlertDialog来创建出一个AlertDialog。
        //要创建一个AlertDialog，就要用到AlertDialog.Builder中的create()方法
        //如这里的dialog就是新建了一个AlertDialog
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        //为对话框设置标题
        dialog.setTitle(R.string.app_name);
        //为对话框设置内容
        dialog.setMessage(mSnippet);
        //给对话框添加"Yes"按钮
        dialog.setPositiveButton(R.string.notealert_ok, this);
        if (isScreenOn()) {
            //对话框添加"No"按钮
            dialog.setNegativeButton(R.string.notealert_enter, this);
        }
        dialog.show().setOnDismissListener(this);
    }

    /**
     * 对话框接收到点击事件后的响应函数
     * @param dialog the dialog that received the click
     * @param which the button that was clicked (ex.
     *              {@link DialogInterface#BUTTON_POSITIVE}) or the position
     *              of the item clicked
     */
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            //取消操作
            case DialogInterface.BUTTON_NEGATIVE:
                //实现两个类间的数据传输
                Intent intent = new Intent(this, NoteEditActivity.class);
                //设置动作属性
                intent.setAction(Intent.ACTION_VIEW);
                //实现key-value对
                //EXTRA_UID为key；mNoteId为键
                intent.putExtra(Intent.EXTRA_UID, mNoteId);
                //开始动作
                startActivity(intent);
                break;
            default:
                break;
        }
    }

    /**
     * 被取消的对话框将被传递到方法中
     * @param dialog the dialog that was dismissed will be passed into the
     *               method
     */
    public void onDismiss(DialogInterface dialog) {
        //停止闹钟声音
        stopAlarmSound();
        //完成
        finish();
    }

    private void stopAlarmSound() {
        if (mPlayer != null) {
            //停止播放
            mPlayer.stop();
            //释放MediaPlayer对象
            mPlayer.release();
            mPlayer = null;
        }
    }
}
