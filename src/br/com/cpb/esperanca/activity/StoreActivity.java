package br.com.cpb.esperanca.activity;

import java.util.List;

import android.content.Intent;
import android.view.MotionEvent;
import android.widget.*;
import br.com.cpb.esperanca.app.Reader;
import br.com.cpb.esperanca.app.ReaderAPI;
import br.com.cpb.esperanca.app.Reader.OnLoadCategoriesListener;
import br.com.cpb.esperanca.fragment.AccountFragment;
import br.com.cpb.esperanca.fragment.BookDetailsFragment;
import br.com.cpb.esperanca.model.Book;
import br.com.cpb.esperanca.model.Category;
import br.com.cpb.esperanca.util.Utils;

import com.actionbarsherlock.widget.SearchView;

import br.com.cpb.esperanca.R;
import roboguice.inject.InjectView;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.OnItemClickListener;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.assist.SimpleImageLoadingListener;


/**
 * 
 * @author Last Update: James Martins
 *
 */

public class StoreActivity extends BaseActivity implements Reader.OnLoadBooksListener, ActionBar.OnNavigationListener {
    private static final String DEFAULT_CATEGORY = "Todas as Categorias";
    
    public static final String URL_BANNER_TABLET_PORTRAIT = "http://ws.cpb.com.br/apps/cpbreader/banners/store-banner-ipad-portrait.png";
    public static final String URL_BANNER_TABLET_LANDSCAPE = "http://ws.cpb.com.br/apps/cpbreader/banners/store-banner-ipad-landscape.png";
    public static final String URL_BANNER_PHONE = "http://ws.cpb.com.br/apps/cpbreader/banners/store-banner-iphone-portrait.png";
    
    private Reader mReader;
    private List<Book> mBooks;
    private ActionBar mActionBar;
    private static ImageLoader sImageLoader;
    private DisplayImageOptions mImageOptions;
    private com.actionbarsherlock.widget.SearchView mSearchView;
    private int mLastSelectedIndex = -1;
    
    @InjectView(R.id.image_banner)
    private ImageView mImageBanner;
    
    @InjectView(R.id.grid_store)
    private GridView mGridStore;
    
    @InjectView(R.id.progress)
    private ProgressBar mProgress;

