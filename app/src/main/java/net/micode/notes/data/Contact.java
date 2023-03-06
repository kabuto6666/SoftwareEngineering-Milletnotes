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

package net.micode.notes.data;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.util.HashMap;

/*
 * 联系人Contact
 */
public class Contact {
    private static HashMap<String, String> sContactCache;   //sContactCache是哈希Map，储存联系人？
    private static final String TAG = "Contact";            //TAG标志，说明是Contact

    //定义了字符串CALLER_ID_SELECTION，比较长
    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
    + ",?) AND " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
    + " AND " + Data.RAW_CONTACT_ID + " IN "
            + "(SELECT raw_contact_id "
            + " FROM phone_lookup"
            + " WHERE min_match = '+')";
    
    //获取联系人函数
    public static String getContact(Context context, String phoneNumber) {
        if(sContactCache == null) { 
            sContactCache = new HashMap<String, String>();  //没有联系人，新建
        }

        //查找联系人号码phonenumber并返回
        if(sContactCache.containsKey(phoneNumber)) {
            return sContactCache.get(phoneNumber);
        }

        //selection，（进行字符串的替换？
        String selection = CALLER_ID_SELECTION.replace("+",
                PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));

        //从数据库中查找
        Cursor cursor = context.getContentResolver().query(
                Data.CONTENT_URI,
                new String [] { Phone.DISPLAY_NAME },   //名字
                selection,                          //其他信息？
                new String[] { phoneNumber },           //号码
                null);
        //对结果进行判断
        if (cursor != null && cursor.moveToFirst()) {   //找到
            try {
                String name = cursor.getString(0);      //尝试找名字
                sContactCache.put(phoneNumber, name);   
                return name;                            //返回名字
            } catch (IndexOutOfBoundsException e) {     //异常
                Log.e(TAG, " Cursor get string error " + e.toString());
                return null;
            } finally {
                cursor.close();     //关闭数据库
            }
        } else {                    //没找到
            Log.d(TAG, "No contact matched with number:" + phoneNumber);    
            return null;
        }
    }
}
