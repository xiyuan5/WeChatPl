package com.zhouxy.wxchatpl;

import android.app.Application;

/**
 * create by zhouxy on 2020.11.03
 */
public class App extends Application {
    private static App ins;

    public static App Ins(){
        return ins;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ins = this;
    }
}