    private AccountFragment mAccountFragment;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_store);
        
        getSherlock().setProgressBarIndeterminateVisibility(true);
        
        mActionBar = getSupportActionBar();
        mActionBar.setDisplayHomeAsUpEnabled(true);
        mActionBar.setDisplayShowTitleEnabled(false);
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        
        String[] fakeArray = new String[] {DEFAULT_CATEGORY};
        SpinnerAdapter adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, fakeArray);
        mActionBar.setListNavigationCallbacks(adapter, this);
        
        mProgress.setVisibility(View.VISIBLE);
        mGridStore.setVisibility(View.GONE);
        
        mReader = Reader.getInstance(this);
        mReader.setBooksListener(this);
        //Setting CategoryListener
        mReader.setCategoryListener(new OnLoadCategoriesListener() {
            
            @Override
            public void onSuccess() {
                getSherlock().setProgressBarIndeterminateVisibility(false);

                if (!mReader.getCategories().get(0).title.equals(DEFAULT_CATEGORY)) {
                    Category category = new Category();
                    category.id = -95;
                    category.title = DEFAULT_CATEGORY;
                    mReader.getCategories().add(0, category);
                }
                SpinnerAdapter adapter = new ArrayAdapter<Category> (StoreActivity.this, R.layout.row_dropdown_menu,
                        mReader.getCategories().toArray(new Category[mReader.getCategories().size()]));
                mActionBar.setListNavigationCallbacks(adapter, StoreActivity.this);
                if (mReader.getCurrentCategory() != -1) {
                    mActionBar.setSelectedNavigationItem(mReader.getCurrentCategory());
                }
            }
            
            @Override
            public void onParseFailure() {
                getSherlock().setProgressBarIndeterminateVisibility(false);
                Utils.showToast(StoreActivity.this, "Ocorreu um erro ao buscar as categorias");
            }
            
            @Override
            public void onFailure() {
                getSherlock().setProgressBarIndeterminateVisibility(false);
                Utils.showToast(StoreActivity.this, "Ocorreu um erro ao buscar as categorias");
            }
        });
        
        // Setting ImageLoader 
        sImageLoader = ImageLoader.getInstance();
        sImageLoader.init(ImageLoaderConfiguration.createDefault(this));
        mImageOptions = new DisplayImageOptions.Builder()
            .cacheInMemory()
            .cacheOnDisc()
            .showStubImage(R.drawable.black)
            .imageScaleType(ImageScaleType.EXACTLY)
            .build();
        
        final DisplayImageOptions bannerImageOptions = new DisplayImageOptions.Builder()
            .cacheInMemory()
            .cacheOnDisc()
            .showStubImage(R.drawable.black_banner)
            .imageScaleType(ImageScaleType.EXACTLY)
            .build();
        
        //Loading Categories
        mReader.loadCategories();
        // Loading Books 
        mReader.loadBooksByCategory(null);

        if (getResources().getBoolean(R.bool.is_tablet)) {
            mAccountFragment = (AccountFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_account);
            mAccountFragment.hide();

            mGridStore.setOnTouchListener(new View.OnTouchListener() {

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (mAccountFragment.isBeingShowed()) {
                        mAccountFragment.toggleVisibility();
                        return true;
                    }
                    return false;
                }

            });
        }
        
        new AsyncTask<Void, Void, String>() {

            @Override
            protected String doInBackground(Void... params) {
                if (!getResources().getBoolean(R.bool.is_tablet)) {
                    return URL_BANNER_PHONE; 
                } else if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    return URL_BANNER_TABLET_LANDSCAPE; 
                } else {
                    return URL_BANNER_TABLET_PORTRAIT; 
                }
            }
            
            protected void onPostExecute(String result) {
                sImageLoader.displayImage(result, mImageBanner, bannerImageOptions);
            };
            
        }.execute();
    }

    @Override
    public void onSuccess(List<Book> books) {
        mProgress.setVisibility(View.GONE);
        mGridStore.setVisibility(View.VISIBLE);
        mBooks = books;
        mReader.mLoadedBooks = books;
        mGridStore.setAdapter(new StoreGridAdapter());
        mGridStore.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Book selectedBook = mBooks.get(position);

                if (getResources().getBoolean(R.bool.is_tablet)) {
                    BookDetailsFragment dialogFragment = BookDetailsFragment.newInstance(selectedBook, sImageLoader);
                    dialogFragment.show(getSupportFragmentManager(), "dialogBook");
                } else {
                    mReader.setTransactionalObject(selectedBook);
                    startActivity(new Intent(StoreActivity.this, BookDetailsActivity.class));
                }
            }
        });

