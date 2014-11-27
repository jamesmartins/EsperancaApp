/*
 * Copyright (C) 2011 Alex Kuiper
 * 
 * This file is part of PageTurner
 *
 * PageTurner is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * PageTurner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PageTurner.  If not, see <http://www.gnu.org/licenses/>.*
 */

package net.nightwhistler.pageturner.view.bookview;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.text.*;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ImageSpan;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import net.nightwhistler.htmlspanner.FontFamily;
import net.nightwhistler.htmlspanner.HtmlSpanner;
import net.nightwhistler.htmlspanner.SpanStack;
import net.nightwhistler.htmlspanner.TagNodeHandler;
import net.nightwhistler.htmlspanner.handlers.TableHandler;
import net.nightwhistler.htmlspanner.spans.CenterSpan;
import net.nightwhistler.pageturner.epub.PageTurnerSpine;
import net.nightwhistler.pageturner.epub.ResourceLoader;
import net.nightwhistler.pageturner.epub.ResourceLoader.ResourceCallback;
import net.nightwhistler.pageturner.view.FastBitmapDrawable;
import br.com.cpb.esperanca.model.Highlight;
import br.com.cpb.esperanca.model.SearchResult;
import br.com.cpb.esperanca.R;
import nl.siegmann.epublib.Constants;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.MediaType;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.domain.TOCReference;
import nl.siegmann.epublib.epub.EpubReader;
import nl.siegmann.epublib.service.MediatypeService;
import nl.siegmann.epublib.util.StringUtil;

import org.htmlcleaner.TagNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.*;

public class BookView extends ScrollView {

	private int storedIndex;
	private String storedAnchor;

    private InnerView childView;

	private Set<BookViewListener> listeners;

	private HtmlSpanner spanner;
	private TableHandler tableHandler;

	private PageTurnerSpine spine;

	private String fileName;
	private Book book;

	private Map<String, Integer> anchors;

	private int prevIndex = -1;
	private int prevPos = -1;

	private PageChangeStrategy strategy;
	private ResourceLoader loader;

	private int horizontalMargin = 0;
	private int verticalMargin = 0;
	private int lineSpacing = 0;
	
	private Handler scrollHandler;

	private static final Logger LOG = LoggerFactory.getLogger(BookView.class);

	private Map<String, FastBitmapDrawable> imageCache = new HashMap<String, FastBitmapDrawable>();

    private List<Highlight> highlights;

