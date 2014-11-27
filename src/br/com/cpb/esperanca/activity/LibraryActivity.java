
package br.com.cpb.esperanca.activity;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.json.JSONException;
import org.json.JSONObject;

import roboguice.inject.InjectView;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.SparseArray;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import br.com.cpb.esperanca.app.Reader;
import br.com.cpb.esperanca.app.ReaderAPI;
import br.com.cpb.esperanca.db.OwnedItemsDatabase;
import br.com.cpb.esperanca.fragment.AccountFragment;
import br.com.cpb.esperanca.model.Book;
import br.com.cpb.esperanca.service.DownloadBookService;
import br.com.cpb.esperanca.util.Utils;
import br.com.cpb.esperanca.R;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.widget.SearchView;
import com.flurry.android.FlurryAgent;
import com.github.kevinsawicki.http.HttpRequest;
import com.github.kevinsawicki.http.HttpRequest.HttpRequestException;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;

public class LibraryActivity extends BaseActivity implements Reader.OnLoadOwnedBooksListener, AdapterView.OnItemClickListener {
    private List<Book> mBooks;
    private ImageLoader mImageLoader;
    private DisplayImageOptions mImageOptions;
    private Reader mReader;
    private List<Integer> mCheckedItems;
    private SearchView mSearchView;
    private List<Book> mSearchBooks;
    ActionBar mActionBar;

    private BroadcastReceiver mDownloadBookReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            final int bookId = intent.getIntExtra(DownloadBookService.EXTRA_BOOK_ID, -1);
            ProgressBar progressBar = sHolders.get(bookId);

            // If progressBar is in the first position in GridView, there is a chance that the sHolder pattern will not work, due to Android way to handle Adapters.
            if (mBooks == null || mBooks.size() == 1 || (mBooks.get(0).id == bookId)) {
                View v = mGrid.getChildAt(0);
                if (v != null) {
                    progressBar = (ProgressBar) v.findViewById(R.id.progress_book);
                }
            }

            if (action.equals(DownloadBookService.ACTION_UPDATE)) {

                int totalSize = intent.getIntExtra(DownloadBookService.EXTRA_TOTAL_SIZE, 0);
                int size = intent.getIntExtra(DownloadBookService.EXTRA_SIZE, 0);

                if (progressBar != null) {
                    if (progressBar.getMax() != totalSize) progressBar.setMax(totalSize);
                    progressBar.setProgress(size);
                    progressBar.invalidate();
                }

            } else if (action.equals(DownloadBookService.ACTION_SUCCESS)) {

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        if (mReader.getBooksBeingDownloaded() == null) return;

                        for (Book book : mReader.getBooksBeingDownloaded()) {
                            if (book.id == bookId) {
                                OwnedItemsDatabase ownedItemsDatabase = new OwnedItemsDatabase(LibraryActivity.this);
                                ownedItemsDatabase.addBookToOwnedItems(book);

                                mReader.getBooksBeingDownloaded().remove(book);
                                break;
                            }
                        }
                    }
                }).start();

                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                    progressBar.invalidate();
                }
                sHolders.remove(bookId);

                checkIfShouldStopService();

            } else if (action.equals(DownloadBookService.ACTION_FAIL)) {

                for (Book book : mReader.getBooksBeingDownloaded()) {
                    if (book.id == bookId) {
                        mReader.getBooksBeingDownloaded().remove(book);
                        if (mBooks != null) mBooks.remove(book);
                        break;
                    }
                }

                Utils.showToast(LibraryActivity.this, "Download do livro falhou");
                setGrid();

                if (progressBar != null) {
                    sHolders.remove(bookId);
                }

                checkIfShouldStopService();

            }

        }
    };
    
    @InjectView(R.id.grid_library)
    private GridView mGrid;

    @InjectView(R.id.text_empty)
    private TextView mTextEmptyLibrary;

    @InjectView(R.id.progress_library)
    private ProgressBar mProgress;
    
    private TextView mTextView;

    private AccountFragment mAccountFragment;

    private static SparseArray<ProgressBar> sHolders;
    private static boolean sUpdateGrid = true;

    private boolean mExternalStorageAvailable = false;
    private boolean mExternalStorageWriteable = false;
    
    public static final String TOTAL_DOWNLOADS_URL = "http://ws.cpb.com.br/apps/cpbreader/downloads/totalDownloads.json";
	public static final String JSON_ENCODING = "ISO-8859-1";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    	this.getWindow().requestFeature(Window.FEATURE_PROGRESS);
