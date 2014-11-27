package br.com.cpb.esperanca.model;

/**
 * Created with IntelliJ IDEA.
 * User: castelanjr
 * Date: 4/4/13
 * Time: 8:31 AM
 * Copyright (C) 2013 Nyvra Software. All rights reserved.
 */
public class Highlight {
    public static final String COLOR_YELLOW = "#FFF864";
    public static final String COLOR_GREEN = "#C9F882";
    public static final String COLOR_BLUE = "#81BEFB";
    public static final String COLOR_PINK = "#FF91C1";
    public static final String COLOR_WHITE = "#FFFFFF";

    public int index, start, end, page;
    public String color, chapter, text;

    public Highlight(int index, int start, int end, String color, int page, String chapter, String text) {
        this.index = index;
        this.start = start;
        this.end = end;
        this.color = color;
        this.page = page;
        this.chapter = chapter;
        this.text = text;
    }

}
