package br.com.cpb.esperanca.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import br.com.cpb.esperanca.R;

public class SplashScreen extends Activity {

    // Splash screen timer
    private static int SPLASH_TIME_OUT = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.splash_screen);
        
        new Handler().postDelayed(new Runnable() {

            /*
             * Showing splash screen with a timer
             */
            @Override
            public void run() {
                startActivity(new Intent(SplashScreen.this, LibraryActivity.class));
                overridePendingTransition(R.anim.grow_from_middle, R.anim.shrink_to_middle);

                finish();
            }
        }, SPLASH_TIME_OUT);
    }

}
