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

package net.micode.notes.tool;

import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;


public class BackupUtils {
    //当前tag叫BackupUtils
    private static final String TAG = "BackupUtils";
    // Singleton stuff
    //声明一个自对象
    private static BackupUtils sInstance;

    /**
     * 获取实例对象函数，并有线程锁
     */
    public static synchronized BackupUtils getInstance(Context context) {
        if (sInstance == null) {
            //根据传来的Contest创建实例对象
            //如果当前备份不存在，则新声明一个
            sInstance = new BackupUtils(context);
        }
        return sInstance;
    }

    /*
     * Following states are signs to represents backup or restore
     * status
     * 以下状态表示备份或恢复状态
     */
    // Currently, the sdcard is not mounted
    /**
     * 未安装sd卡
     */
    public static final int STATE_SD_CARD_UNMOUONTED = 0;
    // The backup file not exist
    /**
     * 备份文件不存在
     */
    public static final int STATE_BACKUP_FILE_NOT_EXIST = 1;
    // The data is not well formated, may be changed by other programs
    /**
     * 数据未格式化，可能被其他程序修改
     */
    public static final int STATE_DATA_DESTROIED = 2;
    // Some run-time exception which causes restore or backup fails
    /**
     * 导致还原或备份失败的某些超时异常
     */
    public static final int STATE_SYSTEM_ERROR = 3;
    // Backup or restore success
    /**
     * 备份或者还原成功
     */
    public static final int STATE_SUCCESS = 4;

    private TextExport mTextExport;

    /**
     * 构造函数
     */
    private BackupUtils(Context context) {
        mTextExport = new TextExport(context);
    }

    /**
     * 检测外部存储功能是否可用
     */
    private static boolean externalStorageAvailable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /**
     * 将note导出到text且是可读的
     */
    public int exportToText() {
        return mTextExport.exportToText();
    }

    /**
     * 获取导出的文本文件名
     */
    public String getExportedTextFileName() {
        return mTextExport.mFileName;
    }

    /**
     * 获取导出的文本文件所在文件夹
     */
    public String getExportedTextFileDir() {
        return mTextExport.mFileDirectory;
    }

    /**
     * 用于文本导出的类
     */
    private static class TextExport {
        /**
         * 便签信息
         */
        private static final String[] NOTE_PROJECTION = {
                NoteColumns.ID,
                NoteColumns.MODIFIED_DATE,
                NoteColumns.SNIPPET,
                NoteColumns.TYPE
        };

        private static final int NOTE_COLUMN_ID = 0;

        private static final int NOTE_COLUMN_MODIFIED_DATE = 1;

        private static final int NOTE_COLUMN_SNIPPET = 2;

        /**
         * 便签内容
         */
        private static final String[] DATA_PROJECTION = {
                DataColumns.CONTENT,
                DataColumns.MIME_TYPE,
                DataColumns.DATA1,
                DataColumns.DATA2,
                DataColumns.DATA3,
                DataColumns.DATA4,
        };

        private static final int DATA_COLUMN_CONTENT = 0;

        private static final int DATA_COLUMN_MIME_TYPE = 1;

        private static final int DATA_COLUMN_CALL_DATE = 2;

        private static final int DATA_COLUMN_PHONE_NUMBER = 4;

        /**
         * 文本的格式
         */
        private final String[] TEXT_FORMAT;
        private static final int FORMAT_FOLDER_NAME = 0;
        private static final int FORMAT_NOTE_DATE = 1;
        private static final int FORMAT_NOTE_CONTENT = 2;

        private Context mContext;
        private String mFileName;
        private String mFileDirectory;

        /**
         * 通过传入的contest构造出导出文本
         */
        public TextExport(Context context) {
            //获取对应的格式
            TEXT_FORMAT = context.getResources().getStringArray(R.array.format_for_exported_note);
            mContext = context;
            mFileName = "";
            mFileDirectory = "";
        }

        /**
         * 根据传入的id返回文本格式
         */
        private String getFormat(int id) {
            return TEXT_FORMAT[id];
        }

        /**
         * Export the folder identified by folder id to text
         * <p>将文件夹id标识的文件夹下的内容全部导出为文本</p>
         */
        private void exportFolderToText(String folderId, PrintStream ps) {
            // Query notes belong to this folder
            // 通过查询parent id是文件夹id的note来选出指定ID文件夹下的Note
            Cursor notesCursor = mContext.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION, NoteColumns.PARENT_ID + "=?", new String[]{
                            folderId
                    }, null);

