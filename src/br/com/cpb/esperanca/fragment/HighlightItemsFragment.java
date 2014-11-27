package br.com.cpb.esperanca.fragment;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import br.com.cpb.esperanca.model.Highlight;
import br.com.cpb.esperanca.R;

import com.github.rtyley.android.sherlock.roboguice.fragment.RoboSherlockDialogFragment;

import roboguice.inject.InjectView;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: castelanjr
 * Date: 4/8/13
 * Time: 10:32 AM
 * Copyright (C) 2013 Nyvra Software. All rights reserved.
 */
public class HighlightItemsFragment extends RoboSherlockDialogFragment implements AdapterView.OnItemClickListener {
    private List<Highlight> mHighlights;
    private OnHighlightedItemSelectedListener mListener;

    @InjectView(R.id.list_search)
    private ListView mListHighlight;

    @InjectView(R.id.progress_search)
    private ProgressBar mProgressHighlight;

    @InjectView(R.id.text_no_results)
    private TextView mTextNoResults;

    public static HighlightItemsFragment newInstance() {
        HighlightItemsFragment frag = new HighlightItemsFragment();
        return frag;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mProgressHighlight.setVisibility(View.VISIBLE);
        mListHighlight.setVisibility(View.GONE);
        mTextNoResults.setVisibility(View.GONE);
    }

    public void setContent(List<Highlight> highlights) {
        mProgressHighlight.setVisibility(View.GONE);
        if (highlights != null && highlights.size() > 0) {
            mHighlights = highlights;
            mListHighlight.setVisibility(View.VISIBLE);
            mTextNoResults.setVisibility(View.GONE);
            mListHighlight.setAdapter(new HighlightAdapter());
            mListHighlight.setOnItemClickListener(this);
        } else {
            mHighlights = null;
            mListHighlight.setVisibility(View.GONE);
            mTextNoResults.setVisibility(View.VISIBLE);
            mTextNoResults.setText(R.string.label_no_highlights);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (mListener != null) mListener.onHighlightedItemSelected(mHighlights, position);
    }

    public void setListener(OnHighlightedItemSelectedListener listener) {
        mListener = listener;
    }

    private class HighlightAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mHighlights.size();
        }

        @Override
        public Highlight getItem(int position) {
            return mHighlights.get(position);
        }

        @Override
        public long getItemId(int position) {
            return (long) position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(getActivity()).inflate(R.layout.row_highlight_item, parent, false);
                holder = new ViewHolder();
                holder.textChapter = (TextView) convertView.findViewById(R.id.text_chapter);
                holder.textPage = (TextView) convertView.findViewById(R.id.text_page);
                holder.textHighlighText = (TextView) convertView.findViewById(R.id.text_stretch);
                holder.viewColor = convertView.findViewById(R.id.view_color);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            Highlight highlight = getItem(position);

            holder.textChapter.setText(highlight.chapter);
            holder.textPage.setText("Página " + highlight.page);
            holder.textHighlighText.setText(highlight.text);

            holder.viewColor.setBackgroundColor(Color.parseColor(highlight.color));

            return convertView;
        }
    }

    static class ViewHolder {
        TextView textChapter, textPage, textHighlighText;
        View viewColor;
    }

    public interface OnHighlightedItemSelectedListener {
        public void onHighlightedItemSelected(List<Highlight> highlights, int position);
    }
}