//		requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
//		requestWindowFeature(Window.FEATURE_ACTION_MODE_OVERLAY);
        setContentView(R.layout.activity_library);
        
//      ActionBar actionBar = getSupportActionBar();
//      actionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#76c867")));
//      actionBar.setStackedBackgroundDrawable(new ColorDrawable(Color.parseColor("#00000000")));
        
        mTextView = (TextView) findViewById(R.id.textcount);
        
        ImageButton imageButton = (ImageButton) findViewById(R.id.image_share); 
        imageButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				Intent sendIntent = new Intent();
				sendIntent.setAction(Intent.ACTION_SEND);
				sendIntent.putExtra(Intent.EXTRA_TEXT, "Viva com Esperança: www.esperança.com.br");
				sendIntent.setType("text/plain");
				startActivity(sendIntent);
			}
		});

        mTextEmptyLibrary.setVisibility(View.GONE);
        mProgress.setVisibility(View.VISIBLE);
        mGrid.setVisibility(View.GONE);

        mImageLoader = ImageLoader.getInstance();
        mImageLoader.init(ImageLoaderConfiguration.createDefault(this));
        mImageOptions = new DisplayImageOptions.Builder()
                .cacheInMemory()
                .cacheOnDisc()
                .showStubImage(R.drawable.black)
                .imageScaleType(ImageScaleType.EXACTLY)
                .build();

        mReader = Reader.getInstance(this);

        mReader.setOwnedBooksListener(this);

