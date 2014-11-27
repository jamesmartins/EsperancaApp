package br.com.cpb.esperanca.fragment;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import br.com.cpb.esperanca.activity.LibraryActivity;
import br.com.cpb.esperanca.activity.ReadingActivity;
import br.com.cpb.esperanca.app.Reader;
import br.com.cpb.esperanca.app.ReaderAPI;
import br.com.cpb.esperanca.iab.IabHelper;
import br.com.cpb.esperanca.iab.IabResult;
import br.com.cpb.esperanca.iab.Inventory;
import br.com.cpb.esperanca.iab.Purchase;
import br.com.cpb.esperanca.model.Book;
import br.com.cpb.esperanca.model.Category;
import br.com.cpb.esperanca.service.DownloadBookService;
import br.com.cpb.esperanca.util.Utils;

import com.github.rtyley.android.sherlock.roboguice.fragment.RoboSherlockDialogFragment;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.nostra13.universalimageloader.core.ImageLoader;

import br.com.cpb.esperanca.R;
import roboguice.inject.InjectView;

import java.util.ArrayList;

import org.apache.http.Header;

/**
 * Created with IntelliJ IDEA.
 * User: angelocastelanjr
 * Date: 2/6/13
 * Time: 9:38 AM
 * To change this template use File | Settings | File Templates.
 */
public class BookDetailsFragment extends RoboSherlockDialogFragment implements IabHelper.QueryInventoryFinishedListener, IabHelper.OnIabPurchaseFinishedListener {

    private enum ButtonAction {FIRST_ACTION, SECOND_ACTION, OWNED_BOOK_ACTION};
    private ButtonAction mButtonAction = ButtonAction.FIRST_ACTION;

    private static Book mBook;
    private static ImageLoader mImageLoader;
    private Reader mReader;
    private String mOrderId;

    @InjectView(R.id.image_cover)
    private ImageView mImageCover;

    @InjectView(R.id.text_title)
    private TextView mTextTitle;

    @InjectView(R.id.text_author)
    private TextView mTextAuthor;

    @InjectView(R.id.text_category)
    private TextView mTextCategory;

    @InjectView(R.id.text_page_number)
    private TextView mTextPageNumber;

    @InjectView(R.id.text_ISBN)
    private TextView mTextISBN;

    @InjectView(R.id.button_buy)
    private Button mButtonBuy;

    @InjectView(R.id.text_description)
    private TextView mTextDescription;

    private boolean mIsTablet, mAPI16, mOwnedBook = false;

    public static BookDetailsFragment newInstance(Book book, ImageLoader imageLoader) {
        BookDetailsFragment fragment = new BookDetailsFragment();
        mBook = book;
        mImageLoader = imageLoader;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAPI16 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
        mReader = Reader.getInstance(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_book_details, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        new AsyncTask<Void, Void, Void>() {
            Category category;

            @Override
            protected void onPreExecute() {
                if (mIsTablet = getResources().getBoolean(R.bool.is_tablet)) getDialog().setTitle("Detalhes do livro: " + mBook.title);

                mTextTitle.setText(mBook.title);
                mTextAuthor.setText(mBook.author);
                mButtonBuy.setText("Aguarde...");
                mButtonBuy.setEnabled(false);
            }

            @Override
            protected Void doInBackground(Void... params) {
                category = Reader.getInstance(getActivity()).getCategoryFromBook(mBook);

                if (getActivity() == null) return null;

                for (Book ownedBook : Reader.getInstance(getActivity()).getOwnedBooks()) {
                    if (ownedBook.id == mBook.id) {
                        mOwnedBook = true;
                        break;
                    }
                }

                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {

                if (getActivity() == null) return;

                if (category != null) {
                    mTextCategory.setText(category.title);
                }

                mImageLoader.displayImage(ReaderAPI.getAbsoluteUrl(mBook.cover_url), mImageCover);

                mTextPageNumber.setText("Número de páginas: " + mBook.pages_number);
                mTextISBN.setText("ISBN: " + mBook.isbn);
                mTextDescription.setText(mBook.description);
                if (mIsTablet) {
                    mTextDescription.setMovementMethod(ScrollingMovementMethod.getInstance());
                }

                if (mOwnedBook) {
                    mButtonAction = ButtonAction.OWNED_BOOK_ACTION;
                    mButtonBuy.setEnabled(true);
                    mButtonBuy.setText("Abrir");
                } else {
                    mButtonAction = ButtonAction.FIRST_ACTION;

                    if (isBookFree()) {
                        mButtonBuy.setEnabled(true);
                        mButtonBuy.setText("Grátis");
                    } else {
                        if (mReader.isInAppBillingEnabled()) {
                            mReader.checkifBookIsAvailable(mBook.getSku(), BookDetailsFragment.this);
                        } else {
                            Utils.showIABDisabledDialog(getActivity());
                        }
                    }

                }

                mButtonBuy.setOnClickListener(new View.OnClickListener() {

                    @SuppressWarnings("deprecation")
                    @Override
                    public void onClick(View view) {
                        switch (mButtonAction) {
                            case FIRST_ACTION:
                                if (mAPI16) {
                                    mButtonBuy.setBackground(getResources().getDrawable(R.drawable.btn_holo_green));
                                } else {
                                    mButtonBuy.setBackgroundDrawable(getResources().getDrawable(R.drawable.btn_holo_green));
                                }
                                mButtonBuy.setText("Comprar");
                                mButtonBuy.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                                    @Override
                                    public void onFocusChange(View v, boolean hasFocus) {
                                        if (!hasFocus) {
                                            mButtonBuy.setBackground(getResources().getDrawable(R.drawable.btn_holo_blue));
                                            if (mBook.price.startsWith("0.0")) {
                                                mButtonBuy.setText("Grátis");
                                            } else {
                                                mButtonBuy.setText("$" + mBook.price);
                                            }
                                            mButtonAction = ButtonAction.FIRST_ACTION;
                                        }
                                    }
                                });
                                mButtonAction = ButtonAction.SECOND_ACTION;
                                break;
                            case SECOND_ACTION:
                                 
                            	buyBook();//Routine for to buy and download book selected
                            	//
                                if (mAPI16) {
                                    mButtonBuy.setBackground(getResources().getDrawable(R.drawable.btn_holo_blue));
                                } else {
                                    mButtonBuy.setBackgroundDrawable(getResources().getDrawable(R.drawable.btn_holo_blue));
                                }
                                if (mBook.price.startsWith("0.0")) {
                                    mButtonBuy.setText("Grátis");
                                } else {
                                    mButtonBuy.setText("$" + mBook.price);
                                }
                                mButtonAction = ButtonAction.FIRST_ACTION;
                                break;

                            case OWNED_BOOK_ACTION:
                                Intent intent = new Intent(getActivity(), ReadingActivity.class);
                                intent.putExtra("file_name", mBook.getPath(getActivity()));
                                intent.putExtra("book_id", mBook.id);
                                startActivity(intent);
                                break;

                        }
                    }
                });
                ScrollView scrollView = (ScrollView) getView().findViewById(R.id.scroll_book);
                if (scrollView != null) {
                    //scrollView.fullScroll(ScrollView.FOCUS_UP);
                    scrollView.smoothScrollTo(0, 0);
                }
            }
        }.execute();
    }

