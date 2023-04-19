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

package net.micode.notes.gtask.remote;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.data.MetaData;
import net.micode.notes.gtask.data.Node;
import net.micode.notes.gtask.data.SqlNote;
import net.micode.notes.gtask.data.Task;
import net.micode.notes.gtask.data.TaskList;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.gtask.exception.NetworkFailureException;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

 
public class GTaskManager {
    private static final String TAG = GTaskManager.class.getSimpleName();
    public static final int STATE_SUCCESS = 0;
    public static final int STATE_NETWORK_ERROR = 1;
    public static final int STATE_INTERNAL_ERROR = 2;
    public static final int STATE_SYNC_IN_PROGRESS = 3;
    public static final int STATE_SYNC_CANCELLED = 4; 
    private static GTaskManager mInstance = null;
 
    private Activity mActivity;
    private Context mContext;
    private ContentResolver mContentResolver;
    private boolean mSyncing;
    private boolean mCancelled;
    private HashMap<String, TaskList> mGTaskListHashMap;
    private HashMap<String, Node> mGTaskHashMap;
    private HashMap<String, MetaData> mMetaHashMap;
    private TaskList mMetaList;
    private HashSet<Long> mLocalDeleteIdMap;   
    private HashMap<String, Long> mGidToNid;
    private HashMap<Long, String> mNidToGid;
 
    private GTaskManager() {                                   //对象初始化函数
        mSyncing = false;                                      //正在同步,flase代表未执行
        mCancelled = false;                                    //全局标识，flase代表可以执行
        mGTaskListHashMap = new HashMap<String, TaskList>();   //<>代表Java的泛型,就是创建一个用类型作为参数的类。
        mGTaskHashMap = new HashMap<String, Node>();
        mMetaHashMap = new HashMap<String, MetaData>();
        mMetaList = null;
        mLocalDeleteIdMap = new HashSet<Long>();
        mGidToNid = new HashMap<String, Long>();    //GoogleID to NodeID??
        mNidToGid = new HashMap<Long, String>();    //NodeID to GoogleID???通过hashmap散列表建立映射
    }
 
    /**
     * 包含关键字synchronized，语言级同步，指明该函数可能运行在多线程的环境下。
     * 功能：类初始化函数
     * @author TTS
     * @return GtaskManger
     */
    public static synchronized GTaskManager getInstance() {    //可能运行在多线程环境下，使用语言级同步--synchronized
        if (mInstance == null) {
            mInstance = new GTaskManager();
        }
        return mInstance;
    }
 
    /**
     * 包含关键字synchronized，语言级同步，指明该函数可能运行在多线程的环境下。
     * @author TTS
     * @param activity
     */
    public synchronized void setActivityContext(Activity activity) {
        // used for getting auth token
        mActivity = activity;
    }
 
