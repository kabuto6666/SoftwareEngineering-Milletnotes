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

import android.content.Context;
import android.graphics.Rect;
import android.text.Layout;
import android.text.Selection;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.widget.EditText;

import net.micode.notes.R;

import java.util.HashMap;
import java.util.Map;

public class NoteEditText extends android.support.v7.widget.AppCompatEditText {
    private static final String TAG = "NoteEditText";
    private int mIndex;
    private int mSelectionStartBeforeDelete;

    private static final String SCHEME_TEL = "tel:" ;
    private static final String SCHEME_HTTP = "http:" ;
    private static final String SCHEME_EMAIL = "mailto:" ;

    /**
     * 建立一个字符和整数的hash表，用于链接电话，网站，还有邮箱
     */
    private static final Map<String, Integer> sSchemaActionResMap = new HashMap<String, Integer>();
    static {
        sSchemaActionResMap.put(SCHEME_TEL, R.string.note_link_tel);
        sSchemaActionResMap.put(SCHEME_HTTP, R.string.note_link_web);
        sSchemaActionResMap.put(SCHEME_EMAIL, R.string.note_link_email);
    }

    /**
     * Call by the {@link NoteEditActivity} to delete or add edit text<br/>
     * 在NoteEditActivity中删除或添加文本的操作，可以看做是一个文本是否被变的标记
     */
    public interface OnTextViewChangeListener {
        /**
         * Delete current edit text when {@link KeyEvent#KEYCODE_DEL} happens
         * and the text is null<br/>
         * 处理删除按键时的操作
         */
        void onEditTextDelete(int index, String text);

        /**
         * Add edit text after current edit text when {@link KeyEvent#KEYCODE_ENTER}
         * happen<br/>
         * 处理进入按键时的操作
         */
        void onEditTextEnter(int index, String text);

        /**
         * Hide or show item option when text change<br/>
         * 文本更改时隐藏或显示项目选项
         */
        void onTextChange(int index, boolean hasText);
    }

    private OnTextViewChangeListener mOnTextViewChangeListener;

    /**
     * 根据context设置文本
     */
    public NoteEditText(Context context) {
        super(context, null);
        mIndex = 0;
    }

    /**
     * 设置当前光标
     */
    public void setIndex(int index) {
        mIndex = index;
    }

    /**
     * 初始化文本修改标记
     */
    public void setOnTextViewChangeListener(OnTextViewChangeListener listener) {
        mOnTextViewChangeListener = listener;
    }

    /**
     * 初始化便签
     */
    public NoteEditText(Context context, AttributeSet attrs) {
        super(context, attrs, android.R.attr.editTextStyle);
    }

