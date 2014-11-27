package br.com.cpb.esperanca.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;

import com.github.rtyley.android.sherlock.roboguice.fragment.RoboSherlockDialogFragment;

import br.com.cpb.esperanca.app.Configs;
import br.com.cpb.esperanca.R;
import roboguice.inject.InjectView;

/**
 * Created with IntelliJ IDEA.
 * User: castelanjr
 * Date: 3/26/13
 * Time: 9:05 AM
 * Copyright (C) 2013 Nyvra Software. All rights reserved.
 */
public class FontsDialogFragment extends RoboSherlockDialogFragment {
    private OnFontChangedListener mListener;
    private int mBookId;
    private Configs mConfigs;

    private String[] mFontsArray;

    @InjectView(R.id.seek_bright)
    private SeekBar mSeekBright;

    @InjectView(R.id.button_minor)
    private Button mButtonMinor;

    @InjectView(R.id.button_bigger)
    private Button mButtonBigger;

    @InjectView(R.id.spinner_fontface)
    private Spinner mSpinnerFontface;

    public static FontsDialogFragment newInstance(OnFontChangedListener listener, int bookId) {
        FontsDialogFragment frag = new FontsDialogFragment();
        frag.mListener = listener;
        frag.mBookId = bookId;
        return frag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFontsArray = getResources().getStringArray(R.array.fonts);
        mConfigs = Configs.getInstance(getActivity(), mBookId);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (!getResources().getBoolean(R.bool.is_tablet)) {
            getDialog().setTitle("Fontes");
            getDialog().setCanceledOnTouchOutside(true);
        }

        int pos = 0;
        for (int i = 0; i < mFontsArray.length; i++) {
            if (mFontsArray[i].equals(mConfigs.fontName)) {
                pos = i;
                break;
            }
        }

        mSpinnerFontface.setSelection(pos);

        mSeekBright.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (mListener != null) mListener.onBrightChanged(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        mButtonMinor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null) mListener.onFontSizeChanged(-2);
            }
        });

        mButtonBigger.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null) mListener.onFontSizeChanged(+2);
            }
        });

        mSpinnerFontface.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (mListener != null) mListener.onFontFaceChanged(mFontsArray[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_fonts, container, false);
    }

    public void setListener(OnFontChangedListener listener) {
        mListener = listener;
    }


    public interface OnFontChangedListener {
        public void onBrightChanged(int bright);
        public void onFontSizeChanged(int fontSize);
        public void onFontFaceChanged(String font);
    }

}
