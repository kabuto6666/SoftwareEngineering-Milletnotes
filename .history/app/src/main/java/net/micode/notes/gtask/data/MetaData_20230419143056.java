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

package net.micode.notes.gtask.data;

import android.database.Cursor;
import android.util.Log;

import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
import org.json.JSONObject;
 
public class MetaData extends Task {
	/*
	 * 功能描述：得到类的简写名称存入字符串TAG中
	 * 实现过程：调用getSimpleName ()函数
	 */
    private final static String TAG = MetaData.class.getSimpleName();
    private String mRelatedGid = null;
    /*
     * 功能描述：设置数据，即生成元数据库
     * 实现过程：调用JSONObject库函数put ()，Task类中的setNotes ()和setName ()函数
     * 参数注解：
     */
    public void setMeta(String gid, JSONObject metaInfo) 
    {
    	//对函数块进行注释
        try {
            metaInfo.put(GTaskStringUtils.META_HEAD_GTASK_ID, gid);
            /*
             * 将这对键值放入metaInfo这个jsonobject对象中
             */
        } catch (JSONException e) {
            Log.e(TAG, "failed to put related gid");
            /*
             * 输出错误信息
             */
        }
        setNotes(metaInfo.toString());
        setName(GTaskStringUtils.META_NOTE_NAME);
    }
    /*
     * 功能描述：获取相关联的Gid
     */
    public String getRelatedGid() {
        return mRelatedGid;
    }
    /*
     * 功能描述：判断当前数据是否为空，若为空则返回真即值得保存
     * Made By CuiCan
     */
    @Override
    public boolean isWorthSaving() {
        return getNotes() != null;
    }
    /*
     * 功能描述：使用远程json数据对象设置元数据内容
     * 实现过程：调用父类Task中的setContentByRemoteJSON ()函数，并
     * 参数注解： 
     */
    @Override
    public void setContentByRemoteJSON(JSONObject js) {
        super.setContentByRemoteJSON(js);
        if (getNotes() != null) {
            try {
                JSONObject metaInfo = new JSONObject(getNotes().trim());
                mRelatedGid = metaInfo.getString(GTaskStringUtils.META_HEAD_GTASK_ID);
            } catch (JSONException e) {
                Log.w(TAG, "failed to get related gid");
                /*
                 * 输出警告信息
                 */
                mRelatedGid = null;
            }
        }
    }
    /*
     * 功能描述：使用本地json数据对象设置元数据内容，一般不会用到，若用到，则抛出异常
     * Made By CuiCan
     */
    @Override
    public void setContentByLocalJSON(JSONObject js) {
        // this function should not be called
        throw new IllegalAccessError("MetaData:setContentByLocalJSON should not be called");
        /*
         * 传递非法参数异常
         */
    }
    /*
     * 功能描述：从元数据内容中获取本地json对象，一般不会用到，若用到，则抛出异常
     * Made By CuiCan
     */
    @Override
    public JSONObject getLocalJSONFromContent() {
        throw new IllegalAccessError("MetaData:getLocalJSONFromContent should not be called");
        /*
         * 传递非法参数异常
         * Made By Cui Can
         */
    }
    /*
     * 功能描述：获取同步动作状态，一般不会用到，若用到，则抛出异常
     * Made By CuiCan
     */
    @Override
    public int getSyncAction(Cursor c) {
        throw new IllegalAccessError("MetaData:getSyncAction should not be called");
        /*
         * 传递非法参数异常
         * Made By Cui Can
         */
    }
 
}