            //如果查询到了
            if (notesCursor != null) {
                //如果成功将光标移动到第一行
                if (notesCursor.moveToFirst()) {
                    do {
                        // Print note's last modified date
                        // 打印这份note最近一次修改的数据
                        // ps里面保存有这份note的日期
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE), DateFormat.format(
                                mContext.getString(R.string.format_datetime_mdhm),
                                notesCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));
                        // Query data belong to this note
                        // 根据属于这份note的数据查询
                        String noteId = notesCursor.getString(NOTE_COLUMN_ID);
                        // 将文件导出到text
                        exportNoteToText(noteId, ps);
                    } while (notesCursor.moveToNext());
                }
                notesCursor.close();
            }
        }

        /**
         * Export note identified by id to a print stream
         * <p>将id标识的note导出到打印流</p>
         */
        private void exportNoteToText(String noteId, PrintStream ps) {
            //根据id查询note
            Cursor dataCursor = mContext.getContentResolver().query(Notes.CONTENT_DATA_URI,
                    DATA_PROJECTION, DataColumns.NOTE_ID + "=?", new String[]{
                            noteId
                    }, null);

            //利用光标来扫描内容，区别为callnote和note两种，靠ps.printline输出
            if (dataCursor != null) {
                //先将光标移动到第一行
                if (dataCursor.moveToFirst()) {
                    do {
                        String mimeType = dataCursor.getString(DATA_COLUMN_MIME_TYPE);
                        // 根据数据类型查看，如果是通话类型
                        if (DataConstants.CALL_NOTE.equals(mimeType)) {
                            // Print phone number
                            // 打印电话号码
                            String phoneNumber = dataCursor.getString(DATA_COLUMN_PHONE_NUMBER);
                            long callDate = dataCursor.getLong(DATA_COLUMN_CALL_DATE);
                            String location = dataCursor.getString(DATA_COLUMN_CONTENT);

                            if (!TextUtils.isEmpty(phoneNumber)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        phoneNumber));
                            }
                            // Print call date
                            // 打印通话日期
                            ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT), DateFormat
                                    .format(mContext.getString(R.string.format_datetime_mdhm),
                                            callDate)));
                            // Print call attachment location
                            // 打印呼叫位置
                            if (!TextUtils.isEmpty(location)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        location));
                            }
                        } else if (DataConstants.NOTE.equals(mimeType)) {
                            //如果是note类型，打印
                            String content = dataCursor.getString(DATA_COLUMN_CONTENT);
                            if (!TextUtils.isEmpty(content)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        content));
                            }
                        }
                    } while (dataCursor.moveToNext());//一行一行的扫描
                }
                dataCursor.close();
            }
            // print a line separator between note
            // 在note之间打印行分隔符
            try {
                ps.write(new byte[]{
                        Character.LINE_SEPARATOR, Character.LETTER_NUMBER
                });
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }

        /**
         * Note will be exported as text which is user readable
         * note将导出为用户可读的文本
         *
         * @return 返回STATE_SUCCESS, 即备份或者还原成功
         */
        public int exportToText() {
            //外部存储功能不可用
            if (!externalStorageAvailable()) {
                Log.d(TAG, "Media was not mounted");
                return STATE_SD_CARD_UNMOUONTED;
            }

            PrintStream ps = getExportToTextPrintStream();
            if (ps == null) {
                Log.e(TAG, "get print stream error");
                return STATE_SYSTEM_ERROR;
            }
            // First export folder and its notes
            // 首先导出文件夹及其注释

            //查询
            Cursor folderCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    "(" + NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER + " AND "
                            + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER + ") OR "
                            + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER, null, null);
            //文件夹光标不为空，说明查询到了
            if (folderCursor != null) {
                //将文件夹光标移动到第一行
                if (folderCursor.moveToFirst()) {
                    do {
                        // Print folder's name
                        // 打印文件夹名称
                        String folderName = "";
                        //如果是通话记录
                        if (folderCursor.getLong(NOTE_COLUMN_ID) == Notes.ID_CALL_RECORD_FOLDER) {
                            folderName = mContext.getString(R.string.call_record_folder_name);
                        } else {
                            folderName = folderCursor.getString(NOTE_COLUMN_SNIPPET);
                        }
                        if (!TextUtils.isEmpty(folderName)) {
                            ps.println(String.format(getFormat(FORMAT_FOLDER_NAME), folderName));
                        }
                        //获取文件夹id
                        String folderId = folderCursor.getString(NOTE_COLUMN_ID);
                        //根据文件夹id把内容打印出来
                        exportFolderToText(folderId, ps);
                    } while (folderCursor.moveToNext());
                }
                folderCursor.close();
            }

            // Export notes in root's folder
            // 打印根目录下的note
            Cursor noteCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    NoteColumns.TYPE + "=" + +Notes.TYPE_NOTE + " AND " + NoteColumns.PARENT_ID
                            + "=0", null, null);

            if (noteCursor != null) {
                if (noteCursor.moveToFirst()) {
                    do {
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE), DateFormat.format(
                                mContext.getString(R.string.format_datetime_mdhm),
                                noteCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));
                        // Query data belong to this note
                        String noteId = noteCursor.getString(NOTE_COLUMN_ID);
                        exportNoteToText(noteId, ps);
                    } while (noteCursor.moveToNext());
                }
                noteCursor.close();
            }
            ps.close();

            return STATE_SUCCESS;
        }

        /**
         * Get a print stream pointed to the file {@generateExportedTextFile}
         * <p>获取指向文件的打印流</p>
         */
        private PrintStream getExportToTextPrintStream() {
            File file = generateFileMountedOnSDcard(mContext, R.string.file_path,
                    R.string.file_name_txt_format);
            if (file == null) {
                Log.e(TAG, "create file to exported failed");
                return null;
            }
            mFileName = file.getName();
            mFileDirectory = mContext.getString(R.string.file_path);
            PrintStream ps = null;
            try {
                FileOutputStream fos = new FileOutputStream(file);
                ps = new PrintStream(fos);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return null;
            } catch (NullPointerException e) {
                e.printStackTrace();
                return null;
            }
            return ps;
        }
    }

    /**
     * Generate the text file to store imported data
     * <p>生成文本文件以存储导入的数据</p>
     */
    private static File generateFileMountedOnSDcard(Context context, int filePathResId, int fileNameFormatResId) {
        StringBuilder sb = new StringBuilder();
        sb.append(Environment.getExternalStorageDirectory());//外部（SD卡）的存储路径
        sb.append(context.getString(filePathResId));//文件的存储路径
        File filedir = new File(sb.toString());//filedir用来存储路径信息
        sb.append(context.getString(
                fileNameFormatResId,
                DateFormat.format(context.getString(R.string.format_date_ymd),
                        System.currentTimeMillis())));
        File file = new File(sb.toString());

        try {//如果这些文件不存在，则新建
            if (!filedir.exists()) {
                filedir.mkdir();
            }
            if (!file.exists()) {
                file.createNewFile();
            }
            return file;
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}


