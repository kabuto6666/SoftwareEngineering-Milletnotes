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
  
 import org.json.JSONObject;
  
 /**
  * 应该是同步操作的基础数据类型，定义了相关指示同步操作的常量
  * 关键字：abstract
  */
 public abstract class Node {
     //定义了各种用于表征同步状态的常量
     public static final int SYNC_ACTION_NONE = 0;// 本地和云端都无可更新内容（即本地和云端内容一致）
     
     public static final int SYNC_ACTION_ADD_REMOTE = 1;// 需要在远程云端增加内容
  
     public static final int SYNC_ACTION_ADD_LOCAL = 2;// 需要在本地增加内容
  
     public static final int SYNC_ACTION_DEL_REMOTE = 3;// 需要在远程云端删除内容
  
     public static final int SYNC_ACTION_DEL_LOCAL = 4;// 需要在本地删除内容
  
     public static final int SYNC_ACTION_UPDATE_REMOTE = 5;// 需要将本地内容更新到远程云端
  
     public static final int SYNC_ACTION_UPDATE_LOCAL = 6;// 需要将远程云端内容更新到本地
  
     public static final int SYNC_ACTION_UPDATE_CONFLICT = 7;// 同步出现冲突
  
     public static final int SYNC_ACTION_ERROR = 8;// 同步出现错误
  
     private String mGid;
  
     private String mName;
  
     private long mLastModified;//记录最后一次修改时间
  
     private boolean mDeleted;//表征是否被删除
  
     public Node() {
         mGid = null;
         mName = "";
         mLastModified = 0;
         mDeleted = false;
     }
  
     public abstract JSONObject getCreateAction(int actionId);
  
     public abstract JSONObject getUpdateAction(int actionId);
  
     public abstract void setContentByRemoteJSON(JSONObject js);
  
     public abstract void setContentByLocalJSON(JSONObject js);
  
     public abstract JSONObject getLocalJSONFromContent();
  
     public abstract int getSyncAction(Cursor c);
  
     public void setGid(String gid) {
         this.mGid = gid;
     }
  
     public void setName(String name) {
         this.mName = name;
     }
  
     public void setLastModified(long lastModified) {
         this.mLastModified = lastModified;
     }
  
     public void setDeleted(boolean deleted) {
         this.mDeleted = deleted;
     }
  
     public String getGid() {
         return this.mGid;
     }
  
     public String getName() {
         return this.mName;
     }
  
     public long getLastModified() {
         return this.mLastModified;
     }
  
     public boolean getDeleted() {
         return this.mDeleted;
     }
  
 }