package com.zhouxy.wxchatpl;

import android.content.ContentValues;
import android.database.Cursor;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * create by zhouxy on 2020.10.28
 */
public class Main implements IXposedHookLoadPackage {

    private static Object storageInsertClazz;
    private static Map<Long, Object> msgCacheMap = new HashMap<>();


    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        MLog.d("当前包名：----> app:" + loadPackageParam.packageName);

        String packageName = loadPackageParam.packageName;

        if (!packageName.equals("com.tencent.mm")) {
            return;
        }
        hookInsertMsg(loadPackageParam);
        handleReCallMsg(loadPackageParam);
        msgHandle(loadPackageParam);
        insertMsgDAOListener(loadPackageParam);
    }

    /** hook db insert */
    private void hookInsertMsg(XC_LoadPackage.LoadPackageParam lpparam) {
        MLog.d("插入消息监听中...");
        Class<?> classDb = XposedHelpers.findClassIfExists("com.tencent.wcdb.database.SQLiteDatabase", lpparam.classLoader);
        if (classDb == null) {
            MLog.d("没有找到类：" + "com.tencent.wcdb.database.SQLiteDatabase");
        }
        XposedHelpers.findAndHookMethod(classDb, "insertWithOnConflict", String.class, String.class, ContentValues.class, int.class, new XC_MethodHook() {

            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                try {
                    if (param.args[0].equals("message")) {
                        MLog.d("                                   ");
                        MLog.d("              插入消息               ");
                        MLog.d("!!-------------start-------------!!");
                        MLog.d("pm.obj:" + param.thisObject.toString());
                        MLog.d("pm1:" + param.args[0].toString());
                        MLog.d("pm2:" + param.args[1].toString());
                        MLog.d("contentValue:");
                        ContentValues contentValues = (ContentValues) param.args[2];
                        for (String s : contentValues.keySet()) {
                            MLog.d(s + ":" + contentValues.getAsString(s));
                            if (s.equals("content")) {
                                contentValues.put(s, contentValues.getAsString(s));
                            }
                        }
                        MLog.d("!!------------end--------------!!");
                        MLog.d("                                 ");
                        MLog.d("                                 ");

                    }
                } catch (Error | Exception e) {
                    e.printStackTrace();
                    MLog.d(e.getMessage());
                }
            }
        });
    }

    public void handleReCallMsg(final XC_LoadPackage.LoadPackageParam lpparam) {
        MLog.d("撤回消息监听中...");
        String database = "com.tencent.wcdb.database.SQLiteDatabase";
        String updateWithOnConflict = "updateWithOnConflict";
        XposedHelpers.findAndHookMethod(database, lpparam.classLoader, updateWithOnConflict, String.class, ContentValues.class, String.class, String[].class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {

                    if (param.args[0].equals("message")) {//消息列表
                        MLog.d("                                   ");
                        MLog.d("           消息修改                  ");
                        MLog.d("!!-------------start-------------!!");
                        MLog.d("insertClass:" + storageInsertClazz.toString());
                        MLog.d("pm1:" + param.args[0].toString());
                        MLog.d("contentValue:");
                        ContentValues contentValues = (ContentValues) param.args[1];
                        for (String s : contentValues.keySet()) {
                            MLog.d(s + ":" + contentValues.getAsString(s));
                        }
                        MLog.d("!!------------end--------------!!");
                        MLog.d("                                 ");
                        MLog.d("                                 ");

                        contentValues = ((ContentValues) param.args[1]);

                        if (contentValues.getAsInteger("type") == 10000) {
                            handleMessageRecall(contentValues);//插入提示消息
                            param.setResult(1);//return
                        }
                    }
                } catch (Error | Exception e) {
                    MLog.d(e.getMessage());
                }
            }
        });

    }

    private void handleMessageRecall(ContentValues contentValues) {
        long msgId = contentValues.getAsLong("msgId");
        Object msg = msgCacheMap.get(msgId);
        long createTime = XposedHelpers.getLongField(msg, "field_createTime");
        XposedHelpers.setIntField(msg, "field_type", contentValues.getAsInteger("type"));
        XposedHelpers.setObjectField(msg, "field_content", contentValues.getAsString("content") + "(已拦截)");
        XposedHelpers.setLongField(msg, "field_createTime", createTime + 1L);
        XposedHelpers.callMethod(storageInsertClazz, HookParams.MsgInfoStorageInsertMethod, msg, false);

    }

    private void msgHandle(XC_LoadPackage.LoadPackageParam lpparam){
        Class msgInfoClass = XposedHelpers.findClass(HookParams.MsgInfoClassName, lpparam.classLoader);
        XposedHelpers.findAndHookMethod(HookParams.MsgInfoStorageClassName, lpparam.classLoader, HookParams.MsgInfoStorageInsertMethod, msgInfoClass, boolean.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {

                    storageInsertClazz = param.thisObject;

                    Object msg = param.args[0];
                    long msgId = XposedHelpers.getLongField(msg, "field_msgId");
                    msgCacheMap.put(msgId, msg);
                } catch (Error | Exception e) {
                }

            }
        });
    }


    /**
     * 注册接收消息的监听，处理UI触发流程
     */
    public static void uiMsgListener(XC_LoadPackage.LoadPackageParam lpparam) {
        MLog.d("uiMsgListener 开始");
        Object[] arrayOfObject = new Object[2];
        arrayOfObject[0] = Cursor.class;
        arrayOfObject[1] = new XC_MethodHook() {
            protected void afterHookedMethod(MethodHookParam methodHookParam) throws XmlPullParserException, IOException {
                //0代表别人发的消息，1代表是自己发的消息
                int field_isSend = ((Integer) XposedHelpers.getObjectField(methodHookParam.thisObject, "field_isSend")).intValue();
                //消息类型：1是文本...参考wechat_manager里的消息类型定义
                int field_type = ((Integer) XposedHelpers.getObjectField(methodHookParam.thisObject, "field_type")).intValue();
                //微信服务器端的消息id
                Object field_msgSvrId =  XposedHelpers.getObjectField(methodHookParam.thisObject, "field_msgSvrId");
                //消息内容
                String field_content = (String) XposedHelpers.getObjectField(methodHookParam.thisObject, "field_content");
                String field_talker = (String) XposedHelpers.getObjectField(methodHookParam.thisObject, "field_talker");
                //消息创建时间
                long field_createTime = ((Long) XposedHelpers.getObjectField(methodHookParam.thisObject, "field_createTime")).longValue();
                MLog.d("uiMsgListener field_isSend:" + field_isSend + "--field_type:" + field_type + "--field_msgSvrId--" + field_msgSvrId + "--field_talker--" + field_talker + "--field_content--" + field_content);

            }
        };
        XposedHelpers.findAndHookMethod("com.tencent.mm.storage.bi", lpparam.classLoader, "d", arrayOfObject);
        MLog.d("uiMsgListener 结束");
    }

    /**
     * 插入消息监听 处理微信 dao层
     */
    public static void insertMsgDAOListener(XC_LoadPackage.LoadPackageParam lpparam) {
        MLog.d("insertMsgDAOListener 开始");
        Class<?> au = XposedHelpers.findClass("com.tencent.mm.storage.bi", lpparam.classLoader);
        Object[] arrayOfObject = new Object[3];
        arrayOfObject[0] = au;
        arrayOfObject[1] = boolean.class;
        arrayOfObject[2] = new XC_MethodHook() {
            protected void afterHookedMethod(MethodHookParam paramAnonymousMethodHookParam) throws XmlPullParserException, IOException {
                Object au = paramAnonymousMethodHookParam.args[0];
                if (au == null) {
                    return;
                }
                int field_isSend = ((Integer) XposedHelpers.getObjectField(au, "field_isSend")).intValue();
                int field_type = ((Integer) XposedHelpers.getObjectField(au, "field_type")).intValue();
                Object field_msgSvrId = XposedHelpers.getObjectField(au, "field_msgSvrId");
                String field_content = (String) XposedHelpers.getObjectField(au, "field_content");
                String field_talker = (String) XposedHelpers.getObjectField(au, "field_talker");
                MLog.d("insertMsgDAOListener field_isSend:" + field_isSend + "--field_type:" + field_type + "--field_msgSvrId--" + field_msgSvrId + "--field_talker--" + field_talker + "--field_content--" + field_content);

            }
        };
        XposedHelpers.findAndHookMethod(XposedHelpers.findClass("com.tencent.mm.storage.bj", lpparam.classLoader), "b", arrayOfObject);
        MLog.d("insertMsgDAOListener 结束");
    }

}
