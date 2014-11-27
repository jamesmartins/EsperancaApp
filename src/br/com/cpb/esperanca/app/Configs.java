package br.com.cpb.esperanca.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import net.nightwhistler.htmlspanner.FontFamily;
import br.com.cpb.esperanca.model.Highlight;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: castelanjr
 * Date: 3/26/13
 * Time: 3:30 PM
 * Copyright (C) 2013 Nyvra Software. All rights reserved.
 */
public class Configs {
    private static final String PREF_TEXT_SIZE = "text_size";
    private static final String PREF_FONT_NAME = "font_name";
    private static final String PREF_BASE_CURRENT_POSITION = "position_";
    private static final String PREF_BASE_CURRENT_INDEX = "index_";
    private static final String PREF_BASE_HIGHLIGHTS = "highlights_";
    private static final String PREFERENCES_FILENAME = "book_settings";

    private Context mContext;
    private SharedPreferences mPrefs;
    private int mCurrentBookId;
    private FontFamily mCachedFont;
    private Gson mGson;

    private static Configs sInstance;

    public int textSize, position, index;
    public String fontName;
    public List<Highlight> highlights;

    private Configs(Context context, int bookId) {
        mContext 	   = context;
        mCurrentBookId = bookId;
        textSize = getPreferences().getInt(PREF_TEXT_SIZE, 18);
        position = getPreferences().getInt(PREF_BASE_CURRENT_POSITION + mCurrentBookId, 0);
        index 	 = getPreferences().getInt(PREF_BASE_CURRENT_INDEX + mCurrentBookId, 0);
        fontName = getPreferences().getString(PREF_FONT_NAME, "gen_book_bas");

        Type type = new TypeToken<List<Highlight>>(){}.getType();
        highlights = getGson().fromJson(getPreferences().getString(PREF_BASE_HIGHLIGHTS + mCurrentBookId, ""), type);

        if (highlights == null) {
            highlights = new ArrayList<Highlight>();
        }
    }

    public static Configs getInstance(Context context, int bookId) {
        if (sInstance == null) {
            sInstance = new Configs(context, bookId);
        } else {
            if (sInstance.mCurrentBookId != bookId || sInstance.mContext == null) {
                sInstance = new Configs(context, bookId);
            }
        }
        return sInstance;
    }

    public void save() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                SharedPreferences.Editor editor = getPreferences().edit();
                editor.putInt(PREF_TEXT_SIZE, textSize);
                editor.putInt(PREF_BASE_CURRENT_POSITION + mCurrentBookId, position);
                editor.putInt(PREF_BASE_CURRENT_INDEX + mCurrentBookId, index);
                editor.putString(PREF_FONT_NAME, fontName);

                String highlightsJSON = getGson().toJson(highlights);
                editor.putString(PREF_BASE_HIGHLIGHTS + mCurrentBookId, highlightsJSON);

                editor.commit();
            }
        }).start();

    }

    public FontFamily getFontFamily(String face) {
        if (mCachedFont != null && mCachedFont.getName().equals(face)) {
            return mCachedFont;
        }

        if ("gen_book_bas".equals(face)) {
            return mCachedFont = loadFontFromAssets(face,
                    "GentiumBookBasic");
        }
        if ("gen_bas".equals(face)) {
            return mCachedFont = loadFontFromAssets(face,
                    "GentiumBasic");
        }

        Typeface typeFace = Typeface.SANS_SERIF;

        if ("sans".equals(face)) {
            typeFace = Typeface.SANS_SERIF;
        } else if ("serif".equals(face)) {
            typeFace = Typeface.SERIF;
        } else if ("mono".equals(face)) {
            typeFace = Typeface.MONOSPACE;
        }

        return mCachedFont = new FontFamily(face, typeFace);
    }

    private FontFamily loadFontFromAssets(String face, String fullName) {

        Typeface basic = Typeface.createFromAsset(mContext.getAssets(), fullName + ".otf");
        Typeface boldFace = Typeface.createFromAsset(mContext.getAssets(),
                fullName + "-Bold.otf");
        Typeface italicFace = Typeface.createFromAsset(mContext.getAssets(),
                fullName + "-Italic.otf");
        Typeface biFace = Typeface.createFromAsset(mContext.getAssets(),
                fullName + "-BoldItalic.otf");

        mCachedFont = new FontFamily(face, basic);
        mCachedFont.setBoldTypeface(boldFace);
        mCachedFont.setItalicTypeface(italicFace);
        mCachedFont.setBoldItalicTypeface(biFace);

        return mCachedFont;
    }

    private SharedPreferences getPreferences() {
        if (mPrefs == null) {
            mPrefs = mContext.getSharedPreferences(PREFERENCES_FILENAME, 0);
        }
        return mPrefs;
    }

    public Gson getGson() {
        if (mGson == null) {
            mGson = new Gson();
        }
        return mGson;
    }
}
