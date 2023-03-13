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

package net.micode.notes.model;

import android.appwidget.AppWidgetManager;  //导入用于操作应用程序小部件的AppWidgetManager类。
import android.content.ContentUris;         //导入用于解析内容URI的ContentUris类。
import android.content.Context;             //导入用于提供应用程序上下文环境的Context类。
import android.database.Cursor;             //导入用于在数据库查询中获取结果集的Cursor类。
import android.text.TextUtils;              // 导入用于处理文本字符串的TextUtils类。
import android.util.Log;                    //导入用于记录调试信息的Log类。

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.tool.ResourceParser.NoteBgResources;

/**
 * 正在工作的便签类
 */
public class WorkingNote {
    // Note for the working note
    private Note mNote;
    // Note Id
    private long mNoteId;
    // Note content
    private String mContent;
    // Note mode
    private int mMode;

    //一些变量的初始化
    private long mAlertDate;        //警告数据
    private long mModifiedDate;     //修改的数据
    private int mBgColorId;         //背景颜色
    private int mWidgetId;          //小部件ID
    private int mWidgetType;        //小部件类型
    private long mFolderId;         //文件夹ID
    private Context mContext;       //文本内容

    private static final String TAG = "WorkingNote";

    private boolean mIsDeleted;

    private NoteSettingChangedListener mNoteSettingStatusListener;

    //数据投影（保存数据用，保存DataColumns的数据
    public static final String[] DATA_PROJECTION = new String[] {
            DataColumns.ID,
            DataColumns.CONTENT,
            DataColumns.MIME_TYPE,
            DataColumns.DATA1,
            DataColumns.DATA2,
            DataColumns.DATA3,
            DataColumns.DATA4,
    };
    //数据投影（保存数据用，保存NoteColumns的数据
    public static final String[] NOTE_PROJECTION = new String[] {
            NoteColumns.PARENT_ID,
            NoteColumns.ALERTED_DATE,
            NoteColumns.BG_COLOR_ID,
            NoteColumns.WIDGET_ID,
            NoteColumns.WIDGET_TYPE,
            NoteColumns.MODIFIED_DATE
    };

    //DATACOLUMN的一些标识
    private static final int DATA_ID_COLUMN = 0;
    private static final int DATA_CONTENT_COLUMN = 1;
    private static final int DATA_MIME_TYPE_COLUMN = 2;
    private static final int DATA_MODE_COLUMN = 3;
    //NOTECOLUMN的一些标识
    private static final int NOTE_PARENT_ID_COLUMN = 0;
    private static final int NOTE_ALERTED_DATE_COLUMN = 1;
    private static final int NOTE_BG_COLOR_ID_COLUMN = 2;
    private static final int NOTE_WIDGET_ID_COLUMN = 3;
    private static final int NOTE_WIDGET_TYPE_COLUMN = 4;
    private static final int NOTE_MODIFIED_DATE_COLUMN = 5;

    // New note construct WorkingNote正在工作的便签的初始化
    private WorkingNote(Context context, long folderId) {
        mContext = context;
        mAlertDate = 0;
        mModifiedDate = System.currentTimeMillis();
        mFolderId = folderId;
        mNote = new Note();
        mNoteId = 0;
        mIsDeleted = false;
        mMode = 0;
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE;
    }

    // Existing note construct WorkingNote构造函数
    private WorkingNote(Context context, long noteId, long folderId) {
        mContext = context;
        mNoteId = noteId;
        mFolderId = folderId;
        mIsDeleted = false;
        mNote = new Note();
        loadNote();
    }

    /**
     * 加载便签Note
     */
    private void loadNote() {
        //定义数据库光标
        Cursor cursor = mContext.getContentResolver().query(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mNoteId), NOTE_PROJECTION, null,
                null, null);

