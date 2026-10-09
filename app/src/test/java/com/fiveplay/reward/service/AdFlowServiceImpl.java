package com.fiveplay.reward.service;

import androidx.appcompat.app.AppCompatActivity;
import com.fiveplay.commonlibrary.bean.SessionAdData;

public final class AdFlowServiceImpl {
    public int creations;

    public void Z0(AppCompatActivity activity, SessionAdData data) {
        creations++;
    }

    public void l(boolean visible) {
        // The inspected implementation returns when its binding has not been created.
        if (creations == 0) return;
    }
}
