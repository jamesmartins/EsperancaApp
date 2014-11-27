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

package net.nightwhistler.pageturner.activity;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.*;
import android.content.DialogInterface.OnClickListener;
import android.graphics.*;
import android.graphics.Bitmap.Config;
import android.os.*;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.DisplayMetrics;
import android.view.*;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.*;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;

import net.nightwhistler.htmlspanner.HtmlSpanner;
import net.nightwhistler.htmlspanner.SpanStack;
import net.nightwhistler.htmlspanner.TagNodeHandler;
import net.nightwhistler.htmlspanner.handlers.TableHandler;
import net.nightwhistler.htmlspanner.spans.CenterSpan;
import net.nightwhistler.pageturner.animation.*;
import net.nightwhistler.pageturner.epub.PageTurnerSpine;
import net.nightwhistler.pageturner.view.AnimatedImageView;
import net.nightwhistler.pageturner.view.NavGestureDetector;
import net.nightwhistler.pageturner.view.bookview.BookView;
import net.nightwhistler.pageturner.view.bookview.BookViewListener;
import br.com.cpb.esperanca.activity.ReadingActivity;
import br.com.cpb.esperanca.app.Configs;
import br.com.cpb.esperanca.fragment.FontsDialogFragment;
import br.com.cpb.esperanca.fragment.HighlightItemsFragment;
import br.com.cpb.esperanca.fragment.SearchResultsFragment;
import br.com.cpb.esperanca.model.Highlight;
import br.com.cpb.esperanca.model.SearchResult;
import br.com.cpb.esperanca.util.Utils;
import br.com.cpb.esperanca.R;
import nl.siegmann.epublib.domain.Author;
import nl.siegmann.epublib.domain.Book;

import org.htmlcleaner.TagNode;

