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
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.model.WorkingNote.NoteSettingChangedListener;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.tool.ResourceParser.TextAppearanceResources;
import net.micode.notes.ui.DateTimePickerDialog.OnDateTimeSetListener;
import net.micode.notes.ui.NoteEditText.OnTextViewChangeListener;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/***
 * 	该类主要是针对标签的编辑
 */
public class NoteEditActivity extends AppCompatActivity implements OnClickListener,
        NoteSettingChangedListener, OnTextViewChangeListener {
    private class HeadViewHolder {
        public TextView tvModified;//文本视图-修改

        public ImageView ivAlertIcon;//图像视图-闹钟图标

        public TextView tvAlertDate;//文本试图-闹钟时间

        public ImageView ibSetBgColor;//图像视图-设置背景颜色
    }

    /**
     * 背景颜色的选择（即给用户选择的背景颜色）
     */
    private static final Map<Integer, Integer> sBgSelectorBtnsMap = new HashMap<Integer, Integer>();

    static {
        sBgSelectorBtnsMap.put(R.id.iv_bg_yellow, ResourceParser.YELLOW);
        sBgSelectorBtnsMap.put(R.id.iv_bg_red, ResourceParser.RED);
        sBgSelectorBtnsMap.put(R.id.iv_bg_blue, ResourceParser.BLUE);
        sBgSelectorBtnsMap.put(R.id.iv_bg_green, ResourceParser.GREEN);
        sBgSelectorBtnsMap.put(R.id.iv_bg_white, ResourceParser.WHITE);
    }

    /**
     * 背景颜色已选择（即用户已按下颜色对应的选择）
     */
    private static final Map<Integer, Integer> sBgSelectorSelectionMap = new HashMap<Integer, Integer>();

    static {
        sBgSelectorSelectionMap.put(ResourceParser.YELLOW, R.id.iv_bg_yellow_select);
        sBgSelectorSelectionMap.put(ResourceParser.RED, R.id.iv_bg_red_select);
        sBgSelectorSelectionMap.put(ResourceParser.BLUE, R.id.iv_bg_blue_select);
        sBgSelectorSelectionMap.put(ResourceParser.GREEN, R.id.iv_bg_green_select);
        sBgSelectorSelectionMap.put(ResourceParser.WHITE, R.id.iv_bg_white_select);
    }

    /**
     * 字体大小的控制
     */
    private static final Map<Integer, Integer> sFontSizeBtnsMap = new HashMap<Integer, Integer>();

    static {
        sFontSizeBtnsMap.put(R.id.ll_font_large, ResourceParser.TEXT_LARGE);
        sFontSizeBtnsMap.put(R.id.ll_font_small, ResourceParser.TEXT_SMALL);
        sFontSizeBtnsMap.put(R.id.ll_font_normal, ResourceParser.TEXT_MEDIUM);
        sFontSizeBtnsMap.put(R.id.ll_font_super, ResourceParser.TEXT_SUPER);
    }

    /**
     * 用户已选择具体的字体大小
     */
    private static final Map<Integer, Integer> sFontSelectorSelectionMap = new HashMap<Integer, Integer>();

    static {
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_LARGE, R.id.iv_large_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SMALL, R.id.iv_small_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_MEDIUM, R.id.iv_medium_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SUPER, R.id.iv_super_select);
    }

    private static final String TAG = "NoteEditActivity";

    private HeadViewHolder mNoteHeaderHolder;

    /**
     * 私有化一个界面操作mHeadViewPanel，对表头的操作
     */
    private View mHeadViewPanel;

    /**
     * 私有化一个界面操作mNoteBgColorSelector，对背景颜色的操作
     */
    private View mNoteBgColorSelector;

    /**
     * 私有化一个界面操作mFontSizeSelector，对标签字体大小的操作
     */
    private View mFontSizeSelector;

    /**
     * 声明编辑控件，对文本操作
     */
    private EditText mNoteEditor;

    /**
     * 私有化一个界面操作mNoteEditorPanel，文本编辑的控制板
     */
    private View mNoteEditorPanel;

    /**
     * 对模板WorkingNote的初始化
     */
    private WorkingNote mWorkingNote;

    /**
     * 私有化SharedPreferences的数据存储方式
     * 它的本质是基于XML文件存储key-value键值对数据
     */
    private SharedPreferences mSharedPrefs;
    private int mFontSizeId;//用于操作字体的大小

    private static final String PREFERENCE_FONT_SIZE = "pref_font_size";

    private static final int SHORTCUT_ICON_TITLE_MAX_LEN = 10;

    public static final String TAG_CHECKED = String.valueOf('\u221A');
    public static final String TAG_UNCHECKED = String.valueOf('\u25A1');

    private LinearLayout mEditTextList;

    private String mUserQuery;
    private Pattern mPattern;

    private final int PHOTO_REQUEST = 1;//请求码


    /**
     * 初始化窗口
     *
     * @param savedInstanceState If the activity is being re-initialized after
     *                           previously being shut down then this Bundle contains the data it most
     *                           recently supplied in {@link #onSaveInstanceState}.
     *                           <p>如果活动在之前关闭后正在重新初始化，则此捆绑包包含它最近在中提供的数据</p><br/>
     *                           <b><i>Note: Otherwise it is null.</i></b>
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.note_edit);

        if (savedInstanceState == null && !initActivityState(getIntent())) {
            finish();
            return;
        }
        initResources();

        //根据id获取添加图片按钮
        final ImageButton add_img_btn = (ImageButton) findViewById(R.id.add_img_btn);
        //为点击图片按钮设置监听器
        add_img_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onClick: click add image button");
                //ACTION_GET_CONTENT: 允许用户选择特殊种类的数据，并返回（特殊种类的数据：照一张相片或录一段音）
                Intent loadImage = new Intent(Intent.ACTION_GET_CONTENT);
                //Category属性用于指定当前动作（Action）被执行的环境.
                //CATEGORY_OPENABLE; 用来指示一个ACTION_GET_CONTENT的intent
                loadImage.addCategory(Intent.CATEGORY_OPENABLE);
                loadImage.setType("image/*");
                startActivityForResult(loadImage, PHOTO_REQUEST);
            }
        });
    }

    /**
     * Current activity may be killed when the memory is low. Once it is killed, for another time
     * user load this activity, we should restore the former state<br/>
     * 当内存不足时，当前活动可能会被终止。一旦它被杀死，下次用户加载此活动时，我们应该恢复以前的状态<br/>
     * 即当前函数用来保存现场
     */
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null && savedInstanceState.containsKey(Intent.EXTRA_UID)) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.putExtra(Intent.EXTRA_UID, savedInstanceState.getLong(Intent.EXTRA_UID));
            if (!initActivityState(intent)) {
                finish();
                return;
            }
            Log.d(TAG, "Restoring from killed activity");
        }
    }

    private boolean initActivityState(Intent intent) {
        /**
         * If the user specified the {@link Intent#ACTION_VIEW} but not provided with id,
         * then jump to the NotesListActivity
         * 如果用户实例化标签时，系统并未给出标签ID，则跳转到NotesListActivity
         */
        mWorkingNote = null;
        if (TextUtils.equals(Intent.ACTION_VIEW, intent.getAction())) {
            long noteId = intent.getLongExtra(Intent.EXTRA_UID, 0);
            mUserQuery = "";

            /**
             * Starting from the searched result
             * 根据键值去查找id
             */
            if (intent.hasExtra(SearchManager.EXTRA_DATA_KEY)) {
                noteId = Long.parseLong(intent.getStringExtra(SearchManager.EXTRA_DATA_KEY));
                mUserQuery = intent.getStringExtra(SearchManager.USER_QUERY);
            }
            //如果未找到就要跳转到NotesListActivity
            if (!DataUtils.visibleInNoteDatabase(getContentResolver(), noteId, Notes.TYPE_NOTE)) {
                Intent jump = new Intent(this, NotesListActivity.class);
                startActivity(jump);
                showToast(R.string.error_note_not_exist);
                finish();
                return false;
            } else {
                mWorkingNote = WorkingNote.load(this, noteId);
                if (mWorkingNote == null) {
                    Log.e(TAG, "load note failed with note id" + noteId);
                    finish();
                    return false;
                }
            }
            //setSoftInputMode——软键盘输入模式
            getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        } else if (TextUtils.equals(Intent.ACTION_INSERT_OR_EDIT, intent.getAction())) {
            // New note
            // intent.getAction()大多用于broadcast发送广播时给机制（intent）设置一个action，就是一个字符串
            // 用户可以通过receive（接受）intent，通过getAction得到的字符串，来决定做什么
            long folderId = intent.getLongExtra(Notes.INTENT_EXTRA_FOLDER_ID, 0);
            int widgetId = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            int widgetType = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_TYPE,
                    Notes.TYPE_WIDGET_INVALIDE);
            int bgResId = intent.getIntExtra(Notes.INTENT_EXTRA_BACKGROUND_ID,
                    ResourceParser.getDefaultBgId(this));

            // Parse call-record note
            String phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            long callDate = intent.getLongExtra(Notes.INTENT_EXTRA_CALL_DATE, 0);
            //将电话号码与手机的号码簿相关
            if (callDate != 0 && phoneNumber != null) {
                if (TextUtils.isEmpty(phoneNumber)) {
                    Log.w(TAG, "The call record number is null");
                }
                long noteId = 0;
                if ((noteId = DataUtils.getNoteIdByPhoneNumberAndCallDate(getContentResolver(),
                        phoneNumber, callDate)) > 0) {
                    mWorkingNote = WorkingNote.load(this, noteId);
                    if (mWorkingNote == null) {
                        Log.e(TAG, "load call note failed with note id" + noteId);
                        finish();
                        return false;
                    }
                } else {
                    mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId,
                            widgetType, bgResId);
                    mWorkingNote.convertToCallNote(phoneNumber, callDate);
                }
            } else {
                mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId, widgetType,
                        bgResId);
            }

            getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                            | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        } else {
            Log.e(TAG, "Intent not specified action, should not support");
            finish();
            return false;
        }
        mWorkingNote.setOnSettingStatusChangedListener(this);
        return true;
    }

    /**
     * 能获得用户焦点：可以操作
     */
    @Override
    protected void onResume() {
        super.onResume();
        initNoteScreen();//初始化便签屏幕
    }

    /**
     * 初始化便签界面
     */
    private void initNoteScreen() {
        mNoteEditor.setTextAppearance(this, TextAppearanceResources
                .getTexAppearanceResource(mFontSizeId));
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            switchToListMode(mWorkingNote.getContent());
        } else {
            mNoteEditor.setText(getHighlightQueryResult(mWorkingNote.getContent(), mUserQuery));
            mNoteEditor.setSelection(mNoteEditor.getText().length());
        }
        for (Integer id : sBgSelectorSelectionMap.keySet()) {
            findViewById(sBgSelectorSelectionMap.get(id)).setVisibility(View.GONE);
        }
        mHeadViewPanel.setBackgroundResource(mWorkingNote.getTitleBgResId());
        mNoteEditorPanel.setBackgroundResource(mWorkingNote.getBgColorResId());

        mNoteHeaderHolder.tvModified.setText(DateUtils.formatDateTime(this,
                mWorkingNote.getModifiedDate(), DateUtils.FORMAT_SHOW_DATE
                        | DateUtils.FORMAT_NUMERIC_DATE | DateUtils.FORMAT_SHOW_TIME
                        | DateUtils.FORMAT_SHOW_YEAR));

        /**
         * TODO: Add the menu for setting alert. Currently disable it because the DateTimePicker is not ready
         */
        showAlertHeader();
        //将有图片路径的位置转换为图片
        convertToImage();
    }

    /**
     * 设置闹钟的显示
     */
    private void showAlertHeader() {
        if (mWorkingNote.hasClockAlert()) {
            long time = System.currentTimeMillis();
            //如果系统时间大于了闹钟设置的时间，那么闹钟失效
            if (time > mWorkingNote.getAlertDate()) {
                mNoteHeaderHolder.tvAlertDate.setText(R.string.note_alert_expired);
            } else {
                mNoteHeaderHolder.tvAlertDate.setText(DateUtils.getRelativeTimeSpanString(
                        mWorkingNote.getAlertDate(), time, DateUtils.MINUTE_IN_MILLIS));
            }
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.VISIBLE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.VISIBLE);
        } else {
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.GONE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.GONE);
        }
        ;
    }

    //路径字符串格式 转换为 图片image格式
    private void convertToImage() {
        NoteEditText noteEditText = (NoteEditText) findViewById(R.id.note_edit_view); //获取当前的edit
        Editable editable = noteEditText.getText();//1.获取text
        String noteText = editable.toString(); //2.将note内容转换为字符串
        int length = editable.length(); //内容的长度
        //3.截取img片段 [local]+uri+[local]，提取uri
        for (int i = 0; i < length; i++) {
            for (int j = i; j < length; j++) {
                String img_fragment = noteText.substring(i, j + 1); //img_fragment：关于图片路径的片段
                if (img_fragment.length() > 15 && img_fragment.endsWith("[/local]") && img_fragment.startsWith("[local]")) {
                    int limit = 7;  //[local]为7个字符
                    //[local][/local]共15个字符，剩下的为真正的path长度
                    int len = img_fragment.length() - 15;
                    //从[local]之后的len个字符就是path
                    String path = img_fragment.substring(limit, limit + len);//获取到了图片路径
                    Bitmap bitmap = null;
                    Log.d(TAG, "图片的路径是：" + path);
                    try {
                        bitmap = BitmapFactory.decodeFile(path);//将图片路径解码为图片格式
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (bitmap != null) {  //若图片存在
                        Log.d(TAG, "图片不为null");
                        ImageSpan imageSpan = new ImageSpan(NoteEditActivity.this, bitmap);
                        //4.创建一个SpannableString对象，以便插入用ImageSpan对象封装的图像
                        String ss = "[local]" + path + "[/local]";
                        SpannableString spannableString = new SpannableString(ss);
                        //5.将指定的标记对象附加到文本的开始...结束范围
                        spannableString.setSpan(imageSpan, 0, ss.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        Log.d(TAG, "Create spannable string success!");
                        Editable edit_text = noteEditText.getEditableText();
                        edit_text.delete(i, i + len + 15); //6.删掉图片路径的文字
                        edit_text.insert(i, spannableString); //7.在路径的起始位置插入图片
                    }
                }
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        initActivityState(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        /**
         * For new note without note id, we should firstly save it to
         * generate a id. If the editing note is not worth saving, there
         * is no id which is equivalent to create new note
         * 对于没有便签id的新便签，我们应该首先保存它以生成id。如果编辑中的便签还没有保存，则其没有id，没有等同于创建新便签
         */
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }
        outState.putLong(Intent.EXTRA_UID, mWorkingNote.getNoteId());
        Log.d(TAG, "Save working note id: " + mWorkingNote.getNoteId() + " onSaveInstanceState");
    }

    /**
     * @param ev MotionEvent是对屏幕触控的传递机制
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        //颜色选择器在屏幕上可见
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE
                && !inRangeOfView(mNoteBgColorSelector, ev)) {
            mNoteBgColorSelector.setVisibility(View.GONE);
            return true;
        }
        //字体大小选择器在屏幕上可见
        if (mFontSizeSelector.getVisibility() == View.VISIBLE
                && !inRangeOfView(mFontSizeSelector, ev)) {
            mFontSizeSelector.setVisibility(View.GONE);
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * 对屏幕触控的坐标进行操作
     */
    private boolean inRangeOfView(View view, MotionEvent ev) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        int x = location[0];
        int y = location[1];
        //如果触控的位置超出了给定的范围，返回false
        if (ev.getX() < x
                || ev.getX() > (x + view.getWidth())
                || ev.getY() < y
                || ev.getY() > (y + view.getHeight())) {
            return false;
        }
        return true;
    }

    /**
     * 对标签各项属性内容的初始化
     */
    private void initResources() {
        mHeadViewPanel = findViewById(R.id.note_title);
        mNoteHeaderHolder = new HeadViewHolder();
        mNoteHeaderHolder.tvModified = (TextView) findViewById(R.id.tv_modified_date);
        mNoteHeaderHolder.ivAlertIcon = (ImageView) findViewById(R.id.iv_alert_icon);
        mNoteHeaderHolder.tvAlertDate = (TextView) findViewById(R.id.tv_alert_date);
        mNoteHeaderHolder.ibSetBgColor = (ImageView) findViewById(R.id.btn_set_bg_color);
        mNoteHeaderHolder.ibSetBgColor.setOnClickListener(this);
        mNoteEditor = (EditText) findViewById(R.id.note_edit_view);
        mNoteEditorPanel = findViewById(R.id.sv_note_edit);
        mNoteBgColorSelector = findViewById(R.id.note_bg_color_selector);
        for (int id : sBgSelectorBtnsMap.keySet()) {
            ImageView iv = (ImageView) findViewById(id);
            iv.setOnClickListener(this);
        }

        mFontSizeSelector = findViewById(R.id.font_size_selector);
        for (int id : sFontSizeBtnsMap.keySet()) {
            View view = findViewById(id);
            view.setOnClickListener(this);
        }
        ;
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mFontSizeId = mSharedPrefs.getInt(PREFERENCE_FONT_SIZE, ResourceParser.BG_DEFAULT_FONT_SIZE);
        /**
         * HACKME: Fix bug of store the resource id in shared preference.
         * The id may larger than the length of resources, in this case,
         * return the {@link ResourceParser#BG_DEFAULT_FONT_SIZE}
         */
        if (mFontSizeId >= TextAppearanceResources.getResourcesSize()) {
            mFontSizeId = ResourceParser.BG_DEFAULT_FONT_SIZE;
        }
        mEditTextList = (LinearLayout) findViewById(R.id.note_edit_list);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (saveNote()) {
            Log.d(TAG, "Note data was saved with length:" + mWorkingNote.getContent().length());
        }
        clearSettingState();
    }

    /**
     * 同步桌面小组件
     */
    private void updateWidget() {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x.class);
        } else if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x.class);
        } else {
            Log.e(TAG, "Unspported widget type");
            return;
        }

        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{
                mWorkingNote.getWidgetId()
        });

        sendBroadcast(intent);
        setResult(RESULT_OK, intent);
    }

    /**
     * 点击响应
     *
     * @param v The view that was clicked.
     */
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_set_bg_color) {
            mNoteBgColorSelector.setVisibility(View.VISIBLE);
            findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(
                    View.VISIBLE);
        } else if (sBgSelectorBtnsMap.containsKey(id)) {
            findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(
                    View.GONE);
            mWorkingNote.setBgColorId(sBgSelectorBtnsMap.get(id));
            mNoteBgColorSelector.setVisibility(View.GONE);
        } else if (sFontSizeBtnsMap.containsKey(id)) {
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.GONE);
            mFontSizeId = sFontSizeBtnsMap.get(id);
            mSharedPrefs.edit().putInt(PREFERENCE_FONT_SIZE, mFontSizeId).commit();
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.VISIBLE);
            if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
                getWorkingText();
                switchToListMode(mWorkingNote.getContent());
            } else {
                mNoteEditor.setTextAppearance(this,
                        TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
            }
            mFontSizeSelector.setVisibility(View.GONE);
        }
    }

    /**
     * 当手机按下back键时，执行此方法
     */
    @Override
    public void onBackPressed() {
        if (clearSettingState()) {
            return;
        }

        saveNote();
        super.onBackPressed();
    }

    private boolean clearSettingState() {
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE) {
            mNoteBgColorSelector.setVisibility(View.GONE);
            return true;
        } else if (mFontSizeSelector.getVisibility() == View.VISIBLE) {
            mFontSizeSelector.setVisibility(View.GONE);
            return true;
        }
        return false;
    }

    /**
     * 背景颜色改变
     */
    public void onBackgroundColorChanged() {
        findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(
                View.VISIBLE);
        mNoteEditorPanel.setBackgroundResource(mWorkingNote.getBgColorResId());
        mHeadViewPanel.setBackgroundResource(mWorkingNote.getTitleBgResId());
    }

    /**
     * 对选择菜单的准备
     *
     * @param menu The options menu as last shown or first initialized by
     *             onCreateOptionsMenu().
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (isFinishing()) {
            return true;
        }
        clearSettingState();
        menu.clear();
        // MenuInflater是用来实例化Menu目录下的Menu布局文件的
        if (mWorkingNote.getFolderId() == Notes.ID_CALL_RECORD_FOLDER) {
            getMenuInflater().inflate(R.menu.call_note_edit, menu);
        } else {
            getMenuInflater().inflate(R.menu.note_edit, menu);
        }
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_normal_mode);
        } else {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_list_mode);
        }
        if (mWorkingNote.hasClockAlert()) {
            menu.findItem(R.id.menu_alert).setVisible(false);
        } else {
            menu.findItem(R.id.menu_delete_remind).setVisible(false);
        }
        return true;
    }

    /**
     * 动态改变菜单选项内容
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            //创建一个新的便签
            case R.id.menu_new_note:
                createNewNote();
                break;
            //删除便签
            case R.id.menu_delete:
                //创建关于删除操作的对话框
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                //设置标题
                builder.setTitle(getString(R.string.alert_title_delete));
                //设置图标
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                //设置信息
                builder.setMessage(getString(R.string.alert_message_delete_note));
                //设置确定按钮
                builder.setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                deleteCurrentNote();
                                finish();
                            }
                        });
                //设置取消按钮
                builder.setNegativeButton(android.R.string.cancel, null);
                builder.show();
                break;
            //字体大小的改变
            case R.id.menu_font_size:
                mFontSizeSelector.setVisibility(View.VISIBLE);
                findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.VISIBLE);
                break;
            //列表模式的改变
            case R.id.menu_list_mode:
                mWorkingNote.setCheckListMode(mWorkingNote.getCheckListMode() == 0 ?
                        TextNote.MODE_CHECK_LIST : 0);
                break;
            //共享按钮
            case R.id.menu_share:
                getWorkingText();
                sendTo(this, mWorkingNote.getContent());
                break;
            //发送到桌面
            case R.id.menu_send_to_desktop:
                sendToDesktop();
                break;
            //创建闹钟
            case R.id.menu_alert:
                setReminder();
                break;
            //删除日期提醒
            case R.id.menu_delete_remind:
                mWorkingNote.setAlertDate(0, false);
                break;
            default:
                break;
        }
        return true;
    }

    /**
     * 建立事件提醒器
     */
    private void setReminder() {
        // 建立修改时间日期的对话框
        DateTimePickerDialog d = new DateTimePickerDialog(this, System.currentTimeMillis());
        d.setOnDateTimeSetListener(new OnDateTimeSetListener() {
            public void OnDateTimeSet(AlertDialog dialog, long date) {
                //选择提醒的日期
                mWorkingNote.setAlertDate(date, true);
            }
        });
        //显示对话框
        d.show();
    }

    /**
     * 将当前便签分享给支持的app<br/>
     * Share note to apps that support {@link Intent#ACTION_SEND} action
     * and {@text/plain} type
     */
    private void sendTo(Context context, String info) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, info);
        intent.setType("text/plain");
        context.startActivity(intent);
    }

    /**
     * 创建新便签
     */
    private void createNewNote() {
        // Firstly, save current editing notes
        saveNote();

        // For safety, start a new NoteEditActivity
        finish();
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mWorkingNote.getFolderId());
        startActivity(intent);
    }

    /**
     * 删除当前便签
     */
    private void deleteCurrentNote() {
        if (mWorkingNote.existInDatabase()) {
            HashSet<Long> ids = new HashSet<Long>();
            long id = mWorkingNote.getNoteId();
            if (id != Notes.ID_ROOT_FOLDER) {
                ids.add(id);
            } else {
                Log.d(TAG, "Wrong note id, should not happen");
            }
            if (!isSyncMode()) {
                if (!DataUtils.batchDeleteNotes(getContentResolver(), ids)) {
                    Log.e(TAG, "Delete Note error");
                }
            } else {
                if (!DataUtils.batchMoveToFolder(getContentResolver(), ids, Notes.ID_TRASH_FOLER)) {
                    Log.e(TAG, "Move notes to trash folder error, should not happens");
                }
            }
        }
        mWorkingNote.markDeleted(true);
    }

    /**
     * 是否同步
     */
    private boolean isSyncMode() {
        return NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0;
    }

    /**
     * 闹钟提醒改变
     */
    public void onClockAlertChanged(long date, boolean set) {
        /**
         * User could set clock to an unsaved note, so before setting the
         * alert clock, we should save the note first
         */
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }
        if (mWorkingNote.getNoteId() > 0) {
            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mWorkingNote.getNoteId()));
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
            AlarmManager alarmManager = ((AlarmManager) getSystemService(ALARM_SERVICE));
            showAlertHeader();
            if (!set) {
                alarmManager.cancel(pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, date, pendingIntent);
            }
        } else {
            /**
             * There is the condition that user has input nothing (the note is
             * not worthy saving), we have no note id, remind the user that he
             * should input something
             */
            Log.e(TAG, "Clock alert setting error");
            showToast(R.string.error_note_empty_for_clock);
        }
    }

    /**
     * 桌面组件改变
     */
    public void onWidgetChanged() {
        updateWidget();
    }

    /**
     * 删除编辑文本框所触发的事件
     */
    public void onEditTextDelete(int index, String text) {
        int childCount = mEditTextList.getChildCount();
        if (childCount == 1) {
            return;
        }

        for (int i = index + 1; i < childCount; i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text))
                    .setIndex(i - 1);
        }

        mEditTextList.removeViewAt(index);
        NoteEditText edit = null;
        if (index == 0) {
            edit = (NoteEditText) mEditTextList.getChildAt(0).findViewById(
                    R.id.et_edit_text);
        } else {
            edit = (NoteEditText) mEditTextList.getChildAt(index - 1).findViewById(
                    R.id.et_edit_text);
        }
        int length = edit.length();
        edit.append(text);
        edit.requestFocus();
        edit.setSelection(length);
    }

    /**
     * 进入编辑文本框所触发的事件
     */
    public void onEditTextEnter(int index, String text) {
        /**
         * Should not happen, check for debug
         */
        if (index > mEditTextList.getChildCount()) {
            Log.e(TAG, "Index out of mEditTextList boundrary, should not happen");
        }

        View view = getListItem(text, index);
        mEditTextList.addView(view, index);
        NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
        edit.requestFocus();
        edit.setSelection(0);
        for (int i = index + 1; i < mEditTextList.getChildCount(); i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text))
                    .setIndex(i);
        }
    }

    /**
     * 函数功能：切换至列表模式
     */
    private void switchToListMode(String text) {
        mEditTextList.removeAllViews();
        String[] items = text.split("\n");
        int index = 0;
        for (String item : items) {
            if (!TextUtils.isEmpty(item)) {
                mEditTextList.addView(getListItem(item, index));
                index++;
            }
        }
        mEditTextList.addView(getListItem("", index));
        mEditTextList.getChildAt(index).findViewById(R.id.et_edit_text).requestFocus();

        mNoteEditor.setVisibility(View.GONE);
        mEditTextList.setVisibility(View.VISIBLE);
    }

    /**
     * 获取高亮效果的反馈情况
     */
    private Spannable getHighlightQueryResult(String fullText, String userQuery) {
        SpannableString spannable = new SpannableString(fullText == null ? "" : fullText);
        if (!TextUtils.isEmpty(userQuery)) {
            mPattern = Pattern.compile(userQuery);
            Matcher m = mPattern.matcher(fullText);
            int start = 0;
            while (m.find(start)) {
                spannable.setSpan(
                        new BackgroundColorSpan(this.getResources().getColor(
                                R.color.user_query_highlight)), m.start(), m.end(),
                        Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                start = m.end();
            }
        }
        return spannable;
    }

    /**
     * 获取列表项
     */
    private View getListItem(String item, int index) {
        View view = LayoutInflater.from(this).inflate(R.layout.note_edit_list_item, null);
        final NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
        edit.setTextAppearance(this, TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
        CheckBox cb = ((CheckBox) view.findViewById(R.id.cb_edit_item));
        cb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                } else {
                    edit.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
                }
            }
        });

        if (item.startsWith(TAG_CHECKED)) {
            cb.setChecked(true);
            edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            item = item.substring(TAG_CHECKED.length(), item.length()).trim();
        } else if (item.startsWith(TAG_UNCHECKED)) {
            cb.setChecked(false);
            edit.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
            item = item.substring(TAG_UNCHECKED.length(), item.length()).trim();
        }

        edit.setOnTextViewChangeListener(this);
        edit.setIndex(index);
        edit.setText(getHighlightQueryResult(item, mUserQuery));
        return view;
    }

    /**
     * 便签内容发生改变所触发的事件
     */
    public void onTextChange(int index, boolean hasText) {
        if (index >= mEditTextList.getChildCount()) {
            Log.e(TAG, "Wrong index, should not happen");
            return;
        }
        if (hasText) {
            mEditTextList.getChildAt(index).findViewById(R.id.cb_edit_item).setVisibility(View.VISIBLE);
        } else {
            mEditTextList.getChildAt(index).findViewById(R.id.cb_edit_item).setVisibility(View.GONE);
        }
    }

    /**
     * 检查模式和列表模式的切换
     *
     * @param oldMode is previous mode before change
     * @param newMode is new mode
     */
    public void onCheckListModeChanged(int oldMode, int newMode) {
        if (newMode == TextNote.MODE_CHECK_LIST) {
            switchToListMode(mNoteEditor.getText().toString());
        } else {
            if (!getWorkingText()) {
                mWorkingNote.setWorkingText(mWorkingNote.getContent().replace(TAG_UNCHECKED + " ",
                        ""));
            }
            mNoteEditor.setText(getHighlightQueryResult(mWorkingNote.getContent(), mUserQuery));
            mEditTextList.setVisibility(View.GONE);
            mNoteEditor.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 设置勾选选项表并返回是否勾选的标记
     */
    private boolean getWorkingText() {
        boolean hasChecked = false;
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mEditTextList.getChildCount(); i++) {
                View view = mEditTextList.getChildAt(i);
                NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
                if (!TextUtils.isEmpty(edit.getText())) {
                    if (((CheckBox) view.findViewById(R.id.cb_edit_item)).isChecked()) {
                        sb.append(TAG_CHECKED).append(" ").append(edit.getText()).append("\n");
                        hasChecked = true;
                    } else {
                        sb.append(TAG_UNCHECKED).append(" ").append(edit.getText()).append("\n");
                    }
                }
            }
            mWorkingNote.setWorkingText(sb.toString());
        } else {
            mWorkingNote.setWorkingText(mNoteEditor.getText().toString());
        }
        return hasChecked;
    }

    /**
     * 保存便签
     */
    private boolean saveNote() {
        getWorkingText();
        boolean saved = mWorkingNote.saveNote();
        if (saved) {
            /**
             * There are two modes from List view to edit view, open one note,
             * create/edit a node. Opening node requires to the original
             * position in the list when back from edit view, while creating a
             * new node requires to the top of the list. This code
             * {@link #RESULT_OK} is used to identify the create/edit state
             */
            setResult(RESULT_OK);
        }
        return saved;
    }

    /**
     * 发送至桌面
     */
    private void sendToDesktop() {
        /**
         * Before send message to home, we should make sure that current
         * editing note is exists in databases. So, for new note, firstly
         * save it
         */
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }

        if (mWorkingNote.getNoteId() > 0) {
            Intent sender = new Intent();
            Intent shortcutIntent = new Intent(this, NoteEditActivity.class);
            shortcutIntent.setAction(Intent.ACTION_VIEW);
            shortcutIntent.putExtra(Intent.EXTRA_UID, mWorkingNote.getNoteId());
            sender.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
            sender.putExtra(Intent.EXTRA_SHORTCUT_NAME,
                    makeShortcutIconTitle(mWorkingNote.getContent()));
            sender.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                    Intent.ShortcutIconResource.fromContext(this, R.drawable.icon_app));
            sender.putExtra("duplicate", true);
            sender.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
            showToast(R.string.info_note_enter_desktop);
            sendBroadcast(sender);
        } else {
            /**
             * There is the condition that user has input nothing (the note is
             * not worthy saving), we have no note id, remind the user that he
             * should input something
             */
            Log.e(TAG, "Send to desktop error");
            showToast(R.string.error_note_empty_for_send_to_desktop);
        }
    }

    /**
     * 编辑小图标的标题
     */
    private String makeShortcutIconTitle(String content) {
        content = content.replace(TAG_CHECKED, "");
        content = content.replace(TAG_UNCHECKED, "");
        return content.length() > SHORTCUT_ICON_TITLE_MAX_LEN ? content.substring(0,
                SHORTCUT_ICON_TITLE_MAX_LEN) : content;
    }

    /**
     * 显示提示的视图
     */
    private void showToast(int resId) {
        showToast(resId, Toast.LENGTH_SHORT);
    }

    /**
     * 持续显示提示的视图
     */
    private void showToast(int resId, int duration) {
        Toast.makeText(this, resId, duration).show();
    }

    //重写onActivityResult()来处理返回的数据
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        ContentResolver resolver = getContentResolver();
        switch (requestCode) {
            case PHOTO_REQUEST:
                Uri originalUri = intent.getData(); //1.获得图片的真实路径
                Bitmap bitmap = null;
                try {
                    bitmap = BitmapFactory.decodeStream(resolver.openInputStream(originalUri));//2.解码图片
                } catch (FileNotFoundException e) {
                    Log.d(TAG, "onActivityResult: get file_exception");
                    e.printStackTrace();
                }

                if (bitmap != null) {
                    //3.根据Bitmap对象创建ImageSpan对象
                    Log.d(TAG, "onActivityResult: bitmap is not null");
                    ImageSpan imageSpan = new ImageSpan(NoteEditActivity.this, bitmap);
                    String path = getPath(this, originalUri);
                    //4.使用[local][/local]将path括起来，用于之后方便识别图片路径在note中的位置
                    String img_fragment = "[local]" + path + "[/local]";
                    //创建一个SpannableString对象，以便插入用ImageSpan对象封装的图像
                    SpannableString spannableString = new SpannableString(img_fragment);
                    spannableString.setSpan(imageSpan, 0, img_fragment.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    //5.将选择的图片追加到EditText中光标所在位置
                    NoteEditText e = (NoteEditText) findViewById(R.id.note_edit_view);
                    int index = e.getSelectionStart(); //获取光标所在位置
                    Log.d(TAG, "Index是: " + index);
                    Editable edit_text = e.getEditableText();
                    edit_text.insert(index, spannableString); //将图片插入到光标所在位置

                    mWorkingNote.mContent = e.getText().toString();
                    //6.把改动提交到数据库中,两个数据库表都要改的
                    ContentResolver contentResolver = getContentResolver();
                    ContentValues contentValues = new ContentValues();
                    final long id = mWorkingNote.getNoteId();
                    contentValues.put("snippet", mWorkingNote.mContent);
                    contentResolver.update(Uri.parse("content://micode_notes/note"), contentValues, "_id=?", new String[]{"" + id});
                    ContentValues contentValues1 = new ContentValues();
                    contentValues1.put("content", mWorkingNote.mContent);
                    contentResolver.update(Uri.parse("content://micode_notes/data"), contentValues1, "mime_type=? and note_id=?", new String[]{"vnd.android.cursor.item/text_note", "" + id});

                } else {
                    Toast.makeText(NoteEditActivity.this, "获取图片失败", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                break;
        }
    }

    //获取文件的real path
    public String getPath(final Context context, final Uri uri) {
        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{split[1]};

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // Media
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    /**
    * 获取数据列_获取此 Uri 的数据列的值。这对MediaStore Uris 和其他基于文件的 ContentProvider。
    */
    public String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {column};

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    /**
     * 是否为媒体文件
     */
    public boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }
}