        //如果找到数据
        if (cursor != null) {
            if (cursor.moveToFirst()) { //将光标移到第一行，查询数据
                mFolderId = cursor.getLong(NOTE_PARENT_ID_COLUMN);
                mBgColorId = cursor.getInt(NOTE_BG_COLOR_ID_COLUMN);
                mWidgetId = cursor.getInt(NOTE_WIDGET_ID_COLUMN);
                mWidgetType = cursor.getInt(NOTE_WIDGET_TYPE_COLUMN);
                mAlertDate = cursor.getLong(NOTE_ALERTED_DATE_COLUMN);
                mModifiedDate = cursor.getLong(NOTE_MODIFIED_DATE_COLUMN);
            }
            cursor.close();
        } else {    //出错
            Log.e(TAG, "No note with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note with id " + mNoteId);
        }
        loadNoteData();
    }

    /**
     * 加载便签数据
     */
    private void loadNoteData() {
        //查找，返回光标
        Cursor cursor = mContext.getContentResolver().query(Notes.CONTENT_DATA_URI, DATA_PROJECTION,
                DataColumns.NOTE_ID + "=?", new String[] {
                    String.valueOf(mNoteId)
                }, null);

        if (cursor != null) {   //查到信息，光标不为空
            if (cursor.moveToFirst()) {//移到第一项
                do {
                    String type = cursor.getString(DATA_MIME_TYPE_COLUMN);  //获取type
                    if (DataConstants.NOTE.equals(type)) {  //便签数据匹配
                        mContent = cursor.getString(DATA_CONTENT_COLUMN);
                        mMode = cursor.getInt(DATA_MODE_COLUMN);
                        mNote.setTextDataId(cursor.getLong(DATA_ID_COLUMN));
                    } else if (DataConstants.CALL_NOTE.equals(type)) {//调取数据匹配
                        mNote.setCallDataId(cursor.getLong(DATA_ID_COLUMN));
                    } else {
                        Log.d(TAG, "Wrong note type with type:" + type);//类型错误
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();
        } else {   //没找到
            Log.e(TAG, "No data with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note's data with id " + mNoteId);
        }
    }

    /**
     * 创建空便签
     */
    public static WorkingNote createEmptyNote(Context context, long folderId, int widgetId,
            int widgetType, int defaultBgColorId) {
        WorkingNote note = new WorkingNote(context, folderId);//分别设置内容、文件夹ID
        note.setBgColorId(defaultBgColorId);//默认背景颜色
        note.setWidgetId(widgetId);         //小部件ID
        note.setWidgetType(widgetType);     //小部件类型
        return note;
    }

    /**
     * 加载，返回当前正在工作的便签
     */
    public static WorkingNote load(Context context, long id) {
        return new WorkingNote(context, id, 0);
    }

    /**
     * 保存便签
     */
    public synchronized boolean saveNote() {
        if (isWorthSaving()) {  //是否应该保存
            if (!existInDatabase()) {   //是否在数据库中
                if ((mNoteId = Note.getNewNoteId(mContext, mFolderId)) == 0) {  //均符合条件
                    Log.e(TAG, "Create new note fail with id:" + mNoteId);
                    return false;
                }
            }

            mNote.syncNote(mContext, mNoteId);

            /**
             * Update widget content if there exist any widget of this note
             * 如果便签存在小部件，更新小部件目录
             */
            if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                    && mWidgetType != Notes.TYPE_WIDGET_INVALIDE
                    && mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onWidgetChanged();
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * 检查数据库中是否存在
     */
    public boolean existInDatabase() {
        return mNoteId > 0;
    }

    /**
     * 是否应该保存
     */
    private boolean isWorthSaving() {
        if (mIsDeleted || (!existInDatabase() && TextUtils.isEmpty(mContent))//如果不再数据库，或者内容为空，或者已经保存
                || (existInDatabase() && !mNote.isLocalModified())) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * 设置“设置状态监听器”变量————用于在markDeleted中判断是否可以删除
     */
    public void setOnSettingStatusChangedListener(NoteSettingChangedListener l) {
        mNoteSettingStatusListener = l;
    }

    //////////////////////////////////////////////////////////////
    /*         以下函数均为利用setNoteValue设置便签的某一项值      */
    //////////////////////////////////////////////////////////////
    /**
     * 设置警告数据alert data
     */
    public void setAlertDate(long date, boolean set) {
        if (date != mAlertDate) {
            mAlertDate = date;      //设置警告数据，并且设置便签关于警告数据的值
            mNote.setNoteValue(NoteColumns.ALERTED_DATE, String.valueOf(mAlertDate));
        }
        if (mNoteSettingStatusListener != null) {
            mNoteSettingStatusListener.onClockAlertChanged(date, set);
        }
    }

    /**
     * 删除标记mark
     */
    public void markDeleted(boolean mark) {
        mIsDeleted = mark;
        if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID  //ID不为非法部件ID，且类型不为非法类型
                && mWidgetType != Notes.TYPE_WIDGET_INVALIDE && mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onWidgetChanged();
        }
    }

    /**
     * 设置背景颜色
     */
    public void setBgColorId(int id) {
        if (id != mBgColorId) { //id与当前id不同
            mBgColorId = id;
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onBackgroundColorChanged();
            }
            mNote.setNoteValue(NoteColumns.BG_COLOR_ID, String.valueOf(id));//改变便签中关于背景颜色的值
        }
    }

    /**
     * 设置检查列表模式
     */
    public void setCheckListMode(int mode) {
        if (mMode != mode) {        //模式不同于当前
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onCheckListModeChanged(mMode, mode);
            }
            mMode = mode;
            mNote.setTextData(TextNote.MODE, String.valueOf(mMode));
        }
    }

    /** 
     * 设置小部件类型
     */
    public void setWidgetType(int type) {
        if (type != mWidgetType) {
            mWidgetType = type;
            mNote.setNoteValue(NoteColumns.WIDGET_TYPE, String.valueOf(mWidgetType));
        }
    }

    /**
     * 设置小部件ID
     */
    public void setWidgetId(int id) {
        if (id != mWidgetId) {
            mWidgetId = id;
            mNote.setNoteValue(NoteColumns.WIDGET_ID, String.valueOf(mWidgetId));
        }
    }

    /**
     * 设置工作文本
     */
    public void setWorkingText(String text) {
        if (!TextUtils.equals(mContent, text)) {
            mContent = text;
            mNote.setTextData(DataColumns.CONTENT, mContent);
        }
    }

    /**
     * 将phoneNumber转换为电话号码
     */
    public void convertToCallNote(String phoneNumber, long callDate) {
        mNote.setCallData(CallNote.CALL_DATE, String.valueOf(callDate));
        mNote.setCallData(CallNote.PHONE_NUMBER, phoneNumber);
        mNote.setNoteValue(NoteColumns.PARENT_ID, String.valueOf(Notes.ID_CALL_RECORD_FOLDER));
    }

    //////////////////////////////////////////////////////////////
    /*                 以下函数都是返回相应的变量                  */
    //////////////////////////////////////////////////////////////
    public boolean hasClockAlert() {
        return (mAlertDate > 0 ? true : false); //是否警告
    }

    public String getContent() {
        return mContent;        //获取内容
    }

    public long getAlertDate() {
        return mAlertDate;      //获取警告数据
    }

    public long getModifiedDate() {
        return mModifiedDate;   //获取修改数据
    }

    public int getBgColorResId() {
        return NoteBgResources.getNoteBgResource(mBgColorId);   //获取背景颜色资源
    }

    public int getBgColorId() {
        return mBgColorId;      //获取背景颜色
    }

    public int getTitleBgResId() {
        return NoteBgResources.getNoteTitleBgResource(mBgColorId);  //获取题目背景资源
    }

    public int getCheckListMode() {
        return mMode;           //获取检查列表的模式
    }

    public long getNoteId() {
        return mNoteId;         //获取便签ID
    }

    public long getFolderId() {
        return mFolderId;       //获取文件夹ID
    }

    public int getWidgetId() {
        return mWidgetId;       //获取小部件ID
    }

    public int getWidgetType() {
        return mWidgetType;     //获取小部件类型
    }

    /**
     * 创建接口用于监视便签
     */
    public interface NoteSettingChangedListener {
        /**
         * Called when the background color of current note has just changed
         * 当当前便签的背景颜色变化时调用
         */
        void onBackgroundColorChanged();

        /**
         * Called when user set clock
         * 当用户定时时调用
         */
        void onClockAlertChanged(long date, boolean set);

        /**
         * Call when user create note from widget
         * 当用户从小部件创建便签时调用
         */
        void onWidgetChanged();

        /**
         * Call when switch between check list mode and normal mode
         * 当列表查看模型变化时调用
         * @param oldMode is previous mode before change
         * @param newMode is new mode
         */
        void onCheckListModeChanged(int oldMode, int newMode);
    }
}
