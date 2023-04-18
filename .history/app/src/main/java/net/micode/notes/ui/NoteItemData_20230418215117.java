package net.micode.notes.ui;
 
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
 
import net.micode.notes.data.Contact;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.DataUtils;
 
 
public class NoteItemData {
    static final String [] PROJECTION = new String [] {
        NoteColumns.ID,
        NoteColumns.ALERTED_DATE,
        NoteColumns.BG_COLOR_ID,
        NoteColumns.CREATED_DATE,
        NoteColumns.HAS_ATTACHMENT,
        NoteColumns.MODIFIED_DATE,
        NoteColumns.NOTES_COUNT,
        NoteColumns.PARENT_ID,
        NoteColumns.SNIPPET,
        NoteColumns.TYPE,
        NoteColumns.WIDGET_ID,
        NoteColumns.WIDGET_TYPE,
    };
    //常量标记和数据意义即其翻译字面义
    private static final int ID_COLUMN                    = 0;
    private static final int ALERTED_DATE_COLUMN          = 1;
    private static final int BG_COLOR_ID_COLUMN           = 2;
    private static final int CREATED_DATE_COLUMN          = 3;
    private static final int HAS_ATTACHMENT_COLUMN        = 4;
    private static final int MODIFIED_DATE_COLUMN         = 5;
    private static final int NOTES_COUNT_COLUMN           = 6;
    private static final int PARENT_ID_COLUMN             = 7;
    private static final int SNIPPET_COLUMN               = 8;
    private static final int TYPE_COLUMN                  = 9;
    private static final int WIDGET_ID_COLUMN             = 10;
    private static final int WIDGET_TYPE_COLUMN           = 11;
 
    private long mId;
    private long mAlertDate;
    private int mBgColorId;
    private long mCreatedDate;
    private boolean mHasAttachment;
    private long mModifiedDate;
    private int mNotesCount;
    private long mParentId;
    private String mSnippet;
    private int mType;
    private int mWidgetId;
    private int mWidgetType;
    private String mName;
    private String mPhoneNumber;
 
    private boolean mIsLastItem;
    private boolean mIsFirstItem;
    private boolean mIsOnlyOneItem;
    private boolean mIsOneNoteFollowingFolder;
    private boolean mIsMultiNotesFollowingFolder;
    //初始化NoteItemData，主要利用光标cursor获取的东西
    public NoteItemData(Context context, Cursor  cursor) {
    	//getxxx为转换格式
        mId = cursor.getLong(ID_COLUMN);
        mAlertDate = cursor.getLong(ALERTED_DATE_COLUMN);
        mBgColorId = cursor.getInt(BG_COLOR_ID_COLUMN);
        mCreatedDate = cursor.getLong(CREATED_DATE_COLUMN);
        mHasAttachment = (cursor.getInt(HAS_ATTACHMENT_COLUMN) > 0) ? true : false;
        mModifiedDate = cursor.getLong(MODIFIED_DATE_COLUMN);
        mNotesCount = cursor.getInt(NOTES_COUNT_COLUMN);
        mParentId = cursor.getLong(PARENT_ID_COLUMN);
        mSnippet = cursor.getString(SNIPPET_COLUMN);
        mSnippet = mSnippet.replace(NoteEditActivity.TAG_CHECKED, "").replace(
                NoteEditActivity.TAG_UNCHECKED, "");
        mType = cursor.getInt(TYPE_COLUMN);
        mWidgetId = cursor.getInt(WIDGET_ID_COLUMN);
        mWidgetType = cursor.getInt(WIDGET_TYPE_COLUMN);
 
        //初始化号码信息
        mPhoneNumber = "";
        if (mParentId == Notes.ID_CALL_RECORD_FOLDER) {
            mPhoneNumber = DataUtils.getCallNumberByNoteId(context.getContentResolver(), mId);
            if (!TextUtils.isEmpty(mPhoneNumber)) {//mphonenumber里有符合字符串，则用contart功能连接
                mName = Contact.getContact(context, mPhoneNumber);
                if (mName == null) {
                    mName = mPhoneNumber;
                }
            }
        }
 
        if (mName == null) {
            mName = "";
        }
        checkPostion(cursor);
    }
    ///根据鼠标的位置设置标记，和位置
    private void checkPostion(Cursor cursor) {
    	//初始化几个标记，cursor具体功能笔记中已提到，不一一叙述
        mIsLastItem = cursor.isLast() ? true : false;
        mIsFirstItem = cursor.isFirst() ? true : false;
        mIsOnlyOneItem = (cursor.getCount() == 1);
        //初始化“多重子文件”“单一子文件”2个标记
        mIsMultiNotesFollowingFolder = false;
        mIsOneNoteFollowingFolder = false;
 
        //主要是设置上诉2标记
        if (mType == Notes.TYPE_NOTE && !mIsFirstItem) {//若是note格式并且不是第一个元素
            int position = cursor.getPosition();
            if (cursor.moveToPrevious()) {//获取光标位置后看上一行
                if (cursor.getInt(TYPE_COLUMN) == Notes.TYPE_FOLDER
                        || cursor.getInt(TYPE_COLUMN) == Notes.TYPE_SYSTEM) {//若光标满足系统或note格式
                    if (cursor.getCount() > (position + 1)) {
                        mIsMultiNotesFollowingFolder = true;//若是数据行数大于但前位置+1则设置成正确
                    } else {
                        mIsOneNoteFollowingFolder = true;//否则单一文件夹标记为true
                    }
                }
                if (!cursor.moveToNext()) {//若不能再往下走则报错
                    throw new IllegalStateException("cursor move to previous but can't move back");
                }
            }
        }
    }
    //都是获取相关标记
    public boolean isOneFollowingFolder() {
        return mIsOneNoteFollowingFolder;
    }
 
    public boolean isMultiFollowingFolder() {
        return mIsMultiNotesFollowingFolder;
    }
 
    public boolean isLast() {
        return mIsLastItem;
    }
 
    public String getCallName() {
        return mName;
    }
 
    public boolean isFirst() {
        return mIsFirstItem;
    }
 
    public boolean isSingle() {
        return mIsOnlyOneItem;
    }
 
    public long getId() {
        return mId;
    }
 
    public long getAlertDate() {
        return mAlertDate;
    }
 
    public long getCreatedDate() {
        return mCreatedDate;
    }
 
    public boolean hasAttachment() {
        return mHasAttachment;
    }
 
    public long getModifiedDate() {
        return mModifiedDate;
    }
 
    public int getBgColorId() {
        return mBgColorId;
    }
 
    public long getParentId() {
        return mParentId;
    }
 
    public int getNotesCount() {
        return mNotesCount;
    }
 
    public long getFolderId () {
        return mParentId;
    }
 
    public int getType() {
        return mType;
    }
 
    public int getWidgetType() {
        return mWidgetType;
    }
 
    public int getWidgetId() {
        return mWidgetId;
    }
 
    public String getSnippet() {
        return mSnippet;
    }
 
    public boolean hasAlert() {
        return (mAlertDate > 0);
    }
 
    //若数据父id为保存至文件夹模式的id且满足电话号码单元不为空，则isCallRecord为true
    public boolean isCallRecord() {
        return (mParentId == Notes.ID_CALL_RECORD_FOLDER && !TextUtils.isEmpty(mPhoneNumber));
    }
 
    public static int getNoteType(Cursor cursor) {
        return cursor.getInt(TYPE_COLUMN);
    }
}