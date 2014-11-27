package br.com.cpb.esperanca.activity;

import android.os.Bundle;

import com.actionbarsherlock.view.MenuItem;

import br.com.cpb.esperanca.fragment.AccountFragment;
import br.com.cpb.esperanca.R;

/**
 * Created with IntelliJ IDEA.
 * User: castelanjr
 * Date: 3/18/13
 * Time: 5:10 PM
 * Copyright (C) 2013 Nyvra Software. All rights reserved.
 */
public class AccountActivity extends BaseActivity {

    private AccountFragment mFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);

        //getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mFragment = (AccountFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_account);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
