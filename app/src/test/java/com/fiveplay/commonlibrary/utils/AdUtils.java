package com.fiveplay.commonlibrary.utils;

import android.app.Activity;
import android.view.ViewGroup;
import com.fiveplay.commonlibrary.bean.AdListBean;
import y2.b;

/** Models observed 7.2.5 control flow, not the SDK or ART implementation. */
public final class AdUtils {
    public int requests;
    public int displays;
    public int rewardRequests;
    public boolean isOver = true;
    public boolean timerActive = true;

    public void showSplashAd(Activity activity, ViewGroup container, b listener) {
        requests++;
    }

    public void showSplashAd(Activity activity, ViewGroup container, AdListBean list, b listener) {
        isOver = false;
        if (list != null) {
            displays++;
            return;
        }
        isOver = true;
        timerActive = false;
        listener.e(false, null);
    }

    public void showReward(Activity activity, b listener, String adId) {
        rewardRequests++;
    }
}
