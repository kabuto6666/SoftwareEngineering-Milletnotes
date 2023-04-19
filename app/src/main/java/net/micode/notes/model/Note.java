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

import android.content.ContentProviderOperation;//内容提供操作————批量的插入、更新、删除数据，以便一次性进行数据库事务处理
import android.content.ContentProviderResult;   //返回ContentProvider操作的结果，将结果封装后进行返回，并在操作失误时传递错误代码
import android.content.ContentUris;             //获取ContentProvider中与数据相关联的Uri，可以根据ID或Uri获取表格的信息
import android.content.ContentValues;           //存储数据，可以用于更新和插入数据库表格；同时提供了一种简单的方式传递复杂的数据库记录
import android.content.Context;                 //包含了应用程序上下文的信息，可以使在整个应用程序中访问应用程序级别的资源
import android.content.OperationApplicationException;   //应用程序操作容错，当出现错误时返回给调用者
import android.net.Uri;                         //待操作的数据
import android.os.RemoteException;              //远程容错
import android.util.Log;                        //日志

//引入之前所定义的包
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;

import java.util.ArrayList;     //数组


public class Note {

    //创建便签实例
    private ContentValues mNoteDiffValues;  
    private NoteData mNoteData;
    private static final String TAG = "Note";
    /**
     * Create a new note id for adding a new note to databases
     * 为数据库的新便签创建一个新ID
     */
    public static synchronized long getNewNoteId(Context context, long folderId) {
        // Create a new note in the database    在数据库中创建一个新便签
        ContentValues values = new ContentValues();     //数据内容
        long createdTime = System.currentTimeMillis();  //创建时间

        //在数据库中更新数据内容
        values.put(NoteColumns.CREATED_DATE, createdTime);
        values.put(NoteColumns.MODIFIED_DATE, createdTime);
        values.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
        values.put(NoteColumns.LOCAL_MODIFIED, 1);
        values.put(NoteColumns.PARENT_ID, folderId);

        /**
         * ContentResolver主要是实现外部应用
         * 对ContentProvider中的数据进行添加、删除、更改、查询等
         */
        Uri uri = context.getContentResolver().insert(Notes.CONTENT_NOTE_URI, values);

        long noteId = 0;
        try {
            noteId = Long.valueOf(uri.getPathSegments().get(1));    
        } catch (NumberFormatException e) {
            Log.e(TAG, "Get note id error :" + e.toString());   //异常处理
            noteId = 0;
        }
        if (noteId == -1) {
            throw new IllegalStateException("Wrong note id:" + noteId);
        }
        return noteId;  //返回便签的ID
    }

    /**
     * 定义两个变量来储存便签的数据
     * mNoteDiffValues存储便签的属性
     * mNoteData存储便签内容
     */
    public Note() {
        mNoteDiffValues = new ContentValues();
        mNoteData = new NoteData();
    }

    //根据传入的key和value设置便签的属性数据
    public void setNoteValue(String key, String value) {
        mNoteDiffValues.put(key, value);
        mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
        mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
    }

    //设置便签的文本内容
    public void setTextData(String key, String value) {
        mNoteData.setTextData(key, value);
    }

    //设置文本数据的ID

    public void setTextDataId(long id) {
        mNoteData.setTextDataId(id);
    }

    //获得文本数据的ID
    public long getTextDataId() {
        return mNoteData.mTextDataId;
    }

    //设置调取数据的ID（电话号码数据的ID？
    public void setCallDataId(long id) {
        mNoteData.setCallDataId(id);
    }

    //设置调取记录数据
    public void setCallData(String key, String value) {
        mNoteData.setCallData(key, value);
    }

    //判断本地数据是否修改
    public boolean isLocalModified() {
        return mNoteDiffValues.size() > 0 || mNoteData.isLocalModified();
    }

