package com.planeteers.aivision;

import android.app.Application;

import com.parse.Parse;
import com.planeteers.aivision.helpers.Constants;

import timber.log.Timber;

/**
 * Created by Jose on 11/9/15.
 */
public class AiVisionApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        if(BuildConfig.DEBUG){
            Timber.plant(new Timber.DebugTree());
        }

        Parse.initialize(this, Constants.PARSE.PARSE_APP_ID, Constants.PARSE.PARSE_CLIENT_KEY);

    }
}