    /**
     * 核心函数
     * 功能：实现了本地同步操作和远端同步操作
     * @author TTS
     * @param context-----获取上下文
     * @param asyncTask-------用于同步的异步操作类
     * @return int
     */
    public int sync(Context context, GTaskASyncTask asyncTask) {           //核心函数
        if (mSyncing) {
            Log.d(TAG, "Sync is in progress");                       //创建日志文件（调试信息），debug
            return STATE_SYNC_IN_PROGRESS;
        }
        mContext = context;
        mContentResolver = mContext.getContentResolver();
        mSyncing = true;
        mCancelled = false;
        mGTaskListHashMap.clear();
        mGTaskHashMap.clear();
        mMetaHashMap.clear();
        mLocalDeleteIdMap.clear();
        mGidToNid.clear();
        mNidToGid.clear();
 
        try {
            GTaskClient client = GTaskClient.getInstance();    //getInstance即为创建一个实例,client--客户机
            client.resetUpdateArray();     //JSONArray类型，reset即置为NULL
 
            // login google task
            if (!mCancelled) {
                if (!client.login(mActivity)) {
                    throw new NetworkFailureException("login google task failed");
                }
            }
 
            // get the task list from google
            asyncTask.publishProgess(mContext.getString(R.string.sync_progress_init_list));
            initGTaskList();                                 //获取Google上的JSONtasklist转为本地TaskList
 
            // do content sync work
            asyncTask.publishProgess(mContext.getString(R.string.sync_progress_syncing));
            syncContent();
        } catch (NetworkFailureException e) {                       //分为两种异常，此类异常为网络异常
            Log.e(TAG, e.toString());                             //创建日志文件（调试信息），error
            return STATE_NETWORK_ERROR;
        } catch (ActionFailureException e) {                        //此类异常为操作异常
            Log.e(TAG, e.toString());
            return STATE_INTERNAL_ERROR;
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return STATE_INTERNAL_ERROR;
        } finally {
            mGTaskListHashMap.clear();
            mGTaskHashMap.clear();
            mMetaHashMap.clear();
            mLocalDeleteIdMap.clear();
            mGidToNid.clear();
            mNidToGid.clear();
            mSyncing = false;
        }
 
        return mCancelled ? STATE_SYNC_CANCELLED : STATE_SUCCESS;
    }
 
    /**
     *功能：初始化GtaskList，获取Google上的JSONtasklist转为本地TaskList。
     *获得的数据存储在mMetaList，mGTaskListHashMap，mGTaskHashMap
     *@author TTS
     *@exception NetworkFailureException
     *@return void
     */
    private void initGTaskList() throws NetworkFailureException {
        if (mCancelled)
            return;
        GTaskClient client = GTaskClient.getInstance();    //getInstance即为创建一个实例，client应指远端客户机
        try {
        	//Json对象是Name Value对(即子元素)的无序集合，相当于一个Map对象。JsonObject类是bantouyan-json库对Json对象的抽象，提供操纵Json对象的各种方法。
        	//其格式为{"key1":value1,"key2",value2....};key 必须是字符串。
        	//因为ajax请求不刷新页面，但配合js可以实现局部刷新，因此json常常被用来作为异步请求的返回对象使用。
            JSONArray jsTaskLists = client.getTaskLists();     //原注释为get task list------lists？？？
 
            // init meta list first
            mMetaList = null;                                       //TaskList类型
            for (int i = 0; i < jsTaskLists.length(); i++) {
                JSONObject object = jsTaskLists.getJSONObject(i);  //JSONObject与JSONArray一个为对象，一个为数组。此处取出单个JASONObject
                String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);
 
                if (name.equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_META)) {
                    mMetaList = new TaskList();                    //MetaList意为元表,Tasklist类型，此处为初始化
                    mMetaList.setContentByRemoteJSON(object);      //将JSON中部分数据复制到自己定义的对象中相对应的数据：name->mname...
 
                    // load meta data
                    JSONArray jsMetas = client.getTaskList(gid);   //原注释为get action_list------list？？？
                    for (int j = 0; j < jsMetas.length(); j++) {
                        object = (JSONObject) jsMetas.getJSONObject(j);
                        MetaData metaData = new MetaData();            //继承自Node
                        metaData.setContentByRemoteJSON(object);
                        if (metaData.isWorthSaving()) {                             //if not worth to save，metadata将不加入mMetaList
                            mMetaList.addChildTask(metaData);
                            if (metaData.getGid() != null) {
                                mMetaHashMap.put(metaData.getRelatedGid(), metaData);
                            }
                        }
                    }
                }
            }
 