//        if (mReader.isFirstTimeOpeningApp()) {
//            startActivity(new Intent(this, StoreActivity.class));
//            overridePendingTransition(R.anim.grow_from_middle, R.anim.shrink_to_middle);
//        }

        if (sHolders == null) sHolders = new SparseArray<ProgressBar>();

        if (getResources().getBoolean(R.bool.is_tablet)) {
            mAccountFragment = (AccountFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_account);
            mAccountFragment.hide();

            mGrid.setOnTouchListener(new View.OnTouchListener() {

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

        mCheckedItems = new ArrayList<Integer>();

        //Verify external storage state
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            mExternalStorageAvailable = mExternalStorageWriteable = true;
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            mExternalStorageAvailable = true;
            mExternalStorageWriteable = false;
        } else {
            mExternalStorageAvailable = mExternalStorageWriteable = false;
        }

        if (!mExternalStorageWriteable) {
            Utils.showToast(this, "Não foi possível acessar o armazenamento externo");
        }
        
        //AsyncTask to get total downloads of the Esperanca product 
        new AsyncTask<Void, Void, Boolean>() {
        	
        	private String mValue;

			@Override
			protected Boolean doInBackground(Void... params) {
				// TODO Auto-generated method stub
				try {
					String response = HttpRequest.get(TOTAL_DOWNLOADS_URL).connectTimeout(6000).body(JSON_ENCODING);
					JSONObject object = new JSONObject(response);
					mValue = object.getString("downloads");
					DecimalFormat formatter = new DecimalFormat("###,###,##0");
					mValue = formatter.format(Long.parseLong(mValue));
					
					return true;
				        		
				} catch (HttpRequestException e) {
					e.printStackTrace();
					return false;
				} catch (JSONException e) {
					e.printStackTrace();
					return false;
				}
			}
        	
        	@Override
        	protected void onPostExecute(Boolean result) {
        		// TODO Auto-generated method stub
        		if (result){
				   //Toast.makeText(getApplicationContext(), mValue, Toast.LENGTH_SHORT).show();
        			mTextView.setText(mValue.toString());	
        		}
        	}
		}.execute();
		
		//
		Typeface tf_lato_light = Typeface.createFromAsset(getAssets(), "Lato/Lato-LigIta.ttf");
		Typeface tf_lato_reg = Typeface.createFromAsset(getAssets(), "Lato/Lato-Reg.ttf");
		
		TextView textDownload = (TextView) findViewById(R.id.textdownload);
		TextView textCount = (TextView) findViewById(R.id.textcount);
		TextView textShare = (TextView) findViewById(R.id.text_share);
		
		textDownload.setTypeface(tf_lato_light);
		textCount.setTypeface(tf_lato_reg);
		textShare.setTypeface(tf_lato_light);
    }

    @Override
    protected void onResume() {
        super.onResume();

        
        mReader.setInAppBilling();

        IntentFilter f= new IntentFilter();
        f.addAction(DownloadBookService.ACTION_SUCCESS);
        f.addAction(DownloadBookService.ACTION_FAIL);
        f.addAction(DownloadBookService.ACTION_UPDATE);

        registerReceiver(mDownloadBookReceiver, f);

        if (sUpdateGrid) {
            mReader.loadOwnedEnabledBooks();
        } else {
            sUpdateGrid = true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        unregisterReceiver(mDownloadBookReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mReader.disposeInAppBilling();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getSupportMenuInflater().inflate(R.menu.activity_library, menu);
        menu.removeItem(R.id.menu_search);
        menu.removeItem(R.id.menu_account);
        menu.removeItem(R.id.menu_shop);
//        mSearchView = (SearchView) menu.findItem(R.id.menu_search).getActionView();
//        mSearchView.setOnQueryTextListener(new com.actionbarsherlock.widget.SearchView.OnQueryTextListener() {
//
//            @Override
//            public boolean onQueryTextSubmit(String query) {
//                return false;
//            }
//
//            @Override
//            public boolean onQueryTextChange(String newText) {
//                if (mGrid.getAdapter() != null) {
//                    ((LibraryGridAdapter) mGrid.getAdapter()).getFilter().filter(newText);
//                }
//                return true;
//            }
//        });

        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_account:
                if (getResources().getBoolean(R.bool.is_tablet)) {
                    mAccountFragment.toggleVisibility();
                } else {
                    //sUpdateGrid = false;
                    startActivity(new Intent(this, AccountActivity.class));
                    overridePendingTransition(R.anim.in_to_top, R.anim.out_from_top);
                }
                break;
            case R.id.menu_shop:
                startActivity(new Intent(this, StoreActivity.class));
                overridePendingTransition(R.anim.grow_from_middle, R.anim.shrink_to_middle);
                break;
        }
        return true;
    }

    //Integration for Flurry
    @Override
    protected void onStart() {
    	// TODO Auto-generated method stub
    	super.onStart();
    	FlurryAgent.onStartSession(this, "75WTGH9TXVJC28YZ2PVC");
    }
    
    @Override
    protected void onStop() {
    	// TODO Auto-generated method stub
    	super.onStop();
    	FlurryAgent.onEndSession(this);
    }
    

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.context_library, menu);
        menu.removeItem(R.id.menu_select_all);
    }

    // Delete Book selected ------------
    @Override
    public boolean onContextItemSelected(final android.view.MenuItem item) {
        if (item.getItemId() == R.id.menu_delete) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
            final Book book = mBooks.get(info.position);

            AlertDialog.Builder builder = new AlertDialog.Builder(LibraryActivity.this);
            builder.setTitle("Confirmar exclusão");
            builder.setMessage("Deseja realmente excluir o livro \"" + book.title + "\"?");
            builder.setPositiveButton("Excluir", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    new AsyncTask<Void, Void, Void>() {

                        @Override
                        protected Void doInBackground(Void... params) {
                            File file = new File(book.getPath(LibraryActivity.this));
                            boolean result = file.delete();

                            OwnedItemsDatabase database = new OwnedItemsDatabase(LibraryActivity.this);
                            database.removeBookFromOwnedItems(book);

                            return null;
                        }

                        @Override
                        protected void onPostExecute(Void aVoid) {
                            mReader.loadOwnedEnabledBooks();
                        }
                    }.execute();
                }
            });
            builder.setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            builder.show();
            return true;
        } else {
            return super.onContextItemSelected(item);
        }
    }

    private void setGrid() {
        if (mReader.getBooksBeingDownloaded() != null && mReader.getBooksBeingDownloaded().size() > 0) {
            for (Book book : mReader.getBooksBeingDownloaded()) {
                if (mBooks != null && !mBooks.contains(book)) {
                    mBooks.add(book);
                    
                }
            }
        }

        mGrid.setAdapter(new LibraryGridAdapter(false));
        mProgress.setVisibility(View.GONE);
        mGrid.setVisibility(View.VISIBLE);
        mGrid.setOnItemClickListener(LibraryActivity.this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            mGrid.setChoiceMode(GridView.CHOICE_MODE_MULTIPLE_MODAL);
            mGrid.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {

                @Override
                public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
                    if (mCheckedItems.contains(position)) {
                        if (!checked) {
                            mCheckedItems.remove(Integer.valueOf(position));
                        }
                    } else {
                        if (checked) {
                            mCheckedItems.add(Integer.valueOf(position));
                        }
                    }
                }

                @Override
                public boolean onCreateActionMode(ActionMode mode, android.view.Menu menu) {
                    android.view.MenuInflater inflater = mode.getMenuInflater();
                    inflater.inflate(R.menu.context_library, menu);
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, android.view.Menu menu) {
                    mCheckedItems.clear();
                    return false;
                }

                @Override
                public boolean onActionItemClicked(final ActionMode mode, android.view.MenuItem item) {
                    switch (item.getItemId()) {
                        case R.id.menu_select_all:
                            for (int i = 0; i < mGrid.getCount(); i++) {
                                mGrid.setItemChecked(i, true);
                            }
                            return true;
                        case R.id.menu_delete:
                            AlertDialog.Builder builder = new AlertDialog.Builder(LibraryActivity.this);
                            builder.setTitle("Confirmar exclusão");
                            builder.setMessage("Deseja realmente excluir os livros selecionados?");
                            builder.setPositiveButton("Excluir", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    deleteSelectedItems();
                                    mode.finish();
                                }
                            });
                            builder.setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                            builder.show();
                            return true;
                        default:
                            return false;
                    }
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {

                }
            });
        } else {
            registerForContextMenu(mGrid);
        }

        if (mBooks != null && mBooks.size() > 0) {
            mTextEmptyLibrary.setVisibility(View.GONE);

            if (mReader.getBooksBeingDownloaded() != null && mReader.getBooksBeingDownloaded().size() > 0) {
                mGrid.setSelection(mBooks.size() - 1);
            }

        } else {
            mTextEmptyLibrary.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onSuccess(List<Book> books) {
        //mBooks = Reader.getInstance(LibraryActivity.this).getOwnedBooks();
        mBooks = books;
        //Routine for Verification FirstTime 
        if (mReader.isFirstTimeOpeningApp())
        	mReader.downloadBook(books.get(0),null);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setGrid();
            }
        });
    }

    @Override
    public void onFailure() {

    }

    @Override
    public void onParseFailure() {

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

    private void deleteSelectedItems() {
        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {

                for (Integer pos : mCheckedItems) {
                    Book book = mBooks.get(pos.intValue());
                    OwnedItemsDatabase database = new OwnedItemsDatabase(LibraryActivity.this);
                    database.removeBookFromOwnedItems(book);

                    File f = new File(book.getPath(LibraryActivity.this));
                    f.delete();
                }

                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                mReader.loadOwnedEnabledBooks();
            }
        }.execute();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Book book;
        if (mSearchBooks == null) {
            book = mBooks.get(position);
        } else {
            book = mSearchBooks.get(position);
        }

        if (mReader.getBooksBeingDownloaded() == null || (mReader.getBooksBeingDownloaded() != null && !mReader.getBooksBeingDownloaded().contains(book))) {
            Intent intent = new Intent(this, ReadingActivity.class);
            intent.putExtra("file_name", book.getPath(this));
            intent.putExtra("book_id", book.id);
            startActivity(intent);
        }
    }

    private void checkIfShouldStopService() {
        if (mReader.getBooksBeingDownloaded() == null || mReader.getBooksBeingDownloaded().size() == 0) {
            stopService(new Intent(this, DownloadBookService.class));
        }
    }

    class LibraryGridAdapter extends BaseAdapter implements Filterable {
        private boolean fromSearch;
        private BooksFilter filter;

        public LibraryGridAdapter(boolean fromSearch) {
            this.fromSearch = fromSearch;
            if (mBooks.size() > 1)
        		mBooks.remove(1);
        }

        @Override
        public int getCount() {
            if (fromSearch && mSearchBooks != null) {
                return mSearchBooks.size();
            } else {
                if (mBooks != null) {
                    return mBooks.size();
                } else {
                    Log.d("LibraryActivity", "There's something really strange happening here.");
                    return 0;
                }
            }
        }

        @Override
        public Book getItem(int position) {
            if (fromSearch && mSearchBooks != null) {
                return mSearchBooks.get(position);
            } else {
                return mBooks.get(position);
            }
        }

        @Override
        public long getItemId(int position) {
            return Long.valueOf(getItem(position).id);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(LibraryActivity.this).inflate(R.layout.cell_library, parent, false);
                holder = new ViewHolder();
                holder.imageCover = (ImageView) convertView.findViewById(R.id.image_cover);
                holder.progressBook = (ProgressBar) convertView.findViewById(R.id.progress_book);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            final Book book = getItem(position);
            
            mImageLoader.displayImage(ReaderAPI.getAbsoluteUrl(book.cover_url), holder.imageCover, mImageOptions);
            holder.imageCover.bringToFront();
            holder.progressBook.bringToFront();

            if (mReader.getBooksBeingDownloaded() != null && mReader.getBooksBeingDownloaded().contains(book)) {
                holder.progressBook.setVisibility(View.VISIBLE);
                sHolders.put(book.id, holder.progressBook);
            } else {
                holder.progressBook.setVisibility(View.GONE);
            }

            return convertView;
        }

        @Override
        public Filter getFilter() {
            if (filter == null) filter = new BooksFilter();
            return filter;
        }
    }

    static class ViewHolder {
        ImageView imageCover;
        ProgressBar progressBook;
    }

    private class BooksFilter extends Filter {

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            constraint = constraint.toString().toLowerCase(Locale.getDefault());
            FilterResults results = new FilterResults();

            if (constraint != null && constraint.length() > 0) {
                if (mSearchBooks == null) {
                    mSearchBooks = new ArrayList<Book>();
                } else {
                    mSearchBooks.clear();
                }

                for (Book book : mBooks) {
                    if (book.title.toLowerCase(Locale.getDefault()).contains(constraint)) {
                        mSearchBooks.add(book);
                    }
                }

                results.count = mSearchBooks.size();
                results.values = mSearchBooks;

            } else {
                mSearchBooks = null;
                results.count = mBooks.size();
                results.values = mBooks;
            }

            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            mGrid.setAdapter(new LibraryGridAdapter(true));
        }
    }
    

}