import roboguice.RoboGuice;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReadingFragment extends SherlockFragment implements BookViewListener, FontsDialogFragment.OnFontChangedListener, 
																 SearchResultsFragment.OnSearchResultSelectedListener, 
																 View.OnClickListener, 
																 HighlightItemsFragment.OnHighlightedItemSelectedListener {

	private static final String POS_KEY = "offset:";
	private static final String IDX_KEY = "index:";

	private ViewSwitcher viewSwitcher;

	private BookView bookView;

	private TextView titleBar;

	private RelativeLayout titleBarLayout;

	private SeekBar progressBar;

	private TextView percentageField;

	private TextView authorField;

	private AnimatedImageView dummyView;

	private TextView pageNumberView;

	private AlertDialog tocDialog;

	private String bookTitle;
	private String titleBase;

	private String fileName;
	private int progressPercentage;
	
	private int currentPageNumber = -1;
    private int totalPages;

    private boolean alreadyLoaded = false;

    private static SearchTask sSearchTask = null;

    private Animation mAnimationIn, mAnimationOut;

    private ActionMode mActionMode;

    // Highlight
    private LinearLayout mHighlightContainer;
    private Button mButtonYellow, mButtonGreen, mButtonBlue, mButtonPink, mButtonWhite;
    private int mHighlightStart, mHighlightEnd;
    private boolean mHighlightContainerIsBeingShowed = false;
    private Animation mHighlightAnimationIn, mHighlightAnimationOut;

    private static enum Orientation {
		HORIZONTAL, VERTICAL
	}

	private Handler uiHandler;
	private Handler backgroundHandler;

    private Configs mConfigs;

	private BroadcastReceiver mReceiver = new ScreenReceiver();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.uiHandler = new Handler();
		HandlerThread bgThread = new HandlerThread("background");
		bgThread.start();
		this.backgroundHandler = new Handler(bgThread.getLooper());

        mAnimationOut = AnimationUtils.loadAnimation(getActivity(), R.anim.out_to_bottom);
        mAnimationIn = AnimationUtils.loadAnimation(getActivity(), R.anim.in_from_bottom);

        mHighlightAnimationIn = AnimationUtils.loadAnimation(getActivity(), R.anim.in_to_top);
        mHighlightAnimationOut = AnimationUtils.loadAnimation(getActivity(), R.anim.out_from_top);

        alreadyLoaded = false;
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_reading, container, false);
	}
	
	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

        bookView = (BookView) getView().findViewById(R.id.bookView);
        pageNumberView = (TextView) getView().findViewById(R.id.pageNumberView);
        dummyView = (AnimatedImageView) getView().findViewById(R.id.dummyView);
        viewSwitcher = (ViewSwitcher) getView().findViewById(R.id.mainContainer);
        authorField = (TextView) getView().findViewById(R.id.authorField);
        percentageField = (TextView) getView().findViewById(R.id.percentageField);
        progressBar  = (SeekBar) getView().findViewById(R.id.titleProgress);
        titleBarLayout = (RelativeLayout) getView().findViewById(R.id.myTitleBarLayout);
        titleBar = (TextView) getView().findViewById(R.id.myTitleBarTextView);

        mHighlightContainer = (LinearLayout) getView().findViewById(R.id.highlight_component);
        mButtonYellow = (Button) getView().findViewById(R.id.button_yellow);
        mButtonBlue = (Button) getView().findViewById(R.id.button_blue);
        mButtonGreen = (Button) getView().findViewById(R.id.button_green);
        mButtonPink = (Button) getView().findViewById(R.id.button_pink);
        mButtonWhite = (Button) getView().findViewById(R.id.button_white);

        Button[] buttons = new Button[] {mButtonYellow, mButtonGreen, mButtonBlue, mButtonPink, mButtonWhite};
        for (Button button : buttons) {
            button.setOnClickListener(this);
        }

        mHighlightContainer.setVisibility(View.GONE);

		this.bookView.init();

		this.progressBar.setFocusable(true);
		this.progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            private int seekValue;

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                bookView.navigateToPercentage(this.seekValue);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar,
                                          int progress, boolean fromUser) {
                if (fromUser) {
                    seekValue = progress;
//                    percentageField.setText(progress + "% ");
                    if (currentPageNumber > 0 && totalPages > 0) {
                        percentageField.setText("Página " + totalPages * progress / 100 + " de " + totalPages + " - " + progress + "%");
                        //displayPageNumber(currentPageNumber);

                    } else {
                        percentageField.setText("" + progress + "%");
                    }
                }
            }
        });

		this.bookView.addListener(this);
		this.bookView.setSpanner(RoboGuice.getInjector(getActivity()).getInstance(
				HtmlSpanner.class));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            setActionMode();
        }
	}

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void setActionMode() {
        ActionMode.Callback callback = new ActionMode.Callback() {

            @Override
            public boolean onCreateActionMode(ActionMode mode, android.view.Menu menu) {
                mActionMode = mode;

                MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.context_reading, menu);

                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, android.view.Menu menu) {
                menu.removeItem(android.R.id.selectAll);
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                int start = bookView.getSelectionStart();
                int end = bookView.getSelectionEnd();

                switch (item.getItemId()) {
                    case R.id.menu_highlight:
                        //String text = bookView.getChildView().getText().subSequence(start, end).toString();
                        mHighlightStart = start;
                        mHighlightEnd = end;

                        toggleHighlightContainer();
//
                        return true;
                    case R.id.menu_share:
                        String title = bookView.getBook().getTitle();
                        String textToShare = "\"" + bookView.getChildView().getText().subSequence(start, end).toString() + "\"";

                        if (bookView.getBook().getMetadata() != null && !bookView.getBook().getMetadata().getAuthors().isEmpty()) {
                            Author author = bookView.getBook().getMetadata().getAuthors().get(0);
                            textToShare = textToShare + "\n\n" + author.getFirstname() + " " + author.getLastname() + " - " + title + "\n\n" + getString(R.string.app_name);
                        }

                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_SUBJECT, "Trecho do livro \"" + title + "\"");
                        intent.putExtra(Intent.EXTRA_TEXT, textToShare);
                        startActivity(intent);
                        return true;
                    default:
                        return false;
                }
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                mActionMode = null;
                mHighlightStart = -1;
                mHighlightEnd = -1;
                if (mHighlightContainerIsBeingShowed) {
                    toggleHighlightContainer();
                }
            }
        };
        this.bookView.getChildView().setCustomSelectionActionModeCallback(callback);
    }

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		DisplayMetrics metrics = new DisplayMetrics();
		getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);

		displayPageNumber(-1); // Initializes the pagenumber view properly

		final GestureDetector gestureDetector = new GestureDetector(getActivity(),
				new NavGestureDetector(bookView, this, metrics));

		View.OnTouchListener gestureListener = new View.OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				return gestureDetector.onTouchEvent(event);
			}
		};
		
		this.viewSwitcher.setOnTouchListener(gestureListener);
		this.bookView.setOnTouchListener(gestureListener);
		
		String file = getActivity().getIntent().getStringExtra("file_name");

		if (file == null && getActivity().getIntent().getData() != null) {
			file = getActivity().getIntent().getData().getPath();
		}

        mConfigs = Configs.getInstance(getActivity(), getActivity().getIntent().getIntExtra("book_id", 0));
        bookView.setHighlights(mConfigs.highlights);

        bookView.setEnableScrolling(false);
		updateFileName(savedInstanceState, file);
		
		if ("".equals(fileName) || ! new File(fileName).exists() ) {
            Utils.showToast(getActivity(), "Livro inválido!");
		} else {

			if (savedInstanceState == null) {
				new DownloadProgressTask().execute();
			} else {
				bookView.restore();
			}

		}
	}

	@Override
	public void onPause() {
        saveReadingPosition();
		getActivity().unregisterReceiver(mReceiver);
        if (sSearchTask != null) {
            sSearchTask.cancel(true);
            sSearchTask = null;
        }
		super.onPause();
	}

	@Override
	public void onResume() {
		registerReceiver();
		super.onResume();
	}

    @Override
    public void onClick(View v) {
        if (mHighlightStart == -1 || mHighlightEnd == -1) return;

        int id = v.getId();
        String color = null;
        switch (id) {
            case R.id.button_yellow:
                color = Highlight.COLOR_YELLOW;
                break;
            case R.id.button_green:
                color = Highlight.COLOR_GREEN;
                break;
            case R.id.button_blue:
                color = Highlight.COLOR_BLUE;
                break;
            case R.id.button_pink:
                color = Highlight.COLOR_PINK;
                break;
            case R.id.button_white:
                color = Highlight.COLOR_WHITE;
                break;
        }
        String chapter = "";
        if (bookView.getIndex() < bookView.getTableOfContents().size()) {
            chapter = bookView.getTableOfContents().get(bookView.getIndex()).getTitle();
        }
        Highlight highlight = new Highlight(bookView.getIndex(), bookView.getPosition() + mHighlightStart, bookView.getPosition() + mHighlightEnd, color, currentPageNumber, chapter, bookView.getSelectedText());
        mConfigs.highlights.add(highlight);
        mConfigs.save();

        if (mActionMode != null) {
            mActionMode.finish();
        }

        toggleHighlightContainer();
        bookView.navigateByHighlight(mConfigs.highlights);
    }

    private void toggleHighlightContainer() {
        if (mHighlightContainerIsBeingShowed) {
            mHighlightContainer.startAnimation(mHighlightAnimationOut);
            mHighlightContainer.setVisibility(View.GONE);
            mHighlightContainerIsBeingShowed = false;
        } else {
            mHighlightContainer.setVisibility(View.VISIBLE);
            mHighlightContainer.startAnimation(mHighlightAnimationIn);
            mHighlightContainerIsBeingShowed = true;
        }
    }

    private void registerReceiver() {
		// initialize receiver
		IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
		filter.addAction(Intent.ACTION_SCREEN_OFF);

		getActivity().registerReceiver(mReceiver, filter);
	}

	private void updateFileName(Bundle savedInstanceState, String fileName) {

		this.fileName = fileName;

		int lastPos = 0;
		int lastIndex = 0;

		if (savedInstanceState != null) {
			lastPos = savedInstanceState.getInt(POS_KEY, lastPos);
			lastIndex = savedInstanceState.getInt(IDX_KEY, lastIndex);
		}

		this.bookView.setFileName(fileName);
		this.bookView.setPosition(lastPos);
		this.bookView.setIndex(lastIndex);
	}	

	@Override
	public void progressUpdate(int progressPercentage, int pageNumber,
			int totalPages) {

		if ( ! isAdded() || getActivity() == null ) {
			return;
		}
		
		this.currentPageNumber = pageNumber;
        this.totalPages = totalPages;
		
		// Work-around for calculation errors and weird values.
		if (progressPercentage < 0 || progressPercentage > 100) {
			return;
		}

		this.progressPercentage = progressPercentage;

		if (pageNumber > 0) {
//			percentageField.setText("" + progressPercentage + "%  "
//					+ pageNumber + " / " + totalPages);
            percentageField.setText("Página " + pageNumber + " de " + totalPages + " - " + progressPercentage + "%");
			displayPageNumber(pageNumber);			

		} else {
			percentageField.setText("" + progressPercentage + "%");			
		}

		this.progressBar.setProgress(progressPercentage);
		this.progressBar.setMax(100);
	}

	private void displayPageNumber(int pageNumber) {

		String pageString;

		if (pageNumber > 0) {
			pageString = Integer.toString(pageNumber) + "\n";
		} else {
			pageString = "\n";
		}

		SpannableStringBuilder builder = new SpannableStringBuilder(pageString);
		builder.setSpan(new CenterSpan(), 0, builder.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

		pageNumberView.setText(builder);
	}

	private void updateFromPrefs() {
		bookView.setTextSize(mConfigs.textSize);

		int marginH = 30;
		int marginV = 25;

		this.bookView.setFontFamily(mConfigs.getFontFamily(mConfigs.fontName));

		bookView.setHorizontalMargin(marginH);
		bookView.setVerticalMargin(marginV);

		if (!isAnimating()) {
			bookView.setEnableScrolling(false);
		}

		bookView.setStripWhiteSpace(false);
		bookView.setLineSpacing(1);
        bookView.setIndex(mConfigs.index);
        bookView.setPosition(mConfigs.position);

		restoreColorProfile();
	}

	public void onWindowFocusChanged(boolean hasFocus) {
		if (hasFocus) {
            if (!alreadyLoaded) {
			    updateFromPrefs();
                alreadyLoaded = true;
            }
		} else {
			getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            saveReadingPosition();
		}
	}

	public boolean onTouchEvent(MotionEvent event) {
		return bookView.onTouchEvent(event);
	}

	@Override
	public void bookOpened(final Book book) {
		
		if ( ! isAdded() || getActivity() == null ) {
			return;
		}

		this.bookTitle = book.getTitle();
		this.titleBase = this.bookTitle;
		getActivity().setTitle(titleBase);
		this.titleBar.setText(titleBase);
		
		getActivity().supportInvalidateOptionsMenu();

		if (book.getMetadata() != null
				&& !book.getMetadata().getAuthors().isEmpty()) {
			Author author = book.getMetadata().getAuthors().get(0);
			this.authorField.setText(author.getFirstname() + " "
					+ author.getLastname());
		}

        initTocDialog();
        updateFromPrefs();

	}

	private void restoreColorProfile() {
		this.bookView.setBackgroundColor(Color.WHITE);
		this.viewSwitcher.setBackgroundColor(Color.WHITE);
		
		this.bookView.setTextColor(Color.BLACK);
		this.bookView.setLinkColor(Color.BLUE);
	}

	private void setScreenBrightnessLevel(int level) {
		WindowManager.LayoutParams lp = getActivity().getWindow().getAttributes();
		lp.screenBrightness = (float) level / 100f;
		getActivity().getWindow().setAttributes(lp);
	}

	@Override
	public void errorOnBookOpening(String errorMessage) {
        Utils.showToast(getActivity(), "Ocorreu um erro ao abrir o livro");
	}

	@Override
	public void parseEntryComplete(int entry, String name) {
		
		if ( ! isAdded() || getActivity() == null ) {
			return;
		}
		
		if (name != null && !name.equals(this.bookTitle)) {
			this.titleBase = this.bookTitle + " - " + name;
		} else {
			this.titleBase = this.bookTitle;
		}
		
		getActivity().setTitle(this.titleBase);
	}

	@Override
    @SuppressWarnings("deprecation")
	public void parseEntryStart(int entry) {	
		
		if ( ! isAdded() || getActivity() == null ) {
			return;
		}
		
		this.viewSwitcher.clearAnimation();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            this.viewSwitcher.setBackground(null);
        } else {
            this.viewSwitcher.setBackgroundDrawable(null);
        }
		restoreColorProfile();
		displayPageNumber(-1); //Clear page number
	}	

	@Override
	public void readingFile() {

	}

	@Override
	public void renderingText() {

	}

	private boolean isAnimating() {
		Animator anim = dummyView.getAnimator();
		return anim != null && !anim.isFinished();
	}

	private void stopAnimating() {

		if (dummyView.getAnimator() != null) {
			dummyView.getAnimator().stop();
			this.dummyView.setAnimator(null);
		}

		if (viewSwitcher.getCurrentView() == this.dummyView) {
			viewSwitcher.showNext();
		}

		this.pageNumberView.setVisibility(View.VISIBLE);
		bookView.setKeepScreenOn(false);
	}

	private Bitmap getBookViewSnapshot() {

		try {
			Bitmap bitmap = Bitmap.createBitmap(viewSwitcher.getWidth(),
					viewSwitcher.getHeight(), Config.ARGB_8888);
			Canvas canvas = new Canvas(bitmap);

			bookView.layout(0, 0, viewSwitcher.getWidth(),
					viewSwitcher.getHeight());

			bookView.draw(canvas);

            /**
             * FIXME: creating an intermediate bitmap here because I can't
             * figure out how to draw the pageNumberView directly on the
             * canvas and have it show up in the right place.
             */

            Bitmap pageNumberBitmap = Bitmap.createBitmap(
                    pageNumberView.getWidth(), pageNumberView.getHeight(),
                    Config.ARGB_8888);
            Canvas pageNumberCanvas = new Canvas(pageNumberBitmap);

            pageNumberView.layout(0, 0, pageNumberView.getWidth(),
                    pageNumberView.getHeight());
            pageNumberView.draw(pageNumberCanvas);

            canvas.drawBitmap(pageNumberBitmap, 0, viewSwitcher.getHeight()
                    - pageNumberView.getHeight(), new Paint());

            pageNumberBitmap.recycle();

			return bitmap;
		} catch (OutOfMemoryError out) {
			viewSwitcher.setBackgroundColor(getResources().getColor(android.R.color.background_light));
            out.printStackTrace();
		}

		return null;
	}

	private void prepareSlide(Animation inAnim, Animation outAnim) {

		Bitmap bitmap = getBookViewSnapshot();
		dummyView.setImageBitmap(bitmap);

		this.pageNumberView.setVisibility(View.GONE);

		inAnim.setAnimationListener(new Animation.AnimationListener() {

			public void onAnimationStart(Animation animation) {}

			public void onAnimationRepeat(Animation animation) {}

			@Override
			public void onAnimationEnd(Animation animation) {
				onSlideFinished();
			}
		});
		
		viewSwitcher.layout(0, 0, viewSwitcher.getWidth(),
				viewSwitcher.getHeight());
		dummyView.layout(0, 0, viewSwitcher.getWidth(),
				viewSwitcher.getHeight());

		this.viewSwitcher.showNext();

		this.viewSwitcher.setInAnimation(inAnim);
		this.viewSwitcher.setOutAnimation(outAnim);
	}

	private void onSlideFinished() {
		if ( currentPageNumber > 0 ) {
			this.pageNumberView.setVisibility(View.VISIBLE);
		}
	}

	private void pageDown(Orientation o) {
		if (bookView.isAtEnd()) {
			return;
		}

		stopAnimating();

		if (o == Orientation.HORIZONTAL) {
            prepareSlide(Animations.inFromRightAnimation(),
                    Animations.outToLeftAnimation());
            viewSwitcher.showNext();
            bookView.pageDown();

		} else {
			if (false) {
				prepareSlide(Animations.inFromBottomAnimation(),
						Animations.outToTopAnimation());
				viewSwitcher.showNext();
			}

			bookView.pageDown();
		}
        saveReadingPosition();

	}

	private void pageUp(Orientation o) {
		if (bookView.isAtStart()) {
			return;
		}

		stopAnimating();

		if (o == Orientation.HORIZONTAL) {
            prepareSlide(Animations.inFromLeftAnimation(),
                    Animations.outToRightAnimation());
            viewSwitcher.showNext();
            bookView.pageUp();

		} else {

			if (false) {
				prepareSlide(Animations.inFromTopAnimation(),
						Animations.outToBottomAnimation());
				viewSwitcher.showNext();
			}

			bookView.pageUp();
		}
        saveReadingPosition();
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {

		if (this.tocDialog == null) {
			initTocDialog();
		}
	}

	private void saveReadingPosition() {
		if (this.bookView != null) {

			int index = this.bookView.getIndex();
			int position = this.bookView.getPosition();
			
			if ( index != -1 && position != -1 ) {
                mConfigs.index = index;
                mConfigs.position = position;
			
				mConfigs.save();
			}
		}

	}

	@Override
	public boolean onSwipeDown() {
		return false;
	}

	@Override
	public boolean onSwipeUp() {
		return false;
	}

	@Override
	public void onScreenTap() {
        if (mActionMode != null) {
            return;
        }

		stopAnimating();

		if (this.titleBarLayout.getVisibility() == View.VISIBLE) {
            dismissActionBar();
		} else {
            titleBarLayout.startAnimation(mAnimationIn);
			titleBarLayout.setVisibility(View.VISIBLE);

			getSherlockActivity().getSupportActionBar().show();
			getActivity().getWindow().addFlags(
					WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
			getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
	}

    private void dismissActionBar() {
        titleBarLayout.startAnimation(mAnimationOut);
        titleBarLayout.setVisibility(View.GONE);
        getSherlockActivity().getSupportActionBar().hide();
        ((ReadingActivity) getActivity()).dismissContainer();
    }

	@Override
	public boolean onSwipeLeft() {

		if (mActionMode == null) {
            if (this.titleBarLayout.getVisibility() == View.VISIBLE) dismissActionBar();
			pageDown(Orientation.HORIZONTAL);
			return true;
		}

		return false;
	}

	@Override
	public boolean onSwipeRight() {

		if (mActionMode == null) {
            if (this.titleBarLayout.getVisibility() == View.VISIBLE) dismissActionBar();
			pageUp(Orientation.HORIZONTAL);
			return true;
		}

		return false;
	}

	@Override
	public boolean onTapLeftEdge() {
        if (mActionMode == null) {
            if (this.titleBarLayout.getVisibility() == View.VISIBLE) dismissActionBar();
            pageUp(Orientation.HORIZONTAL);
            return true;
        }

        return false;
	}

	@Override
	public boolean onTapRightEdge() {
        if (mActionMode == null) {
            if (this.titleBarLayout.getVisibility() == View.VISIBLE) dismissActionBar();
            pageDown(Orientation.HORIZONTAL);
            return true;
        }

        return false;
	}

	@Override
	public boolean onTapTopEdge() {
        onScreenTap();
        return true;
	}

	@Override
	public boolean onTapBottomEdge() {
        onScreenTap();
        return true;
	}

	@Override
	public boolean onLeftEdgeSlide(int value) {
		return false;
	}

	@Override
	public boolean onRightEdgeSlide(int value) {
		return false;
	}

	@Override
	public void onWordLongPressed(CharSequence word) {
	}

	public void initTocDialog() {

		if (this.tocDialog != null) {
			return;
		}

		final List<BookView.TocEntry> tocList = this.bookView
				.getTableOfContents();

		if (tocList == null || tocList.isEmpty()) {
			return;
		}

		final CharSequence[] items = new CharSequence[tocList.size()];

		for (int i = 0; i < items.length; i++) {
			items[i] = tocList.get(i).getTitle();
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle("Índice");

		builder.setItems(items, new OnClickListener() {
			public void onClick(DialogInterface dialog, int item) {
				bookView.navigateTo(tocList.get(item).getHref());

                titleBarLayout.setVisibility(View.GONE);
                getSherlockActivity().getSupportActionBar().hide();
			}
		});

		this.tocDialog = builder.create();
		this.tocDialog.setOwnerActivity(getActivity());
	}

    public void showTocDialog() {
        this.tocDialog.show();
    }

	@Override
	public void onSaveInstanceState(final Bundle outState) {
		if (this.bookView != null) {
			outState.putInt(POS_KEY, this.bookView.getPosition());
			outState.putInt(IDX_KEY, this.bookView.getIndex());
		}

	}

    private class DownloadProgressTask extends
            AsyncTask<Void, Integer, Void> {

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected Void doInBackground(Void... params) {

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {

            bookView.restore();
        }
    }

    @Override
    public void onBrightChanged(int bright) {
        if (bright > 0) setScreenBrightnessLevel(bright);
    }

    @Override
    public void onFontSizeChanged(int fontSize) {
        fontSize = mConfigs.textSize + fontSize;
        if (fontSize > 32 || fontSize < 12) {
            return;
        }
        mConfigs.textSize = fontSize;
        bookView.setTextSize(mConfigs.textSize);
        saveReadingPosition();
        bookView.navigateByHighlight(mConfigs.highlights);
    }

    @Override
    public void onFontFaceChanged(String font) {
        if (!font.equals(mConfigs.fontName)) {
            mConfigs.fontName = font;
            bookView.setFontFamily(mConfigs.getFontFamily(mConfigs.fontName));
            saveReadingPosition();
            bookView.navigateByHighlight(mConfigs.highlights);
        }
    }

    public void performSearch(final String searchText) {
        if (sSearchTask != null) {
            sSearchTask.cancel(true);
            sSearchTask = null;
        }
        sSearchTask = new SearchTask();
        sSearchTask.execute(searchText);
    }

    @Override
    public void onSearchResultSelected(List<SearchResult> results, int position) {
        ((ReadingActivity) getActivity()).dismissContainer();
        bookView.navigateBySearchResult(results, position);
    }

    @Override
    public void onHighlightedItemSelected(List<Highlight> highlights, int position) {
        ((ReadingActivity) getActivity()).dismissContainer();
        bookView.navigateByHighlight(highlights, position);
    }

    private static class DummyHandler extends TagNodeHandler {
//        @Override
//        public void handleTagNode(TagNode node, SpannableStringBuilder builder,
//                                  int start, int end) {
//
//            builder.append("\uFFFC");
//        }

		@Override
		public void handleTagNode(TagNode node, SpannableStringBuilder builder,
				int start, int end, SpanStack spanStack) {
			// TODO Auto-generated method stub
			 builder.append("\uFFFC");
		}
        
    }

    private class SearchTask extends AsyncTask<String, Void, List<SearchResult>> {

        @Override
        protected void onPreExecute() {
            Utils.dismissKeyboard(getActivity());
        }

        @Override
        protected List<SearchResult> doInBackground(String... params) {
            String searchText = params[0];

            HtmlSpanner spanner = new HtmlSpanner();
            DummyHandler dummy = new DummyHandler();

            spanner.registerHandler("img", dummy);
            spanner.registerHandler("image", dummy);

            spanner.registerHandler("table", new TableHandler());


            Pattern pattern = Pattern.compile(Pattern.quote(searchText), Pattern.CASE_INSENSITIVE);
            List<SearchResult> results = new ArrayList<SearchResult>();

            try {
                PageTurnerSpine spine = new PageTurnerSpine(bookView.getBook());

                for ( int index=0; index < spine.size(); index++ ) {

                    spine.navigateByIndex(index);

                    Spanned spanned = spanner.fromHtml(spine.getCurrentResource().getReader());
                    Matcher matcher = pattern.matcher(spanned);

                    while (matcher.find()) {
                        int from = Math.max(0, matcher.start() - 20);
                        int to = Math.min(spanned.length() -1, matcher.end() + 20);

                        if (isCancelled()) {
                            return null;
                        }

                        String text = "…" + spanned.subSequence(from, to).toString().trim() + "…";
                        String chapter = "";
                        if (index < bookView.getTableOfContents().size()) {
                            chapter = bookView.getTableOfContents().get(index).getTitle();
                        }

                        int pageNumber = bookView.getSpine().getPageNumberFor(index, matcher.start());

                        SearchResult res = new SearchResult(chapter, text, pageNumber, index, matcher.start(), matcher.end());
                        results.add(res);
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }

            return results;
        }

        @Override
        protected void onPostExecute(List<SearchResult> results) {
            ((ReadingActivity) getActivity()).showSearchResults(results);
            sSearchTask = null;
        }
    }
}

class ScreenReceiver extends BroadcastReceiver {

	public static boolean wasScreenOn = true;

	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
			// do whatever you need to do here
			wasScreenOn = false;
		} else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
			// and do whatever you need to do here
			wasScreenOn = true;
		}
	}

}
