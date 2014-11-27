package br.com.cpb.esperanca.service;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import android.util.Log;
import br.com.cpb.esperanca.model.Book;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

/**
 * Created with IntelliJ IDEA.
 * User: castelanjr
 * Date: 2/6/13
 * Time: 10:39 AM
 * To change this template use File | Settings | File Templates.
 */

public class DownloadBookService extends Service {
    private static final String TAG = DownloadBookService.class.getSimpleName();

    public static final String ACTION_UPDATE = "net.nyvra.reader.action.UPDATE";
    public static final String ACTION_SUCCESS = "net.nyvra.reader.action.SUCCESS";
    public static final String ACTION_FAIL = "net.nyvra.reader.action.FAIL";

    public static final String EXTRA_BOOK_ID = "bookId";
    public static final String EXTRA_BOOK_URL = "bookUrl";
    public static final String EXTRA_ORDER_ID = "orderId";
    public static final String EXTRA_TOTAL_SIZE = "totalSize";
    public static final String EXTRA_SIZE = "size";

    public class DownloadBinder extends Binder {
        public DownloadBookService getService() {
            return DownloadBookService.this;
        }
    }

    private final IBinder mBinder = new DownloadBinder();
    public static boolean isRunning = false;

    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;

    public DownloadBookService() {
        super();
    }

    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        isRunning = true;

        HandlerThread thread = new HandlerThread(TAG);
        thread.start();

        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");

        if (intent != null) {
            int id = intent.getIntExtra(EXTRA_BOOK_ID, -1);
            String url = intent.getStringExtra(EXTRA_BOOK_URL);
            String orderId = intent.getStringExtra(EXTRA_ORDER_ID);

            Book book = new Book();
            book.id = id;
            book.issue_url = url;

            startDownloading(book, orderId);
        }

        return START_STICKY;
    }

    public void startDownloading(Book book, String orderId) {
        Message msg = mServiceHandler.obtainMessage();
        msg.obj = book;

        if (orderId != null) {
            Bundle bundle = new Bundle();
            bundle.putString(EXTRA_ORDER_ID, orderId);
            msg.setData(bundle);
        }

        mServiceHandler.sendMessage(msg);
    }

    private final class ServiceHandler extends Handler {

        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Book book = (Book) msg.obj;
            String orderId = null;
            if (msg.getData() != null) {
                orderId = msg.getData().getString(EXTRA_ORDER_ID);
            }
            String rawUrl;
            if (orderId == null) {
                rawUrl = book.getFreeURL();
            } else {
                rawUrl = book.getPaidUrl(orderId);
            }

            try {
                URL url = new URL(rawUrl);
                final int size = getFileSize(url);
                int loop = 0;
                if (size > 0) {
                    Log.d(TAG, "Total size: " + size);

                    URLConnection conn = url.openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);

                    BufferedInputStream inputStream = new BufferedInputStream(conn.getInputStream());
                    File f = new File(book.getPath(DownloadBookService.this));
                    f.getParentFile().mkdirs();
                    FileOutputStream outputStream = new FileOutputStream(f);
                    int bytesRead, totalRead = 0;
                    byte[] bytes = new byte[32 * 1024];
                    while ((bytesRead = inputStream.read(bytes)) != -1) {
                        outputStream.write(bytes, 0, bytesRead);
                        totalRead += bytesRead;
                        if (loop++ % 20 == 0) {
                            Log.d(TAG, "Updating to " + totalRead);
                            Intent intent = new Intent(ACTION_UPDATE);
                            intent.putExtra(EXTRA_BOOK_ID, book.id);
                            intent.putExtra(EXTRA_TOTAL_SIZE, size);
                            intent.putExtra(EXTRA_SIZE, totalRead);
                            sendBroadcast(intent);
                        }
                    }
                    outputStream.close();
                    inputStream.close();
                } else {
                    Intent intent = new Intent(ACTION_FAIL);
                    intent.putExtra(EXTRA_BOOK_ID, book.id);
                    sendBroadcast(intent);
                    Log.d(TAG, "File size is 0");
                    return;
                }

                Intent intent = new Intent(ACTION_SUCCESS);
                intent.putExtra(EXTRA_BOOK_ID, book.id);
                sendBroadcast(intent);
                Log.d(TAG, "Completed!");

            } catch (IOException e) {
                e.printStackTrace();
                File f = new File(book.getPath(DownloadBookService.this));
                f.delete();
                Intent intent = new Intent(ACTION_FAIL);
                intent.putExtra(EXTRA_BOOK_ID, book.id);
                sendBroadcast(intent);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
    }

    private static int getFileSize(URL url) {
        int size = -1;
        HttpURLConnection conn = null;

        try {
            conn = (HttpURLConnection) url.openConnection();
            size = conn.getContentLength();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) conn.disconnect();
        }

        return size;
    }

}
