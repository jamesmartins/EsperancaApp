package br.com.cpb.esperanca.db;

import android.content.ContentValues;
import android.database.SQLException;
import br.com.cpb.esperanca.model.Book;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class OwnedItemsDatabase extends SQLiteOpenHelper {
    private static final String TAG = OwnedItemsDatabase.class.getSimpleName();

    private static final String DATABASE_NAME = "ownedItems.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_NAME = "ownedItems";
    private static final String C_ID = "id";
    private static final String C_TITLE = "title";
    private static final String C_AUTHOR = "author";
    private static final String C_PRICE = "price";
    private static final String C_CATEGORY_ID = "category_id";
    private static final String C_COVER_URL = "cover_url";
    private static final String C_ISSUE_URL = "issue_url";
    private static final String C_ENABLED = "enabled";

    private static final String SCRIPT_CREATE = "CREATE TABLE "
            + TABLE_NAME + " ( "
            + C_ID + " integer primary key, "
            + C_TITLE + " text not null, "
            + C_AUTHOR + " text not null, "
            + C_PRICE + " text not null, "
            + C_CATEGORY_ID + " integer not null, "
            + C_COVER_URL + " text not null, "
            + C_ISSUE_URL + " text not null, "
            + C_ENABLED + " boolean not null);";

    public OwnedItemsDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(TAG, "Creating table: " + TABLE_NAME + ", sql: " + SCRIPT_CREATE);
        db.execSQL(SCRIPT_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("drop table if exists " + TABLE_NAME);
        Log.d(TAG, "Upgrading all tables");
        this.onCreate(db);
    }
    
    public List<Book> getOwnedItems() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_NAME, null, C_ENABLED + "=1", null, null, null, null);

        List<Book> books = new ArrayList<Book>();
        while (c.moveToNext()) {
            Book book = new Book();
            book.id = c.getInt(c.getColumnIndex(C_ID));
            book.title = c.getString(c.getColumnIndex(C_TITLE));
            book.author = c.getString(c.getColumnIndex(C_AUTHOR));
            book.price = c.getString(c.getColumnIndex(C_PRICE));
            book.category_id = c.getInt(c.getColumnIndex(C_CATEGORY_ID));
            book.cover_url = c.getString(c.getColumnIndex(C_COVER_URL));
            book.issue_url = c.getString(c.getColumnIndex(C_ISSUE_URL));
            books.add(book);
        }
        c.close();
        db.close();
        return books;
    }
    
    public void addBookToOwnedItems(Book book) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues values = bookToValues(book, true);

            Cursor c = db.query(TABLE_NAME, null, C_ID + "=" + book.id, null, null, null, null);

            if (c.moveToFirst()) {
                Log.d(TAG, "Book already exists in database - enabling it: " + values);
                db.update(TABLE_NAME, values, C_ID + "=" + book.id, null);
            } else {
                Log.d(TAG, "Adding item to database: " + values);
                db.insert(TABLE_NAME, null, values);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            db.close();
        }
    }

    public void removeBookFromOwnedItems(Book book) {
        SQLiteDatabase db = getWritableDatabase();
        //db.delete(TABLE_NAME, C_ID + "=" + book.id, null);
        ContentValues values = bookToValues(book, false);
        db.update(TABLE_NAME, values, C_ID + "=" + book.id, null);
        db.close();
    }

    private ContentValues bookToValues(Book book, boolean enabled) {
        ContentValues values = new ContentValues();
        values.put(C_ID, book.id);
        values.put(C_TITLE, book.title);
        values.put(C_AUTHOR, book.author);
        values.put(C_PRICE, book.price);
        values.put(C_CATEGORY_ID, book.category_id);
        values.put(C_COVER_URL, book.cover_url);
        values.put(C_ISSUE_URL, book.issue_url);
        values.put(C_ENABLED, enabled);
        return values;
    }

}
