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
        // Car pull-down over Android Auto chosen (#21): the screen under its own name, so the car's quick settings
        // pull-down stays allowed over it; this launch (a launcher, rui, MG4 Dash) is passed on to it.
        if (!AapProjectionActivity.Companion.usesAllGoName(this) && (getIntent().getBooleanExtra(EXTRA_FROM_DIAUTO, false) || RelayState.isActive())) {
            Intent own = new Intent(getIntent()).setClass(this, AapProjectionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(own);
            finish();
            return true;
        }
        // A launcher opening Android Auto with no session gets DiAuto's home screen, as before.
        if (getIntent().getBooleanExtra(EXTRA_FROM_DIAUTO, false) || RelayState.isActive()) return false;
        startActivity(new Intent().setClassName(this, "com.andrerinas.openheadunit.main.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
        return true;
    }
}