	public BookView(Context context, AttributeSet attributes) {
		super(context, attributes);
		
		this.scrollHandler = new Handler();
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public void init() {
		this.listeners = new HashSet<BookViewListener>();

		this.childView = (InnerView) this.findViewById(R.id.innerView);
		this.childView.setBookView(this);

		childView.setCursorVisible(false);
		childView.setLongClickable(true);
		this.setVerticalFadingEdgeEnabled(false);
		childView.setFocusable(true);
		childView.setLinksClickable(true);

		if (Build.VERSION.SDK_INT >= 11) {
			childView.setTextIsSelectable(true);
		}
		
		this.setSmoothScrollingEnabled(false);

		this.anchors = new HashMap<String, Integer>();
		this.tableHandler = new TableHandler();
	}

	private void onInnerViewResize() {
		restorePosition();

		int tableWidth = (int) (this.getWidth() * 0.9);
		tableHandler.setTableWidth(tableWidth);
	}

	public void setSpanner(HtmlSpanner spanner) {
		this.spanner = spanner;

		ImageTagHandler imgHandler = new ImageTagHandler(false);
		spanner.registerHandler("img", imgHandler);
		spanner.registerHandler("image", imgHandler);

		spanner.registerHandler("a", new AnchorHandler(new LinkTagHandler()));

		spanner.registerHandler("h1",
				new AnchorHandler(spanner.getHandlerFor("h1")));
		spanner.registerHandler("h2",
				new AnchorHandler(spanner.getHandlerFor("h2")));
		spanner.registerHandler("h3",
				new AnchorHandler(spanner.getHandlerFor("h3")));
		spanner.registerHandler("h4",
				new AnchorHandler(spanner.getHandlerFor("h4")));
		spanner.registerHandler("h5",
				new AnchorHandler(spanner.getHandlerFor("h5")));
		spanner.registerHandler("h6",
				new AnchorHandler(spanner.getHandlerFor("h6")));

		spanner.registerHandler("p",
				new AnchorHandler(spanner.getHandlerFor("p")));
		spanner.registerHandler("table", tableHandler);
	}

    public void setHighlights(List<Highlight> highlights) {
        this.highlights = highlights;
    }

	private void clearImageCache() {
		for (Map.Entry<String, FastBitmapDrawable> draw : imageCache.entrySet()) {
			draw.getValue().destroy();
		}

		imageCache.clear();
	}

	@Override
	protected void onScrollChanged(int l, int t, int oldl, int oldt) {
		super.onScrollChanged(l, t, oldl, oldt);
		progressUpdate();
	}

	/**
	 * Returns if we're at the start of the book, i.e. displaying the title
	 * page.
	 * 
	 * @return
	 */
	public boolean isAtStart() {

		if (spine == null) {
			return true;
		}

		return spine.getPosition() == 0 && strategy.isAtStart();
	}

	public boolean isAtEnd() {
		if (spine == null) {
			return false;
		}

		return spine.getPosition() >= spine.size() - 1 && strategy.isAtEnd();
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
		this.loader = new ResourceLoader(fileName);
	}

	@Override
	public void setOnTouchListener(OnTouchListener l) {
		super.setOnTouchListener(l);
		this.childView.setOnTouchListener(l);
	}

	public void setStripWhiteSpace(boolean stripWhiteSpace) {
		this.spanner.setStripExtraWhiteSpace(stripWhiteSpace);
	}

	public ClickableSpan[] getLinkAt(float x, float y) {
		Integer offset = findOffsetForPosition(x, y);

		CharSequence text = childView.getText();
		
		if (offset == null || ! (text instanceof Spanned)) {
			return null;
		} 

		Spanned spannedText = (Spanned) text;
		ClickableSpan[] spans = spannedText.getSpans(offset, offset,
				ClickableSpan.class);

		return spans;
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {

		if (strategy.isScrolling()) {
			return super.onTouchEvent(ev);
		} else {
			return childView.onTouchEvent(ev);
		}
	}

	public boolean hasPrevPosition() {
		return this.prevIndex != -1 && this.prevPos != -1;
	}

	public void setLineSpacing(int lineSpacing) {
		if (lineSpacing != this.lineSpacing) {
			this.lineSpacing = lineSpacing;
			this.childView.setLineSpacing(lineSpacing, 1);

			if (strategy != null) {
				strategy.updatePosition();
			}
		}
	}

	/*
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public void setTextSelectionCallback(TextSelectionCallback callback) {
		if (Build.VERSION.SDK_INT >= 11) {
			this.childView
					.setCustomSelectionActionModeCallback(new TextSelectionActions(
							callback, this));
		}
	}
	*/

	public int getLineSpacing() {
		return lineSpacing;
	}

	public void setHorizontalMargin(int horizontalMargin) {

		if (horizontalMargin != this.horizontalMargin) {
			this.horizontalMargin = horizontalMargin;
			setPadding(this.horizontalMargin, this.verticalMargin,
					this.horizontalMargin, this.verticalMargin);
			if (strategy != null) {
				strategy.updatePosition();
			}
		}
	}

	public void releaseResources() {
		this.strategy.clearText();
		this.clearImageCache();
	}

	public void setLinkColor(int color) {
		this.childView.setLinkTextColor(color);
	}

	public void setVerticalMargin(int verticalMargin) {
		if (verticalMargin != this.verticalMargin) {
			this.verticalMargin = verticalMargin;
			setPadding(this.horizontalMargin, this.verticalMargin,
					this.horizontalMargin, this.verticalMargin);
			if (strategy != null) {
				strategy.updatePosition();
			}
		}
	}

	public int getHorizontalMargin() {
		return horizontalMargin;
	}

	public int getVerticalMargin() {
		return verticalMargin;
	}

	public int getSelectionStart() {
		return childView.getSelectionStart();
	}

	public int getSelectionEnd() {
		return childView.getSelectionEnd();
	}

	public String getSelectedText() {
		return childView.getText()
				.subSequence(getSelectionStart(), getSelectionEnd()).toString();
	}

	public void goBackInHistory() {

		if (this.prevIndex == this.getIndex()) {
			strategy.setPosition(prevPos);

			this.storedAnchor = null;
			this.prevIndex = -1;
			this.prevPos = -1;

			restorePosition();

		} else {
			this.strategy.clearText();
			this.spine.navigateByIndex(this.prevIndex);
			strategy.setPosition(this.prevPos);

			this.storedAnchor = null;
			this.prevIndex = -1;
			this.prevPos = -1;

			loadText();
		}
	}

	public void clear() {
		this.childView.setText("");
		this.anchors.clear();
		this.storedAnchor = null;
		this.storedIndex = -1;
		this.book = null;
		this.fileName = null;

		this.strategy.reset();
	}

	/**
	 * Loads the text and saves the restored position.
	 */
	public void restore() {
		strategy.clearText();
		loadText();
	}

	public void setIndex(int index) {
		this.storedIndex = index;
	}

	void loadText() {
		executeTask( new LoadTextTask(null, this.highlights));
	}

	private void loadText(List<SearchResult> hightListResults, List<Highlight> highlightedTexts) {
		executeTask( new LoadTextTask(hightListResults, highlightedTexts) );
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private <A, B, C> void executeTask( AsyncTask<A, B, C> task, A... params ) {
		if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.HONEYCOMB) {
			task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
		}
		else {
			task.execute(params);
		}
	}
	
	public void setFontFamily(FontFamily family) {
		this.childView.setTypeface(family.getDefaultTypeface());
		this.tableHandler.setTypeFace(family.getDefaultTypeface());

		//this.spanner.setFontFamily(family);
        //this.spanner.setDefaultFont(family);
	}

	public void pageDown() {
		strategy.pageDown();
		progressUpdate();
	}

	public void pageUp() {
		strategy.pageUp();
		progressUpdate();
	}

	TextView getInnerView() {
		return childView;
	}

	public PageTurnerSpine getSpine() {
		return this.spine;
	}

	private Integer findOffsetForPosition(float x, float y) {

		if (childView == null || childView.getLayout() == null) {
			return null;
		}

		Layout layout = this.childView.getLayout();
		int line = layout.getLineForVertical((int) y);

		return layout.getOffsetForHorizontal(line, x);
	}

	/**
	 * Returns the full word containing the character at the selected location.
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	public CharSequence getWordAt(float x, float y) {

		if (childView == null) {
			return null;
		}

		CharSequence text = this.childView.getText();

		if (text.length() == 0) {
			return null;
		}

		Integer offset = findOffsetForPosition(x, y);

		if (offset == null) {
			return null;
		}

		if (offset < 0 || offset > text.length() - 1) {
			return null;
		}

		if (isBoundaryCharacter(text.charAt(offset))) {
			return null;
		}

		int left = Math.max(0, offset - 1);
		int right = Math.min(text.length(), offset);

		CharSequence word = text.subSequence(left, right);
		while (left > 0 && !isBoundaryCharacter(word.charAt(0))) {
			left--;
			word = text.subSequence(left, right);
		}

		if (word.length() == 0) {
			return null;
		}

		while (right < text.length()
				&& !isBoundaryCharacter(word.charAt(word.length() - 1))) {
			right++;
			word = text.subSequence(left, right);
		}

		int start = 0;
		int end = word.length();

		if (isBoundaryCharacter(word.charAt(0))) {
			start = 1;
		}

		if (isBoundaryCharacter(word.charAt(word.length() - 1))) {
			end = word.length() - 1;
		}

		if (start > 0 && start < word.length() && end < word.length()) {
			return word.subSequence(start, end);
		}

		return null;
	}

	private static boolean isBoundaryCharacter(char c) {
		char[] boundaryChars = { ' ', '.', ',', '\"', '\'', '\n', '\t', ':',
				'!', '\'' };

		for (int i = 0; i < boundaryChars.length; i++) {
			if (boundaryChars[i] == c) {
				return true;
			}
		}

		return false;
	}

	public void navigateTo(String rawHref) {

		this.prevIndex = this.getIndex();
		this.prevPos = this.getPosition();

		// URLDecode the href, so it does not contain %20 etc.
        String href = null;
        try {
            href = URLDecoder.decode(StringUtil.substringBefore(rawHref,
                    Constants.FRAGMENT_SEPARATOR_CHAR), Charset.defaultCharset().name());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        // Don't decode the anchor.
		String anchor = StringUtil.substringAfterLast(rawHref,
                Constants.FRAGMENT_SEPARATOR_CHAR);

		if (!"".equals(anchor)) {
			this.storedAnchor = anchor;
		}

		// Just an anchor and no href; resolve it on this page
		if (href.length() == 0) {
			restorePosition();
		} else {

			this.strategy.clearText();
			this.strategy.setPosition(0);

			if (this.spine.navigateByHref(href)) {
				loadText();
			} else {				
				executeTask(new LoadTextTask(null, highlights), href);
			}
		}
	}

	public void navigateToPercentage(int percentage) {

		if (spine == null) {
			return;
		}
		
		int index = 0;
		
		if ( percentage > 0 ) {

			double targetPoint = (double) percentage / 100d;
			List<Double> percentages = this.spine.getRelativeSizes();

			if (percentages == null || percentages.isEmpty()) {
				return;
			}
			
			double total = 0;

			for (; total < targetPoint && index < percentages.size(); index++) {
				total = total + percentages.get(index);
			}

			index--;

			// Work-around for when we get multiple events.
			if (index < 0 || index >= percentages.size()) {
				return;
			}

			double partBefore = total - percentages.get(index);
			double progressInPart = (targetPoint - partBefore)
					/ percentages.get(index);
			
			this.strategy.setRelativePosition(progressInPart);
		} else {
			
			//Simply jump to titlepage			
			this.strategy.setPosition(0);
		}

		this.prevPos = this.getPosition();
		doNavigation(index);
	}

    public void navigateBySearchResult(List<SearchResult> results, int position) {
        SearchResult result = results.get(position);

        this.prevPos = this.getPosition();
        this.strategy.setPosition(result.start);

        this.prevIndex = this.getIndex();

        this.storedIndex = result.index;
        this.strategy.clearText();
        this.spine.navigateByIndex(result.index);

        loadText(results, null);
    }

    public void navigateByHighlight(List<Highlight> highlights, int position) {
        Highlight highlight = highlights.get(position);

        this.prevPos = this.getPosition();
        this.strategy.setPosition(highlight.start);

        this.prevIndex = this.getIndex();

        this.storedIndex = highlight.index;
        this.strategy.clearText();
        this.spine.navigateByIndex(highlight.index);

        loadText(null, highlights);
    }

    public void navigateByHighlight(List<Highlight> highlights) {
        this.highlights = highlights;

        this.strategy.setPosition(getPosition());
        this.strategy.clearText();

        loadText(null, highlights);
    }


	private void doNavigation(int index) {

		// Check if we're already in the right part of the book
		if (index == this.getIndex()) {
			restorePosition();
			progressUpdate();
			return;
		}

		this.prevIndex = this.getIndex();

		this.storedIndex = index;
		this.strategy.clearText();
		this.spine.navigateByIndex(index);

		loadText();
	}

	public void navigateTo(int index, int position) {

		this.prevPos = this.getPosition();
		this.strategy.setPosition(position);

		doNavigation(index);
	}

	public List<TocEntry> getTableOfContents() {
		if (this.book == null) {
			return null;
		}

		List<TocEntry> result = new ArrayList<TocEntry>();

		flatten(book.getTableOfContents().getTocReferences(), result, 0);

		return result;
	}

	private void flatten(List<TOCReference> refs, List<TocEntry> entries,
			int level) {

		if (refs == null || refs.isEmpty()) {
			return;
		}

		for (TOCReference ref : refs) {

			String title = "";

			for (int i = 0; i < level; i++) {
				title += "-";
			}

			title += ref.getTitle();

			if (ref.getResource() != null) {
				entries.add(new TocEntry(title, spine.resolveTocHref(ref
						.getCompleteHref())));
			}

			flatten(ref.getChildren(), entries, level + 1);
		}
	}

	@Override
	public void fling(int velocityY) {
		strategy.clearStoredPosition();
		super.fling(velocityY);
	}

	public int getIndex() {
		if (this.spine == null) {
			return storedIndex;
		}

		return this.spine.getPosition();
	}

	public int getPosition() {
		return strategy.getPosition();
	}

	public void setPosition(int pos) {
        if (strategy != null) this.strategy.setPosition(pos);
	}

	/**
	 * Scrolls to a previously stored point.
	 * 
	 * Call this after setPosition() to actually go there.
	 */
	private void restorePosition() {

		if (this.storedAnchor != null && this.anchors.containsKey(storedAnchor)) {
			strategy.setPosition(anchors.get(storedAnchor));
			this.storedAnchor = null;
		}

        if (strategy != null) {
		    this.strategy.updatePosition();
        }
	}

	/**
	 * Many books use
	 * <p>
	 * and
	 * <h1>tags as anchor points. This class harvests those point by wrapping
	 * the original handler.
	 * 
	 * @author Alex Kuiper
	 * 
	 */
	private class AnchorHandler extends TagNodeHandler {

		private TagNodeHandler wrappedHandler;

		public AnchorHandler(TagNodeHandler wrappedHandler) {
			this.wrappedHandler = wrappedHandler;
		}

//		@Override
//		public void handleTagNode(TagNode node, SpannableStringBuilder builder,
//				int start, int end) {
//
//			String id = node.getAttributeByName("id");
//			if (id != null) {
//				anchors.put(id, start);
//			}
//
//			wrappedHandler.handleTagNode(node, builder, start, end);
//		}

		@Override
		public void handleTagNode(TagNode node, SpannableStringBuilder builder,
				int start, int end, SpanStack spanStack) {
			// TODO Auto-generated method stub

			String id = node.getAttributeByName("id");
			if (id != null) {
				anchors.put(id, start);
			}

			wrappedHandler.handleTagNode(node, builder, start, end, spanStack);
		}
	}

	/**
	 * Creates clickable links.
	 * 
	 * @author work
	 * 
	 */
	private class LinkTagHandler extends TagNodeHandler {

		private List<String> externalProtocols;

		public LinkTagHandler() {
			this.externalProtocols = new ArrayList<String>();
			externalProtocols.add("http://");
			externalProtocols.add("epub://");
			externalProtocols.add("https://");
			externalProtocols.add("http://");
			externalProtocols.add("ftp://");
			externalProtocols.add("mailto:");
		}

//		@Override
//		public void handleTagNode(TagNode node, SpannableStringBuilder builder,
//				int start, int end) {
//
//			String href = node.getAttributeByName("href");
//
//			if (href == null) {
//				return;
//			}
//
//			final String linkHref = href;
//
//			// First check if it should be a normal URL link
//			for (String protocol : this.externalProtocols) {
//				if (href.toLowerCase(Locale.US).startsWith(protocol)) {
//					builder.setSpan(new URLSpan(href), start, end,
//							Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//					return;
//				}
//			}
//
//			// If not, consider it an internal nav link.
//			ClickableSpan span = new ClickableSpan() {
//
//				@Override
//				public void onClick(View widget) {
//                    if (linkHref.startsWith("#")) {
//                        navigateTo(linkHref);
//                    } else {
//					    navigateTo(spine.resolveHref(linkHref));
//                    }
//				}
//			};
//
//			builder.setSpan(span, start, end,
//					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//		}

		@Override
		public void handleTagNode(TagNode node, SpannableStringBuilder builder,
				int start, int end, SpanStack spanStack) {
			// TODO Auto-generated method stub
			String href = node.getAttributeByName("href");

			if (href == null) {
				return;
			}

			final String linkHref = href;

			// First check if it should be a normal URL link
			for (String protocol : this.externalProtocols) {
				if (href.toLowerCase(Locale.US).startsWith(protocol)) {
					builder.setSpan(new URLSpan(href), start, end,
							Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
					return;
				}
			}

			// If not, consider it an internal nav link.
			ClickableSpan span = new ClickableSpan() {

				@Override
				public void onClick(View widget) {
                    if (linkHref.startsWith("#")) {
                        navigateTo(linkHref);
                    } else {
					    navigateTo(spine.resolveHref(linkHref));
                    }
				}
			};

			builder.setSpan(span, start, end,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			
		}
	}

	private void setImageSpan(SpannableStringBuilder builder,
			Drawable drawable, int start, int end) {
		builder.setSpan(new ImageSpan(drawable), start, end,
				Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

		if (spine.isCover()) {
			builder.setSpan(new CenterSpan(), start, end,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
	}

	private class ImageCallback implements ResourceCallback {

		private SpannableStringBuilder builder;
		private int start;
		private int end;

		private String storedHref;
		
		private boolean fakeImages;

		public ImageCallback(String href, SpannableStringBuilder builder,
				int start, int end, boolean fakeImages) {
			this.builder = builder;
			this.start = start;
			this.end = end;
			this.storedHref = href;
			this.fakeImages = fakeImages;
		}

		@Override
		public void onLoadResource(String href, InputStream input) {

			if ( fakeImages ) {
				LOG.debug("Faking image for href: " + href);
				setFakeImage(input);
			} else {
				LOG.debug("Loading real image for href: " + href);
				setBitmapDrawable(input);
			}

		}
		
		private void setFakeImage(InputStream input) {
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;
			BitmapFactory.decodeStream(input, null, options);
			
			int[] sizes = calculateSize(options.outWidth, options.outHeight );
			
			ShapeDrawable draw = new ShapeDrawable(new RectShape());
			draw.setBounds(0, 0, sizes[0], sizes[1]);
			
			setImageSpan(builder, draw, start, end);
		}
		
		private void setBitmapDrawable(InputStream input) {
			Bitmap bitmap = null;
			try {
				bitmap = getBitmap(input);

				if (bitmap == null || bitmap.getHeight() < 1
						|| bitmap.getWidth() < 1) {
					return;
				}

			} catch (OutOfMemoryError outofmem) {
				LOG.error("Could not load image", outofmem);
				clearImageCache();
			}

			if (bitmap != null) {
				
				FastBitmapDrawable drawable = new FastBitmapDrawable(bitmap);
				
				drawable.setBounds(0, 0, bitmap.getWidth() - 1,
						bitmap.getHeight() - 1);
				setImageSpan(builder, drawable, start, end);

				LOG.debug("Storing image in cache: " + storedHref);
							
				imageCache.put(storedHref, drawable);				
			}
		}		
		

		private Bitmap getBitmap(InputStream input) {

			// BitmapDrawable draw = new BitmapDrawable(getResources(), input);
			Bitmap originalBitmap = BitmapFactory.decodeStream(input);
		
			if (originalBitmap != null) {
				int originalWidth = originalBitmap.getWidth();
				int originalHeight = originalBitmap.getHeight();

				int[] targetSizes = calculateSize(originalWidth, originalHeight);
				int targetWidth = targetSizes[0];
				int targetHeight = targetSizes[1];
				
				if ( targetHeight != originalHeight || targetWidth != originalWidth ) {					
					return Bitmap.createScaledBitmap(originalBitmap,
							targetWidth, targetHeight, true);
				}
			}

			return originalBitmap;
		}
	}
	
	private int[] calculateSize(int originalWidth, int originalHeight ) {
		int[] result = new int[] { originalWidth, originalHeight };		
		
		int screenHeight = getHeight() - (verticalMargin * 2);
		int screenWidth = getWidth() - (horizontalMargin * 2);
		
		// We scale to screen width for the cover or if the image is too
		// wide.
		if (originalWidth > screenWidth
				|| originalHeight > screenHeight || spine.isCover()) {

			float ratio = (float) originalWidth
					/ (float) originalHeight;

			int targetHeight = screenHeight - 1;
			int targetWidth = (int) (targetHeight * ratio);

			if (targetWidth > screenWidth - 1) {
				targetWidth = screenWidth - 1;
				targetHeight = (int) (targetWidth * (1 / ratio));
			}

			LOG.debug("Rescaling from " + originalWidth + "x"
					+ originalHeight + " to " + targetWidth + "x"
					+ targetHeight);

			if (targetWidth > 0 || targetHeight > 0) {
				result[0] = targetWidth;
				result[1] = targetHeight;
			}
		}
		
		return result;		
	}

	private class ImageTagHandler extends TagNodeHandler {

		private boolean fakeImages;
		
		public ImageTagHandler(boolean fakeImages) {
			this.fakeImages = fakeImages;			
		}

//		@Override
//		public void handleTagNode(TagNode node, SpannableStringBuilder builder,
//				int start, int end) {
//			String src = node.getAttributeByName("src");
//
//			if (src == null) {
//				src = node.getAttributeByName("href");
//			}
//
//			if (src == null) {
//				src = node.getAttributeByName("xlink:href");
//			}
//			builder.append("\uFFFC");
//
//			String resolvedHref = spine.resolveHref(src);
//
//			if (imageCache.containsKey(resolvedHref) && ! fakeImages ) {
//				Drawable drawable = imageCache.get(resolvedHref);
//				setImageSpan(builder, drawable, start, builder.length());
//				LOG.debug("Got cached href: " + resolvedHref);
//			} else {
//				LOG.debug("Loading href: " + resolvedHref);
//				this.registerCallback(resolvedHref, new ImageCallback(
//						resolvedHref, builder, start, builder.length(), fakeImages));
//			}
//		}
		
		protected void registerCallback(String resolvedHref, ImageCallback callback ) {
			BookView.this.loader.registerCallback(resolvedHref, callback);
		}

		@Override
		public void handleTagNode(TagNode node, SpannableStringBuilder builder,
				int start, int end, SpanStack spanStack) {
			// TODO Auto-generated method stub
			String src = node.getAttributeByName("src");

			if (src == null) {
				src = node.getAttributeByName("href");
			}

			if (src == null) {
				src = node.getAttributeByName("xlink:href");
			}
			builder.append("\uFFFC");

			String resolvedHref = spine.resolveHref(src);

			if (imageCache.containsKey(resolvedHref) && ! fakeImages ) {
				Drawable drawable = imageCache.get(resolvedHref);
				setImageSpan(builder, drawable, start, builder.length());
				LOG.debug("Got cached href: " + resolvedHref);
			} else {
				LOG.debug("Loading href: " + resolvedHref);
				this.registerCallback(resolvedHref, new ImageCallback(
						resolvedHref, builder, start, builder.length(), fakeImages));
			}
		}

	}

	@Override
	public void setBackgroundColor(int color) {
		super.setBackgroundColor(color);

		if (this.childView != null) {
			this.childView.setBackgroundColor(color);
		}
	}

	public void setTextColor(int color) {
		if (this.childView != null) {
			this.childView.setTextColor(color);
		}

		this.tableHandler.setTextColor(color);
	}

	public static class TocEntry {
		private String title;
		private String href;

		public TocEntry(String title, String href) {
			this.title = title;
			this.href = href;
		}

		public String getHref() {
			return href;
		}

		public String getTitle() {
			return title;
		}
	}

	/**
	 * Sets the given text to be displayed, overriding the book.
	 * 
	 * @param text
	 */
	public void setText(Spanned text) {
		this.strategy.loadText(text);
		this.strategy.updatePosition();
	}

	public Book getBook() {
		return book;
	}

	public float getTextSize() {
		return childView.getTextSize();
	}

	public void setTextSize(float textSize) {
		this.childView.setTextSize(textSize);
		this.tableHandler.setTextSize(textSize);
	}

	public void addListener(BookViewListener listener) {
		this.listeners.add(listener);
	}

	private void bookOpened(Book book) {
		for (BookViewListener listener : this.listeners) {
			listener.bookOpened(book);
		}
	}

	private void errorOnBookOpening(String errorMessage) {
		for (BookViewListener listener : this.listeners) {
			listener.errorOnBookOpening(errorMessage);
		}
	}

	private void parseEntryStart(int entry) {
		for (BookViewListener listener : this.listeners) {
			listener.parseEntryStart(entry);
		}
	}

	private void parseEntryComplete(int entry, String name) {
		for (BookViewListener listener : this.listeners) {
			listener.parseEntryComplete(entry, name);
		}
	}

	private void fireOpenFile() {
		for (BookViewListener listener : this.listeners) {
			listener.readingFile();
		}
	}

	private void fireRenderingText() {
		for (BookViewListener listener : this.listeners) {
			listener.renderingText();
		}
	}

	private void progressUpdate() {

		if (this.spine != null && this.strategy.getText() != null
				&& this.strategy.getText().length() > 0) {

			double progressInPart = (double) this.getPosition()
					/ (double) this.strategy.getText().length();

			if (strategy.getText().length() > 0 && strategy.isAtEnd()) {
				progressInPart = 1d;
			}

			int progress = spine.getProgressPercentage(progressInPart);

			if (progress != -1) {

				int pageNumber = spine.getPageNumberFor(getIndex(),
						getPosition());

				for (BookViewListener listener : this.listeners) {
					listener.progressUpdate(progress, pageNumber,
							spine.getTotalNumberOfPages());
				}
			}
		}
	}

	public void setEnableScrolling(boolean enableScrolling) {

		if (this.strategy == null
				|| this.strategy.isScrolling() != enableScrolling) {

			int pos = -1;
			boolean wasNull = true;

			Spanned text = null;

			if (this.strategy != null) {
				pos = this.strategy.getPosition();
				text = this.strategy.getText();
				this.strategy.clearText();
				wasNull = false;
			}

            this.strategy = new FixedPagesStrategy(this);

			if (!wasNull) {
				this.strategy.setPosition(pos);
			}

			if (text != null && text.length() > 0) {
				this.strategy.loadText(text);
			}
		}
	}

	private List<Integer> getOffsetsForResource(int spineIndex )
			throws IOException {
		
		HtmlSpanner mySpanner = new HtmlSpanner();
		final ResourceLoader privateLoader = new ResourceLoader(fileName);
		
		ImageTagHandler tagHandler = new ImageTagHandler(true) {
			protected void registerCallback(String resolvedHref, ImageCallback callback) {
				privateLoader.registerCallback(resolvedHref, callback);
			}
		};
		
		mySpanner.registerHandler("table", tableHandler );
		mySpanner.registerHandler("img", tagHandler);
		mySpanner.registerHandler("image", tagHandler);
		
		CharSequence text;
		
		if ( spineIndex == getIndex() ) {
			text = strategy.getText();
		} else {
			Resource res = spine.getResourceForIndex(spineIndex);
			text = mySpanner.fromHtml(res.getReader());
			privateLoader.load();
		}
		
		return FixedPagesStrategy.getPageOffsets(this, text, true);
	}
	
	

	public static class InnerView extends TextView {

		private BookView bookView;

		public InnerView(Context context, AttributeSet attributes) {
			super(context, attributes);
		}

		protected void onSizeChanged(int w, int h, int oldw, int oldh) {
			super.onSizeChanged(w, h, oldw, oldh);
			bookView.onInnerViewResize();
		}

		public boolean dispatchKeyEvent(KeyEvent event) {
            try {
			    return bookView.dispatchKeyEvent(event);
            } catch (StackOverflowError e) {
                e.printStackTrace();
                return false;
            }
		}

		public void setBookView(BookView bookView) {
			this.bookView = bookView;
		}
	}

	private static enum BookReadPhase {
		START, OPEN_FILE, PARSE_TEXT, DONE
	};

	private class LoadTextTask extends
			AsyncTask<String, BookReadPhase, Spanned> {

		private String name;

		private boolean wasBookLoaded;

		private String error;
		private boolean needToCalcPageNumbers = false;

		private List<SearchResult> searchResults = new ArrayList<SearchResult>();
        private List<Highlight> highlights = new ArrayList<Highlight>();

		public LoadTextTask() {

		}

		LoadTextTask(List<SearchResult> searchResults, List<Highlight> highlights) {
            if (searchResults != null) this.searchResults = searchResults;
            if (highlights != null) this.highlights = highlights;
		}

		@Override
		protected void onPreExecute() {
			this.wasBookLoaded = book != null;
			clearImageCache();
		}

		private void setBook(Book book) {

			BookView.this.book = book;
			BookView.this.spine = new PageTurnerSpine(book);

			String file = StringUtil.substringAfterLast(fileName, '/');

			BookView.this.spine.navigateByIndex(BookView.this.storedIndex);

            needToCalcPageNumbers = true;

		}

		private void initBook() throws IOException {

			publishProgress(BookReadPhase.OPEN_FILE);

			if (BookView.this.fileName == null) {
				throw new IOException("No file-name specified.");
			}

			// read epub file
			EpubReader epubReader = new EpubReader();

			MediaType[] lazyTypes = {
					MediatypeService.CSS, // We don't support CSS yet

					MediatypeService.GIF, MediatypeService.JPG,
					MediatypeService.PNG,
					MediatypeService.SVG, // Handled by the ResourceLoader

					MediatypeService.OPENTYPE,
					MediatypeService.TTF, // We don't support custom fonts
											// either
					MediatypeService.XPGT,

					MediatypeService.MP3,
					MediatypeService.MP4, // And no audio either
					MediatypeService.SMIL, MediatypeService.XPGT,
					MediatypeService.PLS };

			Book newBook = epubReader.readEpubLazy(fileName, "UTF-8",Arrays.asList(lazyTypes));
			setBook(newBook);

		}

		protected Spanned doInBackground(String... hrefs) {

			publishProgress(BookReadPhase.START);

			if (loader != null) {
				loader.clear();
			}

			if (BookView.this.book == null) {
				try {
					initBook();
				} catch (IOException io) {
					this.error = io.getMessage();
					return null;
				}
			}

			this.name = spine.getCurrentTitle();

			Resource resource;

			if (hrefs.length == 0) {
				resource = spine.getCurrentResource();
			} else {
				resource = book.getResources().getByHref(spine.getCurrentHref() + hrefs[0]);
			}

			if (resource == null) {
				return new SpannedString(
						"Sorry, it looks like you clicked a dead link.\nEven books have 404s these days.");
			}

			publishProgress(BookReadPhase.PARSE_TEXT);

			try {
				Spannable result = spanner.fromHtml(resource.getReader());
				loader.load(); // Load all image resources.

				// Highlight search results (if any)
				for (SearchResult searchResult : this.searchResults) {
					if (searchResult.index == spine.getPosition()) {
						result.setSpan(new BackgroundColorSpan(Color.CYAN),
								searchResult.start, searchResult.end,
								Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
					}
				}

                for (Highlight highlight : this.highlights) {
                    if (highlight.index == spine.getPosition()) {
                        result.setSpan(new BackgroundColorSpan(Color.parseColor(highlight.color)), highlight.start, highlight.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }

				publishProgress(BookReadPhase.DONE);
                strategy.loadText(result);

				return result;
			} catch (IOException io) {
                Log.d("BookView", "Could not load text: " + io.getMessage());
				return new SpannableString("Could not load text: "
						+ io.getMessage());
			}

		}

		@Override
		protected void onProgressUpdate(BookReadPhase... values) {

			BookReadPhase phase = values[0];

			switch (phase) {
			case START:
				parseEntryStart(getIndex());
				break;
			case OPEN_FILE:
				fireOpenFile();
				break;
			case PARSE_TEXT:
				fireRenderingText();
				break;
			case DONE:
				parseEntryComplete(spine.getPosition(), this.name);
				break;
			}
		}

		@Override
		protected void onPostExecute(final Spanned result) {

			if (!wasBookLoaded) {
				if (book != null) {
					bookOpened(book);
				} else {
					errorOnBookOpening(this.error);
					return;
				}
			}

			restorePosition();
			strategy.updateGUI();
			progressUpdate();

			if (needToCalcPageNumbers) {
				executeTask( new CalculatePageNumbersTask() );
			}
			
			/**
			 * This is a hack for scrolling not updating to the right position 
			 * on Android 4+
			 */
			if ( strategy.isScrolling() ) {
				scrollHandler.postDelayed(new Runnable() {
					
					@Override
					public void run() {
						restorePosition();						
					}
				}, 100);
			}
		}
	}

	private class CalculatePageNumbersTask extends
			AsyncTask<Object, Void, List<List<Integer>>> {
		@Override
		protected List<List<Integer>> doInBackground(Object... params) {

			try {
				List<List<Integer>> offsets = new ArrayList<List<Integer>>();

				for (int i = 0; i < spine.size(); i++) {
					offsets.add(getOffsetsForResource(i));
				}

				String file = StringUtil.substringAfterLast(fileName, '/');
				//configuration.setPageOffsets(file, offsets);

				return offsets;

			} catch (IOException io) {
				LOG.error("Could not read pagenumers", io);
			}

			return null;
		}

		@Override
		protected void onPostExecute(List<List<Integer>> result) {
			spine.setPageOffsets(result);
			progressUpdate();
		}
	}

    public InnerView getChildView() {
        return childView;
    }

}
