package br.com.cpb.esperanca.activity;

import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.view.*;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.widget.SearchView;

import net.nightwhistler.pageturner.activity.ReadingFragment;
import br.com.cpb.esperanca.app.Configs;
import br.com.cpb.esperanca.fragment.FontsDialogFragment;
import br.com.cpb.esperanca.fragment.HighlightItemsFragment;
import br.com.cpb.esperanca.fragment.SearchResultsFragment;
import br.com.cpb.esperanca.model.SearchResult;
import br.com.cpb.esperanca.R;
import roboguice.inject.InjectView;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: castelanjr
 * Date: 2/13/13
 * Time: 10:10 AM
 * Copyright (C) 2013 Angelo Castelan Jr. All rights reserved.
 */
public class ReadingActivity extends BaseActivity {

    private enum CurrentFragmentInContainer {
        FONTS_FRAGMENT, SEARCH_FRAGMENT, HIGHLIGHT_ITEMS_FRAGMENT;
    }

    private CurrentFragmentInContainer mCurrentFragmentInContainer;
    private boolean mIsShowingContainer = false, mIsTablet;
    private ReadingFragment mReadingFragment;
    private FontsDialogFragment mFontsDialogFragment;
    private SearchResultsFragment mSearchResultsFragment;
    private HighlightItemsFragment mHighlightItemsFragment;
    private com.actionbarsherlock.widget.SearchView mSearchView;

    @InjectView(R.id.container)
    private LinearLayout mLayoutContainer;

    private Animation mAnimationIn, mAnimationOut;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(com.actionbarsherlock.view.Window.FEATURE_ACTION_BAR_OVERLAY);
        setContentView(R.layout.activity_reading);

        mReadingFragment = (ReadingFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_reading);

        mAnimationOut = AnimationUtils.loadAnimation(this, R.anim.out_from_top);
        mAnimationIn = AnimationUtils.loadAnimation(this, R.anim.in_to_top);

        ActionBar ab = getSupportActionBar();
        ab.setDisplayHomeAsUpEnabled(true);
        ab.hide();

        mFontsDialogFragment = FontsDialogFragment.newInstance(mReadingFragment, getIntent().getIntExtra("book_id", 0));
        mSearchResultsFragment = SearchResultsFragment.newInstance();
        mSearchResultsFragment.setListener(mReadingFragment);
        mHighlightItemsFragment = HighlightItemsFragment.newInstance();
        mHighlightItemsFragment.setListener(mReadingFragment);

        if (mIsTablet = getResources().getBoolean(R.bool.is_tablet)) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.add(R.id.container, mFontsDialogFragment);
            ft.commit();

            mCurrentFragmentInContainer = CurrentFragmentInContainer.FONTS_FRAGMENT;
        } else {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.add(R.id.container, mSearchResultsFragment);
            ft.commit();

            mCurrentFragmentInContainer = CurrentFragmentInContainer.SEARCH_FRAGMENT;
        }

        mLayoutContainer.setVisibility(View.GONE);
    }

    public void dismissContainer() {
        if (mIsShowingContainer) {
            if (mCurrentFragmentInContainer == CurrentFragmentInContainer.SEARCH_FRAGMENT) {
                mSearchView.onActionViewCollapsed();
            }

            toggleContainerVisibility();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        mReadingFragment.onWindowFocusChanged(hasFocus);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mReadingFragment.onTouchEvent(event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getSupportMenuInflater().inflate(R.menu.activity_reading, menu);

        mSearchView = (SearchView) menu.findItem(R.id.menu_search).getActionView();
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (query != null && query.trim().length() > 0) {
                    mSearchResultsFragment.clearView();
                    mSearchResultsFragment.startProgress();

                    mReadingFragment.performSearch(query);
                }
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        mSearchView.setOnSearchClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCurrentFragmentInContainer != CurrentFragmentInContainer.SEARCH_FRAGMENT) {
                    FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                    ft.replace(R.id.container, mSearchResultsFragment);
                    ft.commit();
                    mCurrentFragmentInContainer = CurrentFragmentInContainer.SEARCH_FRAGMENT;
                }

                if (!mIsShowingContainer) {
                    toggleContainerVisibility();
                }
            }
        });

        mSearchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                if (mCurrentFragmentInContainer == CurrentFragmentInContainer.SEARCH_FRAGMENT) {
                    if (mIsShowingContainer) {
                        toggleContainerVisibility();
                    }
                }
                return false;
            }
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.menu_index:
                mReadingFragment.showTocDialog();
                break;
            case R.id.menu_fonts:
                if (mIsTablet) {
                    if (mCurrentFragmentInContainer == CurrentFragmentInContainer.FONTS_FRAGMENT && mIsShowingContainer) {
                        toggleContainerVisibility();
                    } else {
                        if (mCurrentFragmentInContainer != CurrentFragmentInContainer.FONTS_FRAGMENT) {
                            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                            ft.replace(R.id.container, mFontsDialogFragment);
                            ft.commit();

                            mCurrentFragmentInContainer = CurrentFragmentInContainer.FONTS_FRAGMENT;
                        }

                        if (!mIsShowingContainer) {
                            toggleContainerVisibility();
                        }
                    }
                } else {
                    mFontsDialogFragment.show(getSupportFragmentManager(), "fontsDialogFragment");
                }
                break;
            case R.id.menu_highlighted_items:
                if (mCurrentFragmentInContainer == CurrentFragmentInContainer.HIGHLIGHT_ITEMS_FRAGMENT && mIsShowingContainer) {
                    toggleContainerVisibility();
                } else {
                    if (mCurrentFragmentInContainer != CurrentFragmentInContainer.HIGHLIGHT_ITEMS_FRAGMENT) {
                        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                        ft.replace(R.id.container, mHighlightItemsFragment);
                        ft.commit();

                        mCurrentFragmentInContainer = CurrentFragmentInContainer.HIGHLIGHT_ITEMS_FRAGMENT;
                    }
                    mLayoutContainer.post(new Runnable() {
                        @Override
                        public void run() {
                            mHighlightItemsFragment.setContent(Configs.getInstance(ReadingActivity.this, getIntent().getIntExtra("book_id", 0)).highlights);
                        }
                    });

                    if (!mIsShowingContainer) {
                        toggleContainerVisibility();
                    }
                }
                break;
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (!mIsTablet && mIsShowingContainer) {
            toggleContainerVisibility();
            return;
        }
        super.onBackPressed();
    }

    public void showSearchResults(List<SearchResult> results) {
        mSearchResultsFragment.setContent(results);
    }

    private void toggleContainerVisibility() {
        if (mIsShowingContainer) {
            mLayoutContainer.startAnimation(mAnimationOut);
            mLayoutContainer.setVisibility(View.GONE);
            mIsShowingContainer = false;
        } else {
            mLayoutContainer.setVisibility(View.VISIBLE);
            mLayoutContainer.startAnimation(mAnimationIn);
            mIsShowingContainer = true;
        }
    }
}
