package br.com.cpb.esperanca.app;

import android.app.Application;
import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;

/**
 * Created with IntelliJ IDEA.
 * User: castelanjr
 * Date: 5/3/13
 * Time: 9:53 AM
 * Copyright (C) 2013 Nyvra Software. All rights reserved.
 */
@ReportsCrashes(formUri = "http://www.bugsense.com/api/acra?api_key=d626bd1f", formKey = "")
public class ReaderApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        ACRA.init(this);
    }
}
