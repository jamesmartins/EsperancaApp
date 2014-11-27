package br.com.cpb.esperanca.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import br.com.cpb.esperanca.app.Reader;
import br.com.cpb.esperanca.fragment.BookDetailsFragment;
import br.com.cpb.esperanca.model.Book;

import com.actionbarsherlock.view.MenuItem;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;

import br.com.cpb.esperanca.R;

/**
 * Created with IntelliJ IDEA.
 * User: castelanjr
 * Date: 3/15/13
 * Time: 10:23 AM
 * Copyright (C) 2013 Nyvra Software. All rights reserved.
 */
public class BookDetailsActivity extends BaseActivity {
    private Reader mReader;
    private static ImageLoader sImageLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_details);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        sImageLoader = ImageLoader.getInstance();
        sImageLoader.init(ImageLoaderConfiguration.createDefault(this));

        mReader = Reader.getInstance(this);
        Book book = (Book) mReader.getTransactionalObject();

        getSupportActionBar().setTitle(book.title);

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        BookDetailsFragment fragment = BookDetailsFragment.newInstance(book, sImageLoader);
        ft.add(R.id.container, fragment);
        ft.commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Source: http://stackoverflow.com/questions/14130044/android-billing-exception
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);

        // Pass on the activity result to the helper for handling
        if (!mReader.getIABHelper().handleActivityResult(requestCode, resultCode, data)) {
            // not handled, so handle it ourselves (here's where you'd
            // perform any handling of activity results not related to in-app
            // billing...
            super.onActivityResult(requestCode, resultCode, data);
        }
        else {
            //Log.d(TAG, "onActivityResult handled by IABUtil.");
        }
    }
}
