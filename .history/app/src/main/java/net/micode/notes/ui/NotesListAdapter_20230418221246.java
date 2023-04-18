package net.micode.notes.ui;
 
import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.View; 
import android.view.ViewGroup;
import android.widget.CursorAdapter;
 
 
import net.micode.notes.data.Notes;
 
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
 
 
// 便签表连接器，继承CursorAdapter，为cursor和ListView提供了连接的桥梁，实现了光标和编辑便签链接
public class NotesListAdapter extends CursorAdapter {
    private static final String TAG = "NotesListAdapter";
    private Context mContext;
    private HashMap<Integer, Boolean> mSelectedIndex;
    private int mNotesCount;    //便签数
    private boolean mChoiceMode;   //选择模式标记
 
    //桌面widget的属性，包括编号和类型
    public static class AppWidgetAttribute {
        public int widgetId;
        public int widgetType;
    };

     //初始化便签链接器
     //根据传进来的内容设置相关变量
    public NotesListAdapter(Context context) {
        super(context, null);  //父类对象置空
        mSelectedIndex = new HashMap<Integer, Boolean>();  //新建选项下标的hash表
        mContext = context;
        mNotesCount = 0;
    }
 
     //新建一个视图来存储光标所指向的数据
     //使用兄弟类NotesListItem新建一个项目选项
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return new NotesListItem(context);
    }
 
   
    //将已经存在的视图和鼠标指向的数据进行捆绑
    public void bindView(View view, Context context, Cursor cursor) {
        if (view instanceof NotesListItem) {
        	//若view是NotesListItem的一个实例
            NoteItemData itemData = new NoteItemData(context, cursor);
            ((NotesListItem) view).bind(context, itemData, mChoiceMode,
                    isSelectedItem(cursor.getPosition()));
           //则新建一个项目选项并且用bind跟将view和鼠标，内容，便签数据捆绑在一起
        }
    }
 
    //设置勾选框
    public void setCheckedItem(final int position, final boolean checked) {
        mSelectedIndex.put(position, checked);
        //根据定位和是否勾选设置下标
        notifyDataSetChanged();
        //在修改后刷新activity
    }
 
    //判断单选按钮是否勾选
    public boolean isInChoiceMode() {
        return mChoiceMode;
    }
 
    //设置单项选项框
    public void setChoiceMode(boolean mode) {
        mSelectedIndex.clear();//重置下标并且根据参数mode设置选项
        mChoiceMode = mode;
    }
 
    //全选
    public void selectAll(boolean checked) {
        Cursor cursor = getCursor();
        //获取光标位置
        for (int i = 0; i < getCount(); i++) {
            if (cursor.moveToPosition(i)) {
                if (NoteItemData.getNoteType(cursor) == Notes.TYPE_NOTE) {
                    setCheckedItem(i, checked);
                }
            }
        }
        //遍历所有光标可用的位置在判断为便签类型之后勾选单项框
    }
 
    //建立选择项的下标列表
    public HashSet<Long> getSelectedItemIds() {
        HashSet<Long> itemSet = new HashSet<Long>();
        //建立hash表
        for (Integer position : mSelectedIndex.keySet()) {
        	//遍历所有的关键
            if (mSelectedIndex.get(position) == true) {
            	//若光标位置可用
                Long id = getItemId(position);
                if (id == Notes.ID_ROOT_FOLDER) {
                	//原文件不需要添加
                    Log.d(TAG, "Wrong item id, should not happen");
                } else {
                    itemSet.add(id);
                }
                //则将id该下标假如选项集合中
                
            }
        }
 
        return itemSet;
    }
 
    //建立桌面Widget的选项表
    public HashSet<AppWidgetAttribute> getSelectedWidget() {
        HashSet<AppWidgetAttribute> itemSet = new HashSet<AppWidgetAttribute>();
        for (Integer position : mSelectedIndex.keySet()) {
            if (mSelectedIndex.get(position) == true) {
                Cursor c = (Cursor) getItem(position);
                //和getSelectedItemIds一样
                if (c != null) {
                	//光标位置可用的话就建立新的Widget属性并编辑下标和类型，最后添加到选项集中
                    AppWidgetAttribute widget = new AppWidgetAttribute();
                    NoteItemData item = new NoteItemData(mContext, c);
                    widget.widgetId = item.getWidgetId();
                    widget.widgetType = item.getWidgetType();
                    itemSet.add(widget);
                } else {
                    Log.e(TAG, "Invalid cursor");
                    return null;
                }
            }
        }
        return itemSet;
    }
 
    //获取选项个数
    public int getSelectedCount() {
        Collection<Boolean> values = mSelectedIndex.values();
        //首先获取选项下标的值
        if (null == values) {
            return 0;
        }
        Iterator<Boolean> iter = values.iterator();
        //初始化叠加器
        int count = 0;
        while (iter.hasNext()) {
            if (true == iter.next()) {
            	//若value值为真计数+1
                count++;
            }
        }
        return count;
    }
 
    //判断是否全选
    public boolean isAllSelected() {
        int checkedCount = getSelectedCount();
        return (checkedCount != 0 && checkedCount == mNotesCount);
        //获取选项数看是否等于便签的个数
    }
 
    //判断是否为选项表
    public boolean isSelectedItem(final int position) {//通过传递的下标来确定
        if (null == mSelectedIndex.get(position)) {
            return false;
        }
        return mSelectedIndex.get(position);
    }
 
    protected void onContentChanged() {//在activity内容发生局部变动的时候回调该函数计算便签的数量
        super.onContentChanged();
        //执行基类函数
        calcNotesCount();
    }
 
    public void changeCursor(Cursor cursor) {//在activity光标发生局部变动的时候回调该函数计算便签的数量
        super.changeCursor(cursor);
        calcNotesCount();
    }
 
    private void calcNotesCount() {//计算便签数量
        mNotesCount = 0;
        for (int i = 0; i < getCount(); i++) {
        	//获取总数同时遍历
            Cursor c = (Cursor) getItem(i);
            if (c != null) {
                if (NoteItemData.getNoteType(c) == Notes.TYPE_NOTE) {
                    mNotesCount++;
                   //若该位置不为空并且文本类型为便签就+1
                }
            } else {
                Log.e(TAG, "Invalid cursor");
                return;
            }
            //否则报错
        }
    } 
}