    /**
     * 根据defstyle自动初始化
     */
    public NoteEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        // TODO Auto-generated constructor stub
    }

    /**
     *  view里的函数，处理手机屏幕的所有事件
     *  参数event为手机屏幕触摸事件封装类的对象，其中封装了该事件的所有信息，
     *  例如触摸的位置、触摸的类型以及触摸的时间等。该对象会在用户触摸手机屏幕时被创建。
     * @param event The motion event.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            //更新当前坐标值
            case MotionEvent.ACTION_DOWN:

                int x = (int) event.getX();
                int y = (int) event.getY();
                x -= getTotalPaddingLeft();
                y -= getTotalPaddingTop();
                x += getScrollX();
                y += getScrollY();

                //用布局控件layout根据x,y的新值设置新的位置
                Layout layout = getLayout();
                int line = layout.getLineForVertical(y);
                int off = layout.getOffsetForHorizontal(line, x);
                //更新光标新的位置
                Selection.setSelection(getText(), off);
                break;
        }

        return super.onTouchEvent(event);
    }

    /**
     * 处理用户按下一个键盘按键时会触发的事件
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            //“进入”按键
            case KeyEvent.KEYCODE_ENTER:
                if (mOnTextViewChangeListener != null) {
                    return false;
                }
                break;
            //“删除”按键
            case KeyEvent.KEYCODE_DEL:
                mSelectionStartBeforeDelete = getSelectionStart();
                break;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 处理用户松开一个键盘按键时会触发的事件
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch(keyCode) {
            //“删除”按钮
            case KeyEvent.KEYCODE_DEL:
                //若是被修改过
                if (mOnTextViewChangeListener != null) {
                    if (0 == mSelectionStartBeforeDelete && mIndex != 0) {
                        //若之前有被修改并且文档不为空
                        //利用上文OnTextViewChangeListener对KEYCODE_DEL按键情况的删除函数进行删除
                        mOnTextViewChangeListener.onEditTextDelete(mIndex, getText().toString());
                        return true;
                    }
                } else {
                    Log.d(TAG, "OnTextViewChangeListener was not seted");
                }
                break;
            //“进入”按钮
            case KeyEvent.KEYCODE_ENTER:
                //若是被修改过
                if (mOnTextViewChangeListener != null) {
                    //获取当前位置
                    int selectionStart = getSelectionStart();
                    //获取当前文本
                    String text = getText().subSequence(selectionStart, length()).toString();
                    //根据获取的文本设置当前文本
                    setText(getText().subSequence(0, selectionStart));
                    mOnTextViewChangeListener.onEditTextEnter(mIndex + 1, text);
                } else {
                    Log.d(TAG, "OnTextViewChangeListener was not seted");
                }
                break;
            default:
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * 当焦点发生变化时，会自动调用该方法来处理焦点改变的事件
     * @param focused 表示触发该事件的View是否获得了焦点，当该控件获得焦点时，Focused等于true，否则等于false。
     * @param direction 表示焦点移动的方向，用数值表示
     * @param previouslyFocusedRect 表示在触发事件的View的坐标系中，前一个获得焦点的矩形区域，即表示焦点是从哪里来的。如果不可用则为null
     */
    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        //若监听器已经建立
        if (mOnTextViewChangeListener != null) {
            //获取到焦点并且文本不为空
            if (!focused && TextUtils.isEmpty(getText())) {
                mOnTextViewChangeListener.onTextChange(mIndex, false);
            } else {
                mOnTextViewChangeListener.onTextChange(mIndex, true);
            }
        }
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    /**
     * 生成上下文菜单
     * @param menu the context menu to populate
     */
    @Override
    protected void onCreateContextMenu(ContextMenu menu) {
        //有文本存在
        if (getText() instanceof Spanned) {
            //获取文本开始和结尾位置
            int selStart = getSelectionStart();
            int selEnd = getSelectionEnd();

            //获取开始到结尾的最大值和最小值
            int min = Math.min(selStart, selEnd);
            int max = Math.max(selStart, selEnd);

            //设置url的信息的范围值
            final URLSpan[] urls = ((Spanned) getText()).getSpans(min, max, URLSpan.class);
            if (urls.length == 1) {
                int defaultResId = 0;
                //获取计划表中所有的key值
                for(String schema: sSchemaActionResMap.keySet()) {
                    //若url可以添加则在添加后将defaultResId置为key所映射的值
                    if(urls[0].getURL().indexOf(schema) >= 0) {
                        defaultResId = sSchemaActionResMap.get(schema);
                        break;
                    }
                }

                //defaultResId == 0则说明url并没有添加任何东西，所以置为连接其他SchemaActionResMap的值
                if (defaultResId == 0) {
                    defaultResId = R.string.note_link_other;
                }

                //建立菜单
                menu.add(0, 0, 0, defaultResId).setOnMenuItemClickListener(
                        new OnMenuItemClickListener() {
                            public boolean onMenuItemClick(MenuItem item) {
                                // goto a new intent
                                //根据相应的文本设置菜单的按键
                                urls[0].onClick(NoteEditText.this);
                                return true;
                            }
                        });
            }
        }
        super.onCreateContextMenu(menu);
    }
}