    @Override
    public void onDestroyView() {
        if (getDialog() != null && getRetainInstance())
            getDialog().setOnDismissListener(null);
        super.onDestroyView();
    }

    @Override
    public void onQueryInventoryFinished(IabResult result, Inventory inv) {
        mButtonBuy.setText("$" + mBook.price);
        mButtonBuy.setEnabled(true);
        if (!result.isFailure() && inv.hasPurchase(mBook.getSku())) {
            Purchase purchase = inv.getPurchase(mBook.getSku());
            mOrderId = purchase.getOrderId();
            mOwnedBook = true;
        }
    }

    @Override
    public void onIabPurchaseFinished(IabResult result, final Purchase info) {
        if (result.isFailure()) {
            if (result.getResponse() == IabHelper.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle("Item comprado anteriormente");
                builder.setMessage("Você já comprou este livro anteriormente. Realizando download novamente");
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String orderId = null;
                        if (info != null) orderId = info.getOrderId();
                        downloadBook(orderId);
                        dialog.dismiss();
                    }
                });
                builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        String orderId = null;
                        if (info != null) orderId = info.getOrderId();
                        downloadBook(orderId);
                    }
                });
                builder.show();
            } else {
                if (getActivity() != null) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    builder.setTitle("Compra não concluída");
                    builder.setMessage("Não foi possível comprar o livro \"" + mBook.title);
                    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    builder.show();
                }
            }

        } else {
            if (info.getSku().equals(mBook.getSku())) {
                final ProgressDialog dialog = ProgressDialog.show(getActivity(), "Aguarde", "Validando compra", true, false);

                AsyncHttpResponseHandler handler = new AsyncHttpResponseHandler() {
                    @Override
                    public void onSuccess(int i, String s) {
                        dialog.dismiss();
                        downloadBook(info.getOrderId());
                    }

                    @Override
                    public void onFailure(Throwable throwable, String s) {
                        dialog.dismiss();
                        Utils.showToast(getActivity(), "Não foi possível validar a compra.");
                    }
	
                };

                mReader.validatePurchase(mBook, info, handler);

            } else {
                Log.d("BookDetailsFragment", "Sku different from book????" + result + " - " + info);
                Utils.showToast(getActivity(), "Problemas na transiçãoo");
            }
        }
    }

    public void buyBook() {
        if (!isBookFree() && mOwnedBook) {

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Já comprado");
            builder.setMessage("Voçê já comprou esse livro anteriormente, deseja baixá-lo agora?");
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    downloadBook(mOrderId);
                    dialog.dismiss();
                }
            });
            builder.setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            builder.show();
            return;

        }
        if (isBookFree()) {
            downloadBook(null);
        } else {
            if (mReader.isInAppBillingEnabled()) {
                mReader.purchaseBook(getActivity(), mBook, this);
            } else {
                Utils.showIABDisabledDialog(getActivity());
            }
        }
    }

    private void downloadBook(String orderId) {
        if (mReader.getBooksBeingDownloaded() == null) 
        	mReader.setBooksBeingDownloaded(new ArrayList<Book>());
        mReader.getBooksBeingDownloaded().add(mBook);

        Intent serviceIntent = new Intent(getActivity(), DownloadBookService.class);
        serviceIntent.putExtra(DownloadBookService.EXTRA_BOOK_ID, mBook.id);
        serviceIntent.putExtra(DownloadBookService.EXTRA_BOOK_URL, mBook.issue_url);
        if (orderId != null) {
            serviceIntent.putExtra(DownloadBookService.EXTRA_ORDER_ID, orderId);
        }

        getActivity().startService(serviceIntent);

        Intent intent = new Intent(getActivity(), LibraryActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        getActivity().overridePendingTransition(R.anim.grow_from_middle, R.anim.shrink_to_middle);
    }

    private boolean isBookFree() {
        Double price = Double.valueOf(mBook.price);
        return price == 0;
    }
}