//        mGridStore.setOnScrollListener(new AbsListView.OnScrollListener() {
//            @Override
//            public void onScrollStateChanged(AbsListView view, int scrollState) {
//            }
//
//            @Override
//            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
//                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
//                    if (firstVisibleItem == 0 && mImageBanner.getVisibility() == View.GONE) {
//                        mImageBanner.setVisibility(View.VISIBLE);
//                    } else if (firstVisibleItem > 0 && mImageBanner.getVisibility() == View.VISIBLE) {
//                        mImageBanner.setVisibility(View.GONE);
//                    }
//                }
//            }
//        });
    }

    @Override
    public void onFailure() {
        mProgress.setVisibility(View.GONE);
        mGridStore.setVisibility(View.VISIBLE);
        Utils.showToast(StoreActivity.this, "Ocorreu um erro ao buscar os livros");
    }

    @Override
    public void onParseFailure() {
        mProgress.setVisibility(View.GONE);
        mGridStore.setVisibility(View.VISIBLE);
        Utils.showToast(StoreActivity.this, "Ocorreu um erro ao buscar os livros");
    }

    @Override
    public boolean onNavigationItemSelected(int itemPosition, long itemId) {
        if ((mLastSelectedIndex == -1 || mLastSelectedIndex == itemPosition) && mReader.getCurrentCategory() == -1) {
            mLastSelectedIndex = itemPosition;
            return true;
        }

        mLastSelectedIndex = itemPosition;
        mReader.setCurrentCategory(itemPosition);

        mProgress.setVisibility(View.VISIBLE);
        mGridStore.setVisibility(View.GONE);
        if (itemPosition == 0) {
            mReader.loadBooksByCategory(null);
        } else {
            mReader.loadBooksByCategory(mReader.getCategories().get(itemPosition));
        }
        return true;
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getSupportMenuInflater().inflate(R.menu.activity_store, menu);

        mSearchView = (SearchView) menu.findItem(R.id.menu_search).getActionView();
        mSearchView.setOnQueryTextListener(new com.actionbarsherlock.widget.SearchView.OnQueryTextListener() {

            @Override
            public boolean onQueryTextSubmit(String query) {
                mProgress.setVisibility(View.VISIBLE);
                mGridStore.setVisibility(View.GONE);

                mReader.searchBooksByText(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        mSearchView.setOnCloseListener(new SearchView.OnCloseListener() {

            @Override
            public boolean onClose() {
                mProgress.setVisibility(View.VISIBLE);
                mGridStore.setVisibility(View.GONE);

                Category category = null;
                int index = getSupportActionBar().getSelectedNavigationIndex();
                if (index != 0) {
                    category = mReader.getCategories().get(index);
                }

                mReader.loadBooksByCategory(category);

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
                overridePendingTransition(R.anim.grow_from_middle, R.anim.shrink_to_middle);
                break;
            case R.id.menu_account:
                if (getResources().getBoolean(R.bool.is_tablet)) {
                    mAccountFragment.toggleVisibility();
                } else {
                    startActivity(new Intent(this, AccountActivity.class));
                    overridePendingTransition(R.anim.in_to_top, R.anim.out_from_top);
                }
                break;
        }
        
        return true;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.grow_from_middle, R.anim.shrink_to_middle);
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
    }

    class StoreGridAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mBooks.size();
        }

        @Override
        public Book getItem(int position) {
            return mBooks.get(position);
        }

        @Override
        public long getItemId(int position) {
            return (long) mBooks.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final ViewHolder holder;
            if (convertView == null) {
                holder = new ViewHolder();
                convertView = LayoutInflater.from(StoreActivity.this).inflate(R.layout.cell_store, parent, false);
                holder.imageCover = (ImageView) convertView.findViewById(R.id.image_cover);
                holder.textTitle = (TextView) convertView.findViewById(R.id.text_title);
                holder.textAuthor = (TextView) convertView.findViewById(R.id.text_author);
                holder.textPrice = (TextView) convertView.findViewById(R.id.text_price);
                holder.progressCover = (ProgressBar) convertView.findViewById(R.id.progress_cover);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            
            Book book = getItem(position);
            holder.textTitle.setText(book.title);
            holder.textAuthor.setText(book.author);
            if (book.price.startsWith("0.0")) {
                holder.textPrice.setText("Grátis");
            } else {
                holder.textPrice.setText("$" + book.price);
            }

            
            sImageLoader.displayImage(ReaderAPI.getAbsoluteUrl(book.cover_url), holder.imageCover, mImageOptions, new SimpleImageLoadingListener() {

                @Override
                public void onLoadingStarted() {
                    holder.progressCover.setVisibility(View.VISIBLE);
                }

                @Override
                public void onLoadingComplete(Bitmap loadedImage) {
                    holder.progressCover.setVisibility(View.GONE);
                }
            });
            
            return convertView;
        }
    }
    
    static class ViewHolder {
        ImageView imageCover;
        TextView textTitle, textAuthor, textPrice;
        ProgressBar progressCover;
    }

}
