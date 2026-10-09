package xyz.fearr.adfree.xposed;

import org.junit.Test;
import static org.junit.Assert.*;

public final class AggressiveAdPolicyTest {
    @Test public void rewardAndFullscreenAdPagesAreNowBlocked() {
        for (String name : new String[]{XbudTargets.FULL_SCREEN,
                "com.bytedance.sdk.openadsdk.core.component.reward.activity.TTRewardVideoActivity",
                "com.kwad.sdk.api.proxy.app.KsRewardVideoActivity",
                "com.bz.ptgapi.activity.PtgRewardVideoPortraitActivity",
                "com.qq.e.ads.RewardvideoPortraitADActivity",
                "com.fiveplay.sihp_homepage.module.splash.RewardVideoActivity"}) {
            assertTrue(name, AggressiveAdPolicy.adActivity(name));
        }
    }

    @Test public void ordinaryBusinessAndSharedFlutterPagesRemainAllowed() {
        for (String name : new String[]{XbudTargets.MAIN, XbudTargets.SPLASH,
                "run.xbud.android.mvp.ui.other.XBDFlutterActivity",
                "run.xbud.android.mvp.ui.sport.run.RunStartActivity",
                "com.fiveplay.sihp_homepage.module.splash.SplashActivity",
                "com.fiveplay.hotspot.module.propTeaching.PropTeachingVideoActivity"}) {
            assertFalse(name, AggressiveAdPolicy.adActivity(name));
            assertFalse(name, AggressiveAdPolicy.sdkClass(name));
        }
    }

    @Test public void namespaceBoundariesDoNotHideRegularAndroidAndMaterialViews() {
        assertTrue(AggressiveAdPolicy.sdkClass("com.byazt.cc.TsView"));
        assertTrue(AggressiveAdPolicy.sdkClass("com.kwad.components.ad.splashscreen.widget.SkipView"));
        for (String name : new String[]{"android.widget.FrameLayout", "android.webkit.WebView",
                "com.google.android.material.tabs.TabLayout", "com.byaztlookalike.Widget", null}) {
            assertFalse(AggressiveAdPolicy.sdkClass(name));
        }
    }

    @Test public void forcedSplashBypassIsOnlyForXbud() {
        assertTrue(AggressiveAdPolicy.splash("run.xbud.android", XbudTargets.SPLASH));
        assertFalse(AggressiveAdPolicy.splash("com.fiveplay", XbudTargets.SPLASH));
        assertFalse(AggressiveAdPolicy.splash("run.xbud.android", XbudTargets.MAIN));
    }
}
