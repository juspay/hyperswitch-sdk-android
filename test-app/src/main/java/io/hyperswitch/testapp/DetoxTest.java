package io.hyperswitch.testapp;
import com.wix.detox.Detox;
import com.wix.detox.config.DetoxConfig;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;

import io.hyperswitch.BuildConfig;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class DetoxTest {
    @Rule // (2)
    public ActivityTestRule<MainActivity> mActivityRule = new ActivityTestRule<>(MainActivity.class, false, false);

    @Test
    public void runDetoxTests() {
        DetoxConfig detoxConfig = new DetoxConfig();
        detoxConfig.idlePolicyConfig.masterTimeoutSec = 180;
        detoxConfig.idlePolicyConfig.idleResourceTimeoutSec = 120;
        detoxConfig.rnContextLoadTimeoutSec = (BuildConfig.DEBUG ? 360 : 60);

        Detox.runTests(mActivityRule, detoxConfig);
    }
}
