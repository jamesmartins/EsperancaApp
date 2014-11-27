package br.com.cpb.esperanca.model;

/**
 * Created with IntelliJ IDEA.
 * User: castelanjr
 * Date: 4/2/13
 * Time: 7:36 AM
 * Copyright (C) 2013 Nyvra Software. All rights reserved.
 */
public class SearchResult {
    public String chapter, text;
    public int page, index, start, end;

    public SearchResult(String chapter, String text, int page, int index, int start, int end) {
        this.chapter = chapter;
        this.text = text;
        this.page = page;
        this.index = index;
        this.start = start;
        this.end = end;
    }
}
