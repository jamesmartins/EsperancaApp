package br.com.cpb.esperanca.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import br.com.cpb.esperanca.activity.LibraryActivity;
import br.com.cpb.esperanca.db.OwnedItemsDatabase;
import br.com.cpb.esperanca.iab.IabHelper;
import br.com.cpb.esperanca.iab.IabResult;
import br.com.cpb.esperanca.iab.Inventory;
import br.com.cpb.esperanca.iab.Purchase;
import br.com.cpb.esperanca.model.Book;
import br.com.cpb.esperanca.model.Category;
import br.com.cpb.esperanca.model.User;
import br.com.cpb.esperanca.service.DownloadBookService;
import br.com.cpb.esperanca.R;

import com.google.gson.Gson;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.acra.ACRA;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Reader {
    private static final String TAG = Reader.class.getSimpleName();

    private static final String URL_CATEGORIES = "Categorias/lista.json";
    private static final String URL_BOOKS = "Produtos/listaProdutoCategoria.json";
    private static final String URL_BOOK_DETAILS = "Produtos/produtoPorID.json";
    private static final String URL_BOOK_SEARCH = "Produtos/pesquisaProdutoCategoria.json";
    private static final String URL_AUTHENTICATE_USER = "Clientes/login.json";
    private static final String URL_VALIDATE_PURCHASE = "Compras/compraAndroid";

    private static final String CACHE_FILENAME = "cache";
    private static final String CACHE_CURRENT_USER = "currentUserData";
    private static final String FIRST_TIME_OPENING_APP = "firstTimeOpeningApp";
    
    private Context mContext;
    private User mUser;
    private List<Category> mCategories;
    private List<Book> mOwnedBooks, mAvailableBooks, mBooksBeingDownloaded;
    public  List<Book>  mLoadedBooks;
    private Gson mGson = new Gson();
    private Object mTransactionalObject;
    private int mCurrentCategory = -1;
    private List<Integer> mIdsToRestore;
    private SharedPreferences mCache;

    //In-App Billing stuff
    private IabHelper mHelper;
    private boolean mInAppBillingEnabled;

    private static Reader sInstance;
    
    // Listeners
    private OnLoadOwnedBooksListener mOwnedBooksListener;
    private OnLoadCategoriesListener mCategoriesListener;
    private OnLoadBooksListener mBooksListener;
    private OnLoadBookDetailsListener mBookDetailsListener;
    private OnAuthenticateUserListener mAuthenticateUserListener;
    
    private Reader(Context context) {
        mContext = context;
    }
    
    public static Reader getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new Reader(context);
        } else {
            if (sInstance.mContext == null) {
                sInstance.mContext = context;
            }
        }
        
        return sInstance;
    }
    
    public void loadOwnedEnabledBooks() {
        final OwnedItemsDatabase ownedItemsDatabase = new OwnedItemsDatabase(mContext);
        final boolean hasBooks = false;

        new Thread(new Runnable() {
            @Override
            public void run() {
                mOwnedBooks = ownedItemsDatabase.getOwnedItems();
//                if (mOwnedBooksListener != null) {
//                	mOwnedBooksListener.onSuccess(mOwnedBooks);
//                }
                if (mOwnedBooks.size() == 0){ 
                	Book book = new Book();
                	book.id = 87;
                	book.title = "Viva com Esperança";
                	book.price = "0.00";
                	book.author = "Mark Finley / Peter Landless";
                	book.category_id = 10;
                	book.cover_url = "capas/VivaComEsperanca.jpg";
                	book.issue_url = "produtos/download/14888"; 
                	
                	ownedItemsDatabase.addBookToOwnedItems(book);
                	mOwnedBooks = ownedItemsDatabase.getOwnedItems();
                	
                	downloadBook(book,null);
                	if (mOwnedBooksListener != null) 
                    	mOwnedBooksListener.onSuccess(mOwnedBooks);
                }
                else{
                	if (mOwnedBooksListener != null) 
                     	mOwnedBooksListener.onSuccess(mOwnedBooks);
                }
            }
        }).start();
    }

    public void searchBooksByText(String text) {
        RequestParams params = new RequestParams();

        if (mUser != null) {
            params.put("id", String.valueOf(mUser.id));
        }

        if (text != null) {
            params.put("text", text);
        }

        ReaderAPI.post(URL_BOOK_SEARCH, params, new JsonHttpResponseHandler() {

            @Override
            public void onSuccess(JSONObject dumbJSON) {
                try {
                    JSONArray array = dumbJSON.getJSONArray("produtos");

                    List<Book> books = new ArrayList<Book>();

                    for (int i = 0; i < array.length(); i++) {
                        JSONArray nonsenseArray = array.getJSONArray(i);
                        JSONObject theOnlyUsefulJSONObjectNeeded = nonsenseArray.getJSONObject(0);

                       Book book  = mGson.fromJson(theOnlyUsefulJSONObjectNeeded.toString(), Book.class);
                       books.add(book);
                    }

                    if (mBooksListener != null) mBooksListener.onSuccess(books);
                } catch (JSONException e) {
                    e.printStackTrace();
                    if (mBooksListener != null) mBooksListener.onParseFailure();
                }
            }

            @Override
            public void onFailure(Throwable arg0, String arg1) {
                if (mBooksListener != null) mBooksListener.onFailure();
            }

        });
    }
    
    public void loadBooksByCategory(final Category category) {
        Log.d(TAG, "Loading books by category");
        RequestParams params = new RequestParams();
        
        if (mUser != null) {
            params.put("id", String.valueOf(mUser.id));
        }
        
        if (category != null) {
            params.put("category_id", String.valueOf(category.id));
        } else {
            if (mAvailableBooks != null) {
                if (mBooksListener != null) mBooksListener.onSuccess(mAvailableBooks);
            }
        }
        
        ReaderAPI.post(URL_BOOKS, params, new JsonHttpResponseHandler() {
           
            @Override
            public void onSuccess(JSONObject dumbJSON) {
                try {
                    JSONArray array = dumbJSON.getJSONArray("produtos");
                    
                    List<Book> books = new ArrayList<Book>();
                    
                    for (int i = 0; i < array.length(); i++) {
                        JSONArray nonsenseArray = array.getJSONArray(i);
                        JSONObject theOnlyUsefulJSONObjectNeeded = nonsenseArray.getJSONObject(0);
                        // Only book 
                        if (theOnlyUsefulJSONObjectNeeded.getInt("id") == 87) {
                          Book book = mGson.fromJson(theOnlyUsefulJSONObjectNeeded.toString(), Book.class);
                          books.add(book);
                        }  
                    }

                    if (category == null) {
                        mAvailableBooks = books;
                    }
                    
                    if (mBooksListener != null) mBooksListener.onSuccess(books);
                } catch (JSONException e) {
                    e.printStackTrace();
                    if (mBooksListener != null) mBooksListener.onParseFailure();
                }
            }
            
            @Override
            public void onFailure(Throwable arg0, String arg1) {
                if (mBooksListener != null) mBooksListener.onFailure();
            }
            
        });
    }
    
    public void loadCategories() {
        if (mCategories != null) {
            if (mCategoriesListener != null) mCategoriesListener.onSuccess();
            return;
        }
        ReaderAPI.get(URL_CATEGORIES, null, new JsonHttpResponseHandler() {
            
            @Override
            public void onSuccess(JSONObject json) {
                try {
                    JSONArray array = json.getJSONArray("categorias");
                    
                    mCategories = new ArrayList<Category>();

                    for (int i = 0; i < array.length(); i++) {
                    	//if (i == 4) {
                          JSONArray nonsenseArray = array.getJSONArray(i);
                          JSONObject theOnlyUsefulJSONObject = nonsenseArray.getJSONObject(0);
                        
                          Category category = mGson.fromJson(theOnlyUsefulJSONObject.toString(), Category.class);
                          mCategories.add(category);
                    	//} 
                    }
                    
                    if (mCategoriesListener != null) mCategoriesListener.onSuccess();
                    
                } catch (JSONException e) {
                    e.printStackTrace();
                    if (mCategoriesListener != null) mCategoriesListener.onParseFailure();
                }
            }
            
            @Override
            public void onFailure(Throwable arg0, String arg1) {
                if (mCategoriesListener != null) mCategoriesListener.onFailure();
                
                Log.d(TAG, "Failure: " + arg1);
            }
            
        });
    }
    
    public void loadBookById(int id) {
        RequestParams params = new RequestParams();
        params.put("issue_id", String.valueOf(id));
        ReaderAPI.post(URL_BOOK_DETAILS, params, new JsonHttpResponseHandler() {
            
            @Override
            public void onSuccess(JSONObject details) {
                try {
                    JSONArray dumbArray = details.getJSONArray("produtos");
                    JSONArray dumberArray = dumbArray.getJSONArray(0);
                    JSONObject ohGodTellMeWhyJSONObject = dumberArray.getJSONObject(0);
                    
                    Book book = mGson.fromJson(ohGodTellMeWhyJSONObject.toString(), Book.class);
                    
                    if (mBookDetailsListener != null) mBookDetailsListener.onSuccess(book);
                } catch (JSONException e) {
                    e.printStackTrace();
                    if (mBookDetailsListener != null) mBookDetailsListener.onParseFailure();
                }
            }
            
            @Override
            public void onFailure(Throwable arg0, String arg1) {
                if (mBookDetailsListener != null) mBookDetailsListener.onFailure();
                
                Log.d(TAG, "Failure: " + arg1);
            }
            
        });
    }

    public void authenticateUser(final String username, final String password) {
        RequestParams params = new RequestParams();
        params.put("username", username);
        params.put("password", password);
        ReaderAPI.post(URL_AUTHENTICATE_USER, params, new JsonHttpResponseHandler() {

            @Override
            public void onSuccess(JSONObject jsonObject) {
                try {
                    if (!jsonObject.has("cliente")) {
                        if (mAuthenticateUserListener != null) mAuthenticateUserListener.onParseFailure();
                        return;
                    }
                    JSONArray dumbArray = jsonObject.getJSONArray("cliente");
                    JSONObject dumbObject = dumbArray.getJSONObject(0);
                    JSONObject jsonObjectsThatMatters = dumbObject.getJSONObject("Cliente");

                    mUser = mGson.fromJson(jsonObjectsThatMatters.toString(), User.class);
                    mUser.username = username;
                    mUser.password = password;
                    saveCurrentUserInCache();

                    if (mAuthenticateUserListener != null) mAuthenticateUserListener.onSuccess();

                } catch (JSONException e) {
                    e.printStackTrace();
                    if (mAuthenticateUserListener != null) mAuthenticateUserListener.onParseFailure();
                }
            }

            @Override
            public void onFailure(Throwable throwable, String s) {
                Log.d(TAG, "Failure: " + s);
                if (mAuthenticateUserListener != null) mAuthenticateUserListener.onFailure();
            }
        });
    }

    public List<Book> getOwnedBooks() {
        return mOwnedBooks;
    }

    public List<Category> getCategories() {
        return mCategories;
    }

    public User getCurrentUser() {
        if (mUser == null) {
            mUser = getUserFromCache();
        }
        return mUser;
    }
    
    public void setOwnedBooksListener(OnLoadOwnedBooksListener listener) {
        mOwnedBooksListener = listener;
    }
    
    public void setCategoryListener(OnLoadCategoriesListener listener) {
        mCategoriesListener = listener;
    }
    
    public void setBooksListener(OnLoadBooksListener listener) {
        mBooksListener = listener;
    }

    public void setBookDetailsListener(OnLoadBookDetailsListener listener) {
        this.mBookDetailsListener = listener;
    }

    public void setAuthenticateUserListener(OnAuthenticateUserListener authenticateUserListener) {
        mAuthenticateUserListener = authenticateUserListener;
    }

    public Category getCategoryFromBook(Book book) {
        if (mCategories == null) return null;
        for (Category cat : mCategories) {
            if (book.category_id == cat.id) {
                return cat;
            }
        }
        return null;
    }

    public List<Book> getBooksBeingDownloaded() {
        return mBooksBeingDownloaded;
    }

    public void setBooksBeingDownloaded(List<Book> mBooksBeingDownloaded) {
        this.mBooksBeingDownloaded = mBooksBeingDownloaded;
    }

    public Object getTransactionalObject() {
        return mTransactionalObject;
    }

    public void setTransactionalObject(Object mTransactionalObject) {
        this.mTransactionalObject = mTransactionalObject;
    }

    public int getCurrentCategory() {
        return mCurrentCategory;
    }

    public void setCurrentCategory(int currentCategory) {
        mCurrentCategory = currentCategory;
    }

    private SharedPreferences getCache() {
        if (mCache == null) {
            mCache = mContext.getSharedPreferences(CACHE_FILENAME, 0);
        }
        return mCache;
    }

    private void saveCurrentUserInCache() {
        SharedPreferences.Editor editor = getCache().edit();
        editor.putString(CACHE_CURRENT_USER, mGson.toJson(mUser));
        editor.commit();
    }

    private User getUserFromCache() {
        String cachedData = getCache().getString(CACHE_CURRENT_USER, null);
        if (cachedData == null) {
            return null;
        } else {
            return mGson.fromJson(cachedData, User.class);
        }
    }

    public boolean isFirstTimeOpeningApp() {
        boolean res = getCache().getBoolean(FIRST_TIME_OPENING_APP, true);
        if (res) {
            SharedPreferences.Editor editor =  getCache().edit();
            editor.putBoolean(FIRST_TIME_OPENING_APP, false);
            editor.commit();
        }
        return res;
    }

    public void logout() {
        SharedPreferences.Editor editor = getCache().edit();
        editor.remove(CACHE_CURRENT_USER);
        editor.commit();
        mUser = null;
    }

    public IabHelper getIABHelper() {
        return mHelper;
    }

    public boolean isInAppBillingEnabled() {
        return mInAppBillingEnabled;
    }

    public void setInAppBilling() {
        if (mHelper == null) {

            // Creating key from substrings to improve security, as seen here: http://developer.android.com/training/in-app-billing/preparing-iab-app.html#Connect

            String q_1 = "6i5HHmUcE0F0EUehMKARTHXzDB18bQs2X46H2HFwu7VSn8JxFEoOnUJHQFcZ8ZtYZIwnFkTZteJ93p3bjYRN0/duWnNAY/l1UX";
            String q_2 = "w8B4zi8oB48OfMA9ab2oIAsXd2wbDVipNM4U6HC5xIQno/i26w2Y/noD+eMCHy1xuvtw+ieNVByUKvfQcGk7yZvhGjJkNgCnz";
            String q_3 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAsh+gzGj294xS/uNOkjuKFZpSKZUDiiEdMKZq7niNHx9vaD0ZakTR+c";
            String q_4 = "iNudpHNMrYXU7akL2la99OiGsbK3HEFC3sz2mASsEJX1qCfJdj9+hKUDCIucvwk5IHoCJ8zhr76X7OieXmeftab9YohmwIDAQAB";

            String half_1 = q_1 + q_4;
            String half_2 = q_3 + q_2;

            String base64EncodedPublicKey = half_2 + half_1;

            mHelper = new IabHelper(mContext, base64EncodedPublicKey);
            mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
                public void onIabSetupFinished(IabResult result) {
                    if (!result.isSuccess()) {
                        Log.d("CPB Reader - In-App Billing", "Problem setting up In-app Billing: " + result);
                        mInAppBillingEnabled = false;
                    } else {
                        mInAppBillingEnabled = true;
                    }
                }
            });
        } else {
            Log.d("CPB Reader - In-App Billing", "In-App Billing already configured.");
        }
    }

    public void disposeInAppBilling() {
        if (mHelper != null) mHelper.dispose();
        mHelper = null;
    }

    public List<Integer> getIdsToRestore() {
        return mIdsToRestore;
    }

    public void setIdsToRestore(List<Integer> idsToRestore) {
        mIdsToRestore = idsToRestore;
    }

    public interface BaseListener {
        public void onFailure();
        public void onParseFailure();
    }

    public void restoreBook(Book book, String orderId) {
        if (mBooksBeingDownloaded == null) mBooksBeingDownloaded = new ArrayList<Book>();
        mBooksBeingDownloaded.add(book);

        Intent serviceIntent = new Intent(mContext, DownloadBookService.class);
        serviceIntent.putExtra(DownloadBookService.EXTRA_BOOK_ID, book.id);
        serviceIntent.putExtra(DownloadBookService.EXTRA_BOOK_URL, book.issue_url);
        serviceIntent.putExtra(DownloadBookService.EXTRA_ORDER_ID, orderId);
        mContext.startService(serviceIntent);
    }
    
    public void downloadBook(Book book, String orderId) {
        if (getBooksBeingDownloaded() == null) 
        	mBooksBeingDownloaded = new ArrayList<Book>();
        mBooksBeingDownloaded.add(book);

        Intent serviceIntent = new Intent(mContext, DownloadBookService.class);
        serviceIntent.putExtra(DownloadBookService.EXTRA_BOOK_ID, book.id);
        serviceIntent.putExtra(DownloadBookService.EXTRA_BOOK_URL, book.issue_url);
        if (orderId != null) {
            serviceIntent.putExtra(DownloadBookService.EXTRA_ORDER_ID, orderId);
        }

        mContext.startService(serviceIntent);
    }   

    public void checkifBookIsAvailable(String sku, IabHelper.QueryInventoryFinishedListener listener) {
        List<String> additionalSkuList = new ArrayList<String>();
        additionalSkuList.add(sku);
        if (mHelper != null) {
            try {
                mHelper.queryInventoryAsync(true, additionalSkuList, listener);
            } catch (IllegalStateException e) {
                ACRA.getErrorReporter().handleException(e);
            }
        }
    }

    public void queryInventory(IabHelper.QueryInventoryFinishedListener listener) {
        if (mHelper != null) {
            try {
                mHelper.queryInventoryAsync(listener);
            } catch (IllegalStateException e) {
                ACRA.getErrorReporter().handleException(e);
            }
        }
    }

    public void purchaseBook(Activity activity, Book book, IabHelper.OnIabPurchaseFinishedListener listener) {
        if (mHelper != null) {
            try {
                mHelper.launchPurchaseFlow(activity, book.getSku(), 1001, listener);
            } catch (IllegalStateException e) {
                ACRA.getErrorReporter().handleException(e);
            }
        }
    }

    public void validatePurchase(Book book, Purchase purchase, AsyncHttpResponseHandler handler) {
        RequestParams params = new RequestParams();
        params.put("id", String.valueOf(book.id));
        params.put("order_id", purchase.getOrderId());
        ReaderAPI.post(URL_VALIDATE_PURCHASE, params, handler);
    }
    
    public interface OnLoadOwnedBooksListener extends BaseListener {
        public void onSuccess(List<Book> books);
    }
    
    public interface OnLoadCategoriesListener extends BaseListener {
        public void onSuccess();
    }
    
    public interface OnLoadBooksListener extends BaseListener {
        public void onSuccess(List<Book> books);
    }
    
    public interface OnLoadBookDetailsListener extends BaseListener {
        public void onSuccess(Book book);
    }

    public interface OnAuthenticateUserListener extends BaseListener {
        public void onSuccess();
    }

}
