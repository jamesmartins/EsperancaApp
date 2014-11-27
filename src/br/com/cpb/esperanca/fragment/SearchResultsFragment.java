package br.com.cpb.esperanca.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.github.rtyley.android.sherlock.roboguice.fragment.RoboSherlockDialogFragment;

import br.com.cpb.esperanca.model.SearchResult;
import br.com.cpb.esperanca.R;
import roboguice.inject.InjectView;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: castelanjr
 * Date: 3/28/13
 * Time: 12:12 PM
 * Copyright (C) 2013 Nyvra Software. All rights reserved.
 */
public class SearchResultsFragment extends RoboSherlockDialogFragment implements AdapterView.OnItemClickListener {
    private List<SearchResult> mResults;
    private OnSearchResultSelectedListener mListener;

    @InjectView(R.id.list_search)
    private ListView mListSearch;

    @InjectView(R.id.progress_search)
    private ProgressBar mProgressSearch;

    @InjectView(R.id.text_no_results)
    private TextView mTextNoResults;

    public static SearchResultsFragment newInstance() {
        SearchResultsFragment frag = new SearchResultsFragment();
        return frag;
    }

    public void setContent(List<SearchResult> results) {
        mProgressSearch.setVisibility(View.GONE);
        if (results != null && results.size() > 0) {
            mResults = results;
            mListSearch.setVisibility(View.VISIBLE);
            mTextNoResults.setVisibility(View.GONE);
            mListSearch.setAdapter(new SearchResultsAdapter());
            mListSearch.setOnItemClickListener(this);
        } else {
            mResults = null;
            mListSearch.setVisibility(View.GONE);
            mTextNoResults.setVisibility(View.VISIBLE);
        }
    }

    public void setListener(OnSearchResultSelectedListener listener) {
        mListener = listener;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        clearView();
    }

    public void clearView() {
        mProgressSearch.setVisibility(View.INVISIBLE);
        mListSearch.setVisibility(View.GONE);
        mTextNoResults.setVisibility(View.GONE);
    }

    public void startProgress() {
        mProgressSearch.setVisibility(View.VISIBLE);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (mListener != null) mListener.onSearchResultSelected(mResults, position);
    }

    private class SearchResultsAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mResults.size();
        }

        @Override
        public SearchResult getItem(int position) {
            return mResults.get(position);
        }

        @Override
        public long getItemId(int position) {
            return (long) position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(getActivity()).inflate(R.layout.row_search_result, parent, false);
                holder = new ViewHolder();
                holder.textChapter = (TextView) convertView.findViewById(R.id.text_chapter);
                holder.textPage = (TextView) convertView.findViewById(R.id.text_page);
                holder.textStretch = (TextView) convertView.findViewById(R.id.text_stretch);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            SearchResult result = getItem(position);

            holder.textChapter.setText(result.chapter);
            holder.textPage.setText("Página " + result.page);
            holder.textStretch.setText(result.text);

            return convertView;
        }
    }

    static class ViewHolder {
        TextView textChapter, textPage, textStretch;
    }

    public interface OnSearchResultSelectedListener {
        public void onSearchResultSelected(List<SearchResult> results, int position);
    }

}