            // create meta list if not existed
            if (mMetaList == null) {
                mMetaList = new TaskList();
                mMetaList.setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                        + GTaskStringUtils.FOLDER_META);
                GTaskClient.getInstance().createTaskList(mMetaList);
            }
 
            // init task list
            for (int i = 0; i < jsTaskLists.length(); i++) {
                JSONObject object = jsTaskLists.getJSONObject(i);
                String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);  //通过getString函数传入本地某个标志数据的名称，获取其在远端的名称。
                String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);
 
                if (name.startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX)
                        && !name.equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                                + GTaskStringUtils.FOLDER_META)) {
                    TaskList tasklist = new TaskList();     //继承自Node
                    tasklist.setContentByRemoteJSON(object);
                    mGTaskListHashMap.put(gid, tasklist);       
                    mGTaskHashMap.put(gid, tasklist);          //为什么加两遍？？？
 
                    // load tasks
                    JSONArray jsTasks = client.getTaskList(gid);
                    for (int j = 0; j < jsTasks.length(); j++) {
                        object = (JSONObject) jsTasks.getJSONObject(j);
                        gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                        Task task = new Task();
                        task.setContentByRemoteJSON(object);
                        if (task.isWorthSaving()) {
                            task.setMetaInfo(mMetaHashMap.get(gid));
                            tasklist.addChildTask(task);
                            mGTaskHashMap.put(gid, task);
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("initGTaskList: handing JSONObject failed");
        }
    }
 
    /**
     * 功能：本地内容同步操作
     * @throws NetworkFailureException
     * @return 无返回值
     */
    private void syncContent() throws NetworkFailureException {    //本地内容同步操作
        int syncType;
        Cursor c = null;                                           //数据库指针
        String gid;                                                //GoogleID??
        Node node;                                                 //Node包含Sync_Action的不同类型
 
        mLocalDeleteIdMap.clear();                                 //HashSet<Long>类型
 
        if (mCancelled) {
            return;
        }
 
        // for local deleted note
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type<>? AND parent_id=?)", new String[] {
                            String.valueOf(Notes.TYPE_SYSTEM), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, null);
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        doContentSync(Node.SYNC_ACTION_DEL_REMOTE, node, c);
                    }
 
                    mLocalDeleteIdMap.add(c.getLong(SqlNote.ID_COLUMN));
                }
            } else {
                Log.w(TAG, "failed to query trash folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }
 
        // sync folder first
        syncFolder();
 
        // for note existing in database
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type=? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_NOTE), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN));   //通过hashmap建立联系
                        mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid);   //通过hashmap建立联系
                        syncType = node.getSyncAction(c);
                    } else {
                        if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                            // local add
                            syncType = Node.SYNC_ACTION_ADD_REMOTE;
                        } else {
                            // remote delete
                            syncType = Node.SYNC_ACTION_DEL_LOCAL;
                        }
                    }
                    doContentSync(syncType, node, c);
                }
            } else {
                Log.w(TAG, "failed to query existing note in database");
            }
 
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }
 
        // go through remaining items
        Iterator<Map.Entry<String, Node>> iter = mGTaskHashMap.entrySet().iterator();   //Iterator迭代器
        while (iter.hasNext()) {
            Map.Entry<String, Node> entry = iter.next();
            node = entry.getValue();
            doContentSync(Node.SYNC_ACTION_ADD_LOCAL, node, null);
        }
 
        // mCancelled can be set by another thread, so we neet to check one by    //thread----线程
        // one
        // clear local delete table
        if (!mCancelled) {
            if (!DataUtils.batchDeleteNotes(mContentResolver, mLocalDeleteIdMap)) {
                throw new ActionFailureException("failed to batch-delete local deleted notes");
            }
        }
 
        // refresh local sync id
        if (!mCancelled) {
            GTaskClient.getInstance().commitUpdate();
            refreshLocalSyncId();
        }
 
    }
 
    /**
     * 功能：
     * @author TTS
     * @throws NetworkFailureException
     */
    private void syncFolder() throws NetworkFailureException {
        Cursor c = null;
        String gid;
        Node node;
        int syncType;
 
        if (mCancelled) {
            return;
        }
 
        // for root folder
        try {
            c = mContentResolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                    Notes.ID_ROOT_FOLDER), SqlNote.PROJECTION_NOTE, null, null, null);
            if (c != null) {
                c.moveToNext();
                gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                node = mGTaskHashMap.get(gid);
                if (node != null) {
                    mGTaskHashMap.remove(gid);
                    mGidToNid.put(gid, (long) Notes.ID_ROOT_FOLDER);
                    mNidToGid.put((long) Notes.ID_ROOT_FOLDER, gid);
                    // for system folder, only update remote name if necessary
                    if (!node.getName().equals(
                            GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT))
                        doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
                } else {
                    doContentSync(Node.SYNC_ACTION_ADD_REMOTE, node, c);
                }
            } else {
                Log.w(TAG, "failed to query root folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }
 
        // for call-note folder
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE, "(_id=?)",
                    new String[] {
                        String.valueOf(Notes.ID_CALL_RECORD_FOLDER)
                    }, null);
            if (c != null) {
                if (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, (long) Notes.ID_CALL_RECORD_FOLDER);
                        mNidToGid.put((long) Notes.ID_CALL_RECORD_FOLDER, gid);
                        // for system folder, only update remote name if
                        // necessary
                        if (!node.getName().equals(
                                GTaskStringUtils.MIUI_FOLDER_PREFFIX
                                        + GTaskStringUtils.FOLDER_CALL_NOTE))
                            doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
                    } else {
                        doContentSync(Node.SYNC_ACTION_ADD_REMOTE, node, c);
                    }
                }
            } else {
                Log.w(TAG, "failed to query call note folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }
 
        // for local existing folders
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type=? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_FOLDER), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN));
                        mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid);
                        syncType = node.getSyncAction(c);
                    } else {
                        if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                            // local add
                            syncType = Node.SYNC_ACTION_ADD_REMOTE;
                        } else {
                            // remote delete
                            syncType = Node.SYNC_ACTION_DEL_LOCAL;
                        }
                    }
                    doContentSync(syncType, node, c);
                }
            } else {
                Log.w(TAG, "failed to query existing folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }
 
        // for remote add folders
        Iterator<Map.Entry<String, TaskList>> iter = mGTaskListHashMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, TaskList> entry = iter.next();
            gid = entry.getKey();
            node = entry.getValue();
            if (mGTaskHashMap.containsKey(gid)) {
                mGTaskHashMap.remove(gid);
                doContentSync(Node.SYNC_ACTION_ADD_LOCAL, node, null);
            }
        }
 
        if (!mCancelled)
            GTaskClient.getInstance().commitUpdate();
    }
 
    /**
     * 功能：syncType分类，addLocalNode，addRemoteNode，deleteNode，updateLocalNode，updateRemoteNode
     * @author TTS
     * @param syncType
     * @param node
     * @param c
     * @throws NetworkFailureException
     */
    private void doContentSync(int syncType, Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }
 
        MetaData meta;
        switch (syncType) {
            case Node.SYNC_ACTION_ADD_LOCAL:
                addLocalNode(node);
                break;
            case Node.SYNC_ACTION_ADD_REMOTE:
                addRemoteNode(node, c);
                break;
            case Node.SYNC_ACTION_DEL_LOCAL:
                meta = mMetaHashMap.get(c.getString(SqlNote.GTASK_ID_COLUMN));
                if (meta != null) {
                    GTaskClient.getInstance().deleteNode(meta);
                }
                mLocalDeleteIdMap.add(c.getLong(SqlNote.ID_COLUMN));
                break;
            case Node.SYNC_ACTION_DEL_REMOTE:
                meta = mMetaHashMap.get(node.getGid());
                if (meta != null) {
                    GTaskClient.getInstance().deleteNode(meta);
                }
                GTaskClient.getInstance().deleteNode(node);
                break;
            case Node.SYNC_ACTION_UPDATE_LOCAL:
                updateLocalNode(node, c);
                break;
            case Node.SYNC_ACTION_UPDATE_REMOTE:
                updateRemoteNode(node, c);
                break;
            case Node.SYNC_ACTION_UPDATE_CONFLICT:
                // merging both modifications maybe a good idea
                // right now just use local update simply
                updateRemoteNode(node, c);
                break;
            case Node.SYNC_ACTION_NONE:
                break;
            case Node.SYNC_ACTION_ERROR:
            default:
                throw new ActionFailureException("unkown sync action type");
        }
    }
 
    /**
     * 功能：本地增加Node
     * @author TTS
     * @param node
     * @throws NetworkFailureException
     */
    private void addLocalNode(Node node) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }
 
        SqlNote sqlNote;
        if (node instanceof TaskList) {
            if (node.getName().equals(
                    GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT)) {
                sqlNote = new SqlNote(mContext, Notes.ID_ROOT_FOLDER);
            } else if (node.getName().equals(
                    GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_CALL_NOTE)) {
                sqlNote = new SqlNote(mContext, Notes.ID_CALL_RECORD_FOLDER);
            } else {
                sqlNote = new SqlNote(mContext);
                sqlNote.setContent(node.getLocalJSONFromContent());
                sqlNote.setParentId(Notes.ID_ROOT_FOLDER);
            }
        } else {
            sqlNote = new SqlNote(mContext);
            JSONObject js = node.getLocalJSONFromContent();
            try {
                if (js.has(GTaskStringUtils.META_HEAD_NOTE)) {
                    JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
                    if (note.has(NoteColumns.ID)) {
                        long id = note.getLong(NoteColumns.ID);
                        if (DataUtils.existInNoteDatabase(mContentResolver, id)) {
                            // the id is not available, have to create a new one
                            note.remove(NoteColumns.ID);
                        }
                    }
                }
 
                if (js.has(GTaskStringUtils.META_HEAD_DATA)) {
                    JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);
                    for (int i = 0; i < dataArray.length(); i++) {
                        JSONObject data = dataArray.getJSONObject(i);
                        if (data.has(DataColumns.ID)) {
                            long dataId = data.getLong(DataColumns.ID);
                            if (DataUtils.existInDataDatabase(mContentResolver, dataId)) {
                                // the data id is not available, have to create
                                // a new one
                                data.remove(DataColumns.ID);
                            }
                        }
                    }
 
                }
            } catch (JSONException e) {
                Log.w(TAG, e.toString());
                e.printStackTrace();
            }
            sqlNote.setContent(js);
 
            Long parentId = mGidToNid.get(((Task) node).getParent().getGid());
            if (parentId == null) {
                Log.e(TAG, "cannot find task's parent id locally");
                throw new ActionFailureException("cannot add local node");
            }
            sqlNote.setParentId(parentId.longValue());
        }
 
        // create the local node
        sqlNote.setGtaskId(node.getGid());
        sqlNote.commit(false);
 
        // update gid-nid mapping
        mGidToNid.put(node.getGid(), sqlNote.getId());
        mNidToGid.put(sqlNote.getId(), node.getGid());
 
        // update meta
        updateRemoteMeta(node.getGid(), sqlNote);
    }
 
    /**
     * 功能：update本地node
     * @author TTS
     * @param node
     * ----同步操作的基础数据类型
     * @param c
     * ----Cursor
     * @throws NetworkFailureException
     */
    private void updateLocalNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }
 
        SqlNote sqlNote;
        // update the note locally
        sqlNote = new SqlNote(mContext, c);
        sqlNote.setContent(node.getLocalJSONFromContent());
 
        Long parentId = (node instanceof Task) ? mGidToNid.get(((Task) node).getParent().getGid())
                : new Long(Notes.ID_ROOT_FOLDER);
        if (parentId == null) {
            Log.e(TAG, "cannot find task's parent id locally");
            throw new ActionFailureException("cannot update local node");
        }
        sqlNote.setParentId(parentId.longValue());
        sqlNote.commit(true);
 
        // update meta info
        updateRemoteMeta(node.getGid(), sqlNote);
    }
 
    /**
     * 功能：远程增加Node
     * 需要updateRemoteMeta
     * @author TTS
     * @param node
     * ----同步操作的基础数据类型
     * @param c
     * --Cursor
     * @throws NetworkFailureException
     */
    private void addRemoteNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }
 
        SqlNote sqlNote = new SqlNote(mContext, c);     //从本地mContext中获取内容
        Node n;
 
        // update remotely
        if (sqlNote.isNoteType()) {
            Task task = new Task();
            task.setContentByLocalJSON(sqlNote.getContent());
 
            String parentGid = mNidToGid.get(sqlNote.getParentId());
            if (parentGid == null) {
                Log.e(TAG, "cannot find task's parent tasklist");           //调试信息
                throw new ActionFailureException("cannot add remote task");
            }
            mGTaskListHashMap.get(parentGid).addChildTask(task);            //在本地生成的GTaskList中增加子结点
 
            //登录远程服务器，创建Task
            GTaskClient.getInstance().createTask(task); 
            n = (Node) task;
 
            // add meta
            updateRemoteMeta(task.getGid(), sqlNote);
        } else {
            TaskList tasklist = null;
 
            // we need to skip folder if it has already existed
            String folderName = GTaskStringUtils.MIUI_FOLDER_PREFFIX;
            if (sqlNote.getId() == Notes.ID_ROOT_FOLDER)
                folderName += GTaskStringUtils.FOLDER_DEFAULT;
            else if (sqlNote.getId() == Notes.ID_CALL_RECORD_FOLDER)
                folderName += GTaskStringUtils.FOLDER_CALL_NOTE;
            else
                folderName += sqlNote.getSnippet();
 
            //iterator迭代器，通过统一的接口迭代所有的map元素
            Iterator<Map.Entry<String, TaskList>> iter = mGTaskListHashMap.entrySet().iterator(); 
            while (iter.hasNext()) {
                Map.Entry<String, TaskList> entry = iter.next();
                String gid = entry.getKey();
                TaskList list = entry.getValue();
 
                if (list.getName().equals(folderName)) {
                    tasklist = list;
                    if (mGTaskHashMap.containsKey(gid)) {
                        mGTaskHashMap.remove(gid);
                    }
                    break;
                }
            }
 
            // no match we can add now
            if (tasklist == null) {
                tasklist = new TaskList();
                tasklist.setContentByLocalJSON(sqlNote.getContent());
                GTaskClient.getInstance().createTaskList(tasklist);
                mGTaskListHashMap.put(tasklist.getGid(), tasklist);
            }
            n = (Node) tasklist;
        }
 
        // update local note
        sqlNote.setGtaskId(n.getGid());
        sqlNote.commit(false);
        sqlNote.resetLocalModified();
        sqlNote.commit(true);
 
        // gid-id mapping                                             //创建id间的映射
        mGidToNid.put(n.getGid(), sqlNote.getId());
        mNidToGid.put(sqlNote.getId(), n.getGid());
    }
 
    /**
     * 功能：更新远端的Node，包含meta更新(updateRemoteMeta)
     * @author TTS
     * @param node
     * ----同步操作的基础数据类型
     * @param c
     *  --Cursor
     * @throws NetworkFailureException
     */
    private void updateRemoteNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }
 
        SqlNote sqlNote = new SqlNote(mContext, c);
 
        // update remotely
        node.setContentByLocalJSON(sqlNote.getContent());
        GTaskClient.getInstance().addUpdateNode(node);                                //GTaskClient用途为从本地登陆远端服务器 
 
        // update meta
        updateRemoteMeta(node.getGid(), sqlNote);
 
        // move task if necessary
        if (sqlNote.isNoteType()) {
            Task task = (Task) node;
            TaskList preParentList = task.getParent(); 
            //preParentList为通过node获取的父节点列表
            
            String curParentGid = mNidToGid.get(sqlNote.getParentId());
            //curParentGid为通过光标在数据库中找到sqlNote的mParentId，再通过mNidToGid由long类型转为String类型的Gid
            
            if (curParentGid == null) {
                Log.e(TAG, "cannot find task's parent tasklist");
                throw new ActionFailureException("cannot update remote task");
            }
            TaskList curParentList = mGTaskListHashMap.get(curParentGid);
            //通过HashMap找到对应Gid的TaskList
 
            if (preParentList != curParentList) {                                          //?????????????
                preParentList.removeChildTask(task);
                curParentList.addChildTask(task);
                GTaskClient.getInstance().moveTask(task, preParentList, curParentList);
            }
        }
 
        // clear local modified flag
        sqlNote.resetLocalModified();
        //commit到本地数据库
        sqlNote.commit(true); 
    }
 
    /**
     * 功能：升级远程meta。  meta---元数据----计算机文件系统管理数据---管理数据的数据。
     * @author TTS
     * @param gid
     * ---GoogleID为String类型
     * @param sqlNote
     * ---同步前的数据库操作，故使用类SqlNote
     * @throws NetworkFailureException
     */
    private void updateRemoteMeta(String gid, SqlNote sqlNote) throws NetworkFailureException {
        if (sqlNote != null && sqlNote.isNoteType()) {
            MetaData metaData = mMetaHashMap.get(gid);
            if (metaData != null) {
                metaData.setMeta(gid, sqlNote.getContent());
                GTaskClient.getInstance().addUpdateNode(metaData);
            } else {
                metaData = new MetaData();
                metaData.setMeta(gid, sqlNote.getContent());
                mMetaList.addChildTask(metaData);
                mMetaHashMap.put(gid, metaData);
                GTaskClient.getInstance().createTask(metaData);
            }
        }
    }
 
    /**
     * 功能：刷新本地，给sync的ID对应上最后更改过的对象
     * @author TTS
     * @return void
     * @throws NetworkFailureException
     */
    private void refreshLocalSyncId() throws NetworkFailureException {
        if (mCancelled) {
            return;
        }
 
        // get the latest gtask list                                               //获取最近的（最晚的）gtask list
        mGTaskHashMap.clear();
        mGTaskListHashMap.clear();
        mMetaHashMap.clear();
        initGTaskList();
 
        Cursor c = null;
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type<>? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_SYSTEM), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");                                                 //query语句：五个参数，NoteColumns.TYPE + " DESC"-----为按类型递减顺序返回查询结果。new String[] {String.valueOf(Notes.TYPE_SYSTEM), String.valueOf(Notes.ID_TRASH_FOLER)}------为选择参数。"(type<>? AND parent_id<>?)"-------指明返回行过滤器。SqlNote.PROJECTION_NOTE--------应返回的数据列的名字。Notes.CONTENT_NOTE_URI--------contentProvider包含所有数据集所对应的uri
            if (c != null) {
                while (c.moveToNext()) {
                    String gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    Node node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        ContentValues values = new ContentValues();                     //在ContentValues中创建键值对。准备通过contentResolver写入数据
                        values.put(NoteColumns.SYNC_ID, node.getLastModified());
                        mContentResolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,   //进行批量更改，选择参数为NULL，应该可以用insert替换，参数分别为表名和需要更新的value对象。
                                c.getLong(SqlNote.ID_COLUMN)), values, null, null);
                    } else {
                        Log.e(TAG, "something is missed");
                        throw new ActionFailureException(
                                "some local items don't have gid after sync");
                    }
                }
            } else {
                Log.w(TAG, "failed to query local note to refresh sync id");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }
    }
 
    /**
     * 功能：获取同步账号,mAccount.name
     * @author TTS
     * @return String
     */
    public String getSyncAccount() {
        return GTaskClient.getInstance().getSyncAccount().name;
    }
 
    /**
     * 功能：取消同步，置mCancelled为true
     * @author TTS
     */
    public void cancelSync() {
        mCancelled = true;
    }
}
