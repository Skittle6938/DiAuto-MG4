package com.allgo.app.androidauto;

import android.content.Intent;

import com.andrerinas.openheadunit.aap.AapProjectionActivity;

/**
 * DiAuto's Android Auto screen, under AllGo's exact class name. MG4 Dash and the stock launchers
 * open this component, and the SWI68 SystemUI hides its incoming-call banner only while an
 * activity of this class name is in front (CommonUtils.isAndroidAutoActivity, issue #4).
 */
public class ProjectionActivity extends AapProjectionActivity {
    /** Set by DiAuto's own launches: they come before the session is announced to rui. */
    public static final String EXTRA_FROM_DIAUTO = "com.allgo.app.androidauto.FROM_DIAUTO";

    @Override
    protected boolean redirectsHome() {
        // A launcher opening Android Auto with no session gets DiAuto's home screen, as before.
        if (getIntent().getBooleanExtra(EXTRA_FROM_DIAUTO, false) || RelayState.isActive()) return false;
        startActivity(new Intent().setClassName(this, "com.andrerinas.openheadunit.main.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
        return true;
    }
}