    //同步便签？
    public boolean syncNote(Context context, long noteId) {
        if (noteId <= 0) {
            throw new IllegalArgumentException("Wrong note id:" + noteId);
        }

        if (!isLocalModified()) {   //没有修改，返回true
            return true;
        }

        /**
         * In theory, once data changed, the note should be updated on {@link NoteColumns#LOCAL_MODIFIED} and
         * {@link NoteColumns#MODIFIED_DATE}. For data safety, though update note fails, we also update the
         * note data info
         * 理论上，一旦数据发生变化，注释应该在 {@link NoteColumns#LOCAL_MODIFIED} 
         * 和 {@link NoteColumns#MODIFIED_DATE} 上更新。
         * 为了数据安全，虽然更新说明失败，但我们也会更新注释数据信息
         */
        if (context.getContentResolver().update(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), mNoteDiffValues, null,
                null) == 0) {
            Log.e(TAG, "Update note error, should not happen");
            // Do not return, fall through
        }
        //数据更新失败
        mNoteDiffValues.clear();

        if (mNoteData.isLocalModified()
                && (mNoteData.pushIntoContentResolver(context, noteId) == null)) {
            return false;   //数据修改了但是没有同步
        }

        return true;
    }

    /**
     * 便签数据类
     */
    private class NoteData {
        private long mTextDataId;   //文本数据ID

        private ContentValues mTextDataValues;  //文本数据

        private long mCallDataId;   //调取数据ID

        private ContentValues mCallDataValues;  //调取数据

        private static final String TAG = "NoteData";

        //数据初始化
        public NoteData() {
            mTextDataValues = new ContentValues();
            mCallDataValues = new ContentValues();
            mTextDataId = 0;
            mCallDataId = 0;
        }

        //是否本地进行了修改
        boolean isLocalModified() {
            return mTextDataValues.size() > 0 || mCallDataValues.size() > 0;
        }

        //设置文本数据ID
        void setTextDataId(long id) {
            if(id <= 0) {
                throw new IllegalArgumentException("Text data id should larger than 0");
            }
            mTextDataId = id;
        }

        //设置调用数据ID
        void setCallDataId(long id) {
            if (id <= 0) {
                throw new IllegalArgumentException("Call data id should larger than 0");
            }
            mCallDataId = id;
        }

        //设置调取数据
        void setCallData(String key, String value) {
            mCallDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        //设置文本数据
        void setTextData(String key, String value) {
            mTextDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /* 将数据解析到数据库 */
        Uri pushIntoContentResolver(Context context, long noteId) {
            /**
             * Check for safety
             * 检查安全性
             */
            if (noteId <= 0) {
                throw new IllegalArgumentException("Wrong note id:" + noteId);
            }

            //操作列表
            ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
            ContentProviderOperation.Builder builder = null;    

            //向DataColumns中写入文本数据
            if(mTextDataValues.size() > 0) {
                mTextDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mTextDataId == 0) {  
                    mTextDataValues.put(DataColumns.MIME_TYPE, TextNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mTextDataValues);
                    try {
                        setTextDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new text data fail with noteId" + noteId);
                        mTextDataValues.clear();
                        return null;
                    }
                } else {
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mTextDataId));
                    builder.withValues(mTextDataValues);
                    operationList.add(builder.build());
                }
                mTextDataValues.clear();
            } 

            //向DataColumns中写入调用数据
            if(mCallDataValues.size() > 0) {
                mCallDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mCallDataId == 0) {
                    mCallDataValues.put(DataColumns.MIME_TYPE, CallNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mCallDataValues);
                    try {
                        setCallDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new call data fail with noteId" + noteId);
                        mCallDataValues.clear();
                        return null;
                    }
                } else {
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mCallDataId));
                    builder.withValues(mCallDataValues);
                    operationList.add(builder.build());
                }
                mCallDataValues.clear();
            }

            //异常处理
            if (operationList.size() > 0) {
                try {
                    ContentProviderResult[] results = context.getContentResolver().applyBatch(
                            Notes.AUTHORITY, operationList);
                    return (results == null || results.length == 0 || results[0] == null) ? null
                            : ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                } catch (RemoteException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                } catch (OperationApplicationException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                }
            }
            return null;
        }
    }
}
